package ma.project.chat.client.console;

import ma.project.chat.client.model.ChatClient;
import ma.project.chat.client.model.ClientConfig;
import ma.project.chat.client.model.ClientListener;
import ma.project.chat.client.model.UserEntry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A text-only view of the client. It proves the client model works without any GUI (Model/View
 * separation), and lets you chat from a terminal.
 */
public final class ConsoleClient implements ClientListener {

    @Override
    public void onConnected(String name, boolean readOnly) {
        System.out.println("Connected as " + name + (readOnly ? "  [READ-ONLY MODE: you can read and use allUsers only]" : ""));
        System.out.println("Type a message and press Enter. Commands: allUsers, bye");
    }

    @Override
    public void onChatMessage(String time, String sender, String text, boolean fromMe) {
        System.out.println("[" + time + "] " + (fromMe ? "You" : sender) + ": " + text);
    }

    @Override
    public void onInfo(String time, String text) {
        System.out.println("[" + time + "] * " + text);
    }

    @Override
    public void onUserList(List<UserEntry> users) {
        String list = users.stream()
                .map(u -> u.readOnly() ? u.name() + " (read-only)" : u.name())
                .collect(Collectors.joining(", "));
        System.out.println("Online (" + users.size() + "): " + list);
    }

    @Override
    public void onError(String message) {
        System.out.println("! " + message);
    }

    @Override
    public void onDisconnected(String reason) {
        System.out.println("Disconnected: " + reason);
        // The keyboard loop is blocked in readLine(); in a console app the simplest clean ending is to exit.
        System.exit(0);
    }

    /** Asks for a username, connects, then forwards every typed line to the model. */
    public static void run(ClientConfig config) throws IOException {
        BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        System.out.println("Server: " + config.host() + ":" + config.port() + " (from " + config.source() + ")");
        System.out.print("Username (press Enter for READ-ONLY MODE): ");
        String name = keyboard.readLine();
        if (name == null) {
            return;
        }

        ChatClient client = new ChatClient(config);
        client.addListener(new ConsoleClient());
        try {
            client.connect(name);
        } catch (IOException e) {
            System.err.println("Cannot connect to " + config.host() + ":" + config.port() + " - " + e.getMessage());
            System.exit(1);
        }

        String line;
        while ((line = keyboard.readLine()) != null) {
            client.send(line); // the model decides: message, allUsers, or bye
        }
        client.disconnect(); // keyboard closed (Ctrl+Z / Ctrl+D): leave politely
    }
}
