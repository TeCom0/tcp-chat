package ma.project.chat.server.model;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Serves ONE connected client, on its own virtual thread.
 *
 * <p>Life of a connection:
 * <ol>
 *   <li>read the first line = requested username (handshake);</li>
 *   <li>loop: read a line, then answer a command or broadcast a message;</li>
 *   <li>on bye/end, end of stream or error: leave the chat and close the socket.</li>
 * </ol>
 *
 * <p>Package-private: only {@link ChatServer} creates and uses handlers.
 */
final class ClientHandler implements Runnable {

    private final Socket socket;
    private final ChatServer server;
    private final BufferedReader in;
    private final PrintWriter out;
    private final String address;

    /** Set once by ChatServer.join(); read by other threads (allUsers), hence volatile. */
    private volatile ConnectedUser user;

    ClientHandler(Socket socket, ChatServer server) throws IOException {
        this.socket = socket;
        this.server = server;
        this.address = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        // Text over bytes, always UTF-8 so accents and Arabic survive on any OS.
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        // autoFlush = true: every println() is pushed to the network immediately.
        this.out = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)), true);
    }

    @Override
    public void run() {
        Thread.currentThread().setName("client-" + address); // readable name in thread dumps
        try {
            String requestedName = in.readLine(); // 1. handshake: first line is the username
            if (requestedName == null) {
                return; // connected and disconnected without saying anything
            }
            user = server.join(this, requestedName, address);
            if (user == null) {
                return; // server is stopping
            }

            String line;
            while ((line = in.readLine()) != null) { // 2. one loop turn per line received
                if (Protocol.isQuit(line)) {
                    send(Protocol.line(Protocol.BYE, "Goodbye " + user.name()));
                    break;
                }
                if (Protocol.isAllUsers(line)) {
                    send(server.usersLine()); // answered to THIS client only
                    continue;
                }
                if (line.isBlank()) {
                    continue;
                }
                if (user.readOnly()) { // the server enforces read-only, it does not trust the client UI
                    send(Protocol.line(Protocol.ERROR,
                            "You are in READ-ONLY MODE: reconnect with a username to send messages"));
                    continue;
                }
                server.chat(user, line);
            }
        } catch (IOException e) {
            // Abrupt end: the client crashed, the network dropped, or stop() closed this socket.
            // Nothing to report to the client: the connection is already gone.
        } finally {
            if (user != null) { // 3. always clean up, whatever the reason we got here
                server.leave(this, user);
            }
            close();
        }
    }

    /**
     * Sends one line to this client. Many threads call this (every broadcast), so writes are
     * serialised on {@code out}: two messages can never interleave their characters.
     */
    void send(String line) {
        synchronized (out) {
            out.println(line);
        }
    }

    void attach(ConnectedUser user) {
        this.user = user;
    }

    ConnectedUser user() {
        return user;
    }

    /** Closes the socket; a thread blocked in readLine() then gets an exception and finishes. */
    void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // already closed
        }
    }
}
