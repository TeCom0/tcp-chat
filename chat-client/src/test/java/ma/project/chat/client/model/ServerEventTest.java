package ma.project.chat.client.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerEventTest {

    @Test
    void parsesEveryMessageType() {
        assertEquals(new ServerEvent.Welcome("Sara", false), ServerEvent.parse("WELCOME|Sara|FULL"));
        assertEquals(new ServerEvent.Welcome("Guest#1", true), ServerEvent.parse("WELCOME|Guest#1|READ_ONLY"));
        assertEquals(new ServerEvent.Chat("14:05", "Sara", "hi"), ServerEvent.parse("MSG|14:05|Sara|hi"));
        assertEquals(new ServerEvent.Info("14:05", "Omar joined the chat"), ServerEvent.parse("INFO|14:05|Omar joined the chat"));
        assertEquals(new ServerEvent.ServerError("nope"), ServerEvent.parse("ERROR|nope"));
        assertEquals(new ServerEvent.Bye("Goodbye Sara"), ServerEvent.parse("BYE|Goodbye Sara"));
    }

    @Test
    void messageTextKeepsPipes() {
        assertEquals(new ServerEvent.Chat("10:00", "Sara", "a|b|c"), ServerEvent.parse("MSG|10:00|Sara|a|b|c"));
    }

    @Test
    void parsesUserList() {
        ServerEvent e = ServerEvent.parse("USERS|Sara:FULL,Guest#1:READ_ONLY");
        assertEquals(new ServerEvent.Users(List.of(new UserEntry("Sara", false), new UserEntry("Guest#1", true))), e);
        assertEquals(new ServerEvent.Users(List.of()), ServerEvent.parse("USERS|"));
    }

    @Test
    void malformedLinesNeverThrow() {
        assertTrue(ServerEvent.parse("MSG|only-two") instanceof ServerEvent.Unknown);
        assertTrue(ServerEvent.parse("HELLO|from the future") instanceof ServerEvent.Unknown);
        assertTrue(ServerEvent.parse("") instanceof ServerEvent.Unknown);
        assertTrue(ServerEvent.parse(null) instanceof ServerEvent.Unknown);
    }
}
