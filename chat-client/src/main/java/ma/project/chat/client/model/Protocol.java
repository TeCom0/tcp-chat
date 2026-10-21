package ma.project.chat.client.model;

import java.util.Locale;

/**
 * The wire contract between the server and its clients.
 *
 * <p>One message = one line of UTF-8 text ending with a newline.
 *
 * <pre>
 * Client -> Server
 *   line 1      the requested username (an empty line = read-only guest)
 *   lines 2..n  whatever the user typed. Three words are commands
 *               (case-insensitive): allUsers, end, bye
 *
 * Server -> Client   TYPE|field|field...
 *   WELCOME|assignedName|FULL or READ_ONLY   first line, always
 *   MSG|HH:mm|sender|text                    a chat message
 *   INFO|HH:mm|text                          joins, leaves, notices
 *   USERS|name:FULL,name:READ_ONLY,...       answer to allUsers
 *   ERROR|text                               request refused
 *   BYE|reason                               server closes this connection
 * </pre>
 *
 * <p>The server project holds an identical copy of this class: it is the contract both sides agree on.
 */
public final class Protocol {

    private Protocol() {
        // constants and helpers only: never instantiated
    }

    /** Separator between fields of a server -> client line. */
    public static final String SEP = "|";

    // Server -> client message types
    public static final String WELCOME = "WELCOME";
    public static final String MSG = "MSG";
    public static final String INFO = "INFO";
    public static final String USERS = "USERS";
    public static final String ERROR = "ERROR";
    public static final String BYE = "BYE";

    // Access modes sent in WELCOME and USERS
    public static final String MODE_FULL = "FULL";
    public static final String MODE_READ_ONLY = "READ_ONLY";

    // Commands typed by the user
    public static final String CMD_ALL_USERS = "allUsers";
    public static final String CMD_END = "end";
    public static final String CMD_BYE = "bye";

    /** Longest username kept; longer names are cut. */
    public static final int MAX_NAME_LENGTH = 20;

    /** Longest chat message kept; longer messages are cut. */
    public static final int MAX_MESSAGE_LENGTH = 1000;

    /** Characters with a meaning in the protocol, so they are not allowed in names. */
    private static final String RESERVED_NAME_CHARS = "|,:#";

    /** True when the typed text is the "allUsers" command. */
    public static boolean isAllUsers(String text) {
        return text != null && text.strip().equalsIgnoreCase(CMD_ALL_USERS);
    }

    /** True when the typed text is "end" or "bye". */
    public static boolean isQuit(String text) {
        if (text == null) {
            return false;
        }
        String t = text.strip().toLowerCase(Locale.ROOT);
        return t.equals(CMD_END) || t.equals(CMD_BYE);
    }

    /**
     * Cleans a requested username: removes reserved characters and control characters,
     * trims spaces, and cuts it to {@link #MAX_NAME_LENGTH}. Returns "" for null.
     */
    public static String sanitizeName(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : raw.toCharArray()) {
            if (RESERVED_NAME_CHARS.indexOf(c) < 0 && !Character.isISOControl(c)) {
                sb.append(c);
            }
        }
        String cleaned = sb.toString().strip();
        return cleaned.length() > MAX_NAME_LENGTH ? cleaned.substring(0, MAX_NAME_LENGTH).strip() : cleaned;
    }

    /** Cuts a chat message to {@link #MAX_MESSAGE_LENGTH} characters. */
    public static String limitMessage(String text) {
        return text.length() > MAX_MESSAGE_LENGTH ? text.substring(0, MAX_MESSAGE_LENGTH) : text;
    }

    /** Builds a server -> client line, e.g. line("MSG", "14:05", "Sara", "hi") = "MSG|14:05|Sara|hi". */
    public static String line(String type, String... fields) {
        StringBuilder sb = new StringBuilder(type);
        for (String field : fields) {
            sb.append(SEP).append(field);
        }
        return sb.toString();
    }
}
