package ma.project.chat.client;

import javafx.application.Application;
import ma.project.chat.client.console.ConsoleClient;
import ma.project.chat.client.model.ClientConfig;
import ma.project.chat.client.view.ClientApp;

import java.util.Arrays;

/**
 * Entry point of the client: {@code java TCPClient <ServerIPAddress> <PortNumber>} (spec section 4.1),
 * e.g. {@code java TCPClient localhost 3001}. With no arguments, client.properties decides.
 *
 * <ul>
 *   <li>default: opens the JavaFX chat window (the address pre-fills the login form);</li>
 *   <li>{@code --console}: the text client from step 2, e.g. {@code java TCPClient --console localhost 3001}.</li>
 * </ul>
 *
 * <p>Like TCPServer, this class does not extend Application, so it also works from a fat JAR (D11).
 */
public final class TCPClient {

    private TCPClient() {
    }

    public static void main(String[] args) throws Exception {
        boolean console = Arrays.asList(args).contains("--console");
        String[] address = Arrays.stream(args).filter(a -> !a.equals("--console")).toArray(String[]::new);

        ClientConfig config;
        try {
            config = ClientConfig.resolve(address); // validates the arguments in both modes
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(2); // 2 = wrong usage, by convention
            return;
        }

        if (console) {
            ConsoleClient.run(config);
        } else {
            Application.launch(ClientApp.class, address);
        }
    }
}
