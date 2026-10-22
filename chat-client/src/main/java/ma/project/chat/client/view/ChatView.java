package ma.project.chat.client.view;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import ma.project.chat.client.model.ChatClient;
import ma.project.chat.client.model.ClientListener;
import ma.project.chat.client.model.UserEntry;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The chat screen, laid out with a GridPane:
 *
 * <pre>
 *   column 0 (grows)                                  column 1 (fixed)
 *   +-------------------------------------------------------------------+
 *   | TCP Chat       [Y] Youness  FULL ACCESS   (o) Online   [LEAVE]    |  row 0 header (2 cols)
 *   | READ-ONLY MODE: you can read the chat ... (only for guests)       |  row 1 banner (2 cols)
 *   |   Sara: hello                                                     |  row 2 messages (2 cols, grows)
 *   |                                        You: hi Sara               |
 *   | [ type a message ...                    ]  [SEND] [Who's online]  |  row 3 input
 *   +-------------------------------------------------------------------+
 * </pre>
 *
 * <p>It is a {@link ClientListener}: the model pushes events from its reader thread, and every UI
 * change hops onto the JavaFX Application Thread with {@link #onFxThread(Runnable)}.
 */
final class ChatView implements ClientListener {

    private static final DateTimeFormatter NOW = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MAX_MESSAGES = 1_000;

    /** The kinds of rows in the conversation, each drawn differently. */
    enum Kind { CHAT, INFO, ERROR, USERS }

    /** One row of the conversation. */
    record MessageItem(Kind kind, String time, String sender, String text, boolean fromMe) { }

    private final ChatClient client;
    private final Runnable backToLogin;
    private final ObservableList<MessageItem> messages = FXCollections.observableArrayList();

    private final GridPane root = new GridPane();
    private final Label avatar = new Label();
    private final Label userName = new Label();
    private final Label userMode = new Label();
    private final Circle statusDot = new Circle(6);
    private final Label statusLabel = new Label("Connecting…");
    private final Button leaveButton = new Button("LEAVE");
    private final Button backButton = new Button("Back to login");
    private final Label readOnlyBanner = new Label(
            "READ-ONLY MODE: you can read the conversation and see who is online, but you cannot send messages.");
    private final ListView<MessageItem> messageList = new ListView<>(messages);
    private final TextArea input = new TextArea();
    private final Button sendButton = new Button("SEND");
    private final Button usersButton = new Button("Who's online");

    ChatView(ChatClient client, Runnable backToLogin) {
        this.client = client;
        this.backToLogin = backToLogin;
        build();
        setStatus(false, "Connecting…");
    }

    Parent getRoot() {
        return root;
    }

    void focusInput() {
        input.requestFocus();
    }

    // ------------------------------------------------------------------ layout

    private void build() {
        root.getStyleClass().add("chat-page");

        // Row 0: header
        Label title = new Label("TCP Chat");
        title.getStyleClass().add("chat-title");
        Label server = new Label(client.getConfig().host() + ":" + client.getConfig().port());
        server.getStyleClass().add("chat-subtitle");
        VBox titleBox = new VBox(1, title, server);

        avatar.getStyleClass().add("me-avatar");
        userName.getStyleClass().add("me-name");
        userMode.getStyleClass().add("me-mode");
        HBox me = new HBox(8, avatar, new VBox(0, userName, userMode));
        me.setAlignment(Pos.CENTER_LEFT);

        statusDot.getStyleClass().add("status-dot");
        statusLabel.getStyleClass().add("status-text");
        HBox status = new HBox(7, statusDot, statusLabel); // spec: "Online" label + a visual indicator (circle)
        status.setAlignment(Pos.CENTER_LEFT);
        status.getStyleClass().add("status-pill");

        leaveButton.getStyleClass().add("danger");
        leaveButton.setOnAction(e -> client.disconnect()); // polite: sends bye, the server answers BYE
        backButton.getStyleClass().add("primary");
        backButton.setOnAction(e -> backToLogin.run());
        backButton.setVisible(false);
        backButton.setManaged(false);

        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox header = new HBox(14, titleBox, gap, me, status, leaveButton, backButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("chat-header");
        root.add(header, 0, 0, 2, 1);

        // Row 1: read-only banner (hidden unless read-only)
        readOnlyBanner.getStyleClass().add("readonly-banner");
        readOnlyBanner.setWrapText(true);
        readOnlyBanner.setMaxWidth(Double.MAX_VALUE);
        readOnlyBanner.setVisible(false);
        readOnlyBanner.setManaged(false);
        root.add(readOnlyBanner, 0, 1, 2, 1);

        // Row 2: the conversation
        messageList.getStyleClass().add("messages");
        messageList.setCellFactory(list -> new MessageCell());
        messageList.setPlaceholder(new Label("No messages yet. Say hello!"));
        messageList.setFocusTraversable(false);
        root.add(messageList, 0, 2, 2, 1);

        // Row 3: input + buttons
        input.getStyleClass().add("message-input");
        input.setPromptText("Type a message, then press Enter or SEND.  Commands: allUsers, bye");
        input.setWrapText(true);
        input.setPrefRowCount(2);
        input.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) { // Enter sends (spec 2.1); a chat line has no line breaks
                e.consume();
                send();
            }
        });
        sendButton.getStyleClass().add("primary");
        sendButton.setMaxWidth(Double.MAX_VALUE);
        sendButton.setOnAction(e -> send());
        usersButton.getStyleClass().add("subtle");
        usersButton.setMaxWidth(Double.MAX_VALUE);
        usersButton.setOnAction(e -> client.requestUserList()); // same as typing allUsers
        VBox buttons = new VBox(8, sendButton, usersButton);
        root.add(input, 0, 3);
        root.add(buttons, 1, 3);

        ColumnConstraints main = new ColumnConstraints();
        main.setHgrow(Priority.ALWAYS);
        ColumnConstraints side = new ColumnConstraints();
        side.setMinWidth(130);
        side.setPrefWidth(130);
        root.getColumnConstraints().addAll(main, side);
        RowConstraints conversation = new RowConstraints();
        conversation.setVgrow(Priority.ALWAYS);
        root.getRowConstraints().addAll(new RowConstraints(), new RowConstraints(), conversation, new RowConstraints());
    }

    // ------------------------------------------------------------------ user actions (FX thread)

    private void send() {
        String text = input.getText().replaceAll("\\R+", " ").strip(); // pasted line breaks become spaces
        if (text.isEmpty() || !client.isConnected()) {
            return;
        }
        client.send(text); // the model decides: message, allUsers, or bye/end (disconnect)
        input.clear();
    }

    // ------------------------------------------------------------------ ClientListener (model threads)

    @Override
    public void onConnected(String name, boolean readOnly) {
        onFxThread(() -> {
            avatar.setText(name.substring(0, 1).toUpperCase(Locale.ROOT));
            userName.setText(name);
            userMode.setText(readOnly ? "READ-ONLY" : "FULL ACCESS");
            setStatus(true, "Online");
            readOnlyBanner.setVisible(readOnly);
            readOnlyBanner.setManaged(readOnly);
            input.setDisable(readOnly);
            sendButton.setDisable(readOnly);
            if (readOnly) {
                input.setPromptText("READ-ONLY MODE: sending is disabled");
            }
            // No local "you joined" line: the server's own INFO "Sara joined the chat" arrives next.
        });
    }

    @Override
    public void onChatMessage(String time, String sender, String text, boolean fromMe) {
        onFxThread(() -> add(new MessageItem(Kind.CHAT, time, sender, text, fromMe)));
    }

    @Override
    public void onInfo(String time, String text) {
        onFxThread(() -> add(new MessageItem(Kind.INFO, time, "", text, false)));
    }

    @Override
    public void onUserList(List<UserEntry> users) {
        String list = users.stream()
                .map(u -> u.readOnly() ? u.name() + " (read-only)" : u.name())
                .collect(Collectors.joining(",  "));
        onFxThread(() -> add(new MessageItem(Kind.USERS, now(), "",
                "Online now (" + users.size() + "):  " + list, false)));
    }

    @Override
    public void onError(String message) {
        onFxThread(() -> add(new MessageItem(Kind.ERROR, now(), "", message, false)));
    }

    @Override
    public void onDisconnected(String reason) {
        onFxThread(() -> {
            add(new MessageItem(Kind.INFO, now(), "", "Disconnected: " + reason, false));
            setStatus(false, "Offline");
            input.setDisable(true);
            sendButton.setDisable(true);
            usersButton.setDisable(true);
            leaveButton.setVisible(false);
            leaveButton.setManaged(false);
            backButton.setVisible(true);
            backButton.setManaged(true);
        });
    }

    // ------------------------------------------------------------------ helpers (FX thread)

    private static void onFxThread(Runnable uiWork) {
        if (Platform.isFxApplicationThread()) {
            uiWork.run();
        } else {
            Platform.runLater(uiWork);
        }
    }

    private void add(MessageItem item) {
        messages.add(item);
        if (messages.size() > MAX_MESSAGES) {
            messages.remove(0);
        }
        messageList.scrollTo(messages.size() - 1); // follow the conversation
    }

    private void setStatus(boolean online, String text) {
        statusDot.getStyleClass().setAll("status-dot", online ? "online" : "offline");
        statusLabel.setText(text);
    }

    private static String now() {
        return LocalTime.now().format(NOW);
    }

    // ------------------------------------------------------------------ how one row is drawn

    /**
     * Chat rows are bubbles (mine on the right, others on the left with a coloured name); notices,
     * errors and user lists are centred lines. Cells are recycled, so every call rebuilds the graphic.
     */
    private static final class MessageCell extends ListCell<MessageItem> {

        MessageCell() {
            setPrefWidth(0); // the cell takes the list's width, so long text wraps instead of scrolling
        }

        @Override
        protected void updateItem(MessageItem item, boolean empty) {
            super.updateItem(item, empty);
            setText(null);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            setGraphic(switch (item.kind()) {
                case CHAT -> bubble(item);
                case INFO -> centred(item.text() + "  ·  " + item.time(), "info-line");
                case ERROR -> centred(item.text(), "error-line");
                case USERS -> centred(item.text(), "users-line");
            });
        }

        private HBox bubble(MessageItem item) {
            Label text = new Label(item.text());
            text.getStyleClass().add("bubble-text");
            text.setWrapText(true);
            Label time = new Label(item.time());
            time.getStyleClass().add("bubble-time");

            VBox bubble = new VBox(2);
            bubble.getStyleClass().addAll("bubble", item.fromMe() ? "mine" : "theirs");
            if (!item.fromMe()) {
                Label sender = new Label(item.sender());
                sender.getStyleClass().add("bubble-sender");
                sender.setStyle("-fx-text-fill: " + NameColors.colourFor(item.sender()) + ";");
                bubble.getChildren().add(sender);
            }
            bubble.getChildren().addAll(text, time);
            bubble.maxWidthProperty().bind(widthProperty().multiply(0.70)); // at most 70% of the row

            HBox row = new HBox(bubble);
            row.setAlignment(item.fromMe() ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            return row;
        }

        private HBox centred(String text, String styleClass) {
            Label label = new Label(text);
            label.getStyleClass().add(styleClass);
            label.setWrapText(true);
            HBox row = new HBox(label);
            row.setAlignment(Pos.CENTER);
            return row;
        }
    }
}
