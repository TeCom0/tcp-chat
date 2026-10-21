package ma.project.chat.client;

import ma.project.chat.client.console.ConsoleClient;
import ma.project.chat.client.model.ClientConfig;

/**
 * Entry point of the client: {@code java TCPClient <ServerIPAddress> <PortNumber>} (spec section 4.1),
 * e.g. {@code java TCPClient localhost 3001}. With no arguments, client.properties decides.
 *
 * <p>Step 2: only the console view exists. Step 4 opens the JavaFX window by default.
 */
public final class TCPClient {

    private TCPClient() {
    }

    public static void main(String[] args) throws Exception {
        ClientConfig config;
        try {
            config = ClientConfig.resolve(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(2); // 2 = wrong usage, by convention
            return;
        }
        ConsoleClient.run(config);
    }
}
