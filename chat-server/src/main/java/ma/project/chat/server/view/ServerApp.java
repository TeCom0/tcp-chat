package ma.project.chat.server.view;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ma.project.chat.server.model.ChatServer;
import ma.project.chat.server.model.ServerConfig;

import java.util.Objects;

/**
 * The JavaFX application: creates the model and the view, connects them, shows the window.
 *
 * <p>JavaFX calls the lifecycle methods for us: {@link #start(Stage)} once the toolkit is ready
 * (on the JavaFX Application Thread), {@link #stop()} when the last window closes.
 */
public final class ServerApp extends Application {

    private ChatServer server;

    @Override
    public void start(Stage stage) {
        ServerConfig config = ServerConfig.load();
        server = new ChatServer(config);                         // the Model: knows nothing about JavaFX
        ServerDashboard dashboard = new ServerDashboard(server); // the View
        server.addListener(dashboard);                           // the only link: model -> listener

        Scene scene = new Scene(dashboard.getRoot(), 980, 640);
        scene.getStylesheets().add(
                Objects.requireNonNull(ServerApp.class.getResource("server.css"), "server.css missing").toExternalForm());

        stage.setTitle("TCP Chat Server  ·  port " + config.port());
        stage.setMinWidth(760);
        stage.setMinHeight(480);
        stage.setScene(scene);
        stage.show();

        dashboard.startServer(); // the log immediately shows "Server started" and "Waiting for client..."
    }

    /** Window closed: disconnect every client cleanly (they all receive BYE). */
    @Override
    public void stop() {
        if (server != null) {
            server.stop();
        }
    }
}
