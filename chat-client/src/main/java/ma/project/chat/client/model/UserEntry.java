package ma.project.chat.client.model;

/**
 * One line of the "allUsers" answer as seen by the client.
 *
 * @param name     the user's unique name, e.g. "Sara" or "Guest#1"
 * @param readOnly true when that user cannot send messages
 */
public record UserEntry(String name, boolean readOnly) {
}
