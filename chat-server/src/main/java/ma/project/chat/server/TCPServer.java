package ma.project.chat.server;

import ma.project.chat.server.console.ConsoleServer;

/**
 * Entry point of the server application: {@code java TCPServer} (as required by section 4.1).
 *
 * <p>Step 1: only the console view exists, so it starts that. Step 3 makes the JavaFX dashboard
 * the default and keeps the console view behind a {@code --console} flag.
 */
public final class TCPServer {

    private TCPServer() {
    }

    public static void main(String[] args) throws Exception {
        ConsoleServer.run();
    }
}
