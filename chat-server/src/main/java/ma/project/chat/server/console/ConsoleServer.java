package ma.project.chat.server.console;

import ma.project.chat.server.model.ChatServer;
import ma.project.chat.server.model.ConnectedUser;
import ma.project.chat.server.model.ServerConfig;
import ma.project.chat.server.model.ServerListener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.BindException;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * A text-only view of the server: prints every model event to the terminal.
 *
 * <p>It exists to prove the Model/View separation: the exact same {@link ChatServer} runs here with
 * no JavaFX at all. It is also handy for headless machines and for quick tests.
 */
public final class ConsoleServer implements ServerListener {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public void onLog(String message) {
        print(message);
    }

    @Override
    public void onUserJoined(ConnectedUser user) {
        print("[users] + " + user.name() + " (" + user.mode() + ")");
    }

    @Override
    public void onUserLeft(ConnectedUser user) {
        print("[users] - " + user.name());
    }

    @Override
    public void onChatMessage(String sender, String text, String time) {
        print("[chat]  " + sender + ": " + text);
    }

    private static void print(String line) {
        System.out.println("[" + LocalTime.now().format(STAMP) + "] " + line);
    }

    /** Starts the server and reads admin commands (users, stop) from the keyboard. */
    public static void run() throws IOException, InterruptedException {
        ChatServer server = new ChatServer(ServerConfig.load());
        server.addListener(new ConsoleServer());

        // Ctrl+C or closing the terminal still disconnects every client cleanly.
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "shutdown-hook"));

        try {
            server.start();
        } catch (BindException e) {
            System.err.println("Port " + server.getConfig().port() + " is already in use. "
                    + "Stop the other program or change server.port in server.properties.");
            System.exit(1);
        }

        System.out.println("Commands: users = list connected users, stop = shut down");
        BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String command;
        while ((command = keyboard.readLine()) != null) {
            switch (command.strip().toLowerCase(Locale.ROOT)) {
                case "stop", "quit", "exit" -> {
                    server.stop();
                    return;
                }
                case "users" -> printUsers(server.activeUsers());
                case "" -> { /* ignore empty lines */ }
                default -> System.out.println("Unknown command. Use: users, stop");
            }
        }
        // The keyboard is closed (e.g. started in the background). Virtual threads are daemon
        // threads, so returning from main now would kill the server: wait for stop() instead.
        server.awaitStop();
    }

    private static void printUsers(List<ConnectedUser> users) {
        if (users.isEmpty()) {
            System.out.println("No one is connected.");
            return;
        }
        System.out.println(users.size() + " connected:");
        users.forEach(u -> System.out.println("  " + u.name() + "  " + u.mode() + "  " + u.address()));
    }
}
