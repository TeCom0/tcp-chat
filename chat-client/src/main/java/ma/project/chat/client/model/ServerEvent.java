package ma.project.chat.client.model;

import java.util.ArrayList;
import java.util.List;

/**
 * One line received from the server, turned into a typed Java object.
 *
 * <p>{@code sealed ... permits} lists every possible kind of event. Because the list is closed, a
 * {@code switch} over a ServerEvent can cover all cases without a {@code default}, and the compiler
 * refuses to compile if a case is forgotten (Java 21 pattern matching for switch).
 */
public sealed interface ServerEvent {

    /** WELCOME|name|FULL or READ_ONLY: the server accepted us under this (possibly renamed) name. */
    record Welcome(String name, boolean readOnly) implements ServerEvent { }

    /** MSG|HH:mm|sender|text: a chat message. */
    record Chat(String time, String sender, String text) implements ServerEvent { }

    /** INFO|HH:mm|text: a notice such as "Sara joined the chat". */
    record Info(String time, String text) implements ServerEvent { }

    /** USERS|name:MODE,...: the answer to allUsers. */
    record Users(List<UserEntry> users) implements ServerEvent { }

    /** ERROR|text: the server refused a request. */
    record ServerError(String message) implements ServerEvent { }

    /** BYE|reason: the server is closing the connection. */
    record Bye(String reason) implements ServerEvent { }

    /** A line we do not understand (e.g. a newer server version). Kept, never crashes the client. */
    record Unknown(String rawLine) implements ServerEvent { }

    /**
     * Parses one protocol line. Never throws: anything malformed becomes {@link Unknown}.
     *
     * <p>Each type is split with a LIMIT equal to its number of fields, so the last field (the free
     * text) keeps any '|' the user typed: "MSG|10:00|Sara|a|b" gives text "a|b".
     */
    static ServerEvent parse(String line) {
        if (line == null) {
            return new Unknown("");
        }
        String type = line.contains(Protocol.SEP) ? line.substring(0, line.indexOf(Protocol.SEP)) : line;
        String[] f;
        switch (type) {
            case Protocol.WELCOME -> {
                f = line.split("\\|", 3);
                return f.length == 3 ? new Welcome(f[1], Protocol.MODE_READ_ONLY.equals(f[2])) : new Unknown(line);
            }
            case Protocol.MSG -> {
                f = line.split("\\|", 4);
                return f.length == 4 ? new Chat(f[1], f[2], f[3]) : new Unknown(line);
            }
            case Protocol.INFO -> {
                f = line.split("\\|", 3);
                return f.length == 3 ? new Info(f[1], f[2]) : new Unknown(line);
            }
            case Protocol.USERS -> {
                f = line.split("\\|", 2);
                return f.length == 2 ? new Users(parseUsers(f[1])) : new Users(List.of());
            }
            case Protocol.ERROR -> {
                f = line.split("\\|", 2);
                return new ServerError(f.length == 2 ? f[1] : "");
            }
            case Protocol.BYE -> {
                f = line.split("\\|", 2);
                return new Bye(f.length == 2 ? f[1] : "");
            }
            default -> {
                return new Unknown(line);
            }
        }
    }

    /** "Sara:FULL,Guest#1:READ_ONLY" -> [UserEntry(Sara,false), UserEntry(Guest#1,true)] */
    private static List<UserEntry> parseUsers(String payload) {
        List<UserEntry> users = new ArrayList<>();
        if (payload.isBlank()) {
            return users;
        }
        for (String entry : payload.split(",")) {
            int colon = entry.lastIndexOf(':');
            if (colon > 0) {
                String name = entry.substring(0, colon);
                boolean readOnly = Protocol.MODE_READ_ONLY.equals(entry.substring(colon + 1));
                users.add(new UserEntry(name, readOnly));
            }
        }
        return List.copyOf(users); // immutable: safe to hand to any thread
    }
}
