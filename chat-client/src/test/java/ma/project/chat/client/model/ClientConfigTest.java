package ma.project.chat.client.model;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientConfigTest {

    @Test
    void commandLineArgumentsWin() {
        ClientConfig c = ClientConfig.resolve(new String[]{"192.168.1.20", "4000"});
        assertEquals("192.168.1.20", c.host());
        assertEquals(4000, c.port());
        assertEquals("command line", c.source());
    }

    @Test
    void noArgumentsUsesTheBundledFile() {
        ClientConfig c = ClientConfig.resolve(new String[0]);
        assertTrue(c.source().startsWith("classpath:"), c.source());
    }

    @Test
    void wrongNumberOfArgumentsShowsUsage() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ClientConfig.resolve(new String[]{"localhost"}));
        assertTrue(e.getMessage().startsWith("Usage: java TCPClient"), e.getMessage());
    }

    @Test
    void rejectsBadPorts() {
        assertThrows(IllegalArgumentException.class, () -> ClientConfig.resolve(new String[]{"localhost", "abc"}));
        assertThrows(IllegalArgumentException.class, () -> ClientConfig.resolve(new String[]{"localhost", "0"}));
        assertThrows(IllegalArgumentException.class, () -> ClientConfig.resolve(new String[]{"localhost", "70000"}));
    }

    @Test
    void missingKeysFallBackToDefaults() {
        ClientConfig c = ClientConfig.fromProperties(new Properties(), "test");
        assertEquals(ClientConfig.DEFAULT_HOST, c.host());
        assertEquals(ClientConfig.DEFAULT_PORT, c.port());
    }
}
