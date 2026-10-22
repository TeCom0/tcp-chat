package ma.project.chat.server;

import javafx.application.Application;
import ma.project.chat.server.console.ConsoleServer;
import ma.project.chat.server.view.ServerApp;

import java.util.Arrays;

/**
 * Entry point of the server application: {@code java TCPServer} (spec section 4.1).
 *
 * <ul>
 *   <li>{@code java TCPServer} opens the JavaFX dashboard;</li>
 *   <li>{@code java TCPServer --console} runs the same server in text mode (no GUI).</li>
 * </ul>
 *
 * <p>This class deliberately does NOT extend {@code Application}. When the main class extends
 * Application, the Java launcher checks for JavaFX modules and fails with "JavaFX runtime components
 * are missing" if JavaFX is on the classpath (as in a fat JAR). A plain launcher avoids that check
 * (decision D11).
 */
public final class TCPServer {

    private TCPServer() {
    }

    public static void main(String[] args) throws Exception {
        if (Arrays.asList(args).contains("--console")) {
            ConsoleServer.run();
        } else {
            Application.launch(ServerApp.class, args);
        }
    }
}
