package ma.project.chat.server.model;

/**
 * How the model tells the outside world (a view) that something happened, without knowing who listens.
 *
 * <p>This interface is the wall between Model and View: the model calls these methods, any view
 * (console, JavaFX, a test) implements them. The model never imports a view class.
 *
 * <p>Threading: these methods are called from the server's network threads, never from a UI thread.
 * A JavaFX view must therefore wrap its UI updates in {@code Platform.runLater(...)}.
 *
 * <p>Every method has an empty default body, so a listener only overrides what it cares about.
 */
public interface ServerListener {

    /** A line for the activity log ("Server started...", "Waiting for client...", "Welcome Sara"). */
    default void onLog(String message) {
    }

    /** A user finished the handshake and is now in the chat. */
    default void onUserJoined(ConnectedUser user) {
    }

    /** A user left (typed bye/end, pressed LEAVE, crashed, or the server stopped). */
    default void onUserLeft(ConnectedUser user) {
    }

    /** A chat message was accepted and broadcast. {@code time} is the HH:mm stamp it was sent with. */
    default void onChatMessage(String sender, String text, String time) {
    }

    /** The server has fully stopped: no more connections are accepted. */
    default void onServerStopped() {
    }
}
