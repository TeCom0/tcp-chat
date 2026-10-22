package ma.project.chat.server.view;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import ma.project.chat.server.model.ChatServer;
import ma.project.chat.server.model.ConnectedUser;
import ma.project.chat.server.model.ServerConfig;
import ma.project.chat.server.model.ServerListener;

import java.io.IOException;
import java.net.BindException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The server window: a GridPane with a header, the activity log, the coloured user list and a footer.
 *
 * <pre>
 *   column 0 (64%)                         column 1 (36%)
 *   +----------------------------------------------------------------+
 *   | TCP Chat Server          (o) Running on 0.0.0.0:3001  [Start] [Stop] |  row 0 (spans 2 columns)
 *   | Activity log                           | Connected users (3)    |  row 1
 *   | [20:01:05] Server started ...          | [S] Sara               |  row 2 (grows)
 *   | [20:01:09] Sara: hello                 | [G] Guest#1  READ-ONLY |
 *   | 3 online - 12 messages   [Clear log]   | legend                 |  row 3
 *   +----------------------------------------------------------------+
 * </pre>
 *
 * <p>It is a {@link ServerListener}: the model pushes events into it. Those events arrive on network
 * threads, and JavaFX allows UI changes ONLY on the JavaFX Application Thread, so every callback
 * hops over with {@link #onFxThread(Runnable)}.
 *
 * <p>The view talks to the model only through its public API ({@code start}, {@code stop}); the model
 * never knows this class exists.
 */
public final class ServerDashboard implements ServerListener {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter JOINED = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MAX_LOG_LINES = 2_000;

    /** What kind of log line it is: decides its colour in the CSS (log-system, log-chat, log-error). */
    enum Kind { SYSTEM, CHAT, ERROR }

    /** One line of the activity log. */
    record LogEntry(String time, Kind kind, String text) { }

    private final ChatServer server;
    private final UserColors colours = new UserColors();

    // ObservableList: a list that tells the ListView when it changes, so the screen updates by itself.
    private final ObservableList<LogEntry> logEntries = FXCollections.observableArrayList();
    private final ObservableList<ConnectedUser> users = FXCollections.observableArrayList();

    private final GridPane root = new GridPane();
    private final Circle statusDot = new Circle(6);
    private final Label statusLabel = new Label();
    private final Button startButton = new Button("Start");
    private final Button stopButton = new Button("Stop");
    private final Label usersTitle = new Label();
    private final Label statsLabel = new Label();
    private final ListView<LogEntry> logView = new ListView<>(logEntries);
    private final ListView<ConnectedUser> userView = new ListView<>(users);

    private int messageCount;

    public ServerDashboard(ChatServer server) {
        this.server = server;
        buildLayout();
        showStopped();
        refreshCounters();
    }

    /** The node to put in the Scene. */
    public Parent getRoot() {
        return root;
    }

    // ------------------------------------------------------------------ layout

    private void buildLayout() {
        root.getStyleClass().add("dashboard");

        // Row 0: header spanning both columns
        Label title = new Label("TCP Chat Server");
        title.getStyleClass().add("title");
        Label subtitle = new Label("Multi-client chat over TCP  ·  one virtual thread per client");
        subtitle.getStyleClass().add("subtitle");
        VBox titleBox = new VBox(2, title, subtitle);

        statusDot.getStyleClass().add("status-dot");
        statusLabel.getStyleClass().add("status-text");
        HBox statusPill = new HBox(8, statusDot, statusLabel);
        statusPill.setAlignment(Pos.CENTER_LEFT);
        statusPill.getStyleClass().add("status-pill");

        startButton.getStyleClass().add("primary");
        stopButton.getStyleClass().add("danger");
        startButton.setOnAction(e -> startServer());
        stopButton.setOnAction(e -> stopServer());

        Region headerGap = new Region();
        HBox.setHgrow(headerGap, Priority.ALWAYS);
        HBox header = new HBox(12, titleBox, headerGap, statusPill, startButton, stopButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("header");
        root.add(header, 0, 0, 2, 1); // column 0, row 0, spans 2 columns, 1 row

        // Row 1: section titles
        Label logTitle = new Label("Activity log");
        logTitle.getStyleClass().add("section-title");
        usersTitle.getStyleClass().add("section-title");
        root.add(logTitle, 0, 1);
        root.add(usersTitle, 1, 1);

        // Row 2: the two lists
        logView.getStyleClass().add("log-list");
        logView.setCellFactory(list -> new LogCell());
        logView.setPlaceholder(new Label("Nothing logged yet"));
        userView.getStyleClass().add("user-list");
        userView.setCellFactory(list -> new UserCell());
        userView.setPlaceholder(new Label("No one is connected"));
        root.add(logView, 0, 2);
        root.add(userView, 1, 2);

        // Row 3: footer
        statsLabel.getStyleClass().add("stats");
        Button clearButton = new Button("Clear log");
        clearButton.getStyleClass().add("subtle");
        clearButton.setOnAction(e -> logEntries.clear());
        Region footerGap = new Region();
        HBox.setHgrow(footerGap, Priority.ALWAYS);
        HBox footer = new HBox(12, statsLabel, footerGap, clearButton);
        footer.setAlignment(Pos.CENTER_LEFT);
        Label legend = new Label("Each user gets a random colour  ·  italic = read-only");
        legend.getStyleClass().add("legend");
        root.add(footer, 0, 3);
        root.add(legend, 1, 3);

        // Column widths: 64% / 36%, both grow with the window
        ColumnConstraints left = new ColumnConstraints();
        left.setPercentWidth(64);
        left.setHgrow(Priority.ALWAYS);
        ColumnConstraints right = new ColumnConstraints();
        right.setPercentWidth(36);
        right.setHgrow(Priority.ALWAYS);
        root.getColumnConstraints().addAll(left, right);

        // Only row 2 (the lists) takes the extra height when the window grows
        RowConstraints lists = new RowConstraints();
        lists.setVgrow(Priority.ALWAYS);
        root.getRowConstraints().addAll(new RowConstraints(), new RowConstraints(), lists, new RowConstraints());
    }

    // ------------------------------------------------------------------ actions (FX thread)

    /** Starts the model; called at launch and by the Start button. */
    public void startServer() {
        try {
            server.start(); // fast: binds the port and returns (the accept loop runs in the background)
            showRunning();
        } catch (BindException e) {
            int port = server.getConfig().port();
            addLog(Kind.ERROR, "Port " + port + " is already in use. Stop the other server or change server.port.");
            showStopped();
            showError("Cannot start the server", "Port " + port + " is already in use.");
        } catch (IOException | RuntimeException e) {
            addLog(Kind.ERROR, "Could not start the server: " + e.getMessage());
            showStopped();
        }
    }

    /** Stops the model off the FX thread: stop() waits for every client thread to finish. */
    private void stopServer() {
        stopButton.setDisable(true);
        statusLabel.setText("Stopping…");
        Thread.ofVirtual().name("server-stop").start(server::stop); // onServerStopped() updates the UI
    }

    // ------------------------------------------------------------------ ServerListener (network threads)

    @Override
    public void onLog(String message) {
        onFxThread(() -> addLog(Kind.SYSTEM, message));
    }

    @Override
    public void onChatMessage(String sender, String text, String time) {
        onFxThread(() -> {
            messageCount++;
            addLog(Kind.CHAT, sender + ": " + text);
            refreshCounters();
        });
    }

    @Override
    public void onUserJoined(ConnectedUser user) {
        onFxThread(() -> {
            users.add(user);
            refreshCounters();
        });
    }

    @Override
    public void onUserLeft(ConnectedUser user) {
        onFxThread(() -> {
            users.remove(user);
            refreshCounters();
        });
    }

    @Override
    public void onServerStopped() {
        onFxThread(() -> {
            users.clear();
            showStopped();
            refreshCounters();
        });
    }

    // ------------------------------------------------------------------ helpers (FX thread)

    /** Runs UI code on the JavaFX Application Thread: directly if already there, else queued. */
    private static void onFxThread(Runnable uiWork) {
        if (Platform.isFxApplicationThread()) {
            uiWork.run();
        } else {
            Platform.runLater(uiWork);
        }
    }

    private void addLog(Kind kind, String text) {
        logEntries.add(new LogEntry(LocalTime.now().format(STAMP), kind, text));
        if (logEntries.size() > MAX_LOG_LINES) {
            logEntries.remove(0); // keep memory bounded on a long-running server
        }
        logView.scrollTo(logEntries.size() - 1); // always show the newest line
    }

    private void showRunning() {
        ServerConfig c = server.getConfig();
        statusDot.getStyleClass().setAll("status-dot", "online");
        statusLabel.setText("Running on " + c.ip() + ":" + server.getPort());
        startButton.setDisable(true);
        stopButton.setDisable(false);
    }

    private void showStopped() {
        statusDot.getStyleClass().setAll("status-dot", "offline");
        statusLabel.setText("Stopped");
        startButton.setDisable(false);
        stopButton.setDisable(true);
    }

    private void refreshCounters() {
        usersTitle.setText("Connected users (" + users.size() + ")");
        statsLabel.setText(users.size() + " online  ·  " + messageCount
                + (messageCount == 1 ? " message" : " messages") + " relayed");
    }

    private void showError(String header, String content) {
        // Queued with runLater: at launch this runs inside start(), before the main window has finished
        // appearing; opening a dialog owned by a half-shown window can leave it blank. Queued, it opens
        // once the window is really on screen.
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR, content);
            alert.setHeaderText(header);
            alert.initOwner(root.getScene() == null ? null : root.getScene().getWindow());
            alert.show(); // show(), not showAndWait(): never block the UI thread
        });
    }

    // ------------------------------------------------------------------ list cells

    /** How one log line is drawn: time + text, coloured by kind through a CSS class. */
    private static final class LogCell extends ListCell<LogEntry> {
        LogCell() {
            setWrapText(true); // long lines wrap instead of adding a horizontal scrollbar
            setPrefWidth(0);   // the cell takes the list's width, not its text's width
        }

        @Override
        protected void updateItem(LogEntry entry, boolean empty) {
            super.updateItem(entry, empty);
            getStyleClass().removeAll("log-system", "log-chat", "log-error"); // cells are recycled!
            if (empty || entry == null) {
                setText(null);
                return;
            }
            setText(entry.time() + "   " + entry.text());
            getStyleClass().add("log-" + entry.kind().name().toLowerCase(Locale.ROOT));
        }
    }

    /**
     * How one user is drawn: a card with the user's random background colour.
     *
     * <p>A ListView creates only as many cells as fit on screen and RECYCLES them while scrolling:
     * the same cell shows Sara, then later Omar. So the nodes are built once in the constructor and
     * updateItem() only changes their content.
     */
    private final class UserCell extends ListCell<ConnectedUser> {
        private final Label avatar = new Label();
        private final Label name = new Label();
        private final Label detail = new Label();
        private final Label badge = new Label("READ-ONLY");
        private final HBox card;

        UserCell() {
            avatar.getStyleClass().add("avatar");
            name.getStyleClass().add("user-name");
            detail.getStyleClass().add("user-detail");
            badge.getStyleClass().add("badge");
            badge.setMinWidth(Region.USE_PREF_SIZE); // the badge never shrinks to "READ-..."
            HBox nameRow = new HBox(8, name, badge);  // line 1: name + badge, line 2: details
            nameRow.setAlignment(Pos.CENTER_LEFT);
            VBox text = new VBox(1, nameRow, detail);
            text.setMinWidth(0); // allow long names to be cut with "..." instead of widening the card
            card = new HBox(10, avatar, text);
            card.setAlignment(Pos.CENTER_LEFT);
            card.getStyleClass().add("user-card");

            // The card follows the cell's width, so it never overflows (no horizontal scrollbar).
            setPrefWidth(0);
            card.prefWidthProperty().bind(widthProperty().subtract(6));
            card.setMaxWidth(Region.USE_PREF_SIZE);
            setText(null);
        }

        @Override
        protected void updateItem(ConnectedUser user, boolean empty) {
            super.updateItem(user, empty);
            if (empty || user == null) {
                setGraphic(null);
                return;
            }
            avatar.setText(user.name().substring(0, 1).toUpperCase(Locale.ROOT));
            name.setText(user.name());
            String joined = JOINED.format(user.joinedAt().atZone(ZoneId.systemDefault()));
            detail.setText(user.address() + "  ·  joined " + joined);

            // Read-only users: italic name + badge. Toggle both ways, because cells are reused.
            name.getStyleClass().remove("read-only");
            if (user.readOnly()) {
                name.getStyleClass().add("read-only");
            }
            badge.setVisible(user.readOnly());
            badge.setManaged(user.readOnly()); // not managed = takes no space when hidden

            // The one style that cannot live in the CSS file: each user's own random colour.
            card.setStyle("-fx-background-color: " + colours.colourFor(user.name()) + ";");
            setGraphic(card);
        }
    }
}
