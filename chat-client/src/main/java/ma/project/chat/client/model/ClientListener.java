package ma.project.chat.client.model;

import java.util.List;

/**
 * How the client model reports what happens on the connection, without knowing who listens.
 *
 * <p>Same idea as the server's ServerListener: the model calls these methods, a view (console or
 * JavaFX) implements them, and the model never imports a view class.
 *
 * <p>Threading: callbacks arrive on the thread that called {@code connect()} (for onConnected) or on
 * the client's background reader thread (everything else), never on the JavaFX thread. A JavaFX view
 * must wrap its UI updates in {@code Platform.runLater(...)}.
 */
public interface ClientListener {

    /** Handshake done: the server accepted us as {@code name} (it may differ from what we asked). */
    default void onConnected(String name, boolean readOnly) {
    }

    /** A chat message. {@code fromMe} is true for our own messages echoed back by the server. */
    default void onChatMessage(String time, String sender, String text, boolean fromMe) {
    }

    /** A notice from the server: joins, leaves. */
    default void onInfo(String time, String text) {
    }

    /** The answer to allUsers. */
    default void onUserList(List<UserEntry> users) {
    }

    /** A request was refused, by the server (ERROR line) or locally (e.g. sending while read-only). */
    default void onError(String message) {
    }

    /** The connection is closed for good. Called exactly once per connection. */
    default void onDisconnected(String reason) {
    }
}
