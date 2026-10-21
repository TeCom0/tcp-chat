package ma.project.chat.server.model;

import java.time.Instant;

/**
 * An immutable snapshot of one connected user, handed to listeners (the views).
 *
 * @param name      unique display name assigned by the server (e.g. "Sara", "Sara#2", "Guest#1")
 * @param readOnly  true when the user connected without a username
 * @param address   remote IP and port of the client socket, e.g. "127.0.0.1:52144"
 * @param joinedAt  moment the user was registered; used to list users in join order
 */
public record ConnectedUser(String name, boolean readOnly, String address, Instant joinedAt) {

    /** The mode word sent on the wire: FULL or READ_ONLY. */
    public String mode() {
        return readOnly ? Protocol.MODE_READ_ONLY : Protocol.MODE_FULL;
    }
}
