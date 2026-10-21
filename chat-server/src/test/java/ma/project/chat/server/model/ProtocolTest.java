package ma.project.chat.server.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolTest {

    @Test
    void commandsAreCaseInsensitiveAndTrimmed() {
        assertTrue(Protocol.isAllUsers("allUsers"));
        assertTrue(Protocol.isAllUsers("  ALLUSERS "));
        assertTrue(Protocol.isQuit("bye"));
        assertTrue(Protocol.isQuit(" End"));
        assertFalse(Protocol.isQuit("bye bye"));
        assertFalse(Protocol.isAllUsers("all users"));
        assertFalse(Protocol.isQuit(null));
    }

    @Test
    void sanitizeNameRemovesProtocolCharactersAndLimitsLength() {
        assertEquals("Sara", Protocol.sanitizeName("  Sara  "));
        assertEquals("Youness", Protocol.sanitizeName("You|ne,ss:#"));
        assertEquals("", Protocol.sanitizeName(null));
        assertEquals("", Protocol.sanitizeName("   "));
        assertEquals(Protocol.MAX_NAME_LENGTH, Protocol.sanitizeName("x".repeat(50)).length());
    }

    @Test
    void lineJoinsFieldsWithSeparator() {
        assertEquals("MSG|14:05|Sara|hi", Protocol.line(Protocol.MSG, "14:05", "Sara", "hi"));
        assertEquals("USERS|", Protocol.line(Protocol.USERS, ""));
    }

    @Test
    void longMessagesAreCut() {
        assertEquals(Protocol.MAX_MESSAGE_LENGTH, Protocol.limitMessage("a".repeat(5000)).length());
        assertEquals("short", Protocol.limitMessage("short"));
    }
}
