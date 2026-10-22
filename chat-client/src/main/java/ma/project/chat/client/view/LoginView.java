package ma.project.chat.client.view;

import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import ma.project.chat.client.model.ClientConfig;
import ma.project.chat.client.model.Protocol;

/**
 * The first screen: where to connect and under which name (spec 2.1: "Users must enter a username
 * before accessing the full chat interface"). An empty name joins in READ-ONLY MODE.
 *
 * <pre>
 *          TCP Chat
 *          Join the group chat
 *   Server     [localhost        ] : [3001]
 *   Username   [Youness          ]
 *   Leave the username empty to join in READ-ONLY MODE ...
 *   [            Join the chat             ]
 *   (error message, if any)
 * </pre>
 *
 * The view only collects input; ClientApp does the connecting.
 */
final class LoginView {

    /** What happens when the user presses "Join": implemented by ClientApp. */
    interface ConnectHandler {
        void connect(String host, int port, String username);
    }

    private final StackPane root = new StackPane();
    private final TextField hostField = new TextField();
    private final TextField portField = new TextField();
    private final TextField nameField = new TextField();
    private final Button joinButton = new Button();
    private final Label errorLabel = new Label();
    private final ConnectHandler handler;

    LoginView(ClientConfig defaults, ConnectHandler handler) {
        this.handler = handler;
        hostField.setText(defaults.host());
        portField.setText(String.valueOf(defaults.port()));
        build();
        updateJoinLabel();
    }

    Parent getRoot() {
        return root;
    }

    void focusName() {
        nameField.requestFocus();
    }

    /** While connecting: freeze the form and show progress on the button. */
    void setBusy(boolean busy) {
        hostField.setDisable(busy);
        portField.setDisable(busy);
        nameField.setDisable(busy);
        joinButton.setDisable(busy);
        if (busy) {
            joinButton.setText("Connecting…");
            hideError();
        } else {
            updateJoinLabel();
        }
    }

    void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    private void build() {
        root.getStyleClass().add("login-page");

        GridPane card = new GridPane();
        card.getStyleClass().add("login-card");
        card.setMaxSize(GridPane.USE_PREF_SIZE, GridPane.USE_PREF_SIZE); // stay compact in the middle

        Label title = new Label("TCP Chat");
        title.getStyleClass().add("login-title");
        Label subtitle = new Label("Join the group chat");
        subtitle.getStyleClass().add("login-subtitle");
        card.add(title, 0, 0, 2, 1);
        card.add(subtitle, 0, 1, 2, 1);

        // Row 2: server address
        Label serverLabel = new Label("Server");
        serverLabel.getStyleClass().add("field-label");
        hostField.setPromptText("host or IP");
        portField.setPromptText("port");
        portField.setPrefColumnCount(5);
        portField.setTextFormatter(new TextFormatter<String>(c -> c.getControlNewText().matches("\\d{0,5}") ? c : null));
        Label colon = new Label(":");
        HBox address = new HBox(6, hostField, colon, portField);
        address.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(hostField, Priority.ALWAYS);
        card.add(serverLabel, 0, 2);
        card.add(address, 1, 2);

        // Row 3: username, limited like the server limits it (D12) so what you type is what you get
        Label nameLabel = new Label("Username");
        nameLabel.getStyleClass().add("field-label");
        nameField.setPromptText("e.g. Youness");
        nameField.setTextFormatter(new TextFormatter<String>(c -> {
            String text = c.getControlNewText();
            boolean ok = text.length() <= Protocol.MAX_NAME_LENGTH && text.chars().noneMatch(ch -> "|,:#".indexOf(ch) >= 0);
            return ok ? c : null; // returning null rejects the keystroke
        }));
        nameField.textProperty().addListener((obs, old, now) -> updateJoinLabel());
        card.add(nameLabel, 0, 3);
        card.add(nameField, 1, 3);

        // Row 4: hint about read-only mode
        Label hint = new Label("Leave the username empty to join in READ-ONLY MODE: you can read the chat but not send messages.");
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);
        card.add(hint, 0, 4, 2, 1);

        // Row 5: join button (also triggered by Enter in any field: setDefaultButton)
        joinButton.getStyleClass().add("primary");
        joinButton.setDefaultButton(true);
        joinButton.setMaxWidth(Double.MAX_VALUE);
        joinButton.setOnAction(e -> submit());
        card.add(joinButton, 0, 5, 2, 1);
        GridPane.setHalignment(joinButton, HPos.CENTER);

        // Row 6: error message, hidden until needed
        errorLabel.getStyleClass().add("login-error");
        errorLabel.setWrapText(true);
        hideError();
        card.add(errorLabel, 0, 6, 2, 1);

        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(80);
        ColumnConstraints fields = new ColumnConstraints();
        fields.setPrefWidth(300);
        fields.setHgrow(Priority.ALWAYS);
        card.getColumnConstraints().addAll(labels, fields);

        root.getChildren().add(card);
    }

    private void updateJoinLabel() {
        joinButton.setText(nameField.getText().isBlank() ? "Join in read-only mode" : "Join the chat");
    }

    private void submit() {
        String host = hostField.getText().strip();
        if (host.isEmpty()) {
            showError("Enter the server address (e.g. localhost).");
            return;
        }
        int port;
        try {
            port = Integer.parseInt(portField.getText());
        } catch (NumberFormatException e) {
            port = -1;
        }
        if (port < 1 || port > 65_535) {
            showError("The port must be a number between 1 and 65535.");
            return;
        }
        handler.connect(host, port, nameField.getText().strip());
    }
}
