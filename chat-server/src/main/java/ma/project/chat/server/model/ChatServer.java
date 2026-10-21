package ma.project.chat.server.model;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;

/**
 * The chat server: accepts TCP connections and relays messages between users.
 *
 * <p>Threading model: one virtual thread runs the accept loop, and every accepted client gets its own
 * virtual thread running a {@link ClientHandler}. Virtual threads are cheap (a few hundred bytes of
 * stack until they need more), so a thread per client scales to thousands of users.
 *
 * <p>Shared state: the map of connected users and the act of broadcasting are both guarded by one lock,
 * {@code chatLock}. Holding the same lock while broadcasting guarantees that every client receives the
 * messages in the same order.
 *
 * <p>This class has no UI code at all: it reports events through {@link ServerListener}.
 */
public class ChatServer {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final ServerConfig config;

    /** Connected users by lower-case name; insertion order = join order. Guarded by chatLock. */
    private final Map<String, ClientHandler> clients = new LinkedHashMap<>();
    private final Object chatLock = new Object();

    /** Thread-safe list: listeners can be added from any thread while events are being fired. */
    private final List<ServerListener> listeners = new CopyOnWriteArrayList<>();

    private int guestCounter; // guarded by chatLock

    // Written by start()/stop(), read by network threads: volatile makes the change visible to them.
    private volatile boolean running;
    private volatile ServerSocket serverSocket;
    private ExecutorService executor;
    private CountDownLatch stopped = new CountDownLatch(0);

    public ChatServer(ServerConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public void addListener(ServerListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeListener(ServerListener listener) {
        listeners.remove(listener);
    }

    public ServerConfig getConfig() {
        return config;
    }

    public boolean isRunning() {
        return running;
    }

    /** The port actually listened on (useful when the config asked for port 0), or -1 when stopped. */
    public int getPort() {
        ServerSocket socket = serverSocket;
        return socket == null ? -1 : socket.getLocalPort();
    }

    /**
     * Opens the listening socket and starts the accept loop in the background, then returns at once.
     *
     * @throws IOException if the port cannot be opened (typically: already in use)
     */
    public synchronized void start() throws IOException {
        if (running) {
            throw new IllegalStateException("Server is already running");
        }
        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true); // allows an immediate restart on the same port
        socket.bind(new InetSocketAddress(config.ip(), config.port()));

        serverSocket = socket;
        executor = Executors.newVirtualThreadPerTaskExecutor();
        stopped = new CountDownLatch(1);
        running = true;

        log("Server started on " + config.ip() + ":" + getPort() + " (settings from " + config.source() + ")");
        executor.submit(this::acceptLoop);
    }

    /**
     * Stops the server: no new connections, every client gets BYE and is disconnected,
     * and the method waits until all client threads have finished. Calling it twice is harmless.
     */
    public void stop() {
        synchronized (this) {
            if (!running) {
                return;
            }
            running = false;
        }
        closeQuietly(serverSocket); // makes the blocked accept() throw, which ends the accept loop

        List<ClientHandler> toClose;
        synchronized (chatLock) {
            broadcastLocked(Protocol.line(Protocol.BYE, "Server is shutting down"));
            toClose = new ArrayList<>(clients.values());
        }
        toClose.forEach(ClientHandler::close); // makes each blocked readLine() fail, ending each handler

        executor.close(); // Java 19+: waits until every submitted task has finished
        serverSocket = null;
        log("Server stopped");
        listeners.forEach(ServerListener::onServerStopped);
        stopped.countDown();
    }

    /** Blocks the calling thread until {@link #stop()} has completed. */
    public void awaitStop() throws InterruptedException {
        stopped.await();
    }

    /** Snapshot of the connected users, in join order. */
    public List<ConnectedUser> activeUsers() {
        synchronized (chatLock) {
            return clients.values().stream().map(ClientHandler::user).toList();
        }
    }

    // ------------------------------------------------------------------ accept loop

    private void acceptLoop() {
        while (running) {
            log("Waiting for client...");
            Socket socket;
            try {
                socket = serverSocket.accept(); // blocks (cheaply: it's a virtual thread) until a client connects
            } catch (IOException e) {
                if (running) {
                    log("Could not accept a connection: " + e.getMessage());
                }
                continue; // after stop(), 'running' is false and the loop ends
            }
            try {
                executor.submit(new ClientHandler(socket, this)); // one new virtual thread per client
            } catch (IOException | RejectedExecutionException e) {
                closeQuietly(socket); // streams failed, or stop() raced with this accept
            }
        }
    }

    // ------------------------------------------------------------------ called by ClientHandler

    /**
     * Registers a new client under a unique name, sends it WELCOME and announces it to everyone.
     * Returns null if the server is stopping.
     */
    ConnectedUser join(ClientHandler handler, String requestedName, String address) {
        ConnectedUser user;
        synchronized (chatLock) {
            if (!running) {
                handler.send(Protocol.line(Protocol.BYE, "Server is shutting down"));
                return null;
            }
            String base = Protocol.sanitizeName(requestedName);
            boolean readOnly = base.isEmpty();
            String name = readOnly ? nextGuestName() : base;
            int suffix = 2;
            while (clients.containsKey(key(name))) { // name taken: Sara -> Sara#2 -> Sara#3 ...
                name = readOnly ? nextGuestName() : base + "#" + suffix++;
            }
            user = new ConnectedUser(name, readOnly, address, Instant.now());
            handler.attach(user);
            clients.put(key(name), handler);

            // Both sends happen while holding chatLock, so WELCOME is guaranteed to be this client's
            // first line: no other broadcast can slip in between.
            handler.send(Protocol.line(Protocol.WELCOME, name, user.mode()));
            broadcastLocked(Protocol.line(Protocol.INFO, now(),
                    name + " joined the chat" + (readOnly ? " (read-only)" : "")));
        }
        log("Welcome " + user.name() + (user.readOnly() ? " (READ-ONLY MODE)" : "") + " from " + address);
        listeners.forEach(l -> l.onUserJoined(user));
        return user;
    }

    /** Stamps a chat message with the time and sends it to every connected client, sender included. */
    void chat(ConnectedUser from, String text) {
        String message = Protocol.limitMessage(text);
        String time;
        synchronized (chatLock) {
            time = now();
            broadcastLocked(Protocol.line(Protocol.MSG, time, from.name(), message));
        }
        listeners.forEach(l -> l.onChatMessage(from.name(), message, time));
    }

    /** Removes a client and, unless the whole server is stopping, tells the others. */
    void leave(ClientHandler handler, ConnectedUser user) {
        boolean removed;
        synchronized (chatLock) {
            removed = clients.remove(key(user.name()), handler);
            if (removed && running) {
                broadcastLocked(Protocol.line(Protocol.INFO, now(), user.name() + " left the chat"));
            }
        }
        if (removed) {
            log(user.name() + " left the chat");
            listeners.forEach(l -> l.onUserLeft(user));
        }
    }

    /** The answer to "allUsers": USERS|Sara:FULL,Guest#1:READ_ONLY */
    String usersLine() {
        String list = activeUsers().stream()
                .map(u -> u.name() + ":" + u.mode())
                .collect(Collectors.joining(","));
        return Protocol.line(Protocol.USERS, list);
    }

    // ------------------------------------------------------------------ helpers

    /** Sends one line to every client. Caller MUST hold chatLock (hence the name). */
    private void broadcastLocked(String line) {
        for (ClientHandler client : clients.values()) {
            client.send(line);
        }
    }

    private String nextGuestName() {
        return "Guest#" + (++guestCounter);
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT); // "Sara" and "sara" count as the same name
    }

    private static String now() {
        return LocalTime.now().format(TIME);
    }

    private void log(String message) {
        listeners.forEach(l -> l.onLog(message));
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // nothing useful to do: we are closing anyway
        }
    }
}
