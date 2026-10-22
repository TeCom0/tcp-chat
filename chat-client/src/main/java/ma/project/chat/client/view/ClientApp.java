package ma.project.chat.client.view;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ma.project.chat.client.model.ChatClient;
import ma.project.chat.client.model.ClientConfig;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * The JavaFX client application: shows the login screen, connects in the background, then swaps
 * the window's content to the chat screen. One window, two screens.
 */
public final class ClientApp extends Application {

    private Stage stage;
    private Scene scene;
    private LoginView login;
    private volatile ChatClient client; // the current connection, if any

    @Override
    public void start(Stage primaryStage) {
        stage = primaryStage;
        login = new LoginView(defaults(getParameters().getRaw()), this::connect);

        scene = new Scene(login.getRoot(), 860, 640);
        scene.getStylesheets().add(
                Objects.requireNonNull(ClientApp.class.getResource("client.css"), "client.css missing").toExternalForm());
        stage.setTitle("TCP Chat");
        stage.setMinWidth(640);
        stage.setMinHeight(480);
        stage.setScene(scene);
        stage.show();
        login.focusName();
    }

    /** Window closed: leave politely so the server tells the others. */
    @Override
    public void stop() {
        ChatClient c = client;
        if (c != null && c.isConnected()) {
            c.disconnect(); // sends "bye" before the JVM exits
        }
    }

    /** Pre-fills the login form: command-line arguments, else client.properties, else defaults. */
    private static ClientConfig defaults(List<String> args) {
        try {
            return ClientConfig.resolve(args.toArray(String[]::new));
        } catch (IllegalArgumentException e) {
            return ClientConfig.loadFile(); // TCPClient already rejected bad args; this is a safety net
        }
    }

    /**
     * Called by the login form. connect() blocks (network), so it runs on a virtual thread; the
     * result comes back to the JavaFX thread with Platform.runLater.
     */
    private void connect(String host, int port, String username) {
        ClientConfig config = new ClientConfig(host, port, "login form");
        ChatClient newClient = new ChatClient(config);
        ChatView chat = new ChatView(newClient, this::backToLogin);
        newClient.addListener(chat); // registered BEFORE connecting, so no event is missed

        login.setBusy(true);
        Thread.ofVirtual().name("connect").start(() -> {
            try {
                newClient.connect(username);
                Platform.runLater(() -> {
                    client = newClient;
                    scene.setRoot(chat.getRoot()); // swap screens inside the same window
                    stage.setTitle("TCP Chat  ·  " + newClient.getUsername());
                    chat.focusInput();
                });
            } catch (IOException | RuntimeException e) {
                Platform.runLater(() -> {
                    login.setBusy(false);
                    login.showError("Cannot connect to " + host + ":" + port + ". " + e.getMessage());
                });
            }
        });
    }

    /** From the chat screen after a disconnect: back to the form, fields still filled in. */
    private void backToLogin() {
        client = null;
        login.setBusy(false);
        scene.setRoot(login.getRoot());
        stage.setTitle("TCP Chat");
        login.focusName();
    }
}
