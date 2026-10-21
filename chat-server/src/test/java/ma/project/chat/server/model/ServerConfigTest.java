package ma.project.chat.server.model;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerConfigTest {

    @Test
    void readsIpAndPort() {
        Properties p = new Properties();
        p.setProperty("server.ip", "127.0.0.1");
        p.setProperty("server.port", " 4000 ");
        ServerConfig c = ServerConfig.fromProperties(p, "test");
        assertEquals("127.0.0.1", c.ip());
        assertEquals(4000, c.port());
    }

    @Test
    void missingKeysFallBackToDefaults() {
        ServerConfig c = ServerConfig.fromProperties(new Properties(), "test");
        assertEquals(ServerConfig.DEFAULT_IP, c.ip());
        assertEquals(ServerConfig.DEFAULT_PORT, c.port());
    }

    @Test
    void rejectsBadPorts() {
        Properties notANumber = new Properties();
        notANumber.setProperty("server.port", "abc");
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.fromProperties(notANumber, "test"));

        Properties tooBig = new Properties();
        tooBig.setProperty("server.port", "70000");
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.fromProperties(tooBig, "test"));
    }

    @Test
    void bundledFileIsFound() {
        // src/main/resources/server.properties is on the test classpath. We only check that it was
        // used, not which port it holds, so editing the port never breaks this test.
        ServerConfig c = ServerConfig.load();
        assertTrue(c.source().startsWith("classpath:"), c.source());
        assertTrue(c.port() > 0 && c.port() <= 65_535);
    }
}
