package ma.project.chat.client.model;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The client side of the chat: one TCP connection to the server.
 *
 * <p>Two threads touch it:
 * <ul>
 *   <li>the caller (console or JavaFX code) calls {@link #connect}, {@link #send}, {@link #disconnect};</li>
 *   <li>one background virtual thread, the reader, blocks in {@code readLine()} and turns every
 *       incoming line into a {@link ClientListener} callback.</li>
 * </ul>
 * This split is what keeps a GUI responsive: the window never waits for the network.
 *
 * <p>No UI code here: everything is reported through {@link ClientListener}.
 */
public class ChatClient {

    /** Give up connecting after this long instead of hanging when the server is unreachable. */
    static final int CONNECT_TIMEOUT_MS = 5_000;
    /** How long we wait for WELCOME after connecting. */
    static final int HANDSHAKE_TIMEOUT_MS = 5_000;
    /** After sending "bye", how long we wait for the server's BYE before closing anyway. */
    static final int GOODBYE_TIMEOUT_MS = 2_000;

    private final ClientConfig config;
    private final List<ClientListener> listeners = new CopyOnWriteArrayList<>();

    // The current connection. volatile: background threads compare against it (see finish).
    private volatile Socket socket;
    private volatile PrintWriter out;

    // Read by the caller thread, written by the reader thread (and vice versa): volatile.
    private volatile boolean connected;
    private volatile String username;
    private volatile boolean readOnly;

    /** Guarantees onDisconnected fires once, whichever thread notices the end first. */
    private final AtomicBoolean finished = new AtomicBoolean(true);

    public ChatClient(ClientConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public void addListener(ClientListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeListener(ClientListener listener) {
        listeners.remove(listener);
    }

    public ClientConfig getConfig() {
        return config;
    }

    public boolean isConnected() {
        return connected;
    }

    /** Name assigned by the server (may be "Sara#2" or "Guest#1"); null before connecting. */
    public String getUsername() {
        return username;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    /**
     * Opens the connection and performs the handshake. BLOCKS for up to ~10 s: a GUI must call it from
     * a background thread, never from the JavaFX Application Thread.
     *
     * @param requestedName the name typed by the user; empty or blank = READ-ONLY MODE
     * @throws IOException if the server cannot be reached or does not answer the handshake
     */
    public synchronized void connect(String requestedName) throws IOException {
        if (connected) {
            throw new IllegalStateException("Already connected");
        }
        Socket s = new Socket();
        BufferedReader reader;
        try {
            s.connect(new InetSocketAddress(config.host(), config.port()), CONNECT_TIMEOUT_MS);
            reader = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter writer = new PrintWriter(new BufferedWriter(
                    new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8)), true);

            writer.println(requestedName == null ? "" : requestedName.strip()); // handshake line 1

            s.setSoTimeout(HANDSHAKE_TIMEOUT_MS); // don't wait forever for WELCOME
            String first = reader.readLine();
            s.setSoTimeout(0);                    // from now on, wait for messages without limit

            ServerEvent event = ServerEvent.parse(first);
            if (!(event instanceof ServerEvent.Welcome welcome)) {
                throw new IOException(first == null
                        ? "The server closed the connection during the handshake"
                        : "Unexpected answer from the server: " + first);
            }

            socket = s;
            out = writer;
            username = welcome.name();
            readOnly = welcome.readOnly();
            connected = true;
            finished.set(false);
        } catch (SocketTimeoutException e) {
            closeQuietly(s);
            throw new IOException("No answer from " + config.host() + ":" + config.port()
                    + " within " + (CONNECT_TIMEOUT_MS / 1000) + " s", e);
        } catch (IOException e) {
            closeQuietly(s);
            throw e;
        }

        listeners.forEach(l -> l.onConnected(username, readOnly)); // the view learns our name first...
        Socket current = socket;
        Thread.ofVirtual().name("chat-reader").start(() -> readLoop(current, reader)); // ...then messages flow
    }

    /**
     * Handles whatever the user typed, exactly like the server expects it.
     * <ul>
     *   <li>blank: ignored;</li>
     *   <li>end / bye: polite disconnect;</li>
     *   <li>allUsers: asks the server for the user list (allowed in read-only mode);</li>
     *   <li>anything else: a chat message, refused locally in READ-ONLY MODE.</li>
     * </ul>
     */
    public void send(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (!connected) {
            notifyError("Not connected to the server");
            return;
        }
        if (Protocol.isQuit(text)) {
            disconnect();
        } else if (Protocol.isAllUsers(text)) {
            requestUserList();
        } else if (readOnly) {
            // The UI should already prevent this; the model refuses too (and so does the server).
            notifyError("You are in READ-ONLY MODE: reconnect with a username to send messages");
        } else {
            writeLine(text);
        }
    }

    /** Asks the server who is online; the answer arrives later through onUserList. */
    public void requestUserList() {
        if (connected) {
            writeLine(Protocol.CMD_ALL_USERS);
        }
    }

    /**
     * Polite disconnect: tells the server "bye" and returns at once. The reader thread receives the
     * server's BYE and closes; if the server stays silent, a watchdog closes after 2 s.
     * Safe to call from the JavaFX thread and safe to call twice.
     */
    public void disconnect() {
        if (!connected) {
            return;
        }
        Socket leaving = socket; // the watchdog may only close THIS connection, not a newer one
        writeLine(Protocol.CMD_BYE);
        Thread.ofVirtual().name("chat-goodbye-watchdog").start(() -> {
            try {
                Thread.sleep(GOODBYE_TIMEOUT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            finish(leaving, "Disconnected");
        });
    }

    // ------------------------------------------------------------------ reader thread

    /** Runs on the reader thread for the lifetime of ONE connection ({@code owner}). */
    private void readLoop(Socket owner, BufferedReader reader) {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                ServerEvent event = ServerEvent.parse(line);
                if (event instanceof ServerEvent.Bye bye) {
                    finish(owner, bye.reason().isBlank() ? "Disconnected by the server" : bye.reason());
                    return;
                }
                dispatch(event);
            }
            finish(owner, "Connection lost: the server closed the connection");
        } catch (IOException e) {
            finish(owner, "Connection lost: " + e.getMessage()); // no-op if we closed it ourselves
        }
    }

    /** Turns a typed event into the matching callback. The compiler checks every case is handled. */
    private void dispatch(ServerEvent event) {
        switch (event) {
            case ServerEvent.Chat c -> {
                boolean fromMe = c.sender().equals(username);
                listeners.forEach(l -> l.onChatMessage(c.time(), c.sender(), c.text(), fromMe));
            }
            case ServerEvent.Info i -> listeners.forEach(l -> l.onInfo(i.time(), i.text()));
            case ServerEvent.Users u -> listeners.forEach(l -> l.onUserList(u.users()));
            case ServerEvent.ServerError e -> notifyError(e.message());
            case ServerEvent.Welcome w -> { /* only valid during the handshake: ignore */ }
            case ServerEvent.Bye b -> { /* handled in readLoop */ }
            case ServerEvent.Unknown u -> { /* unknown line from a newer server: ignore, don't crash */ }
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Closes the connection {@code owner} and fires onDisconnected, exactly once per connection.
     * A thread belonging to an older connection (owner != socket) can never close a newer one.
     */
    private void finish(Socket owner, String reason) {
        if (owner != socket) {
            return; // stale thread from a previous connection
        }
        if (!finished.compareAndSet(false, true)) {
            return; // someone else (reader, watchdog) already finished this connection
        }
        connected = false;
        closeQuietly(owner);
        listeners.forEach(l -> l.onDisconnected(reason));
    }

    private void writeLine(String line) {
        synchronized (this) { // one line at a time, even if two threads send together
            out.println(line);
        }
    }

    private void notifyError(String message) {
        listeners.forEach(l -> l.onError(message));
    }

    private static void closeQuietly(Socket s) {
        if (s == null) {
            return;
        }
        try {
            s.close();
        } catch (IOException ignored) {
            // closing anyway
        }
    }
}
