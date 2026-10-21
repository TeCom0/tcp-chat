package ma.project.chat.server.model;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests: a real server on a free port, real sockets as clients.
 * Nothing is mocked, so these tests prove the protocol works end to end.
 */
class ChatServerTest {

    private ChatServer server;
    private final List<TestClient> clients = new ArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        server = new ChatServer(new ServerConfig("127.0.0.1", 0, "test")); // port 0 = any free port
        server.start();
    }

    @AfterEach
    void stopServer() {
        clients.forEach(TestClient::close);
        server.stop();
    }

    @Test
    void welcomeIsTheFirstLineAndCarriesNameAndMode() throws IOException {
        TestClient sara = connect("Sara");
        assertEquals("WELCOME|Sara|FULL", sara.readLine());
    }

    @Test
    void messageIsStampedAndBroadcastToEveryoneIncludingSender() throws IOException {
        TestClient sara = joined("Sara");
        TestClient omar = joined("Omar");
        sara.skipUntil("INFO|", "Omar joined"); // Sara is told that Omar arrived

        sara.send("Hello everyone");

        String toSara = sara.readLine();
        String toOmar = omar.readLine();
        assertEquals(toSara, toOmar, "everyone receives the identical line");
        assertTrue(toSara.matches("MSG\\|\\d{2}:\\d{2}\\|Sara\\|Hello everyone"), toSara);
    }

    @Test
    void pipesInsideAMessageArePreserved() throws IOException {
        TestClient sara = joined("Sara");
        sara.send("a|b|c");
        String line = sara.readLine();
        assertEquals("a|b|c", line.split("\\|", 4)[3]); // the client splits with a limit of 4
    }

    @Test
    void emptyUsernameGivesReadOnlyGuestThatCannotSend() throws IOException {
        TestClient guest = connect("");
        assertEquals("WELCOME|Guest#1|READ_ONLY", guest.readLine());
        guest.skipUntil("INFO|", "Guest#1 joined the chat (read-only)");
        TestClient sara = joined("Sara");
        guest.skipUntil("INFO|", "Sara joined");

        guest.send("I should not be allowed");
        assertTrue(guest.readLine().startsWith("ERROR|"));
        assertNull(sara.readLineOrNull(), "nothing was broadcast");
    }

    @Test
    void allUsersAnswersOnlyTheAsker() throws IOException {
        TestClient sara = joined("Sara");
        TestClient guest = connect("");
        guest.readLine(); // WELCOME
        sara.skipUntil("INFO|", "Guest#1 joined");

        guest.send("allUsers"); // read-only users may still ask who is online
        String answer = guest.skipUntil("USERS|", "");
        assertEquals("USERS|Sara:FULL,Guest#1:READ_ONLY", answer);
        assertNull(sara.readLineOrNull(), "Sara did not receive the list");
    }

    @Test
    void duplicateNamesGetASuffix() throws IOException {
        joined("Sara");
        TestClient second = connect("sara"); // case-insensitive clash
        assertEquals("WELCOME|sara#2|FULL", second.readLine());
    }

    @Test
    void byeDisconnectsAndOthersAreTold() throws IOException {
        TestClient sara = joined("Sara");
        TestClient omar = joined("Omar");
        sara.skipUntil("INFO|", "Omar joined");

        omar.send("BYE");
        assertEquals("BYE|Goodbye Omar", omar.skipUntil("BYE|", ""));
        sara.skipUntil("INFO|", "Omar left the chat");
        waitUntil(() -> server.activeUsers().size() == 1);
        assertEquals("Sara", server.activeUsers().get(0).name());
    }

    @Test
    void crashedClientIsRemoved() throws IOException {
        TestClient sara = joined("Sara");
        TestClient omar = joined("Omar");
        sara.skipUntil("INFO|", "Omar joined");

        omar.close(); // no bye: simulates a crash or a lost network
        sara.skipUntil("INFO|", "Omar left the chat");
        waitUntil(() -> server.activeUsers().size() == 1);
    }

    @Test
    void stopSendsByeToEveryone() throws IOException {
        TestClient sara = joined("Sara");
        server.stop();
        assertEquals("BYE|Server is shutting down", sara.skipUntil("BYE|", ""));
        assertFalse(server.isRunning());
    }

    @Test
    void manyClientsAtOnce() throws Exception {
        int count = 50;
        for (int i = 0; i < count; i++) {
            joined("user" + i);
        }
        waitUntil(() -> server.activeUsers().size() == count);
    }

    // ------------------------------------------------------------------ helpers

    private TestClient connect(String name) throws IOException {
        TestClient client = new TestClient(server.getPort());
        clients.add(client);
        client.send(name);
        return client;
    }

    /** Connects and consumes WELCOME and the client's own "joined" notice. */
    private TestClient joined(String name) throws IOException {
        TestClient client = connect(name);
        assertTrue(client.readLine().startsWith("WELCOME|" + name));
        client.skipUntil("INFO|", name + " joined");
        return client;
    }

    private static void waitUntil(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 3000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("condition not met within 3 s");
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** A minimal raw-socket client, exactly what a real client sends on the wire. */
    private static final class TestClient {
        private final Socket socket;
        private final BufferedReader in;
        private final PrintWriter out;

        TestClient(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(2000); // a test must fail, not hang, if a line never comes
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
        }

        void send(String line) {
            out.println(line);
        }

        String readLine() throws IOException {
            return in.readLine();
        }

        /** Returns the next line, or null if nothing arrives within 300 ms. */
        String readLineOrNull() throws IOException {
            socket.setSoTimeout(300);
            try {
                return in.readLine();
            } catch (SocketTimeoutException e) {
                return null;
            } finally {
                socket.setSoTimeout(2000);
            }
        }

        /** Reads lines until one starts with prefix and contains text; returns it. */
        String skipUntil(String prefix, String text) throws IOException {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith(prefix) && line.contains(text)) {
                    return line;
                }
            }
            throw new AssertionError("connection closed before " + prefix + " " + text);
        }

        void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
