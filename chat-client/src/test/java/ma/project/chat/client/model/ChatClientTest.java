package ma.project.chat.client.model;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the client against a FAKE server that we script line by line. The client project never
 * depends on the server project; the protocol is the only contract between them.
 */
class ChatClientTest {

    private FakeServer server;
    private ChatClient client;
    private final Recorder events = new Recorder();

    @BeforeEach
    void setUp() throws IOException {
        server = new FakeServer();
        client = new ChatClient(new ClientConfig("127.0.0.1", server.port(), "test"));
        client.addListener(events);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void handshakeSendsNameAndReportsAssignedName() throws Exception {
        server.onConnectReply("WELCOME|Sara#2|FULL");
        client.connect("  Sara  ");

        assertEquals("Sara", server.nextLine(), "name is sent trimmed");
        assertEquals("connected:Sara#2:false", events.next());
        assertEquals("Sara#2", client.getUsername());
        assertTrue(client.isConnected());
    }

    @Test
    void chatMessagesAreParsedAndOwnMessagesFlagged() throws Exception {
        connectAs("Sara", "FULL");
        server.send("MSG|10:00|Omar|hello");
        server.send("MSG|10:01|Sara|a|b");
        assertEquals("chat:10:00:Omar:hello:false", events.next());
        assertEquals("chat:10:01:Sara:a|b:true", events.next());
    }

    @Test
    void typedTextIsRoutedLikeTheServerExpects() throws Exception {
        connectAs("Sara", "FULL");
        client.send("   ");         // ignored
        client.send("hi all");
        client.send("ALLUSERS");    // command, case-insensitive
        assertEquals("hi all", server.nextLine());
        assertEquals("allUsers", server.nextLine());
        server.send("USERS|Sara:FULL,Guest#1:READ_ONLY");
        assertEquals("users:[UserEntry[name=Sara, readOnly=false], UserEntry[name=Guest#1, readOnly=true]]", events.next());
    }

    @Test
    void readOnlyClientRefusesLocallyButMayAskForUsers() throws Exception {
        connectAs("Guest#1", "READ_ONLY");
        assertTrue(client.isReadOnly());
        client.send("can I talk?");
        assertTrue(events.next().startsWith("error:You are in READ-ONLY MODE"));
        client.send("allUsers");
        assertEquals("allUsers", server.nextLine(), "the refused message never reached the server");
    }

    @Test
    void byeDisconnectsPolitelyAndFiresOnce() throws Exception {
        connectAs("Sara", "FULL");
        client.send("bye");
        assertEquals("bye", server.nextLine());
        server.send("BYE|Goodbye Sara");
        assertEquals("disconnected:Goodbye Sara", events.next());
        assertFalse(client.isConnected());
        Thread.sleep(ChatClient.GOODBYE_TIMEOUT_MS + 300); // let the watchdog fire too
        assertNull(events.poll(100), "onDisconnected must fire exactly once");
    }

    @Test
    void silentServerIsClosedByTheWatchdog() throws Exception {
        connectAs("Sara", "FULL");
        client.disconnect(); // the fake server never answers BYE
        assertEquals("disconnected:Disconnected", events.next(ChatClient.GOODBYE_TIMEOUT_MS + 2000));
    }

    @Test
    void serverCrashIsDetected() throws Exception {
        connectAs("Sara", "FULL");
        server.dropClient();
        assertTrue(events.next().startsWith("disconnected:Connection lost"));
        assertFalse(client.isConnected());
    }

    @Test
    void serverErrorsAreReported() throws Exception {
        connectAs("Sara", "FULL");
        server.send("ERROR|Message too long");
        assertEquals("error:Message too long", events.next());
    }

    @Test
    void unknownLinesAreIgnored() throws Exception {
        connectAs("Sara", "FULL");
        server.send("PING|from a future version");
        server.send("INFO|10:00|still alive");
        assertEquals("info:10:00:still alive", events.next());
    }

    @Test
    void connectFailsCleanlyWhenTheServerRefuses() throws Exception {
        server.onConnectReply("BYE|Server is shutting down");
        IOException e = assertThrows(IOException.class, () -> client.connect("Sara"));
        assertTrue(e.getMessage().contains("Unexpected answer"), e.getMessage());
        assertFalse(client.isConnected());
    }

    @Test
    void connectFailsWhenNobodyListens() throws Exception {
        int freePort;
        try (ServerSocket probe = new ServerSocket(0)) { // borrow a free port, then release it at once
            freePort = probe.getLocalPort();
        }
        ChatClient lonely = new ChatClient(new ClientConfig("127.0.0.1", freePort, "test"));
        assertThrows(IOException.class, () -> lonely.connect("Sara"));
        assertFalse(lonely.isConnected());
    }

    @Test
    void canReconnectAfterDisconnecting() throws Exception {
        connectAs("Sara", "FULL");
        client.send("bye");
        server.nextLine();
        server.send("BYE|Goodbye Sara");
        assertEquals("disconnected:Goodbye Sara", events.next());

        server.onConnectReply("WELCOME|Sara|FULL");
        client.connect("Sara");
        assertEquals("connected:Sara:false", events.next());
        Thread.sleep(ChatClient.GOODBYE_TIMEOUT_MS + 300); // the OLD watchdog fires now...
        assertTrue(client.isConnected(), "...and must not close the NEW connection");
        assertNull(events.poll(100));
    }

    // ------------------------------------------------------------------ helpers

    private void connectAs(String name, String mode) throws Exception {
        server.onConnectReply("WELCOME|" + name + "|" + mode);
        client.connect(name);
        assertEquals(name, server.nextLine()); // handshake line
        assertTrue(events.next().startsWith("connected:"));
    }

    /** Records every callback as a short string, in order. */
    private static final class Recorder implements ClientListener {
        private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();

        @Override public void onConnected(String name, boolean readOnly) { queue.add("connected:" + name + ":" + readOnly); }
        @Override public void onChatMessage(String time, String sender, String text, boolean fromMe) {
            queue.add("chat:" + time + ":" + sender + ":" + text + ":" + fromMe);
        }
        @Override public void onInfo(String time, String text) { queue.add("info:" + time + ":" + text); }
        @Override public void onUserList(List<UserEntry> users) { queue.add("users:" + users); }
        @Override public void onError(String message) { queue.add("error:" + message); }
        @Override public void onDisconnected(String reason) { queue.add("disconnected:" + reason); }

        String next() throws InterruptedException {
            return next(2000);
        }

        String next(long timeoutMs) throws InterruptedException {
            String e = queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
            if (e == null) {
                throw new AssertionError("no event within " + timeoutMs + " ms");
            }
            return e;
        }

        String poll(long timeoutMs) throws InterruptedException {
            return queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
        }
    }

    /** A scripted server: accepts connections, answers the handshake, records what the client sends. */
    private static final class FakeServer {
        private final ServerSocket serverSocket = new ServerSocket(0);
        private final BlockingQueue<String> received = new LinkedBlockingQueue<>();
        private volatile String connectReply = "WELCOME|x|FULL";
        private volatile Socket current;
        private volatile PrintWriter out;

        FakeServer() throws IOException {
            Thread.ofVirtual().start(this::acceptLoop);
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void onConnectReply(String line) {
            connectReply = line;
        }

        private void acceptLoop() {
            while (!serverSocket.isClosed()) {
                try {
                    Socket s = serverSocket.accept();
                    BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                    PrintWriter w = new PrintWriter(s.getOutputStream(), true, StandardCharsets.UTF_8);
                    current = s;
                    out = w;
                    String first = in.readLine();              // the username
                    received.add(first == null ? "<eof>" : first);
                    w.println(connectReply);
                    Thread.ofVirtual().start(() -> {
                        try {
                            String line;
                            while ((line = in.readLine()) != null) {
                                received.add(line);
                            }
                        } catch (IOException ignored) {
                        }
                    });
                } catch (IOException e) {
                    return;
                }
            }
        }

        void send(String line) {
            out.println(line);
        }

        String nextLine() throws InterruptedException {
            String line = received.poll(2, TimeUnit.SECONDS);
            if (line == null) {
                throw new AssertionError("the client sent nothing within 2 s");
            }
            return line;
        }

        void dropClient() throws IOException {
            current.close();
        }

        void close() {
            try {
                serverSocket.close();
                if (current != null) {
                    current.close();
                }
            } catch (IOException ignored) {
            }
        }
    }
}
