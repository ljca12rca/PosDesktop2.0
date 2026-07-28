package com.posdesktop.pos;

import com.posdesktop.pos.integration.PosApiClient;
import java.awt.Desktop;
import com.posdesktop.pos.mockfx.MockData;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;

public class PosDesktopFxApplication extends Application {

    private static final String APP_TITLE = "POS Desktop";
    private static final String FILTER_ALL = "Todos";
    private static final Duration API_RETRY_DELAY = Duration.seconds(3);
    private static final Duration API_OVERLAY_FADE_DURATION = Duration.millis(320);
    private static final PseudoClass COMPACT = PseudoClass.getPseudoClass("compact");
    private static final DateTimeFormatter SHORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
    private static final DateTimeFormatter RECEIPT_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter RECEIPT_TIME_FORMATTER = DateTimeFormatter.ofPattern("hh:mm a");
    private static final String RECEIPT_BUSINESS_NAME = "Milenaso del norte";
    private static final String RECEIPT_OWNER_NAME = "KELI MONSALVE";
    private static final String RECEIPT_ADDRESS = "CALLE 28 # 29-18";
    private static final String RECEIPT_NIT = "NIT. 1.035.830.505-7";
    private static final String RECEIPT_CASHIER = "Caja principal";
    private static final String PERM_VENTAS_VIEW = "VENTAS_VIEW";
    private static final String PERM_VENTAS_EDIT = "VENTAS_EDIT";
    private static final String PERM_CIERRES_VIEW = "CIERRES_VIEW";
    private static final String PERM_CIERRES_EDIT = "CIERRES_EDIT";
    private static final String PERM_SEPARADOS_VIEW = "SEPARADOS_VIEW";
    private static final String PERM_SEPARADOS_EDIT = "SEPARADOS_EDIT";
    private static final String PERM_MOVIMIENTOS_VIEW = "MOVIMIENTOS_VIEW";
    private static final String PERM_FACTURAS_VIEW = "FACTURAS_VIEW";
    private static final String PERM_FACTURAS_EDIT = "FACTURAS_EDIT";
    private static final String PERM_PROVEEDORES_VIEW = "PROVEEDORES_VIEW";
    private static final String PERM_PROVEEDORES_EDIT = "PROVEEDORES_EDIT";
    private final Map<String, Supplier<Node>> screenFactories = new LinkedHashMap<>();
    private final StackPane applicationRoot = new StackPane();
    private final StackPane contentHost = new StackPane();
    private final PosApiClient posApiClient = PosApiClient.createDefault();
    private final ObservableList<SaleDraftRow> saleDraftRows = FXCollections.observableArrayList();
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO"));
    private final SimpleObjectProperty<BigDecimal> saleTotal = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final BooleanProperty apiAvailable = new SimpleBooleanProperty(false);
    private final BooleanProperty apiProbeInProgress = new SimpleBooleanProperty(false);
    private final PauseTransition apiRetryPause = new PauseTransition(API_RETRY_DELAY);
    private StackPane apiStartupOverlay;
    private Label apiStartupStatusLabel;
    private ProgressBar apiStartupProgressBar;
    private int apiProbeAttempt;
    private Stage primaryStage;
    private PosApiClient.AuthSessionResponse authenticatedSession;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;

        Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
        double initialWidth = Math.min(1360, visualBounds.getWidth() * 0.96);
        double initialHeight = Math.min(860, visualBounds.getHeight() * 0.94);

        apiStartupOverlay = createApiStartupOverlay();
        applicationRoot.getChildren().setAll(createLoginView(), apiStartupOverlay);

        Scene scene = new Scene(applicationRoot, initialWidth, initialHeight);
        scene.getStylesheets().add(getClass().getResource("/com/posdesktop/pos/mockfx/mock-theme.css").toExternalForm());

        stage.setTitle(APP_TITLE);
        stage.setMinWidth(Math.min(920, visualBounds.getWidth() * 0.78));
        stage.setMinHeight(Math.min(620, visualBounds.getHeight() * 0.72));
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();

        apiRetryPause.setOnFinished(event -> beginApiAvailabilityCheck(false));
        beginApiAvailabilityCheck(true);
    }

    @Override
    public void stop() {
        apiRetryPause.stop();
    }

    private Node createLoginView() {
        StackPane shell = new StackPane();
        shell.getStyleClass().add("app-shell");
        shell.setPadding(new Insets(28));

        HBox layout = new HBox(28);
        layout.setAlignment(Pos.CENTER);

        VBox hero = new VBox(16);
        hero.getStyleClass().addAll("surface-card", "login-hero-card");
        hero.setMaxWidth(420);

        StackPane badge = createIconBadge("PD", "badge-xl");
        Label heroTitle = new Label("Bienvenido al POS");
        heroTitle.getStyleClass().add("login-hero-title");
        Label heroCopy = new Label("Inicia sesion para acceder solo a los modulos y acciones permitidas para tu perfil.");
        heroCopy.getStyleClass().add("login-hero-copy");
        heroCopy.setWrapText(true);

        VBox bullets = new VBox(10,
                createLoginFeature("Ventas y separados con control por rol"),
                createLoginFeature("Facturas, proveedores y cierres protegidos"),
                createLoginFeature("Interfaz adaptada a los permisos del usuario")
        );
        hero.getChildren().addAll(badge, heroTitle, heroCopy, bullets);

        VBox card = new VBox(16);
        card.getStyleClass().addAll("surface-card", "login-card");
        card.setMaxWidth(380);

        Label overline = new Label("Acceso seguro");
        overline.getStyleClass().add("login-overline");
        Label title = new Label("Iniciar sesion");
        title.getStyleClass().add("login-title");
        Label subtitle = new Label("Usa tu usuario y clave registrados en la base de datos.");
        subtitle.getStyleClass().add("card-subtitle");
        subtitle.setWrapText(true);

        TextField usernameField = createField("");
        usernameField.setPromptText("Usuario");
        PasswordField passwordField = new PasswordField();
        passwordField.getStyleClass().add("soft-field");
        passwordField.setPromptText("Clave");

        Label statusLabel = new Label("Esperando disponibilidad de la API...");
        statusLabel.getStyleClass().add("login-status");
        statusLabel.setWrapText(true);
        BooleanProperty loginInProgress = new SimpleBooleanProperty(false);
        apiAvailable.addListener((obs, oldValue, available) -> {
            if (available) {
                statusLabel.getStyleClass().remove("status-error");
                statusLabel.setText("API disponible. Ingresa tus credenciales para continuar.");
            } else {
                if (!statusLabel.getStyleClass().contains("status-error")) {
                    statusLabel.getStyleClass().add("status-error");
                }
                statusLabel.setText("Conectando con la API. Espera unos segundos...");
            }
        });
        if (apiAvailable.get()) {
            statusLabel.getStyleClass().remove("status-error");
            statusLabel.setText("API disponible. Ingresa tus credenciales para continuar.");
        }

        Button loginButton = createActionButton("Entrar al sistema", "primary-button");
        loginButton.setMaxWidth(Double.MAX_VALUE);
        loginButton.disableProperty().bind(apiAvailable.not().or(loginInProgress));
        loginButton.setDefaultButton(true);
        Runnable performLogin = () -> {
            if (!apiAvailable.get()) {
                if (!statusLabel.getStyleClass().contains("status-error")) {
                    statusLabel.getStyleClass().add("status-error");
                }
                statusLabel.setText("La API aun no esta lista. Espera un momento e intenta de nuevo.");
                return;
            }
            if (usernameField.getText() == null || usernameField.getText().isBlank()
                    || passwordField.getText() == null || passwordField.getText().isBlank()) {
                if (!statusLabel.getStyleClass().contains("status-error")) {
                    statusLabel.getStyleClass().add("status-error");
                }
                statusLabel.setText("Debes ingresar usuario y clave.");
                return;
            }
            loginInProgress.set(true);
            statusLabel.getStyleClass().remove("status-error");
            statusLabel.setText("Validando credenciales...");
            runAsync(
                    () -> posApiClient.login(usernameField.getText(), passwordField.getText()),
                    session -> {
                        authenticatedSession = session;
                        loginInProgress.set(false);
                        passwordField.clear();
                        showAuthenticatedShell();
                    },
                    exception -> {
                        loginInProgress.set(false);
                        passwordField.clear();
                        if (!statusLabel.getStyleClass().contains("status-error")) {
                            statusLabel.getStyleClass().add("status-error");
                        }
                        statusLabel.setText(exception.getMessage());
                    }
            );
        };
        loginButton.setOnAction(event -> performLogin.run());
        usernameField.setOnAction(event -> passwordField.requestFocus());
        passwordField.setOnAction(event -> performLogin.run());

        VBox form = new VBox(12,
                createFieldGroup("Usuario", usernameField, 320),
                createFieldGroup("Clave", passwordField, 320)
        );
        form.getStyleClass().add("login-form");

        card.getChildren().addAll(overline, title, subtitle, form, statusLabel, loginButton);
        layout.getChildren().addAll(hero, card);
        shell.getChildren().add(layout);
        Platform.runLater(usernameField::requestFocus);
        return shell;
    }

    private Node createLoginFeature(String text) {
        HBox row = new HBox(10);
        row.getStyleClass().add("login-feature-row");
        row.setAlignment(Pos.CENTER_LEFT);
        Label marker = new Label("•");
        marker.getStyleClass().add("login-feature-marker");
        Label copy = new Label(text);
        copy.getStyleClass().add("login-feature-copy");
        row.getChildren().addAll(marker, copy);
        return row;
    }

    private void showAuthenticatedShell() {
        configureAuthorizedScreens();
        BorderPane shell = new BorderPane();
        shell.getStyleClass().add("app-shell");
        shell.disableProperty().bind(apiAvailable.not());
        VBox sidebar = (VBox) createSidebar();
        shell.setLeft(sidebar);
        shell.setCenter(contentHost);
        bindRegionWidthToScene(sidebar, 0.17, 172, 210);

        Scene scene = primaryStage.getScene();
        if (scene != null) {
            updateResponsiveState(shell, scene);
            scene.widthProperty().addListener((obs, oldValue, newValue) -> updateResponsiveState(shell, scene));
            scene.heightProperty().addListener((obs, oldValue, newValue) -> updateResponsiveState(shell, scene));
        }

        String firstScreen = screenFactories.keySet().stream().findFirst().orElse("Acceso");
        showScreen(firstScreen);
        applicationRoot.getChildren().set(0, shell);
        primaryStage.setTitle(APP_TITLE + " | " + authenticatedSession.nombreCompleto());
    }

    private void returnToLoginView() {
        saleDraftRows.clear();
        screenFactories.clear();
        contentHost.getChildren().clear();
        authenticatedSession = null;
        posApiClient.clearSession();
        applicationRoot.getChildren().set(0, createLoginView());
        primaryStage.setTitle(APP_TITLE);
    }

    private StackPane createApiStartupOverlay() {
        apiStartupStatusLabel = new Label("Cargando...");
        apiStartupStatusLabel.getStyleClass().add("startup-status");
        apiStartupStatusLabel.setWrapText(true);

        apiStartupProgressBar = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
        apiStartupProgressBar.getStyleClass().addAll("accent-progress", "startup-progress");
        apiStartupProgressBar.setPrefWidth(180);
        apiStartupProgressBar.setMaxWidth(180);

        VBox card = new VBox(16, apiStartupStatusLabel, apiStartupProgressBar);
        card.getStyleClass().addAll("surface-card", "startup-card");
        card.setPrefWidth(232);
        card.setMinWidth(232);
        card.setMaxWidth(232);
        card.setPrefHeight(104);
        card.setMinHeight(104);
        card.setMaxHeight(104);
        card.setAlignment(Pos.CENTER);

        StackPane overlay = new StackPane(card);
        overlay.getStyleClass().add("startup-overlay");
        overlay.setPickOnBounds(true);
        return overlay;
    }

    private void beginApiAvailabilityCheck(boolean manualTrigger) {
        if (apiAvailable.get() || apiProbeInProgress.get()) {
            return;
        }

        apiRetryPause.stop();
        apiProbeInProgress.set(true);
        apiProbeAttempt++;
        updateApiStartupStatus(
                "Cargando...",
                false
        );
        apiStartupProgressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);

        runAsync(
                posApiClient::consultarEstadoSistema,
                this::handleApiAvailabilitySuccess,
                this::handleApiAvailabilityFailure
        );
    }

    private void handleApiAvailabilitySuccess(PosApiClient.SystemStatusResponse systemStatus) {
        apiProbeInProgress.set(false);
        apiAvailable.set(true);
        apiRetryPause.stop();
        updateApiStartupStatus("Cargando...", false);
        apiStartupProgressBar.setProgress(1);
        fadeOutApiStartupOverlay();
    }

    private void handleApiAvailabilityFailure(Throwable throwable) {
        apiProbeInProgress.set(false);
        apiAvailable.set(false);
        updateApiStartupStatus("Cargando...", false);
        apiStartupProgressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        apiRetryPause.playFromStart();
    }

    private void updateApiStartupStatus(String message, boolean error) {
        if (error) {
            if (!apiStartupStatusLabel.getStyleClass().contains("status-error")) {
                apiStartupStatusLabel.getStyleClass().add("status-error");
            }
        } else {
            apiStartupStatusLabel.getStyleClass().remove("status-error");
        }
        apiStartupStatusLabel.setText(message);
    }

    private void fadeOutApiStartupOverlay() {
        FadeTransition transition = new FadeTransition(API_OVERLAY_FADE_DURATION, apiStartupOverlay);
        transition.setFromValue(1);
        transition.setToValue(0);
        transition.setOnFinished(event -> {
            apiStartupOverlay.setVisible(false);
            apiStartupOverlay.setManaged(false);
            apiStartupOverlay.setOpacity(1);
        });
        transition.play();
    }

    private Node createSidebar() {
        VBox sidebar = new VBox(22);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(24, 16, 24, 16));
        sidebar.setPrefWidth(200);
        sidebar.setMinWidth(180);
        sidebar.setMaxWidth(220);

        VBox brand = new VBox(12);
        brand.getStyleClass().add("brand-block");
        StackPane avatar = createIconBadge("PD", "badge-xl");
        Label title = new Label("POS Desktop");
        title.getStyleClass().add("sidebar-title");
        Label userLabel = new Label(authenticatedSession == null ? "" : authenticatedSession.nombreCompleto());
        userLabel.getStyleClass().add("sidebar-subtitle");
        userLabel.setWrapText(true);
        Label rolesLabel = new Label(authenticatedSession == null ? "" : formatRoleSummary(authenticatedSession.roles()));
        rolesLabel.getStyleClass().add("sidebar-helper");
        rolesLabel.setWrapText(true);
        brand.setAlignment(Pos.CENTER);
        brand.getChildren().addAll(avatar, title, userLabel, rolesLabel);

        ToggleGroup navigation = new ToggleGroup();
        VBox navButtons = new VBox(10);
        navButtons.setFillWidth(true);
        for (String key : screenFactories.keySet()) {
            ToggleButton button = new ToggleButton(key);
            button.setMaxWidth(Double.MAX_VALUE);
            button.setToggleGroup(navigation);
            button.getStyleClass().add("nav-button");
            button.setOnAction(event -> showScreen(key));
            navButtons.getChildren().add(button);
        }
        if (!navButtons.getChildren().isEmpty()) {
            ((ToggleButton) navButtons.getChildren().get(0)).setSelected(true);
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button logoutButton = new Button("Cerrar sesion");
        logoutButton.setMaxWidth(Double.MAX_VALUE);
        logoutButton.getStyleClass().add("nav-button");
        logoutButton.setOnAction(event -> {
            try {
                posApiClient.logout();
            } catch (RuntimeException ignored) {
                posApiClient.clearSession();
            }
            returnToLoginView();
        });

        sidebar.getChildren().addAll(brand, navButtons, spacer, logoutButton);
        return sidebar;
    }

    private void showScreen(String key) {
        Supplier<Node> screenFactory = screenFactories.get(key);
        if (screenFactory == null) {
            contentHost.getChildren().setAll(wrapContent(createNoAccessScreen()));
            return;
        }
        contentHost.getChildren().setAll(wrapContent(screenFactory.get()));
    }

    private void configureAuthorizedScreens() {
        screenFactories.clear();
        if (hasPermission(PERM_VENTAS_VIEW)) {
            screenFactories.put("Ventas", this::createSalesScreen);
        }
        if (hasPermission(PERM_CIERRES_VIEW)) {
            screenFactories.put("Cierre", this::createClosingScreen);
        }
        if (hasPermission(PERM_SEPARADOS_VIEW)) {
            screenFactories.put("Separados", this::createLayawayScreen);
        }
        if (hasPermission(PERM_MOVIMIENTOS_VIEW)) {
            screenFactories.put("Movimientos", this::createMovementsScreen);
        }
        if (hasPermission(PERM_FACTURAS_VIEW) && hasPermission(PERM_PROVEEDORES_VIEW)) {
            screenFactories.put("Facturas", this::createInvoicesScreen);
        }
        if (screenFactories.isEmpty()) {
            screenFactories.put("Acceso", this::createNoAccessScreen);
        }
    }

    private Node wrapContent(Node node) {
        ScrollPane scrollPane = new ScrollPane(node);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
        scrollPane.setPannable(false);
        scrollPane.getStyleClass().add("content-scroll");
        return scrollPane;
    }

    private Node createNoAccessScreen() {
        VBox root = createScreenContainer("Acceso restringido", "Tu usuario no tiene modulos asignados en este momento.");
        root.setAlignment(Pos.TOP_CENTER);
        VBox card = createCard(
                "Sin permisos operativos",
                "Solicita a un administrador que te asigne al menos un rol con acceso a modulos del POS."
        );
        card.setMaxWidth(520);
        card.getChildren().add(createProgressCard("Estado", 0.18, "La sesion esta activa, pero no hay secciones habilitadas."));
        root.getChildren().add(card);
        return root;
    }

    private boolean hasPermission(String permission) {
        return authenticatedSession != null
                && authenticatedSession.permisos() != null
                && authenticatedSession.permisos().contains(permission);
    }

    private void setNodeAllowed(Node node, boolean allowed) {
        if (node == null) {
            return;
        }
        node.setVisible(allowed);
        node.setManaged(allowed);
        if (node instanceof Button button) {
            if (!button.disableProperty().isBound()) {
                button.setDisable(!allowed);
            }
        }
    }

    private String formatRoleSummary(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return "Sin roles";
        }
        return String.join(" · ", roles);
    }

    private Node createSalesScreen() {
        VBox root = createScreenContainer("Sistema de Ventas", "");
        root.setSpacing(14);
        root.setPadding(new Insets(18, 22, 18, 22));

        TextField valorUnitarioField = createField("");
        valorUnitarioField.setPromptText("Valor unitario");
        TextField cantidadField = createField("1");
        TextField montoRecibidoField = createField("");
        montoRecibidoField.setPromptText("Monto recibido opcional");
        Label statusLabel = new Label("API configurada en 8083. Agrega articulos a la tabla para registrar la venta.");
        statusLabel.getStyleClass().add("card-subtitle");
        Label totalLabel = new Label(formatCurrency(saleTotal.get()));
        totalLabel.getStyleClass().add("amount-main");
        Label changeLabel = new Label("Monto recibido opcional");
        changeLabel.getStyleClass().add("amount-helper");
        CheckBox printReceiptCheck = new CheckBox("Imprimir comprobante");
        printReceiptCheck.getStyleClass().add("soft-check");

        TableView<SaleDraftRow> table = createSalesDraftTable();
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        table.setItems(saleDraftRows);

        Button payButton = createActionButton("Cobrar", "primary-button");
        payButton.setMaxWidth(Double.MAX_VALUE);
        payButton.setOnAction(event -> registerSale(montoRecibidoField, statusLabel, valorUnitarioField, printReceiptCheck));
        if (!hasPermission(PERM_VENTAS_EDIT)) {
            payButton.setDisable(true);
            statusLabel.setText("Tu usuario puede consultar la venta en pantalla, pero no registrar comprobantes.");
        }

        saleTotal.addListener((obs, oldValue, newValue) -> {
            totalLabel.setText(formatCurrency(newValue));
            updateReceivedLabel(montoRecibidoField.getText(), newValue, changeLabel);
        });
        montoRecibidoField.textProperty().addListener((obs, oldValue, newValue) -> {
            updateReceivedLabel(newValue, saleTotal.get(), changeLabel);
        });

        configureSalesFocusFlow(valorUnitarioField, cantidadField, statusLabel);
        montoRecibidoField.setOnAction(event -> registerSale(montoRecibidoField, statusLabel, valorUnitarioField, printReceiptCheck));
        root.addEventFilter(KeyEvent.KEY_TYPED, event -> {
            if ("+".equals(event.getCharacter())) {
                if (saleDraftRows.isEmpty()) {
                    setSalesStatusError(statusLabel, "Debe existir al menos un articulo en la tabla antes de cobrar.");
                } else {
                    montoRecibidoField.requestFocus();
                    montoRecibidoField.selectAll();
                }
                event.consume();
            }
        });
        Platform.runLater(valorUnitarioField::requestFocus);

        HBox layout = new HBox(16);
        layout.setAlignment(Pos.TOP_LEFT);

        VBox left = new VBox(12,
                createSalesEntryCard(cantidadField, valorUnitarioField, table, statusLabel),
                createSalesTableCard(table)
        );
        left.setMinWidth(0);
        HBox.setHgrow(left, Priority.ALWAYS);

        VBox right = new VBox(14, createSummaryCard(montoRecibidoField, totalLabel, changeLabel, printReceiptCheck, payButton));
        bindRegionWidthToScene(right, 0.22, 220, 280);

        layout.getChildren().addAll(left, right);
        root.getChildren().add(layout);
        return root;
    }

    private Node createClosingScreen() {
        VBox root = createScreenContainer("Cierre de caja", "Resumen diario elegante para validar ventas, base y consolidado.");
        tuneCompactScreen(root);

        LocalDate today = LocalDate.now();
        DatePicker fechaOperacionPicker = new DatePicker(today);
        DatePicker fechaInicialHistorialPicker = new DatePicker(today.withDayOfYear(1));
        DatePicker fechaFinalHistorialPicker = new DatePicker(today);
        ComboBox<String> estadoHistorialCombo = new ComboBox<>(FXCollections.observableArrayList(FILTER_ALL));
        estadoHistorialCombo.getSelectionModel().selectFirst();
        TextField baseField = createField("0");
        TextField trabajadorasField = createField("0");
        TextField ahorroField = createField("0");
        configureSelectAllOnFocus(baseField);
        configureSelectAllOnFocus(trabajadorasField);
        configureSelectAllOnFocus(ahorroField);
        TextArea observacionArea = new TextArea();
        observacionArea.setWrapText(true);
        observacionArea.setPrefRowCount(2);
        observacionArea.getStyleClass().add("soft-area");

        Label ventasValue = createMetricValueLabel("$ 0");
        Label ventasCaption = createMetricCaptionLabel("0 comprobantes");
        Label baseValue = createMetricValueLabel("$ 0");
        Label baseCaption = createMetricCaptionLabel("Configurado en cierre");
        Label totalValue = createMetricValueLabel("$ 0");
        Label totalCaption = createMetricCaptionLabel("Total proyectado");

        FlowPane cards = new FlowPane(12, 12,
                createMetricCard("Ventas del dia", ventasValue, ventasCaption),
                createMetricCard("Base sugerida", baseValue, baseCaption),
                createMetricCard("Total proyectado", totalValue, totalCaption)
        );

        ObservableList<PosApiClient.CierreDiarioListadoResponse> historySource = FXCollections.observableArrayList();
        FilteredList<PosApiClient.CierreDiarioListadoResponse> filteredHistory = new FilteredList<>(historySource);
        TableView<PosApiClient.CierreDiarioListadoResponse> historyTable = createClosingHistoryTable();
        historyTable.setItems(filteredHistory);
        Label historyFeedbackLabel = new Label("Cargando cierres del rango seleccionado...");
        historyFeedbackLabel.getStyleClass().add("history-filter-feedback");
        historyFeedbackLabel.setWrapText(true);
        Runnable refreshHistory = () -> loadClosingHistory(
                fechaInicialHistorialPicker.getValue(),
                fechaFinalHistorialPicker.getValue(),
                estadoHistorialCombo,
                historySource,
                filteredHistory,
                historyFeedbackLabel
        );

        estadoHistorialCombo.valueProperty().addListener((obs, oldValue, newValue) -> {
            applyClosingHistoryFilter(filteredHistory, newValue);
            updateClosingHistoryFeedback(
                    historyFeedbackLabel,
                    fechaInicialHistorialPicker.getValue(),
                    fechaFinalHistorialPicker.getValue(),
                    estadoHistorialCombo.getValue(),
                    filteredHistory
            );
        });
        filteredHistory.addListener((ListChangeListener<? super PosApiClient.CierreDiarioListadoResponse>) change ->
                updateClosingHistoryFeedback(
                        historyFeedbackLabel,
                        fechaInicialHistorialPicker.getValue(),
                        fechaFinalHistorialPicker.getValue(),
                        estadoHistorialCombo.getValue(),
                        filteredHistory
                )
        );

        HBox grid = createAdaptivePanelRow(
                createClosingFormCard(
                        hasPermission(PERM_CIERRES_EDIT),
                        fechaOperacionPicker,
                        baseField,
                        trabajadorasField,
                        ahorroField,
                        observacionArea,
                        ventasCaption,
                        ventasValue,
                        baseValue,
                        totalValue,
                        totalCaption,
                        refreshHistory
                ),
                createClosingHistoryCard(
                        historyTable,
                        fechaInicialHistorialPicker,
                        fechaFinalHistorialPicker,
                        estadoHistorialCombo,
                        historyFeedbackLabel,
                        refreshHistory
                )
        );
        VBox.setVgrow(grid, Priority.ALWAYS);

        root.getChildren().addAll(cards, grid);
        loadClosingSummary(
                fechaOperacionPicker.getValue(),
                baseField,
                trabajadorasField,
                ahorroField,
                observacionArea,
                ventasCaption,
                ventasValue,
                baseValue,
                totalValue,
                totalCaption
        );
        refreshHistory.run();
        Platform.runLater(baseField::requestFocus);
        return root;
    }

    private Node createLayawayScreen() {
        VBox root = createScreenContainer("Separados", "Vista comercial para apartados y seguimiento visual de saldos.");
        tuneCompactScreen(root);

        ObservableList<PosApiClient.SeparadoListadoResponse> layawaySource = FXCollections.observableArrayList();
        FilteredList<PosApiClient.SeparadoListadoResponse> filteredLayaways = new FilteredList<>(layawaySource);
        TableView<PosApiClient.SeparadoListadoResponse> table = createLayawayTable();
        table.setItems(filteredLayaways);

        TextField searchField = createField("");
        searchField.setPromptText("Buscar por cliente o numero");
        TextField articleFilterField = createField("");
        articleFilterField.setPromptText("Filtrar por articulo");
        ComboBox<String> statusFilter = new ComboBox<>(FXCollections.observableArrayList(
                FILTER_ALL, "Activo", "Pagado", "Entregado", "Cancelado"
        ));
        statusFilter.getSelectionModel().select("Activo");

        Label selectedNumberValue = createMetaValueLabel("-");
        Label selectedClientValue = createMetaValueLabel("-");
        Label selectedItemsValue = createMetaValueLabel("-");
        Label selectedStatusValue = createMetaValueLabel("-");
        Label minimumValue = createMetaValueLabel("-");
        ProgressBar paymentProgressBar = new ProgressBar(0);
        paymentProgressBar.setMaxWidth(Double.MAX_VALUE);
        paymentProgressBar.getStyleClass().add("accent-progress");
        Label paymentProgressCaption = new Label("Sin separado seleccionado");
        paymentProgressCaption.getStyleClass().add("progress-caption");

        Label activeValue = createMetaValueLabel("0 separados");
        Label paidValue = createMetaValueLabel("0 listos para entrega");
        Label totalBalanceValue = createMetaValueLabel(formatCurrency(BigDecimal.ZERO));
        Label paymentsTodayValue = createMetaValueLabel(formatCurrency(BigDecimal.ZERO));

        AtomicReference<String> selectedLayawayId = new AtomicReference<>();
        AtomicReference<PosApiClient.SeparadoDetalleResponse> selectedLayawayDetail = new AtomicReference<>();

        searchField.textProperty().addListener((obs, oldValue, newValue) -> applyLayawayFilters(
                filteredLayaways,
                newValue
        ));

        table.getSelectionModel().selectedItemProperty().addListener((obs, previous, selected) -> {
            if (selected == null) {
                selectedLayawayId.set(null);
                selectedLayawayDetail.set(null);
                updateLayawayProfile(
                        null,
                        selectedNumberValue,
                        selectedClientValue,
                        selectedItemsValue,
                        selectedStatusValue,
                        minimumValue,
                        paymentProgressBar,
                        paymentProgressCaption
                );
                return;
            }

            selectedLayawayId.set(selected.id());
            selectedNumberValue.setText(selected.numeroSeparado());
            selectedClientValue.setText(selected.cliente());
            selectedItemsValue.setText(selected.descripcionArticulos());
            selectedStatusValue.setText(formatLayawayStatus(selected.estado()));
            minimumValue.setText("Consultando...");
            paymentProgressBar.setProgress(calculateLayawayProgress(selected.valorTotal(), selected.totalAbonado()));
            paymentProgressCaption.setText(formatCurrency(selected.totalAbonado()) + " abonados de "
                    + formatCurrency(selected.valorTotal()));
            loadLayawayDetail(
                    selected.id(),
                    selectedLayawayId,
                    selectedLayawayDetail,
                    selectedNumberValue,
                    selectedClientValue,
                    selectedItemsValue,
                    selectedStatusValue,
                    minimumValue,
                    paymentProgressBar,
                    paymentProgressCaption
            );
        });
        Runnable refreshLayaways = () -> loadLayaways(
                layawaySource,
                filteredLayaways,
                table,
                searchField,
                articleFilterField,
                statusFilter,
                selectedLayawayId,
                selectedLayawayDetail,
                activeValue,
                paidValue,
                totalBalanceValue,
                paymentsTodayValue,
                selectedNumberValue,
                selectedClientValue,
                selectedItemsValue,
                selectedStatusValue,
                minimumValue,
                paymentProgressBar,
                paymentProgressCaption
        );
        articleFilterField.textProperty().addListener((obs, oldValue, newValue) -> refreshLayaways.run());
        statusFilter.valueProperty().addListener((obs, oldValue, newValue) -> refreshLayaways.run());
        table.setOnMouseClicked(event -> {
            if (event.getClickCount() < 2) {
                return;
            }
            PosApiClient.SeparadoListadoResponse selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            openLayawayPaymentWindow(
                    contentHost.getScene() == null ? null : contentHost.getScene().getWindow(),
                    selected.id(),
                    selectedLayawayDetail,
                    refreshLayaways
            );
        });

        HBox grid = createAdaptivePanelRow(
                createLayawayListCard(
                        searchField,
                        articleFilterField,
                        statusFilter,
                        table,
                        selectedNumberValue,
                        selectedClientValue,
                        selectedItemsValue,
                        selectedStatusValue,
                        minimumValue,
                        paymentProgressBar,
                        paymentProgressCaption
                ),
                createLayawayActionsCard(
                        hasPermission(PERM_SEPARADOS_EDIT),
                        () -> showNewLayawayWindow(
                                contentHost.getScene() == null ? null : contentHost.getScene().getWindow(),
                                refreshLayaways,
                                createdId -> {
                                    selectedLayawayId.set(createdId);
                                    refreshLayaways.run();
                                }
                        ),
                        () -> {
                            PosApiClient.SeparadoListadoResponse selected = table.getSelectionModel().getSelectedItem();
                            if (selected == null) {
                                showError("Separados", "Selecciona un separado para registrar un abono.");
                                return;
                            }
                            openLayawayPaymentWindow(
                                    contentHost.getScene() == null ? null : contentHost.getScene().getWindow(),
                                    selected.id(),
                                    selectedLayawayDetail,
                                    refreshLayaways
                            );
                        },
                        refreshLayaways,
                        () -> {
                            PosApiClient.SeparadoDetalleResponse detail = selectedLayawayDetail.get();
                            if (detail == null) {
                                showError("Separados", "Selecciona un separado para visualizar sus abonos.");
                                return;
                            }
                            showPaymentsWindow(
                                    contentHost.getScene() == null ? null : contentHost.getScene().getWindow(),
                                    detail
                            );
                        },
                        activeValue,
                        paidValue,
                        totalBalanceValue,
                        paymentsTodayValue
                )
        );
        VBox.setVgrow(grid, Priority.ALWAYS);
        root.getChildren().add(grid);
        refreshLayaways.run();
        return root;
    }

    private Node createInvoicesScreen() {
        VBox root = createScreenContainer(
                "Facturas",
                "Consulta proveedores, registra facturas y abonos usando la API real del POS."
        );
        tuneCompactScreen(root);
        root.getStyleClass().add("invoice-main-screen");
        root.setSpacing(10);
        bindRegionHeightToScene(root, 58, 520);

        ObservableList<PosApiClient.ProveedorResponse> providerSource = FXCollections.observableArrayList();
        FilteredList<PosApiClient.ProveedorResponse> filteredProviders = new FilteredList<>(providerSource);
        TableView<PosApiClient.ProveedorResponse> providerTable = createSupplierProvidersTable();
        providerTable.setItems(filteredProviders);
        AtomicReference<String> selectedProviderId = new AtomicReference<>();
        AtomicReference<List<PosApiClient.FacturaProveedorListadoResponse>> invoiceCache = new AtomicReference<>(List.of());

        TextField providerSearchField = createField("");
        providerSearchField.setPromptText("Buscar proveedor");
        providerSearchField.textProperty().addListener((obs, oldValue, newValue) -> {
            String normalized = newValue == null ? "" : newValue.trim().toLowerCase(Locale.ROOT);
            filteredProviders.setPredicate(provider -> {
                if (provider == null) {
                    return false;
                }
                if (normalized.isBlank()) {
                    return true;
                }
                return containsIgnoreCase(provider.nombre(), normalized)
                        || containsIgnoreCase(provider.nit(), normalized)
                        || containsIgnoreCase(provider.telefono(), normalized)
                        || containsIgnoreCase(provider.correo(), normalized);
            });
            if (!filteredProviders.isEmpty() && providerTable.getSelectionModel().getSelectedItem() == null) {
                providerTable.getSelectionModel().selectFirst();
            }
        });

        Label providersValue = createMetricValueLabel("0");
        Label providersCaption = createMetricCaptionLabel("Proveedores activos");
        Label invoicesValue = createMetricValueLabel("0");
        Label invoicesCaption = createMetricCaptionLabel("Facturas registradas");
        Label balanceValue = createMetricValueLabel("$ 0");
        Label balanceCaption = createMetricCaptionLabel("Saldo total con proveedores");
        Label paidInvoicesValue = createMetricValueLabel("0");
        Label paidInvoicesCaption = createMetricCaptionLabel("Facturas pagadas");

        Label selectedProviderValue = createMetaValueLabel("Selecciona un proveedor");
        Label providerNitValue = createMetaValueLabel("-");
        Label providerPhoneValue = createMetaValueLabel("-");
        Label providerEmailValue = createMetaValueLabel("-");
        Label providerAddressValue = createMetaValueLabel("-");
        Label providerDebtValue = createMetaValueLabel("$ 0");
        Label providerInvoicesValue = createMetaValueLabel("0 facturas");
        Label providerStatusValue = createMetaValueLabel("Sin facturas");
        ProgressBar providerExposureBar = new ProgressBar(0);
        providerExposureBar.setMaxWidth(Double.MAX_VALUE);
        providerExposureBar.getStyleClass().add("accent-progress");
        Label providerExposureCaption = new Label("Selecciona un proveedor para explorar su cartera");
        providerExposureCaption.getStyleClass().add("progress-caption");

        providerTable.getSelectionModel().selectedItemProperty().addListener((obs, previous, provider) -> {
            selectedProviderId.set(provider == null ? null : provider.id());
            updateProviderSummaryState(
                    provider,
                    invoiceCache.get(),
                    selectedProviderValue,
                    providerNitValue,
                    providerPhoneValue,
                    providerEmailValue,
                    providerAddressValue,
                    providerStatusValue,
                    providerDebtValue,
                    providerInvoicesValue,
                    providerExposureBar,
                    providerExposureCaption
            );
        });

        Runnable refreshProviders = () -> runAsync(
                () -> new InvoiceDashboardData(
                        posApiClient.listarProveedores(),
                        posApiClient.listarFacturas(null, null)
                ),
                data -> {
                    invoiceCache.set(data.facturas());
                    providerSource.setAll(data.proveedores());
                    updateInvoiceMetrics(
                            data.proveedores(),
                            data.facturas(),
                            providersValue,
                            providersCaption,
                            invoicesValue,
                            invoicesCaption,
                            balanceValue,
                            balanceCaption,
                            paidInvoicesValue,
                            paidInvoicesCaption
                    );

                    String selectedId = selectedProviderId.get();
                    if (selectedId != null && selectProviderRow(providerTable, selectedId)) {
                        return;
                    }
                    if (!filteredProviders.isEmpty()) {
                        providerTable.getSelectionModel().selectFirst();
                    } else {
                        providerTable.getSelectionModel().clearSelection();
                    }
                },
                exception -> showError("Facturas", exception.getMessage())
        );

        providerTable.setOnMouseClicked(event -> {
            if (event.getClickCount() < 2) {
                return;
            }
            PosApiClient.ProveedorResponse provider = providerTable.getSelectionModel().getSelectedItem();
            if (provider == null) {
                return;
            }
            showProviderInvoicesWindow(
                    contentHost.getScene() == null ? null : contentHost.getScene().getWindow(),
                    provider,
                    refreshProviders
            );
        });

        Button newProviderButton = createActionButton("Nuevo proveedor", "primary-button");
        newProviderButton.setMaxWidth(Double.MAX_VALUE);
        newProviderButton.setOnAction(event -> showProviderWindow(
                contentHost.getScene() == null ? null : contentHost.getScene().getWindow(),
                null,
                createdId -> {
                    selectedProviderId.set(createdId);
                    refreshProviders.run();
                }
        ));

        Button viewInvoicesButton = createActionButton("Ver facturas", "ghost-button");
        viewInvoicesButton.setMaxWidth(Double.MAX_VALUE);
        viewInvoicesButton.disableProperty().bind(Bindings.isNull(providerTable.getSelectionModel().selectedItemProperty()));
        viewInvoicesButton.setOnAction(event -> showProviderInvoicesWindow(
                contentHost.getScene() == null ? null : contentHost.getScene().getWindow(),
                providerTable.getSelectionModel().getSelectedItem(),
                refreshProviders
        ));

        Button refreshButton = createActionButton("Actualizar", "ghost-button");
        refreshButton.setMaxWidth(Double.MAX_VALUE);
        refreshButton.disableProperty().bind(Bindings.isNull(providerTable.getSelectionModel().selectedItemProperty()));
        refreshButton.setOnAction(event -> showUpdateProviderWindow(
                contentHost.getScene() == null ? null : contentHost.getScene().getWindow(),
                providerTable.getSelectionModel().getSelectedItem(),
                null,
                updatedId -> {
                    selectedProviderId.set(updatedId);
                    refreshProviders.run();
                }
        ));
        setNodeAllowed(newProviderButton, hasPermission(PERM_PROVEEDORES_EDIT));
        setNodeAllowed(refreshButton, hasPermission(PERM_PROVEEDORES_EDIT));

        selectedProviderValue.getStyleClass().add("invoice-provider-name");
        providerStatusValue.getStyleClass().add("invoice-provider-status");
        providerExposureCaption.getStyleClass().add("invoice-provider-caption");

        VBox providerIdentity = new VBox(2, createFormLabel("Proveedor seleccionado"), selectedProviderValue);
        providerIdentity.getStyleClass().add("invoice-provider-identity");

        FlowPane providerFacts = new FlowPane();
        providerFacts.setHgap(10);
        providerFacts.setVgap(8);
        providerFacts.getStyleClass().add("invoice-provider-flow");
        providerFacts.getChildren().addAll(
                createCompactInfoBlock("NIT", providerNitValue),
                createCompactInfoBlock("Telefono", providerPhoneValue),
                createCompactInfoBlock("Correo", providerEmailValue),
                createCompactInfoBlock("Direccion", providerAddressValue),
                createCompactInfoBlock("Estado", providerStatusValue),
                createCompactInfoBlock("Deuda", providerDebtValue),
                createCompactInfoBlock("Facturas", providerInvoicesValue)
        );

        VBox providerExposureBox = new VBox(4, createFormLabel("Cobertura del proveedor"), providerExposureBar, providerExposureCaption);
        providerExposureBox.getStyleClass().add("invoice-inline-progress");

        VBox providerSummary = new VBox(8, providerIdentity, providerFacts, providerExposureBox);
        providerSummary.getStyleClass().add("invoice-provider-summary");
        HBox.setHgrow(providerSummary, Priority.ALWAYS);

        newProviderButton.setMaxWidth(Double.MAX_VALUE);
        viewInvoicesButton.setMaxWidth(Double.MAX_VALUE);
        refreshButton.setMaxWidth(Double.MAX_VALUE);
        VBox actionsColumn = new VBox(8, newProviderButton, viewInvoicesButton, refreshButton);
        actionsColumn.getStyleClass().add("invoice-action-column");
        bindRegionWidthToScene(actionsColumn, 0.2, 210, 248);
        actionsColumn.setMaxWidth(Region.USE_PREF_SIZE);

        HBox topBand = new HBox(12, providerSummary, actionsColumn);
        topBand.getStyleClass().addAll("surface-card", "invoice-top-band");
        topBand.setAlignment(Pos.TOP_LEFT);
        topBand.setMaxHeight(Region.USE_PREF_SIZE);

        VBox providersCard = createCard(
                "Bandeja de proveedores",
                "Selecciona un proveedor para revisar su ficha y abrir su tablero de facturas."
        );
        providersCard.getStyleClass().add("invoice-compact-card");
        HBox.setHgrow(providersCard, Priority.ALWAYS);
        providersCard.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(providerTable, Priority.ALWAYS);
        HBox tableToolbar = new HBox(12, createFieldGroup("Buscar proveedor", providerSearchField, 280));
        tableToolbar.getStyleClass().add("invoice-table-toolbar");
        tableToolbar.setAlignment(Pos.BOTTOM_LEFT);
        providersCard.getChildren().addAll(tableToolbar, providerTable);

        VBox.setVgrow(providersCard, Priority.ALWAYS);
        root.getChildren().addAll(topBand, providersCard);
        refreshProviders.run();
        return root;
    }

    private Node createMovementsScreen() {
        VBox root = createScreenContainer("Movimientos de caja", "Consulta limpia para revisar ventas, recibidos, devoluciones y origen.");
        tuneCompactScreen(root);

        DatePicker fechaInicialPicker = new DatePicker(LocalDate.now());
        DatePicker fechaFinalPicker = new DatePicker(LocalDate.now());
        TableView<PosApiClient.MovimientoVentaResponse> movementsTable = createMovementsTable();
        Label totalCajaValue = createMetricValueLabel("$ 0");
        Label totalCajaCaption = createMetricCaptionLabel("Total de ventas");
        Label recibidoValue = createMetricValueLabel("$ 0");
        Label recibidoCaption = createMetricCaptionLabel("Monto recibido");
        Label devueltoValue = createMetricValueLabel("$ 0");
        Label devueltoCaption = createMetricCaptionLabel("Cambio entregado");

        HBox grid = createAdaptivePanelRow(
                createMovementsFilterCard(
                        fechaInicialPicker,
                        fechaFinalPicker,
                        movementsTable,
                        totalCajaValue,
                        totalCajaCaption,
                        recibidoValue,
                        recibidoCaption,
                        devueltoValue,
                        devueltoCaption
                ),
                createMovementsTableCard(movementsTable),
                createMovementsInsightsCard(
                        totalCajaValue,
                        totalCajaCaption,
                        recibidoValue,
                        recibidoCaption,
                        devueltoValue,
                        devueltoCaption
                )
        );
        VBox.setVgrow(grid, Priority.ALWAYS);
        root.getChildren().add(grid);
        loadMovements(
                fechaInicialPicker.getValue(),
                fechaFinalPicker.getValue(),
                movementsTable,
                totalCajaValue,
                totalCajaCaption,
                recibidoValue,
                recibidoCaption,
                devueltoValue,
                devueltoCaption
        );
        return root;
    }

    private VBox createScreenContainer(String title, String subtitle) {
        VBox container = new VBox(22);
        container.getStyleClass().add("screen-container");
        container.setPadding(new Insets(28, 28, 36, 28));

        HBox hero = new HBox(18);
        hero.setAlignment(Pos.CENTER_LEFT);
        StackPane badge = createIconBadge(initials(title), "badge-lg");
        VBox text = new VBox(4);
        Label heading = new Label(title);
        heading.getStyleClass().add("screen-title");
        text.getChildren().add(heading);
        if (subtitle != null && !subtitle.isBlank()) {
            Label copy = new Label(subtitle);
            copy.getStyleClass().add("screen-subtitle");
            text.getChildren().add(copy);
        }
        hero.getChildren().addAll(badge, text);

        container.getChildren().add(hero);
        return container;
    }

    private Node createSalesEntryCard(
            TextField cantidadField,
            TextField valorUnitarioField,
            TableView<SaleDraftRow> table,
            Label statusLabel
    ) {
        VBox card = createCard("", "");
        card.getStyleClass().add("sales-entry-card");

        HBox row = new HBox(16);
        row.setAlignment(Pos.BOTTOM_LEFT);

        VBox valor = createFieldGroup("Valor unitario", valorUnitarioField, 180);
        VBox cantidad = createFieldGroup("Cantidad", cantidadField, 120);

        HBox actions = new HBox(12,
                createActionButton("Agregar", "primary-button"),
                createActionButton("Quitar", "ghost-button")
        );
        actions.setAlignment(Pos.BOTTOM_LEFT);
        Button addButton = (Button) actions.getChildren().get(0);
        Button removeButton = (Button) actions.getChildren().get(1);
        addButton.setOnAction(event -> addSaleDetail(cantidadField, valorUnitarioField, statusLabel));
        removeButton.setOnAction(event -> removeSelectedSaleDetail(table, statusLabel));

        row.getChildren().addAll(valor, cantidad, actions);
        HBox.setHgrow(valor, Priority.ALWAYS);

        card.getChildren().addAll(row, statusLabel);
        return card;
    }

    private Node createSalesTableCard(TableView<SaleDraftRow> table) {
        VBox card = createCard("", "");
        card.getStyleClass().add("sales-table-card");
        card.getChildren().add(table);
        return card;
    }

    private Node createSummaryCard(
            TextField montoRecibidoField,
            Label totalLabel,
            Label changeLabel,
            CheckBox printReceiptCheck,
            Button payButton
    ) {
        VBox card = createCard("", "");
        card.getStyleClass().add("sales-summary-card");

        VBox amountBlock = new VBox(4);
        amountBlock.getStyleClass().add("amount-block");
        Label overline = new Label("Total de la venta");
        overline.getStyleClass().add("amount-overline");
        amountBlock.getChildren().addAll(overline, totalLabel, changeLabel);

        VBox recibido = createFieldGroup("Monto recibido", montoRecibidoField, 220);
        card.getChildren().addAll(printReceiptCheck, amountBlock, recibido, payButton);
        return card;
    }

    private TableView<SaleDraftRow> createSalesDraftTable() {
        TableView<SaleDraftRow> table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        bindTableHeightToScene(table, 0.32, 170, 280);
        table.getColumns().addAll(
                tableColumn("Articulo", row -> "Manual"),
                tableColumn("Cantidad", row -> formatNumber(row.cantidad())),
                tableColumn("Valor unitario", row -> formatCurrency(row.valorUnitario())),
                tableColumn("Total", row -> formatCurrency(row.total()))
        );
        return table;
    }

    private void addSaleDetail(
            TextField cantidadField,
            TextField valorUnitarioField,
            Label statusLabel
    ) {
        try {
            BigDecimal cantidad = parseRequiredPositive(cantidadField.getText(), "La cantidad debe ser mayor a cero.");
            BigDecimal valorUnitario = parseRequiredPositive(
                    valorUnitarioField.getText(),
                    "El valor unitario debe ser mayor a cero."
            );
            saleDraftRows.add(new SaleDraftRow(cantidad, valorUnitario));
            recalculateSaleTotal();

            cantidadField.setText("1");
            valorUnitarioField.clear();
            setSalesStatusInfo(statusLabel, "Articulo agregado a la tabla. Total actualizado en pantalla.");
            valorUnitarioField.requestFocus();
        } catch (IllegalArgumentException exception) {
            setSalesStatusError(statusLabel, exception.getMessage());
        }
    }

    private void removeSelectedSaleDetail(TableView<SaleDraftRow> table, Label statusLabel) {
        SaleDraftRow selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            setSalesStatusError(statusLabel, "Selecciona un detalle para quitarlo de la venta.");
            return;
        }

        saleDraftRows.remove(selected);
        recalculateSaleTotal();
        setSalesStatusInfo(statusLabel, "Detalle retirado de la venta.");
    }

    private void registerSale(
            TextField montoRecibidoField,
            Label statusLabel,
            TextField valorUnitarioField,
            CheckBox printReceiptCheck
    ) {
        if (saleDraftRows.isEmpty()) {
            setSalesStatusError(statusLabel, "Debe existir al menos un articulo en la tabla antes de cobrar.");
            return;
        }

        if (montoRecibidoField.getText() != null && !montoRecibidoField.getText().isBlank()) {
            BigDecimal montoRecibidoValidado = parseCurrencyOrZero(montoRecibidoField.getText());
            if (montoRecibidoValidado.signum() > 0 && montoRecibidoValidado.compareTo(saleTotal.get()) < 0) {
                setSalesStatusError(statusLabel, "El valor recibido no puede ser menor al valor total de la venta.");
                montoRecibidoField.requestFocus();
                montoRecibidoField.selectAll();
                return;
            }
        }

        BigDecimal montoRecibido = parseCurrencyOrZero(montoRecibidoField.getText());
        BigDecimal cambioEntregado = montoRecibido.subtract(saleTotal.get());
        if (!showChangeConfirmationDialog(statusLabel.getScene().getWindow(), montoRecibido, saleTotal.get(), cambioEntregado)) {
            setSalesStatusInfo(statusLabel, "Confirmacion de venta cancelada.");
            valorUnitarioField.requestFocus();
            return;
        }

        List<PosApiClient.RegistrarDetalleVentaRequest> detalles = saleDraftRows.stream()
                .map(row -> new PosApiClient.RegistrarDetalleVentaRequest(
                        null,
                        row.cantidad(),
                        row.valorUnitario()
                ))
                .toList();

        setSalesStatusInfo(statusLabel, "Registrando venta en la API...");
        runAsync(
                () -> posApiClient.registrarVenta(new PosApiClient.RegistrarVentaRequest(detalles, montoRecibido, null)),
                response -> {
                    saleDraftRows.clear();
                    recalculateSaleTotal();
                    montoRecibidoField.clear();
                    setSalesStatusInfo(statusLabel, "Venta " + response.numeroVenta() + " registrada correctamente.");
                    valorUnitarioField.requestFocus();
                    if (printReceiptCheck.isSelected()) {
                        showReceiptPreviewAndPrint(statusLabel.getScene().getWindow(), response);
                    } else {
                        showInfo("Venta registrada", "Se registró la venta " + response.numeroVenta()
                                + " por " + formatCurrency(response.total()) + ".");
                    }
                },
                exception -> setSalesStatusError(statusLabel, exception.getMessage())
        );
    }

    private void recalculateSaleTotal() {
        BigDecimal total = saleDraftRows.stream()
                .map(SaleDraftRow::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        saleTotal.set(total.setScale(2, RoundingMode.HALF_UP));
    }

    private void configureSalesFocusFlow(
            TextField valorUnitarioField,
            TextField cantidadField,
            Label statusLabel
    ) {
        valorUnitarioField.setOnAction(event -> cantidadField.requestFocus());
        cantidadField.setOnAction(event -> addSaleDetail(cantidadField, valorUnitarioField, statusLabel));
    }

    private void updateReceivedLabel(String inputValue, BigDecimal totalVenta, Label changeLabel) {
        if (inputValue == null || inputValue.isBlank()) {
            changeLabel.setText("Monto recibido opcional");
            return;
        }

        BigDecimal recibido = parseCurrencyOrZero(inputValue);
        BigDecimal diferencia = recibido.subtract(totalVenta == null ? BigDecimal.ZERO : totalVenta);
        if (diferencia.signum() >= 0) {
            changeLabel.setText("Debe devolver: " + formatCurrency(diferencia));
            return;
        }

        changeLabel.setText("Faltan: " + formatCurrency(diferencia.abs()));
    }

    private void setSalesStatusInfo(Label statusLabel, String message) {
        statusLabel.getStyleClass().remove("status-error");
        statusLabel.setText(message);
    }

    private void setSalesStatusError(Label statusLabel, String message) {
        if (!statusLabel.getStyleClass().contains("status-error")) {
            statusLabel.getStyleClass().add("status-error");
        }
        statusLabel.setText(message);
    }

    private boolean showChangeConfirmationDialog(
            Window owner,
            BigDecimal montoRecibido,
            BigDecimal totalVenta,
            BigDecimal cambioEntregado
    ) {
        AtomicBoolean confirmed = new AtomicBoolean(false);
        Stage stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.TRANSPARENT);

        StackPane root = new StackPane();
        root.getStyleClass().add("dialog-host");
        root.setPadding(new Insets(24));

        VBox card = new VBox(14);
        card.getStyleClass().addAll("surface-card", "change-confirm-card");

        Label overline = new Label("Confirmacion de caja");
        overline.getStyleClass().add("change-confirm-overline");

        Label title = new Label("Debe devolver");
        title.getStyleClass().add("change-confirm-title");

        BigDecimal cambioVisual = cambioEntregado.signum() > 0 ? cambioEntregado : BigDecimal.ZERO;
        Label amount = new Label(formatCurrency(cambioVisual));
        amount.getStyleClass().add("change-confirm-amount");

        Label helper = new Label(buildChangeConfirmationHelper(montoRecibido, totalVenta, cambioVisual));
        helper.getStyleClass().add("change-confirm-helper");

        Label note = new Label("Verifica el valor visualmente antes de confirmar la venta.");
        note.getStyleClass().add("change-confirm-note");

        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER);
        Button cancelButton = createActionButton("Volver", "ghost-button");
        Button confirmButton = createActionButton("Confirmar venta", "primary-button");
        confirmButton.setDefaultButton(true);
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(event -> stage.close());
        confirmButton.setOnAction(event -> {
            confirmed.set(true);
            stage.close();
        });
        actions.getChildren().addAll(cancelButton, confirmButton);

        card.getChildren().addAll(overline, title, amount, helper, note, actions);
        root.getChildren().add(card);

        Scene scene = new Scene(root, 520, 360);
        scene.getStylesheets().add(getClass().getResource("/com/posdesktop/pos/mockfx/mock-theme.css").toExternalForm());
        scene.setFill(Color.TRANSPARENT);
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                confirmButton.fire();
                event.consume();
            }
        });
        stage.setScene(scene);
        stage.setOnShown(event -> Platform.runLater(confirmButton::requestFocus));
        stage.showAndWait();
        return confirmed.get();
    }

    private String buildChangeConfirmationHelper(
            BigDecimal montoRecibido,
            BigDecimal totalVenta,
            BigDecimal cambioVisual
    ) {
        if (montoRecibido == null || montoRecibido.signum() == 0) {
            return "No se registro monto recibido. La venta quedara con devolucion en $0.";
        }
        if (cambioVisual.signum() == 0) {
            return "El valor recibido coincide con el total. No debes devolver dinero.";
        }
        return "Recibido " + formatCurrency(montoRecibido) + " sobre un total de " + formatCurrency(totalVenta) + ".";
    }

    private void showReceiptPreviewAndPrint(Window owner, PosApiClient.VentaRegistradaResponse response) {
        Stage stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.NONE);
        stage.initStyle(StageStyle.TRANSPARENT);

        StackPane root = new StackPane();
        root.getStyleClass().add("dialog-host");
        root.setPadding(new Insets(20));

        VBox shell = new VBox(16);
        shell.getStyleClass().add("receipt-preview-shell");
        shell.setAlignment(Pos.CENTER);

        Label title = new Label("Previsualizacion del comprobante");
        title.getStyleClass().add("receipt-preview-title");

        Label printStatus = new Label("Enviando recibo automaticamente a la impresora...");
        printStatus.getStyleClass().add("receipt-preview-status");

        VBox receiptPaper = buildReceiptPaper(response);

        Button closeButton = createActionButton("Cerrar", "ghost-button");
        closeButton.setOnAction(event -> stage.close());

        shell.getChildren().addAll(title, receiptPaper, printStatus, closeButton);
        root.getChildren().add(shell);

        Scene scene = new Scene(root, 420, 760);
        scene.getStylesheets().add(getClass().getResource("/com/posdesktop/pos/mockfx/mock-theme.css").toExternalForm());
        scene.setFill(Color.TRANSPARENT);
        stage.setScene(scene);
        stage.setOnShown(event -> Platform.runLater(() -> {
            closeButton.requestFocus();
            boolean printed = printReceipt(response);
            if (printed) {
                printStatus.getStyleClass().remove("status-error");
                printStatus.setText("Recibo enviado a la impresora.");
            } else {
                if (!printStatus.getStyleClass().contains("status-error")) {
                    printStatus.getStyleClass().add("status-error");
                }
                printStatus.setText("No fue posible imprimir automaticamente. La previsualizacion queda disponible.");
            }
        }));
        stage.show();
    }

    private VBox buildReceiptPaper(PosApiClient.VentaRegistradaResponse response) {
        VBox paper = new VBox(8);
        paper.getStyleClass().add("receipt-paper");

        Label business = new Label(RECEIPT_BUSINESS_NAME);
        business.getStyleClass().add("receipt-brand");

        Label owner = new Label(RECEIPT_OWNER_NAME);
        owner.getStyleClass().add("receipt-owner");

        Label address = new Label(RECEIPT_ADDRESS);
        address.getStyleClass().add("receipt-meta");

        Label nit = new Label(RECEIPT_NIT);
        nit.getStyleClass().add("receipt-meta");

        VBox header = new VBox(2, business, owner, address, nit);
        header.setAlignment(Pos.CENTER);

        VBox metaBlock = new VBox(2,
                createReceiptMetaRow("Factura N", response.numeroVenta()),
                createReceiptMetaRow("Cajero", RECEIPT_CASHIER),
                createReceiptMetaRow(
                        "Fecha",
                        RECEIPT_DATE_FORMATTER.format(response.fechaVenta()) + "   Hora: "
                                + formatReceiptTime(response.fechaVenta())
                )
        );

        VBox lines = new VBox(2);
        lines.getChildren().addAll(
                createReceiptHeaderRow(),
                createReceiptDivider()
        );
        for (PosApiClient.DetalleVentaResponse detail : response.detalles()) {
            lines.getChildren().add(createReceiptItemRow(detail));
        }
        lines.getChildren().add(createReceiptDivider());

        BigDecimal totalItems = response.detalles().stream()
                .map(PosApiClient.DetalleVentaResponse::cantidad)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        HBox totals = new HBox();
        totals.getStyleClass().add("receipt-total-row");
        Label itemsLabel = new Label("Items: " + formatReceiptQuantity(totalItems));
        itemsLabel.getStyleClass().add("receipt-total-label");
        Label totalLabel = new Label("Total: " + formatReceiptAmount(response.total()));
        totalLabel.getStyleClass().add("receipt-total-amount");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        totals.getChildren().addAll(itemsLabel, spacer, totalLabel);

        VBox paymentBlock = new VBox(2,
                createReceiptMetaRow("Recibido", formatReceiptAmount(response.montoRecibido())),
                createReceiptMetaRow("Devuelto", formatReceiptAmount(response.cambioEntregado().max(BigDecimal.ZERO)))
        );

        Label thanks = new Label("GRACIAS POR PREFERIRNOS");
        thanks.getStyleClass().add("receipt-thanks");

        paper.getChildren().addAll(header, metaBlock, lines, totals, paymentBlock, thanks);
        return paper;
    }

    private HBox createReceiptMetaRow(String label, String value) {
        HBox row = new HBox(8);
        row.getStyleClass().add("receipt-meta-row");
        Label left = new Label(label + ":");
        left.getStyleClass().add("receipt-meta-key");
        Label right = new Label(value);
        right.getStyleClass().add("receipt-meta-value");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().addAll(left, spacer, right);
        return row;
    }

    private HBox createReceiptHeaderRow() {
        HBox row = new HBox(8);
        row.getStyleClass().add("receipt-header-row");
        row.getChildren().addAll(
                createReceiptColumnLabel("Cant.", "receipt-col-qty"),
                createReceiptColumnLabel("Descripcion", "receipt-col-desc"),
                createReceiptColumnLabel("Valor", "receipt-col-money"),
                createReceiptColumnLabel("Total", "receipt-col-money")
        );
        return row;
    }

    private Label createReceiptDivider() {
        Label divider = new Label("**********************************************");
        divider.getStyleClass().add("receipt-divider");
        return divider;
    }

    private HBox createReceiptItemRow(PosApiClient.DetalleVentaResponse detail) {
        HBox row = new HBox(8);
        row.getStyleClass().add("receipt-item-row");
        row.getChildren().addAll(
                createReceiptColumnLabel(formatReceiptQuantity(detail.cantidad()), "receipt-col-qty"),
                createReceiptColumnLabel(resolveReceiptDescription(detail), "receipt-col-desc"),
                createReceiptColumnLabel(formatReceiptAmount(detail.valorUnitario()), "receipt-col-money"),
                createReceiptColumnLabel(formatReceiptAmount(detail.total()), "receipt-col-money")
        );
        return row;
    }

    private Label createReceiptColumnLabel(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().addAll("receipt-text", styleClass);
        return label;
    }

    private boolean printReceipt(PosApiClient.VentaRegistradaResponse response) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            return false;
        }

        VBox printNode = buildReceiptPaper(response);
        StackPane printRoot = new StackPane(printNode);
        printRoot.setPadding(new Insets(12));
        Scene printScene = new Scene(printRoot);
        printScene.getStylesheets().add(getClass().getResource("/com/posdesktop/pos/mockfx/mock-theme.css").toExternalForm());
        printRoot.applyCss();
        printRoot.layout();

        job.getJobSettings().setJobName("Recibo " + response.numeroVenta());
        boolean printed = job.printPage(printNode);
        if (printed) {
            job.endJob();
        }
        return printed;
    }

    private String resolveReceiptDescription(PosApiClient.DetalleVentaResponse detail) {
        if (detail.descripcion() == null || detail.descripcion().isBlank() || "Venta manual".equalsIgnoreCase(detail.descripcion())) {
            return "Item" + detail.orden();
        }
        return detail.descripcion();
    }

    private TableView<PosApiClient.CierreDiarioListadoResponse> createClosingHistoryTable() {
        TableView<PosApiClient.CierreDiarioListadoResponse> table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        bindTableHeightToScene(table, 0.24, 138, 205);
        table.getColumns().addAll(
                tableColumn("Fecha", row -> row.fechaOperacion().toString()),
                tableColumn("Ventas", row -> String.valueOf(row.cantidadVentas())),
                tableColumn("Total ventas", row -> formatCurrency(row.totalVentas())),
                tableColumn("Base", row -> formatCurrency(row.baseCaja())),
                tableColumn("Trabajadoras", row -> formatCurrency(row.trabajadoras())),
                tableColumn("Ahorro", row -> formatCurrency(row.ahorro())),
                tableColumn("Total cierre", row -> formatCurrency(row.totalFinal()))
        );
        return table;
    }

    private TableView<PosApiClient.MovimientoVentaResponse> createMovementsTable() {
        TableView<PosApiClient.MovimientoVentaResponse> table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        bindTableHeightToScene(table, 0.24, 140, 208);
        table.getColumns().addAll(
                tableColumn("Numero", PosApiClient.MovimientoVentaResponse::numeroVenta),
                tableColumn("Origen", PosApiClient.MovimientoVentaResponse::origen),
                tableColumn("Total", row -> formatCurrency(row.total())),
                tableColumn("Recibido", row -> formatCurrency(row.montoRecibido())),
                tableColumn("Devuelto", row -> formatCurrency(row.cambioEntregado())),
                tableColumn("Fecha", row -> formatDateTime(row.fechaVenta()))
        );
        return table;
    }

    private void loadClosingSummary(
            LocalDate fechaOperacion,
            TextField baseField,
            TextField trabajadorasField,
            TextField ahorroField,
            TextArea observacionArea,
            Label ventasCaption,
            Label ventasValue,
            Label baseValue,
            Label totalValue,
            Label totalCaption
    ) {
        LocalDate fecha = fechaOperacion == null ? LocalDate.now() : fechaOperacion;
        runAsync(
                () -> posApiClient.consultarResumenCierre(fecha),
                resumen -> {
                    ventasValue.setText(formatCurrency(resumen.totalVentas()));
                    ventasCaption.setText(resumen.cantidadVentas() + " comprobantes");
                    baseValue.setText(formatCurrency(resumen.baseCaja()));
                    totalValue.setText(formatCurrency(resumen.totalFinal()));
                    totalCaption.setText(resumen.cierreGuardado() ? "Cierre guardado: " + resumen.estado() : "Pendiente por guardar");
                    baseField.setText(formatPlainNumber(resumen.baseCaja()));
                    trabajadorasField.setText(formatPlainNumber(resumen.trabajadoras()));
                    ahorroField.setText(formatPlainNumber(resumen.ahorro()));
                    observacionArea.setText(resumen.observacion() == null ? "" : resumen.observacion());
                },
                exception -> showError("Cierre de caja", exception.getMessage())
        );
    }

    private void loadClosingHistory(
            LocalDate fechaInicial,
            LocalDate fechaFinal,
            ComboBox<String> statusFilter,
            ObservableList<PosApiClient.CierreDiarioListadoResponse> historySource,
            FilteredList<PosApiClient.CierreDiarioListadoResponse> filteredHistory,
            Label historyFeedbackLabel
    ) {
        LocalDate inicio = fechaInicial == null ? LocalDate.now().withDayOfYear(1) : fechaInicial;
        LocalDate fin = fechaFinal == null ? LocalDate.now() : fechaFinal;
        if (fin.isBefore(inicio)) {
            showError("Historial de cierres", "La fecha final no puede ser menor a la fecha inicial.");
            return;
        }

        historyFeedbackLabel.setText("Consultando cierres entre " + formatDateRange(inicio, fin) + "...");
        runAsync(
                () -> posApiClient.listarCierres(inicio, fin).stream()
                        .sorted(Comparator
                                .comparing(PosApiClient.CierreDiarioListadoResponse::fechaOperacion, Comparator.nullsLast(Comparator.naturalOrder()))
                                .thenComparing(PosApiClient.CierreDiarioListadoResponse::fechaHoraCierre, Comparator.nullsLast(Comparator.naturalOrder()))
                                .reversed())
                        .toList(),
                cierres -> {
                    updateClosingStatusOptions(statusFilter, cierres);
                    historySource.setAll(cierres);
                    applyClosingHistoryFilter(filteredHistory, statusFilter.getValue());
                    updateClosingHistoryFeedback(
                            historyFeedbackLabel,
                            inicio,
                            fin,
                            statusFilter.getValue(),
                            filteredHistory
                    );
                },
                exception -> {
                    historySource.clear();
                    updateClosingHistoryFeedback(
                            historyFeedbackLabel,
                            inicio,
                            fin,
                            statusFilter.getValue(),
                            filteredHistory
                    );
                    showError("Historial de cierres", exception.getMessage());
                }
        );
    }

    private void saveClosing(
            DatePicker fechaOperacionPicker,
            TextField baseField,
            TextField trabajadorasField,
            TextField ahorroField,
            TextArea observacionArea,
            Label ventasCaption,
            Label ventasValue,
            Label baseValue,
            Label totalValue,
            Label totalCaption,
            Runnable refreshHistoryAction
    ) {
        try {
            LocalDate fecha = fechaOperacionPicker.getValue() == null ? LocalDate.now() : fechaOperacionPicker.getValue();
            PosApiClient.RegistrarCierreRequest request = new PosApiClient.RegistrarCierreRequest(
                    fecha,
                    parseCurrencyOrZero(baseField.getText()),
                    parseCurrencyOrZero(trabajadorasField.getText()),
                    parseCurrencyOrZero(ahorroField.getText()),
                    observacionArea.getText()
            );

            runAsync(
                    () -> posApiClient.registrarCierre(request),
                    response -> {
                        showInfo("Cierre registrado", "Se registró el cierre del " + response.fechaOperacion() + ".");
                        loadClosingSummary(
                                fecha,
                                baseField,
                                trabajadorasField,
                                ahorroField,
                                observacionArea,
                                ventasCaption,
                                ventasValue,
                                baseValue,
                                totalValue,
                                totalCaption
                        );
                        refreshHistoryAction.run();
                    },
                    exception -> showError("Cierre de caja", exception.getMessage())
            );
        } catch (IllegalArgumentException exception) {
            showError("Cierre de caja", exception.getMessage());
        }
    }

    private void loadMovements(
            LocalDate fechaInicial,
            LocalDate fechaFinal,
            TableView<PosApiClient.MovimientoVentaResponse> table,
            Label totalCajaValue,
            Label totalCajaCaption,
            Label recibidoValue,
            Label recibidoCaption,
            Label devueltoValue,
            Label devueltoCaption
    ) {
        LocalDate inicio = fechaInicial == null ? LocalDate.now() : fechaInicial;
        LocalDate fin = fechaFinal == null ? inicio : fechaFinal;
        runAsync(
                () -> posApiClient.listarMovimientos(inicio, fin),
                movimientos -> {
                    table.setItems(FXCollections.observableArrayList(movimientos));
                    BigDecimal total = movimientos.stream()
                            .map(PosApiClient.MovimientoVentaResponse::total)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal recibido = movimientos.stream()
                            .map(PosApiClient.MovimientoVentaResponse::montoRecibido)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal devuelto = movimientos.stream()
                            .map(PosApiClient.MovimientoVentaResponse::cambioEntregado)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    totalCajaValue.setText(formatCurrency(total));
                    totalCajaCaption.setText(movimientos.size() + " movimientos");
                    recibidoValue.setText(formatCurrency(recibido));
                    recibidoCaption.setText("Monto recibido");
                    devueltoValue.setText(formatCurrency(devuelto));
                    devueltoCaption.setText("Cambio entregado");
                },
                exception -> showError("Movimientos de caja", exception.getMessage())
        );
    }

    private Node createClosingFormCard(
            boolean editAllowed,
            DatePicker fechaOperacionPicker,
            TextField baseField,
            TextField trabajadorasField,
            TextField ahorroField,
            TextArea observacionArea,
            Label ventasCaption,
            Label ventasValue,
            Label baseValue,
            Label totalValue,
            Label totalCaption,
            Runnable refreshHistoryAction
    ) {
        VBox card = createCard("Consolidar cierre", "Formulario mock para totalizar y guardar el resumen del dia.");
        bindRegionWidthToScene(card, 0.22, 236, 290);
        card.setMaxWidth(Region.USE_PREF_SIZE);

        FlowPane fields = createResponsiveRow(
                createFieldGroup("Fecha de operacion", fechaOperacionPicker, 200),
                createFieldGroup("Base", baseField, 200),
                createFieldGroup("Trabajadoras", trabajadorasField, 200),
                createFieldGroup("Ahorro", ahorroField, 200)
        );

        Button save = createActionButton("Guardar cierre", "primary-button");
        save.setMaxWidth(Double.MAX_VALUE);
        save.setOnAction(event -> saveClosing(
                fechaOperacionPicker,
                baseField,
                trabajadorasField,
                ahorroField,
                observacionArea,
                ventasCaption,
                ventasValue,
                baseValue,
                totalValue,
                totalCaption,
                refreshHistoryAction
        ));
        save.setDisable(!editAllowed);
        fechaOperacionPicker.setDisable(!editAllowed);
        baseField.setDisable(!editAllowed);
        trabajadorasField.setDisable(!editAllowed);
        ahorroField.setDisable(!editAllowed);
        observacionArea.setDisable(!editAllowed);
        configureClosingFocusFlow(baseField, trabajadorasField, ahorroField, save);

        fechaOperacionPicker.valueProperty().addListener((obs, oldValue, newValue) -> loadClosingSummary(
                newValue,
                baseField,
                trabajadorasField,
                ahorroField,
                observacionArea,
                ventasCaption,
                ventasValue,
                baseValue,
                totalValue,
                totalCaption
        ));

        card.getChildren().addAll(fields, observacionArea, save);
        return card;
    }

    private Node createClosingHistoryCard(
            TableView<PosApiClient.CierreDiarioListadoResponse> table,
            DatePicker fechaInicialPicker,
            DatePicker fechaFinalPicker,
            ComboBox<String> estadoHistorialCombo,
            Label historyFeedbackLabel,
            Runnable refreshHistoryAction
    ) {
        VBox card = createCard("Historial de cierres", "Filtra por rango y estado antes de totalizar el consolidado.");
        card.getStyleClass().add("closing-history-card");
        HBox.setHgrow(card, Priority.ALWAYS);
        FlowPane filters = createResponsiveRow(
                createFieldGroup("Fecha inicial", fechaInicialPicker, 184),
                createFieldGroup("Fecha final", fechaFinalPicker, 184),
                createFieldGroup("Estado", estadoHistorialCombo, 184)
        );

        Button search = createActionButton("Buscar cierres", "ghost-button");
        search.setOnAction(event -> refreshHistoryAction.run());

        Button totalize = createActionButton("Totalizar", "primary-button");
        totalize.setOnAction(event -> showClosingTotalsWindow(
                totalize.getScene() == null ? null : totalize.getScene().getWindow(),
                fechaInicialPicker.getValue(),
                fechaFinalPicker.getValue(),
                estadoHistorialCombo.getValue(),
                new ArrayList<>(table.getItems())
        ));

        Region spacer = new Region();
        HBox.setHgrow(historyFeedbackLabel, Priority.ALWAYS);
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(12, historyFeedbackLabel, spacer, search, totalize);
        actions.getStyleClass().add("closing-history-actions");
        actions.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(filters, actions, table);
        return card;
    }

    private void updateClosingStatusOptions(
            ComboBox<String> statusFilter,
            List<PosApiClient.CierreDiarioListadoResponse> cierres
    ) {
        String selectedStatus = statusFilter.getValue();
        List<String> options = new ArrayList<>();
        options.add(FILTER_ALL);
        cierres.stream()
                .map(PosApiClient.CierreDiarioListadoResponse::estado)
                .filter(estado -> estado != null && !estado.isBlank())
                .distinct()
                .sorted()
                .forEach(options::add);
        statusFilter.setItems(FXCollections.observableArrayList(options));
        if (selectedStatus != null && options.contains(selectedStatus)) {
            statusFilter.getSelectionModel().select(selectedStatus);
            return;
        }
        statusFilter.getSelectionModel().selectFirst();
    }

    private void applyClosingHistoryFilter(
            FilteredList<PosApiClient.CierreDiarioListadoResponse> filteredHistory,
            String selectedStatus
    ) {
        String status = selectedStatus == null || selectedStatus.isBlank() ? FILTER_ALL : selectedStatus;
        filteredHistory.setPredicate(row -> FILTER_ALL.equalsIgnoreCase(status)
                || status.equalsIgnoreCase(row.estado()));
    }

    private void updateClosingHistoryFeedback(
            Label historyFeedbackLabel,
            LocalDate fechaInicial,
            LocalDate fechaFinal,
            String selectedStatus,
            List<PosApiClient.CierreDiarioListadoResponse> filteredHistory
    ) {
        String status = selectedStatus == null || selectedStatus.isBlank() ? FILTER_ALL : selectedStatus;
        BigDecimal totalFinal = filteredHistory.stream()
                .map(PosApiClient.CierreDiarioListadoResponse::totalFinal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        historyFeedbackLabel.setText(
                filteredHistory.size() + " cierre(s) en " + formatDateRange(fechaInicial, fechaFinal)
                        + " | Estado: " + status
                        + " | Total final: " + formatCurrency(totalFinal)
        );
    }

    private void showClosingTotalsWindow(
            Window owner,
            LocalDate fechaInicial,
            LocalDate fechaFinal,
            String status,
            List<PosApiClient.CierreDiarioListadoResponse> cierres
    ) {
        ClosingTotals totals = calculateClosingTotals(cierres);
        Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
        double dialogWidth = Math.min(940, visualBounds.getWidth() * 0.88);
        double contentWidth = Math.min(900, dialogWidth - 12);

        Stage stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.TRANSPARENT);

        Label overline = new Label("Totalizacion inteligente");
        overline.getStyleClass().add("change-confirm-overline");

        Label title = new Label("Consolidado de cierres");
        title.getStyleClass().add("closing-totalizer-title");

        Label subtitle = new Label(
                "Rango " + formatDateRange(fechaInicial, fechaFinal) + " | Estado " + normalizeStatusLabel(status)
        );
        subtitle.getStyleClass().add("closing-totalizer-subtitle");
        subtitle.setWrapText(true);

        FlowPane metrics = new FlowPane(10, 10,
                createTotalizerMetricCard("Cierres", String.valueOf(totals.cantidadCierres()), "Registros visibles"),
                createTotalizerMetricCard("Ventas", formatCurrency(totals.totalVentas()), totals.cantidadVentas() + " comprobantes"),
                createTotalizerMetricCard("Total final", formatCurrency(totals.totalFinal()), "Con base, ahorro y trabajadoras")
        );
        metrics.setPrefWrapLength(Math.max(320, contentWidth - 24));

        VBox detailCard = createCard("Desglose del rango", "Lectura lista para validar el cierre consolidado.");
        detailCard.getStyleClass().add("closing-totalizer-detail-card");
        detailCard.setMaxWidth(Double.MAX_VALUE);
        detailCard.getChildren().add(createClosingTotalsDetailLayout(totals, status));

        Label note = new Label(
                totals.cantidadCierres() == 0
                        ? "No hay cierres para totalizar con los filtros actuales. Ajusta el rango o el estado y vuelve a consultar."
                        : "Los valores corresponden exactamente a los cierres visibles en el historial filtrado."
        );
        note.getStyleClass().add("closing-totalizer-note");
        note.setWrapText(true);

        Button closeButton = createActionButton("Cerrar", "ghost-button");
        closeButton.setOnAction(event -> stage.close());

        VBox heading = new VBox(6, overline, title, subtitle);
        HBox header = new HBox(16, heading);
        header.setAlignment(Pos.TOP_LEFT);

        HBox footer = new HBox(closeButton);
        footer.setAlignment(Pos.CENTER_RIGHT);

        VBox content = new VBox(14, header, metrics, detailCard, note, footer);
        content.getStyleClass().addAll("surface-card", "closing-totalizer-card");
        content.setMaxWidth(contentWidth);
        content.setPrefWidth(contentWidth);
        content.setMinWidth(contentWidth);
        content.setFillWidth(true);

        StackPane root = new StackPane(content);
        root.getStyleClass().add("closing-totalizer-overlay");
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(6));

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/com/posdesktop/pos/mockfx/mock-theme.css").toExternalForm());
        scene.setFill(Color.TRANSPARENT);
        stage.setScene(scene);
        stage.sizeToScene();
        stage.centerOnScreen();
        stage.setOnShown(event -> Platform.runLater(closeButton::requestFocus));
        stage.showAndWait();
    }

    private VBox createTotalizerMetricCard(String label, String value, String caption) {
        VBox card = new VBox(8);
        card.getStyleClass().addAll("metric-card", "closing-totalizer-metric");
        card.setPrefWidth(188);
        Label overline = new Label(label);
        overline.getStyleClass().add("metric-label");
        Label number = createMetricValueLabel(value);
        Label helper = createMetricCaptionLabel(caption);
        card.getChildren().addAll(overline, number, helper);
        return card;
    }

    private HBox createClosingTotalsDetailLayout(ClosingTotals totals, String status) {
        VBox leftColumn = new VBox(8,
                createClosingTotalsDetailRow("Base acumulada", formatCurrency(totals.totalBaseCaja())),
                createClosingTotalsDetailRow("Trabajadoras", formatCurrency(totals.totalTrabajadoras())),
                createClosingTotalsDetailRow("Ahorro", formatCurrency(totals.totalAhorro()))
        );
        leftColumn.getStyleClass().add("closing-totalizer-detail-column");

        VBox rightColumn = new VBox(8,
                createClosingTotalsDetailRow("Promedio", formatCurrency(totals.promedioPorCierre())),
                createClosingTotalsDetailRow("Mayor cierre", buildTopClosingValue(totals)),
                createClosingTotalsDetailRow("Estado", normalizeStatusLabel(status))
        );
        rightColumn.getStyleClass().add("closing-totalizer-detail-column");

        HBox columns = new HBox(16, leftColumn, rightColumn);
        columns.getStyleClass().add("closing-totalizer-detail-columns");
        HBox.setHgrow(leftColumn, Priority.ALWAYS);
        HBox.setHgrow(rightColumn, Priority.ALWAYS);
        return columns;
    }

    private HBox createClosingTotalsDetailRow(String key, String value) {
        Label left = new Label(key);
        left.getStyleClass().add("meta-key");
        Label right = new Label(value);
        right.getStyleClass().add("meta-value");
        right.setWrapText(true);
        right.setMaxWidth(220);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(10, left, spacer, right);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private ClosingTotals calculateClosingTotals(List<PosApiClient.CierreDiarioListadoResponse> cierres) {
        List<PosApiClient.CierreDiarioListadoResponse> safeRows = cierres == null ? List.of() : cierres;
        BigDecimal totalVentas = safeRows.stream()
                .map(PosApiClient.CierreDiarioListadoResponse::totalVentas)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalNetoCaja = safeRows.stream()
                .map(PosApiClient.CierreDiarioListadoResponse::montoNetoCaja)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalBaseCaja = safeRows.stream()
                .map(PosApiClient.CierreDiarioListadoResponse::baseCaja)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalTrabajadoras = safeRows.stream()
                .map(PosApiClient.CierreDiarioListadoResponse::trabajadoras)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAhorro = safeRows.stream()
                .map(PosApiClient.CierreDiarioListadoResponse::ahorro)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalFinal = safeRows.stream()
                .map(PosApiClient.CierreDiarioListadoResponse::totalFinal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int cantidadVentas = safeRows.stream()
                .map(PosApiClient.CierreDiarioListadoResponse::cantidadVentas)
                .reduce(0, Integer::sum);
        PosApiClient.CierreDiarioListadoResponse mayorCierre = safeRows.stream()
                .max(Comparator.comparing(PosApiClient.CierreDiarioListadoResponse::totalFinal, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
        BigDecimal promedioPorCierre = safeRows.isEmpty()
                ? BigDecimal.ZERO
                : totalFinal.divide(BigDecimal.valueOf(safeRows.size()), 2, RoundingMode.HALF_UP);
        return new ClosingTotals(
                safeRows.size(),
                cantidadVentas,
                totalVentas,
                totalNetoCaja,
                totalBaseCaja,
                totalTrabajadoras,
                totalAhorro,
                totalFinal,
                promedioPorCierre,
                mayorCierre
        );
    }

    private String buildTopClosingValue(ClosingTotals totals) {
        if (totals.mayorCierre() == null) {
            return "Sin registros";
        }
        return formatCurrency(totals.mayorCierre().totalFinal()) + " - "
                + formatShortDate(totals.mayorCierre().fechaOperacion());
    }

    private String normalizeStatusLabel(String status) {
        return status == null || status.isBlank() ? FILTER_ALL : status;
    }

    private String formatDateRange(LocalDate start, LocalDate end) {
        LocalDate safeStart = start == null ? LocalDate.now() : start;
        LocalDate safeEnd = end == null ? safeStart : end;
        if (safeStart.equals(safeEnd)) {
            return formatShortDate(safeStart);
        }
        return formatShortDate(safeStart) + " al " + formatShortDate(safeEnd);
    }

    private String formatShortDate(LocalDate value) {
        return value == null ? "-" : SHORT_DATE_FORMATTER.format(value);
    }

    private Node createLayawayListCard(
            TextField searchField,
            TextField articleFilterField,
            ComboBox<String> status,
            TableView<PosApiClient.SeparadoListadoResponse> table,
            Label selectedNumberValue,
            Label selectedClientValue,
            Label selectedItemsValue,
            Label selectedStatusValue,
            Label minimumValue,
            ProgressBar paymentProgressBar,
            Label paymentProgressCaption
    ) {
        VBox card = createCard("Listado de separados", "Panel central para visualizar apartados activos y sus saldos.");
        card.getStyleClass().add("layaway-list-card");
        HBox.setHgrow(card, Priority.ALWAYS);
        FlowPane toolbar = createResponsiveRow(
                createFieldGroup("Buscar", searchField, 240),
                createFieldGroup("Articulo", articleFilterField, 240),
                createFieldGroup("Estado", status, 170)
        );

        VBox profile = new VBox(8,
                createKeyValue("Seleccion actual", selectedNumberValue),
                createKeyValue("Cliente", selectedClientValue),
                createKeyValue("Articulos", selectedItemsValue),
                createKeyValue("Estado", selectedStatusValue),
                createKeyValue("Regla inicial", minimumValue),
                createProgressCard("Progreso de pago", paymentProgressBar, paymentProgressCaption)
        );

        card.getChildren().addAll(toolbar, table, profile);
        return card;
    }

    private Node createLayawayActionsCard(
            boolean editAllowed,
            Runnable onNewLayaway,
            Runnable onPayment,
            Runnable onRefresh,
            Runnable onShowPayments,
            Label activeValue,
            Label paidValue,
            Label totalBalanceValue,
            Label paymentsTodayValue
    ) {
        VBox card = createCard("Acciones", "Flujos del modulo conectados con separados y abonos reales.");
        card.getStyleClass().add("layaway-actions-card");
        bindRegionWidthToScene(card, 0.18, 188, 228);
        card.setMaxWidth(Region.USE_PREF_SIZE);

        Button newLayaway = createActionButton("Nuevo separado", "primary-button");
        newLayaway.setMaxWidth(Double.MAX_VALUE);
        newLayaway.setOnAction(event -> onNewLayaway.run());

        Button payment = createActionButton("Realizar abono", "ghost-button");
        payment.setMaxWidth(Double.MAX_VALUE);
        payment.setOnAction(event -> onPayment.run());

        Button refresh = createActionButton("Actualizar lista", "ghost-button");
        refresh.setMaxWidth(Double.MAX_VALUE);
        refresh.setOnAction(event -> onRefresh.run());

        Button payments = createActionButton("Visualizar abonos", "ghost-button");
        payments.setMaxWidth(Double.MAX_VALUE);
        payments.setOnAction(event -> onShowPayments.run());
        setNodeAllowed(newLayaway, editAllowed);
        setNodeAllowed(payment, editAllowed);

        VBox stats = new VBox(8,
                createKeyValue("Activos", activeValue),
                createKeyValue("Pagados", paidValue),
                createKeyValue("Saldo total", totalBalanceValue),
                createKeyValue("Abonos hoy", paymentsTodayValue)
        );

        card.getChildren().addAll(newLayaway, payment, refresh, payments, new Separator(), stats);
        return card;
    }

    private TableView<PosApiClient.SeparadoListadoResponse> createLayawayTable() {
        TableView<PosApiClient.SeparadoListadoResponse> table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        bindTableHeightToScene(table, 0.24, 138, 205);
        table.getColumns().addAll(
                tableColumn("Numero", PosApiClient.SeparadoListadoResponse::numeroSeparado),
                tableColumn("Cliente", PosApiClient.SeparadoListadoResponse::cliente),
                tableColumn("Articulos", PosApiClient.SeparadoListadoResponse::descripcionArticulos),
                tableColumn("Estado", separado -> formatLayawayStatus(separado.estado())),
                tableColumn("Abonado", separado -> formatCurrency(separado.totalAbonado())),
                tableColumn("Restante", separado -> formatCurrency(separado.saldoPendiente())),
                tableColumn("Fecha", separado -> formatShortDate(separado.fechaSeparacion()))
        );
        return table;
    }

    private TableView<PosApiClient.ProveedorResponse> createSupplierProvidersTable() {
        TableView<PosApiClient.ProveedorResponse> table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        bindTableHeightToScene(table, 0.34, 260, 460);
        table.getColumns().addAll(
                tableColumn("Proveedor", PosApiClient.ProveedorResponse::nombre),
                tableColumn("NIT", proveedor -> safeText(proveedor.nit(), "-")),
                tableColumn("Telefono", proveedor -> safeText(proveedor.telefono(), "-")),
                tableColumn("Facturas", proveedor -> String.valueOf(proveedor.cantidadFacturas())),
                tableColumn("Saldo", proveedor -> formatCurrency(proveedor.saldoPendienteTotal()))
        );
        return table;
    }

    private TableView<PosApiClient.FacturaProveedorListadoResponse> createSupplierInvoicesTable() {
        TableView<PosApiClient.FacturaProveedorListadoResponse> table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        bindTableHeightToScene(table, 0.23, 150, 230);
        table.getColumns().addAll(
                tableColumn("Numero", PosApiClient.FacturaProveedorListadoResponse::numeroFactura),
                tableColumn("Emision", factura -> formatShortDate(factura.fechaEmision())),
                tableColumn("Vence", factura -> formatShortDate(factura.fechaVencimiento())),
                tableColumn("Valor", factura -> formatCurrency(factura.montoTotal())),
                tableColumn("Abonado", factura -> formatCurrency(factura.montoPagado())),
                tableColumn("Saldo", factura -> formatCurrency(factura.saldoPendiente())),
                tableColumn("Estado", factura -> formatInvoiceStatus(factura.estado()))
        );
        return table;
    }

    private TableView<PosApiClient.PagoFacturaResponse> createSupplierPaymentsTable() {
        TableView<PosApiClient.PagoFacturaResponse> table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        bindTableHeightToScene(table, 0.18, 120, 185);
        table.getColumns().addAll(
                tableColumn("Fecha", pago -> formatShortDate(pago.fechaPago())),
                tableColumn("Valor", pago -> formatCurrency(pago.montoPago())),
                tableColumn("Medio", pago -> formatPaymentMethod(pago.metodoPago())),
                tableColumn("Restante", pago -> formatCurrency(pago.saldoRestante()))
        );
        return table;
    }

    private TableView<InvoiceSupportRow> createSupplierSupportsTable() {
        TableView<InvoiceSupportRow> table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        bindTableHeightToScene(table, 0.18, 120, 185);
        table.getColumns().addAll(
                tableColumn("Tipo", InvoiceSupportRow::tipo),
                tableColumn("Archivo", InvoiceSupportRow::archivo),
                tableColumn("Origen", InvoiceSupportRow::origen),
                tableColumn("Cargado", InvoiceSupportRow::cargadoEn)
        );
        table.setRowFactory(ignored -> {
            TableRow<InvoiceSupportRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    openSupportDocument(row.getItem());
                }
            });
            return row;
        });
        return table;
    }

    private void updateInvoiceMetrics(
            List<PosApiClient.ProveedorResponse> providers,
            List<PosApiClient.FacturaProveedorListadoResponse> invoices,
            Label providersValue,
            Label providersCaption,
            Label invoicesValue,
            Label invoicesCaption,
            Label balanceValue,
            Label balanceCaption,
            Label paidInvoicesValue,
            Label paidInvoicesCaption
    ) {
        long openInvoices = invoices.stream()
                .filter(invoice -> invoice.saldoPendiente() != null && invoice.saldoPendiente().signum() > 0)
                .count();
        long paidInvoices = invoices.stream()
                .filter(invoice -> "PAGADA".equalsIgnoreCase(invoice.estado()))
                .count();
        BigDecimal supplierBalance = invoices.stream()
                .map(PosApiClient.FacturaProveedorListadoResponse::saldoPendiente)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        providersValue.setText(String.valueOf(providers.size()));
        providersCaption.setText("Proveedores registrados");
        invoicesValue.setText(String.valueOf(openInvoices));
        invoicesCaption.setText(invoices.size() + " facturas consultadas");
        balanceValue.setText(formatCurrency(supplierBalance));
        balanceCaption.setText("Saldo agregado de proveedores");
        paidInvoicesValue.setText(String.valueOf(paidInvoices));
        paidInvoicesCaption.setText("Facturas completamente pagadas");
    }

    private void updateProviderSummaryState(
            PosApiClient.ProveedorResponse provider,
            List<PosApiClient.FacturaProveedorListadoResponse> allInvoices,
            Label selectedProviderValue,
            Label providerNitValue,
            Label providerPhoneValue,
            Label providerEmailValue,
            Label providerAddressValue,
            Label providerStatusValue,
            Label providerDebtValue,
            Label providerInvoicesValue,
            ProgressBar providerExposureBar,
            Label providerExposureCaption
    ) {
        if (provider == null) {
            selectedProviderValue.setText("Selecciona un proveedor");
            providerNitValue.setText("-");
            providerPhoneValue.setText("-");
            providerEmailValue.setText("-");
            providerAddressValue.setText("-");
            providerStatusValue.setText("Sin seleccion");
            providerDebtValue.setText("$ 0");
            providerInvoicesValue.setText("0 facturas");
            providerExposureBar.setProgress(0);
            providerExposureCaption.setText("Selecciona un proveedor para explorar su cartera");
            return;
        }

        List<PosApiClient.FacturaProveedorListadoResponse> providerInvoices = allInvoices.stream()
                .filter(invoice -> provider.id().equals(invoice.proveedorId()))
                .toList();
        BigDecimal total = providerInvoices.stream()
                .map(PosApiClient.FacturaProveedorListadoResponse::montoTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paid = providerInvoices.stream()
                .map(PosApiClient.FacturaProveedorListadoResponse::montoPagado)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal debt = providerInvoices.stream()
                .map(PosApiClient.FacturaProveedorListadoResponse::saldoPendiente)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        selectedProviderValue.setText(provider.nombre());
        providerNitValue.setText(safeText(provider.nit(), "-"));
        providerPhoneValue.setText(safeText(provider.telefono(), "-"));
        providerEmailValue.setText(safeText(provider.correo(), "-"));
        providerAddressValue.setText(safeText(provider.direccion(), "-"));
        providerStatusValue.setText(providerInvoices.isEmpty() ? "Sin facturas" : providerInvoices.size() + " facturas registradas");
        providerDebtValue.setText(formatCurrency(debt));
        providerInvoicesValue.setText(providerInvoices.size() + " facturas");
        providerExposureBar.setProgress(calculateLayawayProgress(total, paid));
        providerExposureCaption.setText(
                providerInvoices.isEmpty()
                        ? "Este proveedor aun no tiene facturas registradas."
                        : formatCurrency(paid) + " abonados de " + formatCurrency(total) + " | Restante " + formatCurrency(debt)
        );
    }

    private void showProviderInvoicesWindow(Window owner, PosApiClient.ProveedorResponse provider, Runnable afterMutation) {
        if (provider == null) {
            showError("Facturas", "Selecciona un proveedor para visualizar sus facturas.");
            return;
        }

        Stage stage = createDialogStage("Facturas | " + provider.nombre());
        Window effectiveOwner = owner != null ? owner : primaryStage;
        if (effectiveOwner != null) {
            stage.initOwner(effectiveOwner);
        }

        ObservableList<PosApiClient.FacturaProveedorListadoResponse> invoiceSource = FXCollections.observableArrayList();
        ObservableList<PosApiClient.PagoFacturaResponse> paymentSource = FXCollections.observableArrayList();
        ObservableList<InvoiceSupportRow> supportSource = FXCollections.observableArrayList();
        AtomicReference<PosApiClient.FacturaProveedorDetalleResponse> selectedInvoiceDetail = new AtomicReference<>();

        TableView<PosApiClient.FacturaProveedorListadoResponse> invoiceTable = createSupplierInvoicesTable();
        invoiceTable.setItems(invoiceSource);
        invoiceTable.prefHeightProperty().unbind();
        invoiceTable.setMaxHeight(Double.MAX_VALUE);
        bindTableHeightToScene(invoiceTable, 0.22, 180, 300);

        AtomicReference<String> selectedInvoiceId = new AtomicReference<>();

        Label debtValue = createMetricValueLabel("$ 0");
        Label invoiceCountValue = createMetricValueLabel("0");
        Label paidInvoiceCountValue = createMetricValueLabel("0");

        Label selectedInvoiceValue = createMetaValueLabel("Selecciona una factura");
        Label invoiceConceptValue = createMetaValueLabel("-");
        Label invoiceDueDateValue = createMetaValueLabel("-");
        Label invoiceBalanceValue = createMetaValueLabel("$ 0");
        Label paymentSummaryValue = createMetaValueLabel("0 abonos");
        Label supportSummaryValue = createMetaValueLabel("0 soportes");
        Label providerNameValue = createMetaValueLabel(provider.nombre());
        Label providerNitValue = createMetaValueLabel(safeText(provider.nit(), "-"));
        Label providerPhoneValue = createMetaValueLabel(safeText(provider.telefono(), "-"));
        Label providerEmailValue = createMetaValueLabel(safeText(provider.correo(), "-"));
        ProgressBar invoiceProgressBar = new ProgressBar(0);
        invoiceProgressBar.setMaxWidth(Double.MAX_VALUE);
        invoiceProgressBar.getStyleClass().add("accent-progress");
        Label invoiceProgressCaption = new Label("Selecciona una factura para revisar su avance");
        invoiceProgressCaption.getStyleClass().add("progress-caption");

        Button newInvoiceButton = createActionButton("Nueva factura", "primary-button");
        newInvoiceButton.setPrefWidth(170);

        Button registerPaymentButton = createActionButton("Registrar abono", "ghost-button");
        registerPaymentButton.setPrefWidth(170);
        registerPaymentButton.disableProperty().bind(Bindings.isNull(invoiceTable.getSelectionModel().selectedItemProperty()));

        Button viewPaymentsButton = createActionButton("Ver abonos", "ghost-button");
        viewPaymentsButton.setPrefWidth(170);
        viewPaymentsButton.disableProperty().bind(Bindings.isNull(invoiceTable.getSelectionModel().selectedItemProperty()));

        Button viewSupportsButton = createActionButton("Ver soportes", "ghost-button");
        viewSupportsButton.setPrefWidth(170);
        viewSupportsButton.disableProperty().bind(Bindings.isNull(invoiceTable.getSelectionModel().selectedItemProperty()));

        Button refreshButton = createActionButton("Actualizar", "ghost-button");
        refreshButton.setPrefWidth(170);
        refreshButton.disableProperty().bind(Bindings.isNull(invoiceTable.getSelectionModel().selectedItemProperty()));
        setNodeAllowed(newInvoiceButton, hasPermission(PERM_FACTURAS_EDIT));
        setNodeAllowed(registerPaymentButton, hasPermission(PERM_FACTURAS_EDIT));
        setNodeAllowed(refreshButton, hasPermission(PERM_FACTURAS_EDIT));

        Runnable refreshInvoices = () -> runAsync(
                () -> posApiClient.listarFacturas(provider.id(), null),
                invoices -> {
                    invoiceSource.setAll(invoices);
                    BigDecimal totalDebt = invoices.stream()
                            .map(PosApiClient.FacturaProveedorListadoResponse::saldoPendiente)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    long paidCount = invoices.stream()
                            .filter(invoice -> "PAGADA".equalsIgnoreCase(invoice.estado()))
                            .count();
                    debtValue.setText(formatCurrency(totalDebt));
                    invoiceCountValue.setText(String.valueOf(invoices.size()));
                    paidInvoiceCountValue.setText(String.valueOf(paidCount));

                    String selectedId = selectedInvoiceId.get();
                    if (selectedId != null && selectSupplierInvoiceRow(invoiceTable, selectedId)) {
                        return;
                    }
                    if (!invoiceSource.isEmpty()) {
                        invoiceTable.getSelectionModel().selectFirst();
                    } else {
                        selectedInvoiceDetail.set(null);
                        invoiceTable.getSelectionModel().clearSelection();
                        paymentSource.clear();
                        supportSource.clear();
                        updateInvoiceDetailState(
                                null,
                                paymentSource,
                                supportSource,
                                selectedInvoiceValue,
                                invoiceConceptValue,
                                invoiceDueDateValue,
                                invoiceBalanceValue,
                                paymentSummaryValue,
                                supportSummaryValue,
                                invoiceProgressBar,
                                invoiceProgressCaption
                        );
                    }
                },
                exception -> showError("Facturas", exception.getMessage())
        );

        invoiceTable.getSelectionModel().selectedItemProperty().addListener((obs, previous, invoice) -> {
            selectedInvoiceId.set(invoice == null ? null : invoice.id());
            if (invoice == null) {
                selectedInvoiceDetail.set(null);
                paymentSource.clear();
                supportSource.clear();
                updateInvoiceDetailState(
                        null,
                        paymentSource,
                        supportSource,
                        selectedInvoiceValue,
                        invoiceConceptValue,
                        invoiceDueDateValue,
                        invoiceBalanceValue,
                        paymentSummaryValue,
                        supportSummaryValue,
                        invoiceProgressBar,
                        invoiceProgressCaption
                );
                return;
            }

            runAsync(
                    () -> posApiClient.consultarFacturaProveedor(invoice.id()),
                    detail -> {
                        if (!invoice.id().equals(selectedInvoiceId.get())) {
                            return;
                        }
                        selectedInvoiceDetail.set(detail);
                        updateInvoiceDetailState(
                                detail,
                                paymentSource,
                                supportSource,
                                selectedInvoiceValue,
                                invoiceConceptValue,
                                invoiceDueDateValue,
                                invoiceBalanceValue,
                                paymentSummaryValue,
                                supportSummaryValue,
                                invoiceProgressBar,
                                invoiceProgressCaption
                        );
                    },
                    exception -> {
                        if (invoice.id().equals(selectedInvoiceId.get())) {
                            selectedInvoiceDetail.set(null);
                            paymentSource.clear();
                            supportSource.clear();
                        }
                        showError("Facturas", exception.getMessage());
                    }
            );
        });

        newInvoiceButton.setOnAction(event -> showInvoiceWindow(
                stage,
                provider,
                () -> {
                    refreshInvoices.run();
                    if (afterMutation != null) {
                        afterMutation.run();
                    }
                },
                createdInvoiceId -> selectedInvoiceId.set(createdInvoiceId)
        ));

        registerPaymentButton.setOnAction(event -> showInvoicePaymentWindow(
                stage,
                provider,
                invoiceTable.getSelectionModel().getSelectedItem(),
                () -> {
                    refreshInvoices.run();
                    if (afterMutation != null) {
                        afterMutation.run();
                    }
                }
        ));

        viewPaymentsButton.setOnAction(event -> {
            PosApiClient.FacturaProveedorDetalleResponse detail = selectedInvoiceDetail.get();
            if (detail == null) {
                showError("Facturas", "Selecciona una factura y espera a que se cargue el detalle para ver sus abonos.");
                return;
            }
            showInvoicePaymentsViewerWindow(stage, detail);
        });

        viewSupportsButton.setOnAction(event -> {
            PosApiClient.FacturaProveedorDetalleResponse detail = selectedInvoiceDetail.get();
            if (detail == null) {
                showError("Facturas", "Selecciona una factura y espera a que se cargue el detalle para ver sus soportes.");
                return;
            }
            showInvoiceSupportsViewerWindow(stage, detail);
        });

        refreshButton.setOnAction(event -> showInvoiceUpdateWindow(
                stage,
                provider,
                invoiceTable.getSelectionModel().getSelectedItem(),
                selectedInvoiceDetail.get(),
                () -> {
                    refreshInvoices.run();
                    if (afterMutation != null) {
                        afterMutation.run();
                    }
                }
        ));

        invoiceTable.setOnMouseClicked(event -> {
            if (event.getClickCount() < 2) {
                return;
            }
            PosApiClient.FacturaProveedorListadoResponse selectedInvoice = invoiceTable.getSelectionModel().getSelectedItem();
            if (selectedInvoice == null) {
                return;
            }
            showInvoicePaymentWindow(
                    stage,
                    provider,
                    selectedInvoice,
                    () -> {
                        refreshInvoices.run();
                        if (afterMutation != null) {
                            afterMutation.run();
                        }
                    }
            );
        });

        VBox root = createDialogRoot(
                "Facturas de " + provider.nombre(),
                ""
        );
        root.getStyleClass().addAll("invoice-workspace", "invoice-review-window");
        root.setSpacing(8);
        root.setPadding(new Insets(10));

        VBox invoiceListCard = createCard(
                "Facturas del proveedor",
                ""
        );
        invoiceListCard.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(invoiceTable, Priority.ALWAYS);
        FlowPane providerSummaryFlow = new FlowPane();
        providerSummaryFlow.setHgap(10);
        providerSummaryFlow.setVgap(8);
        providerSummaryFlow.getStyleClass().add("invoice-provider-flow");
        providerSummaryFlow.getChildren().addAll(
                createCompactInfoBlock("Proveedor", providerNameValue),
                createCompactInfoBlock("NIT", providerNitValue),
                createCompactInfoBlock("Telefono", providerPhoneValue),
                createCompactInfoBlock("Correo", providerEmailValue),
                createCompactInfoBlock("Saldo proveedor", debtValue),
                createCompactInfoBlock("Facturas", invoiceCountValue),
                createCompactInfoBlock("Pagadas", paidInvoiceCountValue)
        );
        invoiceListCard.getChildren().addAll(providerSummaryFlow, invoiceTable);

        VBox paymentsCard = createCard(
                "Resumen de factura",
                ""
        );
        paymentsCard.getStyleClass().add("invoice-compact-card");
        FlowPane selectedInvoiceFlow = new FlowPane();
        selectedInvoiceFlow.setHgap(10);
        selectedInvoiceFlow.setVgap(8);
        selectedInvoiceFlow.getStyleClass().add("invoice-provider-flow");
        selectedInvoiceFlow.getChildren().addAll(
                createCompactInfoBlock("Factura", selectedInvoiceValue),
                createCompactInfoBlock("Vencimiento", invoiceDueDateValue),
                createCompactInfoBlock("Saldo", invoiceBalanceValue),
                createCompactInfoBlock("Abonos", paymentSummaryValue)
        );
        paymentsCard.getChildren().addAll(
                createKeyValue("Detalle", invoiceConceptValue),
                createKeyValue("Soportes", supportSummaryValue),
                selectedInvoiceFlow,
                createProgressCard("Avance de pago", invoiceProgressBar, invoiceProgressCaption)
        );

        VBox supportsCard = createCard(
                "Centro de consulta",
                ""
        );
        supportsCard.getStyleClass().add("invoice-compact-card");
        supportsCard.getChildren().addAll(
                createFloatingActionPanel(
                        "Abonos registrados",
                        "",
                        viewPaymentsButton
                ),
                createFloatingActionPanel(
                        "Soportes documentales",
                        "Explora facturas y comprobantes cargados en una ventana dedicada y más cómoda.",
                        viewSupportsButton
                )
        );

        FlowPane actionButtons = createResponsiveRow(newInvoiceButton, registerPaymentButton, refreshButton);
        actionButtons.getStyleClass().add("invoice-action-row");
        actionButtons.getChildren().forEach(node -> {
            if (node instanceof Button button) {
                button.setPrefWidth(170);
            }
        });

        VBox actionsFooter = createCard(
                "Acciones",
                ""
        );
        actionsFooter.getStyleClass().addAll("invoice-compact-card", "invoice-actions-card");
        actionsFooter.setMaxWidth(Double.MAX_VALUE);
        actionsFooter.getChildren().add(actionButtons);

        VBox leftColumn = new VBox(6, invoiceListCard, actionsFooter);
        leftColumn.setMaxWidth(Double.MAX_VALUE);
        leftColumn.setFillWidth(true);
        bindRegionWidthToScene(leftColumn, 0.62, 640, 920);

        VBox rightColumn = new VBox(6, paymentsCard, supportsCard);
        rightColumn.setMaxWidth(Double.MAX_VALUE);
        rightColumn.getStyleClass().add("invoice-side-rail");
        bindRegionWidthToScene(rightColumn, 0.25, 280, 345);
        rightColumn.setFillWidth(true);

        FlowPane workspace = createResponsiveRow(leftColumn, rightColumn);
        workspace.getStyleClass().add("invoice-responsive-workspace");
        VBox.setVgrow(workspace, Priority.NEVER);
        root.getChildren().add(workspace);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/com/posdesktop/pos/mockfx/mock-theme.css").toExternalForm());
        workspace.prefWrapLengthProperty().bind(Bindings.createDoubleBinding(
                () -> Math.max(820, scene.getWidth() - 48),
                scene.widthProperty()
        ));
        stage.setScene(scene);
        applyResponsiveStageSize(stage, 0.95, 0.88, 1060, 700);
        stage.setOnShown(event -> {
            stage.centerOnScreen();
            stage.toFront();
            stage.requestFocus();
        });
        refreshInvoices.run();
        stage.show();
    }

    private void updateInvoiceDetailState(
            PosApiClient.FacturaProveedorDetalleResponse detail,
            ObservableList<PosApiClient.PagoFacturaResponse> paymentSource,
            ObservableList<InvoiceSupportRow> supportSource,
            Label selectedInvoiceValue,
            Label invoiceConceptValue,
            Label invoiceDueDateValue,
            Label invoiceBalanceValue,
            Label paymentSummaryValue,
            Label supportSummaryValue,
            ProgressBar invoiceProgressBar,
            Label invoiceProgressCaption
    ) {
        if (detail == null) {
            selectedInvoiceValue.setText("Selecciona una factura");
            invoiceConceptValue.setText("-");
            invoiceDueDateValue.setText("-");
            invoiceBalanceValue.setText("$ 0");
            paymentSummaryValue.setText("0 abonos");
            supportSummaryValue.setText("0 soportes");
            invoiceProgressBar.setProgress(0);
            invoiceProgressCaption.setText("Sin factura seleccionada");
            return;
        }

        paymentSource.setAll(detail.abonos());
        supportSource.setAll(buildSupportRows(detail));
        selectedInvoiceValue.setText(detail.numeroFactura());
        invoiceConceptValue.setText(safeText(detail.observacion(), "Sin observacion"));
        invoiceDueDateValue.setText(formatShortDate(detail.fechaVencimiento()) + " | " + formatInvoiceStatus(detail.estado()));
        invoiceBalanceValue.setText(formatCurrency(detail.saldoPendiente()));
        paymentSummaryValue.setText(detail.abonos().size() + " abonos");
        supportSummaryValue.setText(supportSource.size() + " soportes");
        invoiceProgressBar.setProgress(calculateLayawayProgress(detail.montoTotal(), detail.montoPagado()));
        invoiceProgressCaption.setText(
                formatCurrency(detail.montoPagado()) + " abonados de " + formatCurrency(detail.montoTotal())
                        + " | Restante " + formatCurrency(detail.saldoPendiente())
        );
    }

    private VBox createFloatingActionPanel(String title, String description, Button actionButton) {
        VBox panel = new VBox(12);
        panel.getStyleClass().add("invoice-floating-panel");

        Label heading = new Label(title);
        heading.getStyleClass().add("invoice-floating-title");

        actionButton.getStyleClass().add("invoice-floating-button");
        actionButton.setMaxWidth(Double.MAX_VALUE);

        panel.getChildren().add(heading);
        if (description != null && !description.isBlank()) {
            Label copy = new Label(description);
            copy.getStyleClass().add("invoice-floating-copy");
            copy.setWrapText(true);
            panel.getChildren().add(copy);
        }
        panel.getChildren().add(actionButton);
        return panel;
    }

    private void showInvoicePaymentsViewerWindow(Window owner, PosApiClient.FacturaProveedorDetalleResponse detail) {
        Stage stage = createDialogStage("Abonos | " + detail.numeroFactura());
        if (owner != null) {
            stage.initOwner(owner);
        }

        ObservableList<PosApiClient.PagoFacturaResponse> paymentSource = FXCollections.observableArrayList(detail.abonos());
        TableView<PosApiClient.PagoFacturaResponse> paymentTable = createSupplierPaymentsTable();
        paymentTable.setItems(paymentSource);
        paymentTable.prefHeightProperty().unbind();
        paymentTable.setMaxHeight(Double.MAX_VALUE);
        bindTableHeightToScene(paymentTable, 0.42, 220, 420);

        Label invoiceValue = createMetaValueLabel(detail.numeroFactura());
        Label totalValue = createMetaValueLabel(formatCurrency(detail.montoTotal()));
        Label paidValue = createMetaValueLabel(formatCurrency(detail.montoPagado()));
        Label pendingValue = createMetaValueLabel(formatCurrency(detail.saldoPendiente()));
        Label countValue = createMetaValueLabel(detail.abonos().size() + " movimientos");
        ProgressBar progressBar = new ProgressBar(calculateLayawayProgress(detail.montoTotal(), detail.montoPagado()));
        progressBar.getStyleClass().add("accent-progress");
        progressBar.setMaxWidth(Double.MAX_VALUE);
        Label progressCaption = new Label(
                formatCurrency(detail.montoPagado()) + " abonados de " + formatCurrency(detail.montoTotal())
                        + " | Restante " + formatCurrency(detail.saldoPendiente())
        );
        progressCaption.getStyleClass().add("progress-caption");

        VBox root = createDialogRoot(
                "Abonos registrados",
                "Historial limpio de movimientos aplicados a la factura seleccionada."
        );
        root.getStyleClass().add("invoice-workspace");
        root.setSpacing(12);
        root.setPadding(new Insets(16));

        VBox summaryCard = createCard(
                "Resumen de cartera",
                "Una lectura rápida del comportamiento de pago de esta factura."
        );
        FlowPane summaryFlow = new FlowPane();
        summaryFlow.setHgap(10);
        summaryFlow.setVgap(8);
        summaryFlow.getStyleClass().add("invoice-provider-flow");
        summaryFlow.getChildren().addAll(
                createCompactInfoBlock("Factura", invoiceValue),
                createCompactInfoBlock("Valor total", totalValue),
                createCompactInfoBlock("Abonado", paidValue),
                createCompactInfoBlock("Pendiente", pendingValue),
                createCompactInfoBlock("Abonos", countValue)
        );
        summaryCard.getChildren().addAll(summaryFlow, createProgressCard("Avance de pago", progressBar, progressCaption));

        VBox tableCard = createCard(
                "Movimientos",
                detail.abonos().isEmpty()
                        ? "Esta factura aún no tiene abonos registrados."
                        : "Detalle cronológico de cada abono aplicado."
        );
        VBox.setVgrow(paymentTable, Priority.ALWAYS);
        tableCard.getChildren().add(paymentTable);

        VBox.setVgrow(tableCard, Priority.ALWAYS);
        root.getChildren().addAll(summaryCard, tableCard);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/com/posdesktop/pos/mockfx/mock-theme.css").toExternalForm());
        stage.setScene(scene);
        applyResponsiveStageSize(stage, 0.76, 0.74, 880, 560);
        stage.show();
    }

    private void showInvoiceSupportsViewerWindow(Window owner, PosApiClient.FacturaProveedorDetalleResponse detail) {
        Stage stage = createDialogStage("Soportes | " + detail.numeroFactura());
        if (owner != null) {
            stage.initOwner(owner);
        }

        List<InvoiceSupportRow> rows = buildSupportRows(detail);
        ObservableList<InvoiceSupportRow> supportSource = FXCollections.observableArrayList(rows);
        TableView<InvoiceSupportRow> supportTable = createSupplierSupportsTable();
        supportTable.setItems(supportSource);
        supportTable.prefHeightProperty().unbind();
        supportTable.setMaxHeight(Double.MAX_VALUE);
        bindTableHeightToScene(supportTable, 0.42, 220, 420);

        long facturaSupports = detail.soportesFactura().size();
        long paymentSupports = detail.abonos().stream().mapToLong(abono -> abono.soportes().size()).sum();

        VBox root = createDialogRoot(
                "Soportes documentales",
                "Explora evidencias de factura y comprobantes de abono en una vista dedicada."
        );
        root.getStyleClass().add("invoice-workspace");
        root.setSpacing(12);
        root.setPadding(new Insets(16));

        VBox summaryCard = createCard(
                "Cobertura documental",
                "Separación clara entre soportes de origen y comprobantes de pago."
        );
        FlowPane summaryFlow = new FlowPane();
        summaryFlow.setHgap(10);
        summaryFlow.setVgap(8);
        summaryFlow.getStyleClass().add("invoice-provider-flow");
        summaryFlow.getChildren().addAll(
                createCompactInfoBlock("Factura", createMetaValueLabel(detail.numeroFactura())),
                createCompactInfoBlock("Soportes factura", createMetaValueLabel(String.valueOf(facturaSupports))),
                createCompactInfoBlock("Soportes abonos", createMetaValueLabel(String.valueOf(paymentSupports))),
                createCompactInfoBlock("Total", createMetaValueLabel(String.valueOf(rows.size())))
        );
        summaryCard.getChildren().add(summaryFlow);

        VBox tableCard = createCard(
                "Archivos",
                rows.isEmpty()
                        ? "Esta factura aún no tiene soportes cargados."
                        : "Listado de archivos asociados a la factura y a sus abonos."
        );
        VBox.setVgrow(supportTable, Priority.ALWAYS);
        tableCard.getChildren().add(supportTable);

        VBox.setVgrow(tableCard, Priority.ALWAYS);
        root.getChildren().addAll(summaryCard, tableCard);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/com/posdesktop/pos/mockfx/mock-theme.css").toExternalForm());
        stage.setScene(scene);
        applyResponsiveStageSize(stage, 0.76, 0.74, 880, 560);
        stage.show();
    }

    private List<InvoiceSupportRow> buildSupportRows(PosApiClient.FacturaProveedorDetalleResponse detail) {
        List<InvoiceSupportRow> rows = new ArrayList<>();
        for (PosApiClient.DocumentoSoporteResponse soporte : detail.soportesFactura()) {
            rows.add(new InvoiceSupportRow(
                    formatDocumentType(soporte.tipoDocumento()),
                    soporte.nombreArchivo(),
                    "Factura",
                    formatDateTime(soporte.cargadoEn()),
                    soporte.rutaArchivo(),
                    soporte.rutaRelativa()
            ));
        }
        for (PosApiClient.PagoFacturaResponse pago : detail.abonos()) {
            for (PosApiClient.DocumentoSoporteResponse soporte : pago.soportes()) {
                rows.add(new InvoiceSupportRow(
                        formatDocumentType(soporte.tipoDocumento()),
                        soporte.nombreArchivo(),
                        "Abono " + formatShortDate(pago.fechaPago()),
                        formatDateTime(soporte.cargadoEn()),
                        soporte.rutaArchivo(),
                        soporte.rutaRelativa()
                ));
            }
        }
        rows.sort(Comparator.comparing(InvoiceSupportRow::cargadoEn).reversed());
        return rows;
    }

    private void openSupportDocument(InvoiceSupportRow support) {
        if (support == null) {
            return;
        }
        if (!Desktop.isDesktopSupported()) {
            showError("Soportes", "Este equipo no permite abrir archivos automaticamente desde la aplicacion.");
            return;
        }

        Path filePath = resolveSupportPath(support);
        if (filePath == null) {
            showError(
                    "Soportes",
                    "No se encontro una ruta valida para el archivo " + safeText(support.archivo(), "seleccionado") + "."
            );
            return;
        }
        if (!Files.exists(filePath)) {
            showError(
                    "Soportes",
                    "El archivo ya no existe en la ruta almacenada:\n" + filePath
            );
            return;
        }

        try {
            Desktop.getDesktop().open(filePath.toFile());
        } catch (IOException exception) {
            showError(
                    "Soportes",
                    "No fue posible abrir el archivo seleccionado.\n" + exception.getMessage()
            );
        }
    }

    private Path resolveSupportPath(InvoiceSupportRow support) {
        Path absolutePath = buildPath(support.rutaArchivo());
        if (absolutePath != null && Files.exists(absolutePath)) {
            return absolutePath;
        }
        Path relativePath = buildPath(support.rutaRelativa());
        if (relativePath != null) {
            return relativePath;
        }
        return absolutePath;
    }

    private Path buildPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        try {
            return Path.of(rawPath.trim());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private boolean selectProviderRow(TableView<PosApiClient.ProveedorResponse> table, String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return false;
        }
        for (PosApiClient.ProveedorResponse row : table.getItems()) {
            if (providerId.equals(row.id())) {
                table.getSelectionModel().select(row);
                table.scrollTo(row);
                return true;
            }
        }
        return false;
    }

    private boolean selectSupplierInvoiceRow(TableView<PosApiClient.FacturaProveedorListadoResponse> table, String invoiceId) {
        if (invoiceId == null || invoiceId.isBlank()) {
            return false;
        }
        for (PosApiClient.FacturaProveedorListadoResponse row : table.getItems()) {
            if (invoiceId.equals(row.id())) {
                table.getSelectionModel().select(row);
                table.scrollTo(row);
                return true;
            }
        }
        return false;
    }

    private void showProviderWindow(Window owner, Runnable afterSave, Consumer<String> onCreated) {
        if (!hasPermission(PERM_PROVEEDORES_EDIT)) {
            showError("Proveedores", "Tu usuario no tiene permiso para crear proveedores.");
            return;
        }
        Stage stage = createDialogStage("Nuevo proveedor");
        if (owner != null) {
            stage.initOwner(owner);
        }

        VBox root = createDialogRoot(
                "Nuevo proveedor",
                "Crea el proveedor en la base real para poder asociarle facturas de inmediato."
        );
        root.getStyleClass().add("invoice-workspace");
        root.setSpacing(12);
        root.setPadding(new Insets(16));

        TextField nitField = createField("");
        nitField.setPromptText("NIT o identificacion");
        TextField nameField = createField("");
        nameField.setPromptText("Nombre del proveedor");
        TextField phoneField = createField("");
        phoneField.setPromptText("Telefono");
        TextField emailField = createField("");
        emailField.setPromptText("Correo");
        TextField addressField = createField("");
        addressField.setPromptText("Direccion");
        TextArea notesArea = createArea("", 2);
        notesArea.setPromptText("Observacion interna");

        Label providerPreviewValue = createMetaValueLabel("Nuevo proveedor");
        providerPreviewValue.textProperty().bind(Bindings.createStringBinding(
                () -> safeText(nameField.getText(), "Nuevo proveedor"),
                nameField.textProperty()
        ));
        Label nitPreviewValue = createMetaValueLabel("-");
        nitPreviewValue.textProperty().bind(Bindings.createStringBinding(
                () -> safeText(nitField.getText(), "-"),
                nitField.textProperty()
        ));

        Button save = createActionButton("Guardar proveedor", "primary-button");
        save.setMaxWidth(Double.MAX_VALUE);
        save.setOnAction(event -> {
            save.setDisable(true);
            runAsync(
                    () -> posApiClient.registrarProveedor(new PosApiClient.RegistrarProveedorRequest(
                            nitField.getText(),
                            nameField.getText(),
                            phoneField.getText(),
                            emailField.getText(),
                            addressField.getText(),
                            notesArea.getText()
                    )),
                    createdProvider -> {
                        stage.close();
                        if (afterSave != null) {
                            afterSave.run();
                        }
                        if (onCreated != null) {
                            onCreated.accept(createdProvider.id());
                        }
                        showInfo(
                                "Proveedor registrado",
                                "Se creó el proveedor " + createdProvider.nombre() + " y ya quedó disponible para asociarle facturas."
                        );
                    },
                    exception -> {
                        save.setDisable(false);
                        showError("Nuevo proveedor", exception.getMessage());
                    }
            );
        });

        FlowPane fields = createResponsiveRow(
                createFieldGroup("NIT", nitField, 220),
                createFieldGroup("Proveedor", nameField, 280),
                createFieldGroup("Telefono", phoneField, 220),
                createFieldGroup("Correo", emailField, 240),
                createFieldGroup("Direccion", addressField, 280)
        );
        VBox formCard = createCard(
                "Datos del proveedor",
                "Completa la informacion base para registrar el proveedor en la API."
        );
        HBox.setHgrow(formCard, Priority.ALWAYS);
        formCard.getChildren().addAll(fields, createFieldGroup("Observacion", notesArea, 520));

        VBox summaryCard = createCard(
                "Resumen",
                "Validas el registro antes de enviarlo."
        );
        bindRegionWidthToScene(summaryCard, 0.28, 260, 320);
        summaryCard.setMaxWidth(Region.USE_PREF_SIZE);
        summaryCard.getChildren().addAll(
                createKeyValue("Proveedor", providerPreviewValue),
                createKeyValue("NIT", nitPreviewValue),
                createProgressCard("Registro real", 0.62, "Al guardar, el proveedor queda visible en la bandeja principal."),
                save
        );

        HBox workspace = createAdaptivePanelRow(formCard, summaryCard);
        VBox.setVgrow(workspace, Priority.ALWAYS);
        root.getChildren().add(workspace);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/com/posdesktop/pos/mockfx/mock-theme.css").toExternalForm());
        stage.setScene(scene);
        applyResponsiveStageSize(stage, 0.82, 0.74, 900, 560);
        stage.show();
    }

    private void showUpdateProviderWindow(
            Window owner,
            PosApiClient.ProveedorResponse provider,
            Runnable afterSave,
            Consumer<String> onUpdated
    ) {
        if (!hasPermission(PERM_PROVEEDORES_EDIT)) {
            showError("Proveedores", "Tu usuario no tiene permiso para actualizar proveedores.");
            return;
        }
        if (provider == null) {
            showError("Proveedores", "Selecciona un proveedor para actualizar sus datos.");
            return;
        }

        Stage stage = createDialogStage("Actualizar proveedor");
        if (owner != null) {
            stage.initOwner(owner);
        }

        VBox root = createDialogRoot(
                "Actualizar proveedor",
                "Edita la informacion principal del proveedor seleccionado y guarda los cambios en la API."
        );
        root.getStyleClass().add("invoice-workspace");
        root.setSpacing(12);
        root.setPadding(new Insets(16));

        TextField nitField = createField(safeText(provider.nit(), ""));
        nitField.setPromptText("NIT o identificacion");
        TextField nameField = createField(safeText(provider.nombre(), ""));
        nameField.setPromptText("Nombre del proveedor");
        TextField phoneField = createField(safeText(provider.telefono(), ""));
        phoneField.setPromptText("Telefono");
        TextField emailField = createField(safeText(provider.correo(), ""));
        emailField.setPromptText("Correo");
        TextField addressField = createField(safeText(provider.direccion(), ""));
        addressField.setPromptText("Direccion");
        TextArea notesArea = createArea(safeText(provider.observacion(), ""), 2);
        notesArea.setPromptText("Observacion interna");

        Label providerPreviewValue = createMetaValueLabel(safeText(provider.nombre(), "Proveedor"));
        providerPreviewValue.textProperty().bind(Bindings.createStringBinding(
                () -> safeText(nameField.getText(), "Proveedor"),
                nameField.textProperty()
        ));
        Label nitPreviewValue = createMetaValueLabel(safeText(provider.nit(), "-"));
        nitPreviewValue.textProperty().bind(Bindings.createStringBinding(
                () -> safeText(nitField.getText(), "-"),
                nitField.textProperty()
        ));

        Button save = createActionButton("Guardar cambios", "primary-button");
        save.setMaxWidth(Double.MAX_VALUE);
        save.setOnAction(event -> {
            save.setDisable(true);
            runAsync(
                    () -> posApiClient.actualizarProveedor(
                            provider.id(),
                            new PosApiClient.ActualizarProveedorRequest(
                                    nitField.getText(),
                                    nameField.getText(),
                                    phoneField.getText(),
                                    emailField.getText(),
                                    addressField.getText(),
                                    notesArea.getText()
                            )
                    ),
                    updatedProvider -> {
                        stage.close();
                        if (afterSave != null) {
                            afterSave.run();
                        }
                        if (onUpdated != null) {
                            onUpdated.accept(updatedProvider.id());
                        }
                        showInfo(
                                "Proveedor actualizado",
                                "Se actualizo el proveedor " + updatedProvider.nombre() + " y los cambios ya quedaron disponibles en la bandeja."
                        );
                    },
                    exception -> {
                        save.setDisable(false);
                        showError("Actualizar proveedor", exception.getMessage());
                    }
            );
        });

        FlowPane fields = createResponsiveRow(
                createFieldGroup("NIT", nitField, 220),
                createFieldGroup("Proveedor", nameField, 280),
                createFieldGroup("Telefono", phoneField, 220),
                createFieldGroup("Correo", emailField, 240),
                createFieldGroup("Direccion", addressField, 280)
        );
        VBox formCard = createCard(
                "Datos del proveedor",
                "Ajusta la informacion base y conserva la relacion con sus facturas registradas."
        );
        HBox.setHgrow(formCard, Priority.ALWAYS);
        formCard.getChildren().addAll(fields, createFieldGroup("Observacion", notesArea, 520));

        VBox summaryCard = createCard(
                "Resumen",
                "Confirma la identidad del proveedor antes de guardar."
        );
        bindRegionWidthToScene(summaryCard, 0.28, 260, 320);
        summaryCard.setMaxWidth(Region.USE_PREF_SIZE);
        summaryCard.getChildren().addAll(
                createKeyValue("Proveedor", providerPreviewValue),
                createKeyValue("NIT", nitPreviewValue),
                createProgressCard("Actualizacion real", 0.72, "Los cambios impactan de inmediato la ficha del proveedor en el POS."),
                save
        );

        HBox workspace = createAdaptivePanelRow(formCard, summaryCard);
        VBox.setVgrow(workspace, Priority.ALWAYS);
        root.getChildren().add(workspace);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/com/posdesktop/pos/mockfx/mock-theme.css").toExternalForm());
        stage.setScene(scene);
        applyResponsiveStageSize(stage, 0.8, 0.74, 920, 520);
        stage.show();
    }

    private void showInvoiceWindow(
            Window owner,
            PosApiClient.ProveedorResponse provider,
            Runnable afterSave,
            Consumer<String> onCreated
    ) {
        if (!hasPermission(PERM_FACTURAS_EDIT)) {
            showError("Facturas", "Tu usuario no tiene permiso para crear facturas.");
            return;
        }
        if (provider == null) {
            showError("Facturas", "Selecciona un proveedor para crear una factura.");
            return;
        }

        Stage stage = createDialogStage("Nueva factura");
        if (owner != null) {
            stage.initOwner(owner);
        }

        VBox root = createDialogRoot(
                "Nueva factura",
                "Registra la factura real del proveedor y adjunta sus soportes desde el computador."
        );
        root.getStyleClass().add("invoice-workspace");
        root.setSpacing(12);
        root.setPadding(new Insets(16));

        TextField providerField = createField(provider.nombre());
        providerField.setDisable(true);
        TextField numberField = createField("");
        numberField.setPromptText("Numero de factura");
        TextField amountField = createField("0");
        amountField.setPromptText("Valor total de la factura");
        TextField initialPaymentField = createField("0");
        initialPaymentField.setPromptText("Abono inicial");
        configureSelectAllOnFocus(amountField);
        configureSelectAllOnFocus(initialPaymentField);
        TextField remainingField = createField("$ 0");
        remainingField.setDisable(true);
        DatePicker issueDatePicker = new DatePicker(LocalDate.now());
        DatePicker dueDatePicker = new DatePicker(LocalDate.now().plusDays(15));
        TextArea descriptionArea = createArea("", 2);
        descriptionArea.setPromptText("Concepto, detalle u observacion comercial");
        AtomicReference<List<File>> selectedFiles = new AtomicReference<>(List.of());
        Label filesValue = createMetaValueLabel("Sin soportes adjuntos");
        Label filesSummaryValue = createMetaValueLabel(filesValue.getText());
        filesSummaryValue.textProperty().bind(filesValue.textProperty());
        Button chooseFilesButton = createActionButton("Adjuntar soportes", "ghost-button");
        chooseFilesButton.setOnAction(event -> {
            List<File> files = chooseSupportFiles(stage, "Selecciona soportes de la factura");
            if (!files.isEmpty()) {
                selectedFiles.set(files);
                filesValue.setText(formatSelectedFiles(files));
            }
        });

        Runnable updateRemaining = () -> {
            BigDecimal total = parseCurrencyOrZero(amountField.getText());
            BigDecimal initialPayment = parseCurrencyOrZero(initialPaymentField.getText());
            remainingField.setText(formatCurrency(total.subtract(initialPayment).max(BigDecimal.ZERO)));
        };
        amountField.textProperty().addListener((obs, oldValue, newValue) -> updateRemaining.run());
        initialPaymentField.textProperty().addListener((obs, oldValue, newValue) -> updateRemaining.run());
        updateRemaining.run();

        Button save = createActionButton("Guardar factura", "primary-button");
        save.setMaxWidth(Double.MAX_VALUE);
        save.setOnAction(event -> {
            try {
                BigDecimal total = parseRequiredPositive(amountField.getText(), "El valor de la factura debe ser mayor a cero.");
                BigDecimal initialPayment = parseCurrencyOrZero(initialPaymentField.getText());
                if (initialPayment.signum() < 0) {
                    throw new IllegalArgumentException("El abono inicial no puede ser negativo.");
                }
                if (initialPayment.compareTo(total) > 0) {
                    throw new IllegalArgumentException("El abono inicial no puede superar el valor total de la factura.");
                }
                save.setDisable(true);
                runAsync(
                        () -> posApiClient.registrarFacturaProveedor(
                                new PosApiClient.RegistrarFacturaProveedorRequest(
                                        provider.id(),
                                        numberField.getText(),
                                        issueDatePicker.getValue(),
                                        dueDatePicker.getValue(),
                                        total,
                                        total.subtract(initialPayment).max(BigDecimal.ZERO),
                                        descriptionArea.getText()
                                ),
                                selectedFiles.get().stream().map(File::toPath).toList()
                        ),
                        createdInvoice -> {
                            stage.close();
                            if (afterSave != null) {
                                afterSave.run();
                            }
                            if (onCreated != null) {
                                onCreated.accept(createdInvoice.id());
                            }
                            showInfo(
                                    "Factura registrada",
                                    "Se creó la factura " + createdInvoice.numeroFactura() + " para " + provider.nombre()
                                            + " con saldo inicial de " + formatCurrency(createdInvoice.saldoPendiente()) + "."
                            );
                        },
                        exception -> {
                            save.setDisable(false);
                            showError("Nueva factura", exception.getMessage());
                        }
                );
            } catch (IllegalArgumentException exception) {
                showError("Nueva factura", exception.getMessage());
            }
        });

        FlowPane fields = createResponsiveRow(
                createFieldGroup("Proveedor", providerField, 280),
                createFieldGroup("Factura", numberField, 220),
                createFieldGroup("Fecha emision", issueDatePicker, 220),
                createFieldGroup("Vencimiento", dueDatePicker, 220),
                createFieldGroup("Valor total", amountField, 220),
                createFieldGroup("Abono inicial", initialPaymentField, 220)
        );
        VBox formCard = createCard(
                "Datos de la factura",
                "Captura el valor total, cualquier abono inicial y los soportes asociados."
        );
        HBox.setHgrow(formCard, Priority.ALWAYS);
        formCard.getChildren().addAll(
                fields,
                createFieldGroup("Observacion", descriptionArea, 520),
                createFieldGroup("Soportes", new VBox(8, chooseFilesButton, filesValue), 520)
        );

        VBox summaryCard = createCard(
                "Impacto financiero",
                "La pantalla proyecta el saldo restante antes de enviar la factura."
        );
        bindRegionWidthToScene(summaryCard, 0.28, 270, 330);
        summaryCard.setMaxWidth(Region.USE_PREF_SIZE);
        Label remainingValue = createMetaValueLabel(remainingField.getText());
        remainingValue.textProperty().bind(remainingField.textProperty());
        summaryCard.getChildren().addAll(
                createKeyValue("Proveedor", createMetaValueLabel(provider.nombre())),
                createKeyValue("NIT", createMetaValueLabel(safeText(provider.nit(), "-"))),
                createKeyValue("Soportes", filesSummaryValue),
                createKeyValue("Saldo inicial", remainingValue),
                createProgressCard("Registro real", 0.74, "La API guardará la factura, sus soportes y el saldo pendiente."),
                save
        );

        HBox workspace = createAdaptivePanelRow(formCard, summaryCard);
        VBox.setVgrow(workspace, Priority.ALWAYS);
        root.getChildren().add(workspace);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/com/posdesktop/pos/mockfx/mock-theme.css").toExternalForm());
        stage.setScene(scene);
        applyResponsiveStageSize(stage, 0.88, 0.78, 1020, 600);
        stage.show();
    }

    private void showInvoiceUpdateWindow(
            Window owner,
            PosApiClient.ProveedorResponse provider,
            PosApiClient.FacturaProveedorListadoResponse invoice,
            PosApiClient.FacturaProveedorDetalleResponse invoiceDetail,
            Runnable afterSave
    ) {
        if (!hasPermission(PERM_FACTURAS_EDIT)) {
            showError("Facturas", "Tu usuario no tiene permiso para actualizar facturas.");
            return;
        }
        if (provider == null || invoice == null) {
            showError("Facturas", "Selecciona una factura para actualizar sus datos.");
            return;
        }

        Stage stage = createDialogStage("Actualizar factura");
        if (owner != null) {
            stage.initOwner(owner);
        }

        VBox root = createDialogRoot(
                "Actualizar factura",
                "Edita el valor o los detalles principales de la factura seleccionada."
        );
        root.getStyleClass().add("invoice-workspace");
        root.setSpacing(12);
        root.setPadding(new Insets(16));

        TextField providerField = createField(provider.nombre());
        providerField.setDisable(true);
        TextField numberField = createField(safeText(invoice.numeroFactura(), ""));
        numberField.setPromptText("Numero de factura");
        TextField amountField = createField(formatPlainNumber(invoice.montoTotal()));
        amountField.setPromptText("Valor total de la factura");
        configureSelectAllOnFocus(amountField);
        TextField paidField = createField(formatCurrency(invoice.montoPagado()));
        paidField.setDisable(true);
        TextField projectedBalanceField = createField(formatCurrency(invoice.saldoPendiente()));
        projectedBalanceField.setDisable(true);
        DatePicker issueDatePicker = new DatePicker(invoice.fechaEmision());
        DatePicker dueDatePicker = new DatePicker(invoice.fechaVencimiento());
        TextArea descriptionArea = createArea(
                safeText(invoiceDetail == null ? invoice.observacion() : invoiceDetail.observacion(), ""),
                2
        );
        descriptionArea.setPromptText("Concepto, detalle u observacion comercial");

        Runnable updateProjectedBalance = () -> {
            BigDecimal total = parseCurrencyOrZero(amountField.getText());
            BigDecimal paid = invoice.montoPagado() == null ? BigDecimal.ZERO : invoice.montoPagado();
            projectedBalanceField.setText(formatCurrency(total.subtract(paid).max(BigDecimal.ZERO)));
        };
        amountField.textProperty().addListener((obs, oldValue, newValue) -> updateProjectedBalance.run());
        updateProjectedBalance.run();

        Button save = createActionButton("Guardar cambios", "primary-button");
        save.setMaxWidth(Double.MAX_VALUE);
        save.setOnAction(event -> {
            try {
                BigDecimal total = parseRequiredPositive(amountField.getText(), "El valor total debe ser mayor a cero.");
                BigDecimal paid = invoice.montoPagado() == null ? BigDecimal.ZERO : invoice.montoPagado();
                if (total.compareTo(paid) < 0) {
                    throw new IllegalArgumentException(
                            "El valor total no puede ser menor a lo ya abonado en la factura."
                    );
                }
                save.setDisable(true);
                runAsync(
                        () -> posApiClient.actualizarFacturaProveedor(
                                invoice.id(),
                                new PosApiClient.ActualizarFacturaProveedorRequest(
                                        numberField.getText(),
                                        issueDatePicker.getValue(),
                                        dueDatePicker.getValue(),
                                        total,
                                        descriptionArea.getText()
                                )
                        ),
                        updatedInvoice -> {
                            stage.close();
                            if (afterSave != null) {
                                afterSave.run();
                            }
                            showInfo(
                                    "Factura actualizada",
                                    "Se actualizo la factura " + updatedInvoice.numeroFactura()
                                            + " y el nuevo saldo pendiente es "
                                            + formatCurrency(updatedInvoice.saldoPendiente()) + "."
                            );
                        },
                        exception -> {
                            save.setDisable(false);
                            showError("Actualizar factura", exception.getMessage());
                        }
                );
            } catch (IllegalArgumentException exception) {
                showError("Actualizar factura", exception.getMessage());
            }
        });

        FlowPane fields = createResponsiveRow(
                createFieldGroup("Proveedor", providerField, 260),
                createFieldGroup("Factura", numberField, 220),
                createFieldGroup("Fecha emision", issueDatePicker, 220),
                createFieldGroup("Vencimiento", dueDatePicker, 220),
                createFieldGroup("Valor total", amountField, 220),
                createFieldGroup("Abonado", paidField, 220),
                createFieldGroup("Saldo proyectado", projectedBalanceField, 220)
        );
        VBox formCard = createCard(
                "Edicion de factura",
                "Ajusta los valores base sin perder el historico de abonos registrados."
        );
        HBox.setHgrow(formCard, Priority.ALWAYS);
        formCard.getChildren().addAll(
                fields,
                createFieldGroup("Observacion", descriptionArea, 520)
        );

        VBox summaryCard = createCard(
                "Impacto del ajuste",
                "La API recalcula el saldo pendiente con base en lo ya abonado."
        );
        bindRegionWidthToScene(summaryCard, 0.28, 270, 330);
        summaryCard.setMaxWidth(Region.USE_PREF_SIZE);
        Label projectedBalanceLabel = createMetaValueLabel(projectedBalanceField.getText());
        projectedBalanceLabel.textProperty().bind(projectedBalanceField.textProperty());
        summaryCard.getChildren().addAll(
                createKeyValue("Factura", createMetaValueLabel(safeText(invoice.numeroFactura(), "-"))),
                createKeyValue("Proveedor", createMetaValueLabel(provider.nombre())),
                createKeyValue("Abonado", createMetaValueLabel(formatCurrency(invoice.montoPagado()))),
                createKeyValue("Saldo nuevo", projectedBalanceLabel),
                createProgressCard("Actualizacion real", 0.78, "La factura mantiene sus abonos y recalcula el saldo restante."),
                save
        );

        HBox workspace = createAdaptivePanelRow(formCard, summaryCard);
        VBox.setVgrow(workspace, Priority.ALWAYS);
        root.getChildren().add(workspace);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/com/posdesktop/pos/mockfx/mock-theme.css").toExternalForm());
        stage.setScene(scene);
        applyResponsiveStageSize(stage, 0.88, 0.78, 1020, 600);
        stage.show();
    }

    private void showInvoicePaymentWindow(
            Window owner,
            PosApiClient.ProveedorResponse provider,
            PosApiClient.FacturaProveedorListadoResponse invoice,
            Runnable afterSave
    ) {
        if (!hasPermission(PERM_FACTURAS_EDIT)) {
            showError("Facturas", "Tu usuario no tiene permiso para registrar abonos.");
            return;
        }
        if (provider == null || invoice == null) {
            showError("Facturas", "Selecciona una factura para registrar un abono.");
            return;
        }

        Stage stage = createDialogStage("Registrar abono");
        if (owner != null) {
            stage.initOwner(owner);
        }

        VBox root = createDialogRoot(
                "Registrar abono",
                "Registra el pago parcial o total y adjunta su comprobante real."
        );
        root.getStyleClass().add("invoice-workspace");
        root.setSpacing(12);
        root.setPadding(new Insets(16));

        TextField providerField = createField(provider.nombre());
        providerField.setDisable(true);
        TextField invoiceField = createField(invoice.numeroFactura());
        invoiceField.setDisable(true);
        TextField currentBalanceField = createField(formatCurrency(invoice.saldoPendiente()));
        currentBalanceField.setDisable(true);
        TextField amountField = createField("0");
        amountField.setPromptText("Valor del abono");
        configureSelectAllOnFocus(amountField);
        TextField projectedBalanceField = createField(formatCurrency(invoice.saldoPendiente()));
        projectedBalanceField.setDisable(true);
        DatePicker paymentDatePicker = new DatePicker(LocalDate.now());
        ComboBox<String> paymentMethod = new ComboBox<>(FXCollections.observableArrayList(
                "TRANSFERENCIA", "EFECTIVO", "CONSIGNACION", "TARJETA", "OTRO"
        ));
        paymentMethod.getSelectionModel().select("TRANSFERENCIA");
        TextField referenceField = createField("");
        referenceField.setPromptText("Referencia opcional");
        TextArea notesArea = createArea("", 2);
        notesArea.setPromptText("Observacion del abono");
        AtomicReference<List<File>> selectedFiles = new AtomicReference<>(List.of());
        Label supportFilesValue = createMetaValueLabel("Sin comprobantes adjuntos");
        Label supportFilesSummaryValue = createMetaValueLabel(supportFilesValue.getText());
        supportFilesSummaryValue.textProperty().bind(supportFilesValue.textProperty());
        Button chooseFilesButton = createActionButton("Adjuntar comprobantes", "ghost-button");
        chooseFilesButton.setOnAction(event -> {
            List<File> files = chooseSupportFiles(stage, "Selecciona comprobantes del abono");
            if (!files.isEmpty()) {
                selectedFiles.set(files);
                supportFilesValue.setText(formatSelectedFiles(files));
            }
        });

        Runnable updateProjectedBalance = () -> {
            BigDecimal currentBalance = invoice.saldoPendiente() == null ? BigDecimal.ZERO : invoice.saldoPendiente();
            BigDecimal payment = parseCurrencyOrZero(amountField.getText());
            projectedBalanceField.setText(formatCurrency(currentBalance.subtract(payment).max(BigDecimal.ZERO)));
        };
        amountField.textProperty().addListener((obs, oldValue, newValue) -> updateProjectedBalance.run());
        updateProjectedBalance.run();

        Button save = createActionButton("Guardar abono", "primary-button");
        save.setMaxWidth(Double.MAX_VALUE);
        save.setOnAction(event -> {
            try {
                BigDecimal payment = parseRequiredPositive(amountField.getText(), "El valor del abono debe ser mayor a cero.");
                BigDecimal currentBalance = invoice.saldoPendiente() == null ? BigDecimal.ZERO : invoice.saldoPendiente();
                if (payment.compareTo(currentBalance) > 0) {
                    throw new IllegalArgumentException("El abono no puede superar el saldo pendiente de la factura.");
                }
                save.setDisable(true);
                runAsync(
                        () -> posApiClient.registrarAbonoFactura(
                                invoice.id(),
                                new PosApiClient.RegistrarPagoFacturaRequest(
                                        paymentDatePicker.getValue(),
                                        payment,
                                        paymentMethod.getValue(),
                                        referenceField.getText(),
                                        notesArea.getText()
                                ),
                                selectedFiles.get().stream().map(File::toPath).toList()
                        ),
                        updatedInvoice -> {
                            stage.close();
                            if (afterSave != null) {
                                afterSave.run();
                            }
                            showInfo(
                                    "Abono registrado",
                                    "Se registró un abono de " + formatCurrency(payment) + " para la factura "
                                            + updatedInvoice.numeroFactura() + ". Restante: "
                                            + formatCurrency(updatedInvoice.saldoPendiente()) + "."
                            );
                        },
                        exception -> {
                            save.setDisable(false);
                            showError("Registrar abono", exception.getMessage());
                        }
                );
            } catch (IllegalArgumentException exception) {
                showError("Registrar abono", exception.getMessage());
            }
        });

        FlowPane fields = createResponsiveRow(
                createFieldGroup("Proveedor", providerField, 260),
                createFieldGroup("Factura", invoiceField, 220),
                createFieldGroup("Saldo actual", currentBalanceField, 220),
                createFieldGroup("Fecha pago", paymentDatePicker, 220),
                createFieldGroup("Valor abono", amountField, 220),
                createFieldGroup("Restante proyectado", projectedBalanceField, 220),
                createFieldGroup("Medio", paymentMethod, 220),
                createFieldGroup("Referencia", referenceField, 220)
        );
        VBox formCard = createCard(
                "Registro de abono",
                "El saldo se recalcula contra la factura padre en tiempo real."
        );
        HBox.setHgrow(formCard, Priority.ALWAYS);
        formCard.getChildren().addAll(
                fields,
                createFieldGroup("Observacion", notesArea, 520),
                createFieldGroup("Comprobantes", new VBox(8, chooseFilesButton, supportFilesValue), 520)
        );

        VBox summaryCard = createCard(
                "Disminución proyectada",
                "Revisa cuánto bajará la deuda antes de enviar el abono."
        );
        bindRegionWidthToScene(summaryCard, 0.28, 270, 330);
        summaryCard.setMaxWidth(Region.USE_PREF_SIZE);
        Label projectedBalanceLabel = createMetaValueLabel(projectedBalanceField.getText());
        projectedBalanceLabel.textProperty().bind(projectedBalanceField.textProperty());
        Label paymentMethodValue = createMetaValueLabel(formatPaymentMethod(paymentMethod.getValue()));
        paymentMethodValue.textProperty().bind(Bindings.createStringBinding(
                () -> formatPaymentMethod(paymentMethod.getValue()),
                paymentMethod.valueProperty()
        ));
        summaryCard.getChildren().addAll(
                createKeyValue("Factura", createMetaValueLabel(invoice.numeroFactura())),
                createKeyValue("Saldo actual", createMetaValueLabel(formatCurrency(invoice.saldoPendiente()))),
                createKeyValue("Medio", paymentMethodValue),
                createKeyValue("Comprobantes", supportFilesSummaryValue),
                createKeyValue("Restante", projectedBalanceLabel),
                createProgressCard("Actualización real", 0.8, "La API recalcula el saldo de la factura y conserva los soportes del abono."),
                save
        );

        HBox workspace = createAdaptivePanelRow(formCard, summaryCard);
        VBox.setVgrow(workspace, Priority.ALWAYS);
        root.getChildren().add(workspace);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/com/posdesktop/pos/mockfx/mock-theme.css").toExternalForm());
        stage.setScene(scene);
        applyResponsiveStageSize(stage, 0.9, 0.8, 1040, 620);
        stage.show();
    }

    private List<File> chooseSupportFiles(Window owner, String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Soportes", "*.jpg", "*.jpeg", "*.png", "*.webp", "*.pdf"),
                new FileChooser.ExtensionFilter("Imagenes", "*.jpg", "*.jpeg", "*.png", "*.webp"),
                new FileChooser.ExtensionFilter("PDF", "*.pdf")
        );
        List<File> files = chooser.showOpenMultipleDialog(owner);
        return files == null ? List.of() : files;
    }

    private String formatSelectedFiles(List<File> files) {
        if (files == null || files.isEmpty()) {
            return "Sin archivos adjuntos";
        }
        if (files.size() == 1) {
            return files.get(0).getName();
        }
        return files.size() + " archivos seleccionados";
    }

    private String formatInvoiceStatus(String status) {
        if (status == null || status.isBlank()) {
            return "-";
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "REGISTRADA" -> "Registrada";
            case "PARCIALMENTE_PAGADA" -> "Parcial";
            case "PAGADA" -> "Pagada";
            case "VENCIDA" -> "Vencida";
            case "CANCELADA" -> "Cancelada";
            default -> status;
        };
    }

    private String formatPaymentMethod(String method) {
        if (method == null || method.isBlank()) {
            return "-";
        }
        return switch (method.toUpperCase(Locale.ROOT)) {
            case "TRANSFERENCIA" -> "Transferencia";
            case "EFECTIVO" -> "Efectivo";
            case "CONSIGNACION" -> "Consignacion";
            case "TARJETA" -> "Tarjeta";
            case "OTRO" -> "Otro";
            default -> method;
        };
    }

    private String formatDocumentType(String type) {
        if (type == null || type.isBlank()) {
            return "Soporte";
        }
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "IMAGEN_FACTURA" -> "Factura";
            case "COMPROBANTE_PAGO" -> "Comprobante";
            default -> type;
        };
    }

    private TableView<MockData.ProviderMockRow> createSupplierProvidersMockTable() {
        TableView<MockData.ProviderMockRow> table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        bindTableHeightToScene(table, 0.34, 260, 460);
        table.getColumns().addAll(
                tableColumn("Proveedor", MockData.ProviderMockRow::nombre),
                tableColumn("Saldo", MockData.ProviderMockRow::saldoPendiente),
                tableColumn("Estado", MockData.ProviderMockRow::estado)
        );
        return table;
    }

    private TableView<MockData.SupplierInvoiceMockRow> createSupplierInvoicesMockTable() {
        TableView<MockData.SupplierInvoiceMockRow> table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        bindTableHeightToScene(table, 0.23, 150, 230);
        table.getColumns().addAll(
                tableColumn("Numero", MockData.SupplierInvoiceMockRow::numero),
                tableColumn("Concepto", MockData.SupplierInvoiceMockRow::concepto),
                tableColumn("Registro", MockData.SupplierInvoiceMockRow::fechaRegistro),
                tableColumn("Vence", MockData.SupplierInvoiceMockRow::vencimiento),
                tableColumn("Valor", MockData.SupplierInvoiceMockRow::valorTotal),
                tableColumn("Abonado", MockData.SupplierInvoiceMockRow::abonado),
                tableColumn("Saldo", MockData.SupplierInvoiceMockRow::saldo),
                tableColumn("Estado", MockData.SupplierInvoiceMockRow::estado)
        );
        return table;
    }

    private TableView<MockData.SupplierPaymentMockRow> createSupplierPaymentsMockTable() {
        TableView<MockData.SupplierPaymentMockRow> table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        bindTableHeightToScene(table, 0.18, 120, 185);
        table.getColumns().addAll(
                tableColumn("Fecha", MockData.SupplierPaymentMockRow::fecha),
                tableColumn("Valor", MockData.SupplierPaymentMockRow::valor),
                tableColumn("Medio", MockData.SupplierPaymentMockRow::medio),
                tableColumn("Soporte", MockData.SupplierPaymentMockRow::soporte)
        );
        return table;
    }

    private TableView<MockData.SupplierSupportMockRow> createSupplierSupportsMockTable() {
        TableView<MockData.SupplierSupportMockRow> table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        bindTableHeightToScene(table, 0.18, 120, 185);
        table.getColumns().addAll(
                tableColumn("Tipo", MockData.SupplierSupportMockRow::tipo),
                tableColumn("Archivo", MockData.SupplierSupportMockRow::archivo),
                tableColumn("Formato", MockData.SupplierSupportMockRow::formato),
                tableColumn("Estado", MockData.SupplierSupportMockRow::estado)
        );
        return table;
    }

    private void updateInvoicesMockMetrics(
            List<MockData.ProviderMockRow> providers,
            Label providersValue,
            Label providersCaption,
            Label invoicesValue,
            Label invoicesCaption,
            Label balanceValue,
            Label balanceCaption,
            Label supportsValue,
            Label supportsCaption
    ) {
        List<MockData.SupplierInvoiceMockRow> invoices = MockData.supplierInvoices();
        long openInvoices = invoices.stream()
                .filter(invoice -> parseCurrencyOrZero(invoice.saldo()).signum() > 0)
                .count();
        BigDecimal supplierBalance = invoices.stream()
                .map(invoice -> parseCurrencyOrZero(invoice.saldo()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        providersValue.setText(String.valueOf(providers.size()));
        providersCaption.setText("Grupo de proveedores mock");
        invoicesValue.setText(String.valueOf(openInvoices));
        invoicesCaption.setText(invoices.size() + " facturas visibles");
        balanceValue.setText(formatCurrency(supplierBalance));
        balanceCaption.setText("Saldo agregado de proveedores");
        supportsValue.setText(String.valueOf(MockData.supplierSupports().size()));
        supportsCaption.setText("Soportes PDF/JPG listos");
    }

    private void updateSelectedInvoiceMockState(
            MockData.SupplierInvoiceMockRow invoice,
            Label selectedInvoiceValue,
            Label invoiceConceptValue,
            Label invoiceDueDateValue,
            Label invoiceBalanceValue,
            Label invoiceSupportValue,
            Label paymentSummaryValue,
            Label supportSummaryValue,
            ProgressBar invoiceProgressBar,
            Label invoiceProgressCaption
    ) {
        if (invoice == null) {
            selectedInvoiceValue.setText("Selecciona una factura");
            invoiceConceptValue.setText("-");
            invoiceDueDateValue.setText("-");
            invoiceBalanceValue.setText("$ 0");
            invoiceSupportValue.setText("-");
            paymentSummaryValue.setText("0 abonos");
            supportSummaryValue.setText("0 soportes");
            invoiceProgressBar.setProgress(0);
            invoiceProgressCaption.setText("Sin factura seleccionada");
            return;
        }

        List<MockData.SupplierPaymentMockRow> payments = MockData.paymentsByInvoice(invoice.id());
        List<MockData.SupplierSupportMockRow> supports = MockData.supportsByInvoice(invoice.id());
        BigDecimal total = parseCurrencyOrZero(invoice.valorTotal());
        BigDecimal paid = parseCurrencyOrZero(invoice.abonado());
        selectedInvoiceValue.setText(invoice.numero());
        invoiceConceptValue.setText(invoice.concepto());
        invoiceDueDateValue.setText(invoice.vencimiento() + " | " + invoice.estado());
        invoiceBalanceValue.setText(invoice.saldo());
        invoiceSupportValue.setText(supports.isEmpty() ? "Pendiente" : supports.get(0).archivo());
        paymentSummaryValue.setText(payments.size() + " abonos mock");
        supportSummaryValue.setText(supports.size() + " soportes PDF/JPG");
        invoiceProgressBar.setProgress(calculateLayawayProgress(total, paid));
        invoiceProgressCaption.setText(invoice.abonado() + " abonados de " + invoice.valorTotal());
    }

    private void loadLayaways(
            ObservableList<PosApiClient.SeparadoListadoResponse> source,
            FilteredList<PosApiClient.SeparadoListadoResponse> filteredLayaways,
            TableView<PosApiClient.SeparadoListadoResponse> table,
            TextField searchField,
            TextField articleFilterField,
            ComboBox<String> statusFilter,
            AtomicReference<String> selectedLayawayId,
            AtomicReference<PosApiClient.SeparadoDetalleResponse> selectedLayawayDetail,
            Label activeValue,
            Label paidValue,
            Label totalBalanceValue,
            Label paymentsTodayValue,
            Label selectedNumberValue,
            Label selectedClientValue,
            Label selectedItemsValue,
            Label selectedStatusValue,
            Label minimumValue,
            ProgressBar paymentProgressBar,
            Label paymentProgressCaption
    ) {
        runAsync(
                () -> posApiClient.listarSeparados(
                        resolveLayawayStatusFilter(statusFilter.getValue()),
                        articleFilterField.getText()
                ),
                separados -> {
                    source.setAll(separados);
                    applyLayawayFilters(filteredLayaways, searchField.getText());
                    updateLayawayStats(separados, activeValue, paidValue, totalBalanceValue);
                    loadPaymentsTodayMetric(paymentsTodayValue);
                    if (separados.isEmpty()) {
                        selectedLayawayId.set(null);
                        selectedLayawayDetail.set(null);
                        table.getSelectionModel().clearSelection();
                        updateLayawayProfile(
                                null,
                                selectedNumberValue,
                                selectedClientValue,
                                selectedItemsValue,
                                selectedStatusValue,
                                minimumValue,
                                paymentProgressBar,
                                paymentProgressCaption
                        );
                        return;
                    }

                    String selectedId = selectedLayawayId.get();
                    if (selectedId != null && selectLayawayRow(table, selectedId)) {
                        return;
                    }
                    table.getSelectionModel().selectFirst();
                },
                exception -> showError("Separados", exception.getMessage())
        );
    }

    private void loadLayawayDetail(
            String layawayId,
            AtomicReference<String> selectedLayawayId,
            AtomicReference<PosApiClient.SeparadoDetalleResponse> selectedLayawayDetail,
            Label selectedNumberValue,
            Label selectedClientValue,
            Label selectedItemsValue,
            Label selectedStatusValue,
            Label minimumValue,
            ProgressBar paymentProgressBar,
            Label paymentProgressCaption
    ) {
        if (layawayId == null || layawayId.isBlank()) {
            selectedLayawayDetail.set(null);
            updateLayawayProfile(
                    null,
                    selectedNumberValue,
                    selectedClientValue,
                    selectedItemsValue,
                    selectedStatusValue,
                    minimumValue,
                    paymentProgressBar,
                    paymentProgressCaption
            );
            return;
        }

        runAsync(
                () -> posApiClient.consultarSeparado(layawayId),
                detail -> {
                    if (!layawayId.equals(selectedLayawayId.get())) {
                        return;
                    }
                    selectedLayawayDetail.set(detail);
                    updateLayawayProfile(
                            detail,
                            selectedNumberValue,
                            selectedClientValue,
                            selectedItemsValue,
                            selectedStatusValue,
                            minimumValue,
                            paymentProgressBar,
                            paymentProgressCaption
                    );
                },
                exception -> {
                    if (layawayId.equals(selectedLayawayId.get())) {
                        selectedLayawayDetail.set(null);
                    }
                    showError("Separados", exception.getMessage());
                }
        );
    }

    private void applyLayawayFilters(
            FilteredList<PosApiClient.SeparadoListadoResponse> filteredLayaways,
            String searchText
    ) {
        String normalizedSearch = searchText == null ? "" : searchText.trim().toLowerCase(Locale.ROOT);
        filteredLayaways.setPredicate(separado -> {
            if (separado == null) {
                return false;
            }
            if (normalizedSearch.isBlank()) {
                return true;
            }
            return containsIgnoreCase(separado.numeroSeparado(), normalizedSearch)
                    || containsIgnoreCase(separado.cliente(), normalizedSearch);
        });
    }

    private String resolveLayawayStatusFilter(String statusFilter) {
        String normalizedStatus = normalizeStatusLabel(statusFilter);
        if (FILTER_ALL.equals(normalizedStatus)) {
            return null;
        }
        return switch (normalizedStatus.toLowerCase(Locale.ROOT)) {
            case "activo" -> "ACTIVO";
            case "pagado" -> "PAGADO";
            case "entregado" -> "ENTREGADO";
            case "cancelado" -> "CANCELADO";
            default -> null;
        };
    }

    private void updateLayawayStats(
            List<PosApiClient.SeparadoListadoResponse> separated,
            Label activeValue,
            Label paidValue,
            Label totalBalanceValue
    ) {
        long activeCount = separated.stream()
                .filter(item -> "ACTIVO".equalsIgnoreCase(item.estado()))
                .count();
        long paidCount = separated.stream()
                .filter(item -> "PAGADO".equalsIgnoreCase(item.estado()))
                .count();
        BigDecimal totalBalance = separated.stream()
                .map(PosApiClient.SeparadoListadoResponse::saldoPendiente)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        activeValue.setText(activeCount + " separados");
        paidValue.setText(paidCount + " listos para entrega");
        totalBalanceValue.setText(formatCurrency(totalBalance));
    }

    private void loadPaymentsTodayMetric(Label paymentsTodayValue) {
        runAsync(
                () -> posApiClient.listarMovimientos(LocalDate.now(), LocalDate.now()),
                movements -> {
                    BigDecimal paymentsToday = movements.stream()
                            .filter(movement -> "SEPARADO".equalsIgnoreCase(movement.origen()))
                            .map(PosApiClient.MovimientoVentaResponse::total)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    paymentsTodayValue.setText(formatCurrency(paymentsToday));
                },
                exception -> paymentsTodayValue.setText(formatCurrency(BigDecimal.ZERO))
        );
    }

    private void updateLayawayProfile(
            PosApiClient.SeparadoDetalleResponse detail,
            Label selectedNumberValue,
            Label selectedClientValue,
            Label selectedItemsValue,
            Label selectedStatusValue,
            Label minimumValue,
            ProgressBar paymentProgressBar,
            Label paymentProgressCaption
    ) {
        if (detail == null) {
            selectedNumberValue.setText("-");
            selectedClientValue.setText("-");
            selectedItemsValue.setText("-");
            selectedStatusValue.setText("-");
            minimumValue.setText("-");
            paymentProgressBar.setProgress(0);
            paymentProgressCaption.setText("Sin separado seleccionado");
            return;
        }

        selectedNumberValue.setText(detail.numeroSeparado());
        selectedClientValue.setText(detail.cliente());
        selectedItemsValue.setText(detail.descripcionArticulos());
        selectedStatusValue.setText(formatLayawayStatus(detail.estado()));
        minimumValue.setText(formatCurrency(detail.montoMinimoInicial()));
        paymentProgressBar.setProgress(calculateLayawayProgress(detail.valorTotal(), detail.totalAbonado()));
        paymentProgressCaption.setText(formatCurrency(detail.totalAbonado()) + " abonados de "
                + formatCurrency(detail.valorTotal()) + " | Restante "
                + formatCurrency(detail.saldoPendiente()));
    }

    private boolean selectLayawayRow(TableView<PosApiClient.SeparadoListadoResponse> table, String layawayId) {
        if (layawayId == null || layawayId.isBlank()) {
            return false;
        }
        for (PosApiClient.SeparadoListadoResponse row : table.getItems()) {
            if (layawayId.equals(row.id())) {
                table.getSelectionModel().select(row);
                table.scrollTo(row);
                return true;
            }
        }
        return false;
    }

    private boolean containsIgnoreCase(String source, String token) {
        return source != null && token != null && source.toLowerCase(Locale.ROOT).contains(token);
    }

    private String formatLayawayStatus(String status) {
        if (status == null || status.isBlank()) {
            return "-";
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "ACTIVO" -> "Activo";
            case "PAGADO" -> "Pagado";
            case "ENTREGADO" -> "Entregado";
            case "CANCELADO" -> "Cancelado";
            default -> status;
        };
    }

    private double calculateLayawayProgress(BigDecimal total, BigDecimal paid) {
        BigDecimal safeTotal = total == null ? BigDecimal.ZERO : total;
        BigDecimal safePaid = paid == null ? BigDecimal.ZERO : paid;
        if (safeTotal.signum() <= 0) {
            return 0;
        }
        BigDecimal ratio = safePaid.divide(safeTotal, 4, RoundingMode.HALF_UP);
        double progress = ratio.doubleValue();
        if (progress < 0) {
            return 0;
        }
        return Math.min(progress, 1);
    }

    private Node createMovementsFilterCard(
            DatePicker fechaInicialPicker,
            DatePicker fechaFinalPicker,
            TableView<PosApiClient.MovimientoVentaResponse> movementsTable,
            Label totalCajaValue,
            Label totalCajaCaption,
            Label recibidoValue,
            Label recibidoCaption,
            Label devueltoValue,
            Label devueltoCaption
    ) {
        VBox card = createCard("Filtros", "Consulta por rango de fechas y origen del movimiento.");
        card.getStyleClass().add("movements-filter-card");
        bindRegionWidthToScene(card, 0.17, 182, 218);
        card.setMaxWidth(Region.USE_PREF_SIZE);

        ComboBox<String> origin = new ComboBox<>(FXCollections.observableArrayList(
                "Todos", "Venta mostrador", "Venta manual", "Abono separado"
        ));
        origin.getSelectionModel().selectFirst();

        FlowPane fields = createResponsiveRow(
                createFieldGroup("Fecha inicial", fechaInicialPicker, 184),
                createFieldGroup("Fecha final", fechaFinalPicker, 184),
                createFieldGroup("Origen", origin, 184)
        );

        Button search = createActionButton("Buscar movimientos", "primary-button");
        search.setMaxWidth(Double.MAX_VALUE);
        search.setOnAction(event -> loadMovements(
                fechaInicialPicker.getValue(),
                fechaFinalPicker.getValue(),
                movementsTable,
                totalCajaValue,
                totalCajaCaption,
                recibidoValue,
                recibidoCaption,
                devueltoValue,
                devueltoCaption
        ));

        card.getChildren().addAll(fields, search);
        return card;
    }

    private Node createMovementsTableCard(TableView<PosApiClient.MovimientoVentaResponse> table) {
        VBox card = createCard("Movimientos encontrados", "Tabla consolidada con ingresos del dia, recibido y cambio.");
        card.getStyleClass().add("movements-table-card");
        HBox.setHgrow(card, Priority.ALWAYS);
        card.getChildren().add(table);
        return card;
    }

    private Node createMovementsInsightsCard(
            Label totalCajaValue,
            Label totalCajaCaption,
            Label recibidoValue,
            Label recibidoCaption,
            Label devueltoValue,
            Label devueltoCaption
    ) {
        VBox card = createCard("Lectura rapida", "Resumen visual de la jornada.");
        card.getStyleClass().add("movements-insights-card");
        bindRegionWidthToScene(card, 0.17, 176, 224);
        card.setMaxWidth(Region.USE_PREF_SIZE);
        totalCajaValue.getStyleClass().add("movements-insight-value");
        totalCajaCaption.getStyleClass().add("movements-insight-caption");
        recibidoValue.getStyleClass().add("movements-insight-value");
        recibidoCaption.getStyleClass().add("movements-insight-caption");
        devueltoValue.getStyleClass().add("movements-insight-value");
        devueltoCaption.getStyleClass().add("movements-insight-caption");
        VBox totalCajaCard = createMetricCard("Total caja", totalCajaValue, totalCajaCaption);
        totalCajaCard.getStyleClass().add("movements-insight-metric");
        VBox recibidoCard = createMetricCard("Recibido", recibidoValue, recibidoCaption);
        recibidoCard.getStyleClass().add("movements-insight-metric");
        VBox devueltoCard = createMetricCard("Devuelto", devueltoValue, devueltoCaption);
        devueltoCard.getStyleClass().add("movements-insight-metric");
        card.getChildren().addAll(
                totalCajaCard,
                recibidoCard,
                devueltoCard
        );
        return card;
    }

    private void showMockProviderInvoicesWindow(Window owner, MockData.ProviderMockRow provider) {
        showMockProviderInvoicesWindow(owner, provider, null);
    }

    private void showMockProviderInvoicesWindow(Window owner, MockData.ProviderMockRow provider, Runnable afterMutation) {
        if (provider == null) {
            showError("Facturas", "Selecciona un proveedor para visualizar sus facturas.");
            return;
        }

        Stage stage = createDialogStage("Facturas | " + provider.nombre());
        if (owner != null) {
            stage.initOwner(owner);
        }

        ObservableList<MockData.SupplierInvoiceMockRow> invoiceSource = FXCollections.observableArrayList(
                MockData.invoicesByProvider(provider.id())
        );
        ObservableList<MockData.SupplierPaymentMockRow> paymentSource = FXCollections.observableArrayList();

        TableView<MockData.SupplierInvoiceMockRow> invoiceTable = createSupplierInvoicesMockTable();
        invoiceTable.setItems(invoiceSource);
        invoiceTable.prefHeightProperty().unbind();
        invoiceTable.setMaxHeight(Double.MAX_VALUE);
        TableView<MockData.SupplierPaymentMockRow> paymentTable = createSupplierPaymentsMockTable();
        paymentTable.setItems(paymentSource);
        paymentTable.prefHeightProperty().unbind();
        paymentTable.setMaxHeight(Double.MAX_VALUE);
        AtomicReference<String> selectedInvoiceId = new AtomicReference<>();

        Label debtValue = createMetricValueLabel("$ 0");
        Label invoiceCountValue = createMetricValueLabel(String.valueOf(invoiceSource.size()));
        Label supportCountValue = createMetricValueLabel("0");

        Label selectedInvoiceValue = createMetaValueLabel("Selecciona una factura");
        Label invoiceConceptValue = createMetaValueLabel("-");
        Label invoiceDueDateValue = createMetaValueLabel("-");
        Label invoiceBalanceValue = createMetaValueLabel("$ 0");
        Label paymentSummaryValue = createMetaValueLabel("0 abonos");
        Label providerNameValue = createMetaValueLabel(provider.nombre());
        Label providerCategoryValue = createMetaValueLabel(provider.categoria());
        Label providerContactValue = createMetaValueLabel(provider.contacto());
        ProgressBar invoiceProgressBar = new ProgressBar(0);
        invoiceProgressBar.setMaxWidth(Double.MAX_VALUE);
        invoiceProgressBar.getStyleClass().add("accent-progress");
        Label invoiceProgressCaption = new Label("Selecciona una factura para revisar su avance");
        invoiceProgressCaption.getStyleClass().add("progress-caption");

        Button newInvoiceButton = createActionButton("Nueva factura", "primary-button");
        newInvoiceButton.setPrefWidth(170);

        Button registerPaymentButton = createActionButton("Registrar abono", "ghost-button");
        registerPaymentButton.setPrefWidth(170);
        registerPaymentButton.disableProperty().bind(
                Bindings.isNull(invoiceTable.getSelectionModel().selectedItemProperty())
        );

        Button refreshButton = createActionButton("Actualizar mock", "ghost-button");
        refreshButton.setPrefWidth(170);

        invoiceTable.getSelectionModel().selectedItemProperty().addListener((obs, previous, invoice) -> {
            paymentSource.setAll(invoice == null ? List.of() : MockData.paymentsByInvoice(invoice.id()));
            if (invoice == null) {
                selectedInvoiceId.set(null);
                selectedInvoiceValue.setText("Selecciona una factura");
                invoiceConceptValue.setText("-");
                invoiceDueDateValue.setText("-");
                invoiceBalanceValue.setText("$ 0");
                paymentSummaryValue.setText("0 abonos");
                invoiceProgressBar.setProgress(0);
                invoiceProgressCaption.setText("Sin factura seleccionada");
                return;
            }

            selectedInvoiceId.set(invoice.id());
            List<MockData.SupplierPaymentMockRow> payments = MockData.paymentsByInvoice(invoice.id());
            BigDecimal total = parseCurrencyOrZero(invoice.valorTotal());
            BigDecimal paid = parseCurrencyOrZero(invoice.abonado());
            selectedInvoiceValue.setText(invoice.numero());
            invoiceConceptValue.setText(invoice.concepto());
            invoiceDueDateValue.setText(invoice.vencimiento() + " | " + invoice.estado());
            invoiceBalanceValue.setText(invoice.saldo());
            paymentSummaryValue.setText(payments.size() + " abonos mock");
            invoiceProgressBar.setProgress(calculateLayawayProgress(total, paid));
            invoiceProgressCaption.setText(invoice.abonado() + " abonados de " + invoice.valorTotal());
        });

        Runnable refreshInvoices = () -> {
            MockData.ProviderMockRow refreshedProvider = MockData.findProvider(provider.id()).orElse(provider);
            providerNameValue.setText(refreshedProvider.nombre());
            providerCategoryValue.setText(refreshedProvider.categoria());
            providerContactValue.setText(refreshedProvider.contacto());
            invoiceSource.setAll(MockData.invoicesByProvider(refreshedProvider.id()));
            BigDecimal totalDebt = invoiceSource.stream()
                    .map(invoice -> parseCurrencyOrZero(invoice.saldo()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            int supportCount = invoiceSource.stream()
                    .mapToInt(invoice -> MockData.supportsByInvoice(invoice.id()).size())
                    .sum();
            debtValue.setText(formatCurrency(totalDebt));
            invoiceCountValue.setText(String.valueOf(invoiceSource.size()));
            supportCountValue.setText(String.valueOf(supportCount));

            String selectedId = selectedInvoiceId.get();
            if (selectedId != null) {
                for (MockData.SupplierInvoiceMockRow row : invoiceSource) {
                    if (selectedId.equals(row.id())) {
                        invoiceTable.getSelectionModel().select(row);
                        invoiceTable.scrollTo(row);
                        return;
                    }
                }
            }
            if (!invoiceSource.isEmpty()) {
                invoiceTable.getSelectionModel().selectFirst();
            } else {
                invoiceTable.getSelectionModel().clearSelection();
                paymentSource.clear();
            }
        };

        newInvoiceButton.setOnAction(event -> showMockInvoiceWindow(
                stage,
                MockData.findProvider(provider.id()).orElse(provider),
                null,
                createdInvoiceId -> {
                    selectedInvoiceId.set(createdInvoiceId);
                    refreshInvoices.run();
                    if (afterMutation != null) {
                        afterMutation.run();
                    }
                }
        ));

        registerPaymentButton.setOnAction(event -> showMockInvoicePaymentWindow(
                stage,
                MockData.findProvider(provider.id()).orElse(provider),
                invoiceTable.getSelectionModel().getSelectedItem(),
                () -> {
                    refreshInvoices.run();
                    if (afterMutation != null) {
                        afterMutation.run();
                    }
                }
        ));

        refreshButton.setOnAction(event -> {
            refreshInvoices.run();
            if (afterMutation != null) {
                afterMutation.run();
            }
        });

        invoiceTable.setOnMouseClicked(event -> {
            if (event.getClickCount() < 2) {
                return;
            }
            MockData.SupplierInvoiceMockRow selectedInvoice = invoiceTable.getSelectionModel().getSelectedItem();
            if (selectedInvoice == null) {
                return;
            }
            showMockInvoicePaymentWindow(
                    stage,
                    MockData.findProvider(provider.id()).orElse(provider),
                    selectedInvoice,
                    () -> {
                        refreshInvoices.run();
                        if (afterMutation != null) {
                            afterMutation.run();
                        }
                    }
            );
        });

        VBox root = createDialogRoot(
                "Facturas de " + provider.nombre(),
                "Vista de consulta. Todo lo importante queda visible en una sola pantalla de trabajo."
        );
        root.getStyleClass().add("invoice-workspace");
        root.setSpacing(10);
        root.setPadding(new Insets(14));

        VBox invoiceListCard = createCard(
                "Facturas del proveedor",
                "Selecciona una factura para revisar sus abonos sin bajar a otras secciones."
        );
        HBox.setHgrow(invoiceListCard, Priority.ALWAYS);
        VBox.setVgrow(invoiceTable, Priority.ALWAYS);
        FlowPane providerSummaryFlow = new FlowPane();
        providerSummaryFlow.setHgap(10);
        providerSummaryFlow.setVgap(8);
        providerSummaryFlow.getStyleClass().add("invoice-provider-flow");
        providerSummaryFlow.getChildren().addAll(
                createCompactInfoBlock("Proveedor", providerNameValue),
                createCompactInfoBlock("Categoria", providerCategoryValue),
                createCompactInfoBlock("Contacto", providerContactValue),
                createCompactInfoBlock("Saldo proveedor", debtValue),
                createCompactInfoBlock("Facturas visibles", invoiceCountValue),
                createCompactInfoBlock("Soportes cargados", supportCountValue)
        );
        invoiceListCard.getChildren().addAll(
                providerSummaryFlow,
                invoiceTable
        );

        VBox paymentsCard = createCard(
                "Abonos registrados",
                "La derecha se reserva para ver los pagos parciales de la factura seleccionada."
        );
        paymentsCard.getStyleClass().add("invoice-compact-card");
        VBox.setVgrow(paymentTable, Priority.ALWAYS);
        FlowPane selectedInvoiceFlow = new FlowPane();
        selectedInvoiceFlow.setHgap(10);
        selectedInvoiceFlow.setVgap(8);
        selectedInvoiceFlow.getStyleClass().add("invoice-provider-flow");
        selectedInvoiceFlow.getChildren().addAll(
                createCompactInfoBlock("Factura", selectedInvoiceValue),
                createCompactInfoBlock("Concepto", invoiceConceptValue),
                createCompactInfoBlock("Vencimiento", invoiceDueDateValue),
                createCompactInfoBlock("Saldo", invoiceBalanceValue),
                createCompactInfoBlock("Abonos", paymentSummaryValue)
        );
        paymentsCard.getChildren().addAll(
                selectedInvoiceFlow,
                createProgressCard("Avance de pago", invoiceProgressBar, invoiceProgressCaption),
                paymentTable
        );

        FlowPane actionButtons = createResponsiveRow(
                newInvoiceButton,
                registerPaymentButton,
                refreshButton
        );
        actionButtons.getStyleClass().add("invoice-action-grid");
        actionButtons.setPrefWrapLength(760);

        VBox actionsFooter = createCard(
                "Acciones",
                "Accesos rapidos para operar esta factura sin salir de la vista."
        );
        actionsFooter.getStyleClass().add("invoice-compact-card");
        actionsFooter.getChildren().add(actionButtons);

        SplitPane workspace = createWorkspaceSplitPane(false, 0.64, invoiceListCard, paymentsCard);
        VBox.setVgrow(workspace, Priority.ALWAYS);
        root.getChildren().addAll(workspace, actionsFooter);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/com/posdesktop/pos/mockfx/mock-theme.css").toExternalForm());
        stage.setScene(scene);
        applyResponsiveStageSize(stage, 0.95, 0.9, 1220, 760);
        refreshInvoices.run();
        stage.show();
    }

    private void showMockProviderPortfolioWindow(Window owner, MockData.ProviderMockRow provider) {
        if (provider == null) {
            showError("Facturas", "Selecciona un proveedor para gestionar su cartera.");
            return;
        }

        Stage stage = createDialogStage("Gestion cartera | " + provider.nombre());
        if (owner != null) {
            stage.initOwner(owner);
        }

        ObservableList<MockData.SupplierInvoiceMockRow> invoiceSource = FXCollections.observableArrayList(
                MockData.invoicesByProvider(provider.id())
        );
        ObservableList<MockData.SupplierPaymentMockRow> paymentSource = FXCollections.observableArrayList();
        ObservableList<MockData.SupplierSupportMockRow> supportSource = FXCollections.observableArrayList();

        TableView<MockData.SupplierInvoiceMockRow> invoiceTable = createSupplierInvoicesMockTable();
        invoiceTable.setItems(invoiceSource);
        invoiceTable.prefHeightProperty().unbind();
        invoiceTable.setMaxHeight(Double.MAX_VALUE);
        TableView<MockData.SupplierPaymentMockRow> paymentTable = createSupplierPaymentsMockTable();
        paymentTable.setItems(paymentSource);
        paymentTable.prefHeightProperty().unbind();
        paymentTable.setMaxHeight(Double.MAX_VALUE);
        TableView<MockData.SupplierSupportMockRow> supportTable = createSupplierSupportsMockTable();
        supportTable.setItems(supportSource);
        supportTable.prefHeightProperty().unbind();
        supportTable.setMaxHeight(Double.MAX_VALUE);

        Label selectedInvoiceValue = createMetaValueLabel("Selecciona una factura");
        Label invoiceConceptValue = createMetaValueLabel("-");
        Label invoiceDueDateValue = createMetaValueLabel("-");
        Label invoiceBalanceValue = createMetaValueLabel("$ 0");
        Label invoiceSupportValue = createMetaValueLabel("-");
        Label paymentSummaryValue = createMetaValueLabel("0 abonos");
        Label supportSummaryValue = createMetaValueLabel("0 soportes");
        ProgressBar invoiceProgressBar = new ProgressBar(0);
        invoiceProgressBar.setMaxWidth(Double.MAX_VALUE);
        invoiceProgressBar.getStyleClass().add("accent-progress");
        Label invoiceProgressCaption = new Label("Selecciona una factura para operar la cartera");
        invoiceProgressCaption.getStyleClass().add("progress-caption");

        Label totalDebtValue = createMetricValueLabel(
                formatCurrency(invoiceSource.stream()
                        .map(invoice -> parseCurrencyOrZero(invoice.saldo()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
        );
        Label totalDebtCaption = createMetricCaptionLabel("Deuda total del proveedor");
        Label invoiceCountValue = createMetricValueLabel(String.valueOf(invoiceSource.size()));
        Label invoiceCountCaption = createMetricCaptionLabel("Facturas disponibles");
        Button newInvoiceButton = createActionButton("Nueva factura", "primary-button");
        newInvoiceButton.setMaxWidth(Double.MAX_VALUE);
        newInvoiceButton.setPrefWidth(170);
        newInvoiceButton.setOnAction(event -> showMockInvoiceWindow(stage, provider));

        Button registerPaymentButton = createActionButton("Registrar abono", "ghost-button");
        registerPaymentButton.setMaxWidth(Double.MAX_VALUE);
        registerPaymentButton.setPrefWidth(170);
        registerPaymentButton.disableProperty().bind(
                Bindings.isNull(invoiceTable.getSelectionModel().selectedItemProperty())
        );
        registerPaymentButton.setOnAction(event -> showMockInvoicePaymentWindow(
                stage,
                provider,
                invoiceTable.getSelectionModel().getSelectedItem()
        ));

        Button uploadSupportButton = createActionButton("Cargar soporte", "ghost-button");
        uploadSupportButton.setMaxWidth(Double.MAX_VALUE);
        uploadSupportButton.setPrefWidth(170);
        uploadSupportButton.disableProperty().bind(
                Bindings.isNull(invoiceTable.getSelectionModel().selectedItemProperty())
        );
        uploadSupportButton.setOnAction(event -> showMockSupportWindow(
                stage,
                provider,
                invoiceTable.getSelectionModel().getSelectedItem()
        ));

        Button viewInvoicesButton = createActionButton("Abrir vista consulta", "ghost-button");
        viewInvoicesButton.setMaxWidth(Double.MAX_VALUE);
        viewInvoicesButton.setPrefWidth(170);
        viewInvoicesButton.setOnAction(event -> showMockProviderInvoicesWindow(stage, provider));

        Button refreshButton = createActionButton("Actualizar mock", "ghost-button");
        refreshButton.setMaxWidth(Double.MAX_VALUE);
        refreshButton.setPrefWidth(170);
        refreshButton.setOnAction(event -> {
            invoiceSource.setAll(MockData.invoicesByProvider(provider.id()));
            MockData.SupplierInvoiceMockRow selectedInvoice = invoiceTable.getSelectionModel().getSelectedItem();
            if (selectedInvoice != null) {
                for (MockData.SupplierInvoiceMockRow row : invoiceSource) {
                    if (row.id().equals(selectedInvoice.id())) {
                        invoiceTable.getSelectionModel().select(row);
                        return;
                    }
                }
            }
            if (!invoiceSource.isEmpty()) {
                invoiceTable.getSelectionModel().selectFirst();
            } else {
                paymentSource.clear();
                supportSource.clear();
                updateSelectedInvoiceMockState(
                        null,
                        selectedInvoiceValue,
                        invoiceConceptValue,
                        invoiceDueDateValue,
                        invoiceBalanceValue,
                        invoiceSupportValue,
                        paymentSummaryValue,
                        supportSummaryValue,
                        invoiceProgressBar,
                        invoiceProgressCaption
                );
            }
        });

        invoiceTable.getSelectionModel().selectedItemProperty().addListener((obs, previous, invoice) -> {
            updateSelectedInvoiceMockState(
                    invoice,
                    selectedInvoiceValue,
                    invoiceConceptValue,
                    invoiceDueDateValue,
                    invoiceBalanceValue,
                    invoiceSupportValue,
                    paymentSummaryValue,
                    supportSummaryValue,
                    invoiceProgressBar,
                    invoiceProgressCaption
            );
            paymentSource.setAll(invoice == null ? List.of() : MockData.paymentsByInvoice(invoice.id()));
            supportSource.setAll(invoice == null ? List.of() : MockData.supportsByInvoice(invoice.id()));
        });
        invoiceTable.setOnMouseClicked(event -> {
            if (event.getClickCount() < 2) {
                return;
            }
            MockData.SupplierInvoiceMockRow invoice = invoiceTable.getSelectionModel().getSelectedItem();
            if (invoice != null) {
                showMockInvoicePaymentWindow(stage, provider, invoice);
            }
        });

        if (!invoiceSource.isEmpty()) {
            invoiceTable.getSelectionModel().selectFirst();
        }

        VBox root = createDialogRoot(
                "Gestion de cartera | " + provider.nombre(),
                "Ventana operativa diseñada para ver facturas, abonos y soportes sin perder contexto."
        );
        root.getStyleClass().add("invoice-workspace");
        root.setSpacing(10);
        root.setPadding(new Insets(14));

        VBox invoicesCard = createCard(
                "Facturas del proveedor",
                "Doble clic sobre una factura para abrir el registro de abono mock."
        );
        HBox.setHgrow(invoicesCard, Priority.ALWAYS);
        VBox.setVgrow(invoiceTable, Priority.ALWAYS);
        invoicesCard.getChildren().add(invoiceTable);

        VBox actionsCard = createCard(
                "Acciones de cartera",
                "Todo el contexto comercial y las acciones clave viven en este panel lateral."
        );
        actionsCard.getStyleClass().add("invoice-compact-card");
        FlowPane actionButtons = createResponsiveRow(
                newInvoiceButton,
                registerPaymentButton,
                uploadSupportButton,
                viewInvoicesButton,
                refreshButton
        );
        actionButtons.getStyleClass().add("invoice-action-grid");
        actionButtons.setPrefWrapLength(340);
        actionsCard.getChildren().addAll(
                createKeyValue("Deuda total", totalDebtValue),
                createKeyValue("Facturas", invoiceCountValue),
                new Separator(),
                createKeyValue("Factura activa", selectedInvoiceValue),
                createKeyValue("Concepto", invoiceConceptValue),
                createKeyValue("Vencimiento", invoiceDueDateValue),
                createKeyValue("Saldo", invoiceBalanceValue),
                createProgressCard("Avance de pago", invoiceProgressBar, invoiceProgressCaption),
                new Separator(),
                actionButtons
        );

        VBox rightColumn = new VBox(10, actionsCard);
        rightColumn.setMaxWidth(Double.MAX_VALUE);

        SplitPane workspace = createWorkspaceSplitPane(false, 0.63, invoicesCard, rightColumn);
        VBox.setVgrow(workspace, Priority.ALWAYS);
        root.getChildren().add(workspace);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/com/posdesktop/pos/mockfx/mock-theme.css").toExternalForm());
        stage.setScene(scene);
        applyResponsiveStageSize(stage, 0.95, 0.9, 1200, 760);
        stage.show();
    }

    private void showMockProviderWindow(Window owner) {
        showMockProviderWindow(owner, null, null);
    }

    private void showMockProviderWindow(Window owner, Runnable afterSave, Consumer<String> onCreated) {
        Stage stage = createDialogStage("Nuevo proveedor");
        if (owner != null) {
            stage.initOwner(owner);
        }

        VBox root = createDialogRoot(
                "Nuevo proveedor",
                "Mock para crear y administrar un proveedor antes de asociarle facturas."
        );
        root.getStyleClass().add("invoice-workspace");
        root.setSpacing(12);
        root.setPadding(new Insets(16));
        TextField nameField = createField("");
        nameField.setPromptText("Nombre del proveedor");
        TextField categoryField = createField("");
        categoryField.setPromptText("Categoria o linea");
        TextField contactField = createField("");
        contactField.setPromptText("Contacto principal");
        TextField phoneField = createField("");
        phoneField.setPromptText("Telefono");
        TextArea notesArea = createArea("", 2);
        notesArea.setPromptText("Notas internas del proveedor");
        Label providerPreviewValue = createMetaValueLabel("Nuevo registro mock");
        providerPreviewValue.textProperty().bind(Bindings.createStringBinding(
                () -> safeText(nameField.getText(), "Nuevo registro mock"),
                nameField.textProperty()
        ));
        Label relationValue = createMetaValueLabel("Listo para facturas");
        relationValue.textProperty().bind(Bindings.createStringBinding(
                () -> safeText(categoryField.getText(), "Listo para facturas"),
                categoryField.textProperty()
        ));
        Button save = createActionButton("Guardar mock", "primary-button");
        save.setMaxWidth(Double.MAX_VALUE);
        save.setOnAction(event -> {
            try {
                MockData.ProviderMockRow createdProvider = MockData.createProvider(
                        nameField.getText(),
                        categoryField.getText(),
                        contactField.getText(),
                        phoneField.getText()
                );
                stage.close();
                if (afterSave != null) {
                    afterSave.run();
                }
                if (onCreated != null) {
                    onCreated.accept(createdProvider.id());
                }
                showInfo(
                        "Proveedor registrado",
                        "Se creó el proveedor " + createdProvider.nombre() + " y ya quedó disponible para asociarle facturas."
                );
            } catch (IllegalArgumentException exception) {
                showError("Nuevo proveedor", exception.getMessage());
            }
        });

        FlowPane fields = createResponsiveRow(
                createFieldGroup("Proveedor", nameField, 280),
                createFieldGroup("Categoria", categoryField, 220),
                createFieldGroup("Contacto", contactField, 220),
                createFieldGroup("Telefono", phoneField, 220)
        );
        VBox formCard = createCard(
                "Datos del proveedor",
                "Completa la informacion base del proveedor."
        );
        HBox.setHgrow(formCard, Priority.ALWAYS);
        formCard.getChildren().addAll(fields, createFieldGroup("Notas", notesArea, 520));

        VBox summaryCard = createCard(
                "Resumen del flujo",
                "Desde aqui validas lo minimo necesario antes de guardarlo."
        );
        bindRegionWidthToScene(summaryCard, 0.28, 260, 320);
        summaryCard.setMaxWidth(Region.USE_PREF_SIZE);
        summaryCard.getChildren().addAll(
                createKeyValue("Proveedor", providerPreviewValue),
                createKeyValue("Relacion", relationValue),
                createProgressCard("Ruta del registro", 0.62, "Al guardar, el proveedor entra de inmediato a la bandeja principal."),
                save
        );

        HBox workspace = createAdaptivePanelRow(formCard, summaryCard);
        VBox.setVgrow(workspace, Priority.ALWAYS);
        root.getChildren().add(workspace);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/com/posdesktop/pos/mockfx/mock-theme.css").toExternalForm());
        stage.setScene(scene);
        applyResponsiveStageSize(stage, 0.78, 0.72, 860, 520);
        stage.show();
    }

    private void showMockInvoiceWindow(Window owner, MockData.ProviderMockRow provider) {
        showMockInvoiceWindow(owner, provider, null, null);
    }

    private void showMockInvoiceWindow(
            Window owner,
            MockData.ProviderMockRow provider,
            Runnable afterSave,
            Consumer<String> onCreated
    ) {
        if (provider == null) {
            showError("Facturas", "Selecciona un proveedor para crear una factura mock.");
            return;
        }

        Stage stage = createDialogStage("Nueva factura");
        if (owner != null) {
            stage.initOwner(owner);
        }

        VBox root = createDialogRoot(
                "Nueva factura",
                "Mock para cargar factura PDF/JPG, valor adeudado y control de vencimiento."
        );
        root.getStyleClass().add("invoice-workspace");
        root.setSpacing(12);
        root.setPadding(new Insets(16));
        TextField providerField = createField(provider.nombre());
        providerField.setDisable(true);
        TextField numberField = createField("");
        numberField.setPromptText("Numero de factura");
        TextField amountField = createField("0");
        amountField.setPromptText("Valor total adeudado");
        configureSelectAllOnFocus(amountField);
        DatePicker dueDatePicker = new DatePicker(LocalDate.now().plusDays(15));
        ComboBox<String> supportFormat = new ComboBox<>(FXCollections.observableArrayList(MockData.supportFormats()));
        supportFormat.getSelectionModel().select("PDF");
        AtomicReference<String> supportFileName = new AtomicReference<>("");
        Label supportFormatValue = createMetaValueLabel(supportFormat.getSelectionModel().getSelectedItem());
        supportFormatValue.textProperty().bind(Bindings.createStringBinding(
                () -> safeText(supportFormat.getValue(), "-"),
                supportFormat.valueProperty()
        ));
        Label supportFileValue = createMetaValueLabel("Pendiente por adjuntar");
        Button addSupportButton = createActionButton("+", "ghost-button");
        addSupportButton.getStyleClass().add("mini-action-button");
        addSupportButton.setOnAction(event -> {
            String suggestedFile = buildSupportFileNameSuggestion(
                    "factura_" + safeText(numberField.getText(), provider.nombre()),
                    safeText(supportFormat.getValue(), "PDF")
            );
            promptSupportFileName(
                    stage,
                    "Soporte inicial",
                    "Nombre del archivo mock",
                    suggestedFile
            ).ifPresent(fileName -> {
                supportFileName.set(fileName);
                supportFileValue.setText(fileName);
            });
        });
        HBox supportSelector = new HBox(8, supportFormat, addSupportButton);
        supportSelector.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(supportFormat, Priority.ALWAYS);
        TextArea descriptionArea = createArea("", 2);
        descriptionArea.setPromptText("Concepto o descripcion comercial");
        Label amountValue = createMetaValueLabel("$ 0");
        amountValue.textProperty().bind(Bindings.createStringBinding(
                () -> formatCurrency(parseCurrencyOrZero(amountField.getText())),
                amountField.textProperty()
        ));
        Label dueDateValue = createMetaValueLabel("-");
        dueDateValue.textProperty().bind(Bindings.createStringBinding(
                () -> dueDatePicker.getValue() == null ? "-" : SHORT_DATE_FORMATTER.format(dueDatePicker.getValue()),
                dueDatePicker.valueProperty()
        ));
        Button save = createActionButton("Guardar mock", "primary-button");
        save.setMaxWidth(Double.MAX_VALUE);
        save.setOnAction(event -> {
            try {
                BigDecimal total = parseRequiredPositive(amountField.getText(), "El valor de la factura debe ser mayor a cero.");
                MockData.SupplierInvoiceMockRow createdInvoice = MockData.createSupplierInvoice(
                        provider.id(),
                        numberField.getText(),
                        descriptionArea.getText(),
                        dueDatePicker.getValue(),
                        total,
                        supportFormat.getValue(),
                        supportFileName.get()
                );
                stage.close();
                if (afterSave != null) {
                    afterSave.run();
                }
                if (onCreated != null) {
                    onCreated.accept(createdInvoice.id());
                }
                showInfo(
                        "Factura registrada",
                        "Se creó la factura " + createdInvoice.numero() + " para " + provider.nombre()
                                + " con saldo inicial de " + createdInvoice.saldo() + "."
                );
            } catch (IllegalArgumentException exception) {
                showError("Nueva factura", exception.getMessage());
            }
        });

        FlowPane fields = createResponsiveRow(
                createFieldGroup("Proveedor", providerField, 280),
                createFieldGroup("Factura", numberField, 220),
                createFieldGroup("Valor", amountField, 220),
                createFieldGroup("Vencimiento", dueDatePicker, 220),
                createFieldGroup("Soporte inicial", supportSelector, 280)
        );
        VBox formCard = createCard(
                "Datos de la factura",
                "Captura comercial principal de la factura del proveedor."
        );
        HBox.setHgrow(formCard, Priority.ALWAYS);
        formCard.getChildren().addAll(fields, createFieldGroup("Concepto", descriptionArea, 520));

        VBox summaryCard = createCard(
                "Contexto del registro",
                "Resumen compacto del flujo documental y financiero."
        );
        bindRegionWidthToScene(summaryCard, 0.28, 270, 330);
        summaryCard.setMaxWidth(Region.USE_PREF_SIZE);
        summaryCard.getChildren().addAll(
                createKeyValue("Proveedor", createMetaValueLabel(provider.nombre())),
                createKeyValue("Soporte inicial", supportFormatValue),
                createKeyValue("Archivo mock", supportFileValue),
                createKeyValue("Valor capturado", amountValue),
                createKeyValue("Vence", dueDateValue),
                createKeyValue("Formatos", createMetaValueLabel("PDF / JPG / PNG")),
                createKeyValue("Estado esperado", createMetaValueLabel("Pendiente o abonada")),
                createProgressCard("Carga activa", 0.72, "Al guardar se crea la factura, su saldo y el soporte inicial."),
                save
        );

        HBox workspace = createAdaptivePanelRow(formCard, summaryCard);
        VBox.setVgrow(workspace, Priority.ALWAYS);
        root.getChildren().add(workspace);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/com/posdesktop/pos/mockfx/mock-theme.css").toExternalForm());
        stage.setScene(scene);
        applyResponsiveStageSize(stage, 0.84, 0.74, 960, 560);
        stage.show();
    }

    private void showMockInvoicePaymentWindow(
            Window owner,
            MockData.ProviderMockRow provider,
            MockData.SupplierInvoiceMockRow invoice
    ) {
        showMockInvoicePaymentWindow(owner, provider, invoice, null);
    }

    private void showMockInvoicePaymentWindow(
            Window owner,
            MockData.ProviderMockRow provider,
            MockData.SupplierInvoiceMockRow invoice,
            Runnable afterSave
    ) {
        if (provider == null || invoice == null) {
            showError("Facturas", "Selecciona una factura para registrar un abono mock.");
            return;
        }

        Stage stage = createDialogStage("Registrar abono");
        if (owner != null) {
            stage.initOwner(owner);
        }

        VBox root = createDialogRoot(
                "Registrar abono",
                "Mock para ingresar un valor manual y adjuntar imagen o PDF del comprobante."
        );
        root.getStyleClass().add("invoice-workspace");
        root.setSpacing(12);
        root.setPadding(new Insets(16));
        TextField providerField = createField(provider.nombre());
        providerField.setDisable(true);
        TextField invoiceField = createField(invoice.numero());
        invoiceField.setDisable(true);
        TextField currentBalanceField = createField(invoice.saldo());
        currentBalanceField.setDisable(true);
        TextField amountField = createField("0");
        amountField.setPromptText("Valor del abono");
        configureSelectAllOnFocus(amountField);
        TextField projectedBalanceField = createField(invoice.saldo());
        projectedBalanceField.setDisable(true);
        ComboBox<String> paymentMethod = new ComboBox<>(FXCollections.observableArrayList(MockData.paymentMethods()));
        paymentMethod.getSelectionModel().select("Transferencia");
        ComboBox<String> supportFormat = new ComboBox<>(FXCollections.observableArrayList(MockData.supportFormats()));
        supportFormat.getSelectionModel().select("JPG");
        AtomicReference<String> supportFileName = new AtomicReference<>("");
        Label supportFormatValue = createMetaValueLabel(supportFormat.getSelectionModel().getSelectedItem());
        supportFormatValue.textProperty().bind(Bindings.createStringBinding(
                () -> safeText(supportFormat.getValue(), "-"),
                supportFormat.valueProperty()
        ));
        Label supportFileValue = createMetaValueLabel("Pendiente por adjuntar");
        Label paymentMethodValue = createMetaValueLabel(paymentMethod.getSelectionModel().getSelectedItem());
        paymentMethodValue.textProperty().bind(Bindings.createStringBinding(
                () -> safeText(paymentMethod.getValue(), "-"),
                paymentMethod.valueProperty()
        ));
        Button addSupportButton = createActionButton("+", "ghost-button");
        addSupportButton.getStyleClass().add("mini-action-button");
        addSupportButton.setOnAction(event -> {
            String suggestedFile = buildSupportFileNameSuggestion(
                    "abono_" + invoice.numero(),
                    safeText(supportFormat.getValue(), "JPG")
            );
            promptSupportFileName(
                    stage,
                    "Comprobante del abono",
                    "Nombre del archivo mock",
                    suggestedFile
            ).ifPresent(fileName -> {
                supportFileName.set(fileName);
                supportFileValue.setText(fileName);
            });
        });
        HBox supportSelector = new HBox(8, supportFormat, addSupportButton);
        supportSelector.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(supportFormat, Priority.ALWAYS);
        TextArea notesArea = createArea("", 2);
        notesArea.setPromptText("Observacion del abono");

        Runnable updateProjectedBalance = () -> {
            BigDecimal currentBalance = parseCurrencyOrZero(invoice.saldo());
            BigDecimal payment = parseCurrencyOrZero(amountField.getText());
            projectedBalanceField.setText(formatCurrency(currentBalance.subtract(payment).max(BigDecimal.ZERO)));
        };
        amountField.textProperty().addListener((obs, oldValue, newValue) -> updateProjectedBalance.run());
        updateProjectedBalance.run();

        Button save = createActionButton("Guardar mock", "primary-button");
        save.setMaxWidth(Double.MAX_VALUE);
        save.setOnAction(event -> {
            try {
                BigDecimal payment = parseRequiredPositive(amountField.getText(), "El valor del abono debe ser mayor a cero.");
                MockData.SupplierPaymentMockRow createdPayment = MockData.createSupplierPayment(
                        invoice.id(),
                        payment,
                        paymentMethod.getValue(),
                        supportFormat.getValue(),
                        supportFileName.get()
                );
                MockData.SupplierInvoiceMockRow updatedInvoice = MockData.findInvoice(invoice.id()).orElse(invoice);
                stage.close();
                if (afterSave != null) {
                    afterSave.run();
                }
                showInfo(
                        "Abono registrado",
                        "Se registró un abono de " + createdPayment.valor() + " para la factura "
                                + updatedInvoice.numero() + ". Restante: " + updatedInvoice.saldo() + "."
                );
            } catch (IllegalArgumentException exception) {
                showError("Registrar abono", exception.getMessage());
            }
        });

        FlowPane fields = createResponsiveRow(
                createFieldGroup("Proveedor", providerField, 260),
                createFieldGroup("Factura", invoiceField, 220),
                createFieldGroup("Saldo actual", currentBalanceField, 220),
                createFieldGroup("Valor abono", amountField, 220),
                createFieldGroup("Restante estimado", projectedBalanceField, 220),
                createFieldGroup("Medio", paymentMethod, 220),
                createFieldGroup("Soporte", supportSelector, 280)
        );
        VBox formCard = createCard(
                "Registro de abono",
                "Captura el abono y revisa de inmediato el saldo proyectado."
        );
        HBox.setHgrow(formCard, Priority.ALWAYS);
        formCard.getChildren().addAll(fields, createFieldGroup("Observacion", notesArea, 520));

        VBox summaryCard = createCard(
                "Impacto del abono",
                "La ventana deja visible el antes y el despues del pago parcial."
        );
        bindRegionWidthToScene(summaryCard, 0.28, 270, 330);
        summaryCard.setMaxWidth(Region.USE_PREF_SIZE);
        Label projectedBalanceLabel = createMetaValueLabel(projectedBalanceField.getText());
        projectedBalanceLabel.textProperty().bind(projectedBalanceField.textProperty());
        summaryCard.getChildren().addAll(
                createKeyValue("Factura", createMetaValueLabel(invoice.numero())),
                createKeyValue("Saldo actual", createMetaValueLabel(invoice.saldo())),
                createKeyValue("Medio", paymentMethodValue),
                createKeyValue("Soporte del abono", supportFormatValue),
                createKeyValue("Archivo mock", supportFileValue),
                createKeyValue("Restante", projectedBalanceLabel),
                createProgressCard("Escenario realista", 0.78, "Al guardar se crea el abono, su soporte y el nuevo saldo de la factura."),
                save
        );

        HBox workspace = createAdaptivePanelRow(formCard, summaryCard);
        VBox.setVgrow(workspace, Priority.ALWAYS);
        root.getChildren().add(workspace);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/com/posdesktop/pos/mockfx/mock-theme.css").toExternalForm());
        stage.setScene(scene);
        applyResponsiveStageSize(stage, 0.86, 0.76, 980, 580);
        stage.show();
    }

    private void showMockSupportWindow(
            Window owner,
            MockData.ProviderMockRow provider,
            MockData.SupplierInvoiceMockRow invoice
    ) {
        if (provider == null || invoice == null) {
            showError("Facturas", "Selecciona una factura para cargar un soporte mock.");
            return;
        }

        Stage stage = createDialogStage("Cargar soporte");
        if (owner != null) {
            stage.initOwner(owner);
        }

        VBox root = createDialogRoot(
                "Cargar soporte",
                "Mock para asociar facturas proveedor y comprobantes de abono en PDF o JPG."
        );
        root.getStyleClass().add("invoice-workspace");
        root.setSpacing(12);
        root.setPadding(new Insets(16));
        TextField providerField = createField(provider.nombre());
        providerField.setDisable(true);
        TextField invoiceField = createField(invoice.numero());
        invoiceField.setDisable(true);
        ComboBox<String> supportType = new ComboBox<>(FXCollections.observableArrayList(MockData.supportTypes()));
        supportType.getSelectionModel().selectFirst();
        ComboBox<String> supportFormat = new ComboBox<>(FXCollections.observableArrayList(MockData.supportFormats()));
        supportFormat.getSelectionModel().select("PDF");
        TextField fileNameField = createField("");
        fileNameField.setPromptText("Ejemplo: factura_9031.pdf");
        TextArea notesArea = createArea("", 2);
        notesArea.setPromptText("Notas del soporte cargado");
        Button save = createActionButton("Guardar mock", "primary-button");
        save.setMaxWidth(Double.MAX_VALUE);
        save.setOnAction(event -> {
            try {
                MockData.SupplierSupportMockRow support = MockData.createSupplierSupport(
                        invoice.id(),
                        supportType.getValue(),
                        fileNameField.getText(),
                        supportFormat.getValue()
                );
                stage.close();
                showInfo(
                        "Soporte registrado",
                        "Se agregó el soporte " + support.archivo() + " a la factura " + invoice.numero() + "."
                );
            } catch (IllegalArgumentException exception) {
                showError("Cargar soporte", exception.getMessage());
            }
        });

        FlowPane fields = createResponsiveRow(
                createFieldGroup("Proveedor", providerField, 260),
                createFieldGroup("Factura", invoiceField, 220),
                createFieldGroup("Tipo soporte", supportType, 220),
                createFieldGroup("Formato", supportFormat, 220),
                createFieldGroup("Archivo", fileNameField, 280)
        );
        VBox formCard = createCard(
                "Datos del soporte",
                "Asocia el archivo al documento correcto sin perder contexto."
        );
        HBox.setHgrow(formCard, Priority.ALWAYS);
        formCard.getChildren().addAll(fields, createFieldGroup("Notas", notesArea, 520));

        VBox summaryCard = createCard(
                "Control documental",
                "Resumen visual del tipo de evidencia que estas cargando."
        );
        bindRegionWidthToScene(summaryCard, 0.28, 270, 330);
        summaryCard.setMaxWidth(Region.USE_PREF_SIZE);
        summaryCard.getChildren().addAll(
                createKeyValue("Proveedor", createMetaValueLabel(provider.nombre())),
                createKeyValue("Factura", createMetaValueLabel(invoice.numero())),
                createKeyValue("Flujo", createMetaValueLabel("Factura o comprobante")),
                createProgressCard("Carga visual", 0.72, "La misma factura puede tener soportes de factura y de abono."),
                save
        );

        HBox workspace = createAdaptivePanelRow(formCard, summaryCard);
        VBox.setVgrow(workspace, Priority.ALWAYS);
        root.getChildren().add(workspace);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/com/posdesktop/pos/mockfx/mock-theme.css").toExternalForm());
        stage.setScene(scene);
        applyResponsiveStageSize(stage, 0.84, 0.74, 960, 560);
        stage.show();
    }

    private Optional<String> promptSupportFileName(
            Window owner,
            String title,
            String label,
            String suggestedFileName
    ) {
        TextInputDialog dialog = new TextInputDialog(suggestedFileName);
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText(label);
        if (owner != null) {
            dialog.initOwner(owner);
        }
        return dialog.showAndWait()
                .map(String::trim)
                .filter(value -> !value.isBlank());
    }

    private String buildSupportFileNameSuggestion(String baseName, String format) {
        String extension = safeText(format, "PDF").toLowerCase(Locale.ROOT);
        String normalizedBase = safeText(baseName, "archivo")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+", "")
                .replaceAll("_+$", "");
        if (normalizedBase.isBlank()) {
            normalizedBase = "archivo";
        }
        return normalizedBase + "." + extension;
    }

    private void showNewLayawayWindow(Window owner, Runnable refreshAction, Consumer<String> onCreated) {
        Stage stage = createDialogStage("Nuevo separado");
        if (owner != null) {
            stage.initOwner(owner);
        }

        VBox root = createDialogRoot("Nuevo separado", "Apertura real con numeracion, saldo y abono inicial automaticos.");

        TextField clienteField = createField("");
        clienteField.setPromptText("Nombre del cliente");
        TextField telefonoField = createField("");
        telefonoField.setPromptText("Telefono");
        TextArea articulosArea = createArea("", 3);
        articulosArea.setPromptText("Describe uno o varios articulos del separado");
        TextField totalField = createField("0");
        TextField abonoInicialField = createField("20000");
        TextField remaining = createField("0");
        remaining.setDisable(true);
        TextArea observacionArea = createArea("", 2);
        observacionArea.setPromptText("Observacion opcional");
        configureSelectAllOnFocus(totalField);
        configureSelectAllOnFocus(abonoInicialField);

        Runnable updateProjectedRemaining = () -> {
            BigDecimal total = parseCurrencyOrZero(totalField.getText());
            BigDecimal initialPayment = parseCurrencyOrZero(abonoInicialField.getText());
            BigDecimal projectedBalance = total.subtract(initialPayment).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            remaining.setText(formatCurrency(projectedBalance));
        };
        totalField.textProperty().addListener((obs, oldValue, newValue) -> updateProjectedRemaining.run());
        abonoInicialField.textProperty().addListener((obs, oldValue, newValue) -> updateProjectedRemaining.run());
        updateProjectedRemaining.run();

        FlowPane fields = createResponsiveRow(
                createFieldGroup("Cliente", clienteField, 280),
                createFieldGroup("Telefono", telefonoField, 220),
                createFieldGroup("Articulos", articulosArea, 380),
                createFieldGroup("Valor total", totalField, 220),
                createFieldGroup("Abono inicial", abonoInicialField, 220),
                createFieldGroup("Restante estimado", remaining, 220)
        );

        VBox progress = createProgressCard(
                "Regla comercial",
                0.2,
                "Abono minimo inicial: $ 20.000 COP. El numero y la fecha del separado se asignan automaticamente."
        );
        Button save = createActionButton("Guardar separado", "primary-button");
        save.setMaxWidth(Double.MAX_VALUE);
        save.setOnAction(event -> {
            try {
                BigDecimal total = parseRequiredPositive(totalField.getText(), "El valor total debe ser mayor a cero.");
                BigDecimal initialPayment = parseRequiredPositive(
                        abonoInicialField.getText(),
                        "El abono inicial debe ser mayor a cero."
                );
                if (initialPayment.compareTo(new BigDecimal("20000.00")) < 0) {
                    throw new IllegalArgumentException("El abono inicial minimo es de 20.000 COP.");
                }
                if (initialPayment.compareTo(total) > 0) {
                    throw new IllegalArgumentException("El abono inicial no puede superar el valor total del separado.");
                }

                PosApiClient.RegistrarSeparadoRequest request = new PosApiClient.RegistrarSeparadoRequest(
                        clienteField.getText(),
                        telefonoField.getText(),
                        articulosArea.getText(),
                        total,
                        initialPayment,
                        observacionArea.getText()
                );
                save.setDisable(true);
                runAsync(
                        () -> posApiClient.registrarSeparado(request),
                        response -> {
                            stage.close();
                            if (onCreated != null) {
                                onCreated.accept(response.id());
                            } else if (refreshAction != null) {
                                refreshAction.run();
                            }
                            showInfo(
                                    "Separado registrado",
                                    "Se registró el separado " + response.numeroSeparado()
                                            + " con saldo pendiente " + formatCurrency(response.saldoPendiente()) + "."
                            );
                        },
                        exception -> {
                            save.setDisable(false);
                            showError("Nuevo separado", exception.getMessage());
                        }
                );
            } catch (IllegalArgumentException exception) {
                showError("Nuevo separado", exception.getMessage());
            }
        });

        root.getChildren().addAll(fields, createFieldGroup("Observacion", observacionArea, 520), progress, save);

        Scene scene = new Scene(root, 860, 620);
        scene.getStylesheets().add(getClass().getResource("/com/posdesktop/pos/mockfx/mock-theme.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    private void openLayawayPaymentWindow(
            Window owner,
            String layawayId,
            AtomicReference<PosApiClient.SeparadoDetalleResponse> selectedLayawayDetail,
            Runnable afterUpdate
    ) {
        PosApiClient.SeparadoDetalleResponse cachedDetail = selectedLayawayDetail.get();
        if (cachedDetail != null && layawayId.equals(cachedDetail.id())) {
            showLayawayPaymentWindow(owner, cachedDetail, selectedLayawayDetail, afterUpdate);
            return;
        }

        runAsync(
                () -> posApiClient.consultarSeparado(layawayId),
                detail -> {
                    selectedLayawayDetail.set(detail);
                    showLayawayPaymentWindow(owner, detail, selectedLayawayDetail, afterUpdate);
                },
                exception -> showError("Abonos", exception.getMessage())
        );
    }

    private void showLayawayPaymentWindow(
            Window owner,
            PosApiClient.SeparadoDetalleResponse initialDetail,
            AtomicReference<PosApiClient.SeparadoDetalleResponse> selectedLayawayDetail,
            Runnable afterUpdate
    ) {
        if (initialDetail == null) {
            showError("Abonos", "Selecciona un separado para registrar un abono.");
            return;
        }

        Stage stage = createDialogStage("Realizar abono");
        if (owner != null) {
            stage.initOwner(owner);
        }

        VBox root = createDialogRoot(
                "Realizar abono",
                "Ventana operativa para abonar sobre el separado seleccionado."
        );

        TextField separatedField = createField(initialDetail.numeroSeparado());
        separatedField.setDisable(true);
        TextField clientField = createField(initialDetail.cliente());
        clientField.setDisable(true);
        TextArea itemsArea = createArea(initialDetail.descripcionArticulos(), 3);
        itemsArea.setDisable(true);
        TextField totalField = createField(formatCurrency(initialDetail.valorTotal()));
        totalField.setDisable(true);
        TextField currentBalanceField = createField(formatCurrency(initialDetail.saldoPendiente()));
        currentBalanceField.setDisable(true);
        TextField amountField = createField("");
        amountField.setPromptText("Valor a abonar");
        configureSelectAllOnFocus(amountField);
        TextField projectedBalanceField = createField(formatCurrency(initialDetail.saldoPendiente()));
        projectedBalanceField.setDisable(true);
        TextArea observationArea = createArea("", 2);
        observationArea.setPromptText("Observacion opcional del abono");
        Button save = createActionButton("Guardar abono", "primary-button");
        save.setMaxWidth(Double.MAX_VALUE);

        AtomicReference<PosApiClient.SeparadoDetalleResponse> currentDetail = new AtomicReference<>(initialDetail);
        Runnable updateProjectedBalance = () -> {
            PosApiClient.SeparadoDetalleResponse detail = currentDetail.get();
            if (detail == null) {
                projectedBalanceField.setText(formatCurrency(BigDecimal.ZERO));
                return;
            }
            BigDecimal payment = parseCurrencyOrZero(amountField.getText());
            BigDecimal projectedBalance = detail.saldoPendiente().subtract(payment).max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
            projectedBalanceField.setText(formatCurrency(projectedBalance));
        };
        amountField.textProperty().addListener((obs, oldValue, newValue) -> updateProjectedBalance.run());

        Consumer<PosApiClient.SeparadoDetalleResponse> renderDetail = detail -> {
            currentDetail.set(detail);
            separatedField.setText(detail.numeroSeparado());
            clientField.setText(detail.cliente());
            itemsArea.setText(detail.descripcionArticulos());
            totalField.setText(formatCurrency(detail.valorTotal()));
            currentBalanceField.setText(formatCurrency(detail.saldoPendiente()));
            boolean canRegisterPayment = detail.saldoPendiente() != null
                    && detail.saldoPendiente().signum() > 0
                    && "ACTIVO".equalsIgnoreCase(detail.estado());
            amountField.setDisable(!canRegisterPayment);
            observationArea.setDisable(!canRegisterPayment);
            save.setDisable(!canRegisterPayment);
            if (!canRegisterPayment) {
                amountField.clear();
                projectedBalanceField.setText(formatCurrency(detail.saldoPendiente()));
            }
            updateProjectedBalance.run();
        };
        renderDetail.accept(initialDetail);

        FlowPane fields = createResponsiveRow(
                createFieldGroup("Separado", separatedField, 220),
                createFieldGroup("Cliente", clientField, 280),
                createFieldGroup("Articulos", itemsArea, 380),
                createFieldGroup("Valor total", totalField, 220),
                createFieldGroup("Saldo actual", currentBalanceField, 220),
                createFieldGroup("Valor a abonar", amountField, 220),
                createFieldGroup("Restante estimado", projectedBalanceField, 220)
        );

        VBox progress = createProgressCard(
                "Estado del separado",
                calculateLayawayProgress(initialDetail.valorTotal(), initialDetail.totalAbonado()),
                formatCurrency(initialDetail.totalAbonado()) + " abonados de "
                        + formatCurrency(initialDetail.valorTotal())
        );

        save.setOnAction(event -> {
            PosApiClient.SeparadoDetalleResponse detail = currentDetail.get();
            if (detail == null) {
                showError("Abonos", "No fue posible resolver el separado seleccionado.");
                return;
            }
            try {
                BigDecimal payment = parseRequiredPositive(amountField.getText(), "El valor del abono debe ser mayor a cero.");
                if (payment.compareTo(detail.saldoPendiente()) > 0) {
                    throw new IllegalArgumentException("El abono no puede superar el saldo pendiente del separado.");
                }
                save.setDisable(true);
                runAsync(
                        () -> posApiClient.registrarAbonoSeparado(
                                detail.id(),
                                new PosApiClient.RegistrarAbonoSeparadoRequest(payment, observationArea.getText())
                        ),
                        updatedDetail -> {
                            selectedLayawayDetail.set(updatedDetail);
                            if (afterUpdate != null) {
                                afterUpdate.run();
                            }
                            stage.close();
                            showInfo(
                                    "Abono registrado",
                                    "Se registró el abono del separado " + updatedDetail.numeroSeparado()
                                            + ". Restante: " + formatCurrency(updatedDetail.saldoPendiente()) + "."
                            );
                        },
                        exception -> {
                            renderDetail.accept(detail);
                            showError("Abonos", exception.getMessage());
                        }
                );
            } catch (IllegalArgumentException exception) {
                showError("Abonos", exception.getMessage());
            }
        });

        root.getChildren().addAll(fields, createFieldGroup("Observacion", observationArea, 520), progress, save);

        Scene scene = new Scene(root, 860, 620);
        scene.getStylesheets().add(getClass().getResource("/com/posdesktop/pos/mockfx/mock-theme.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    private void showPaymentsWindow(
            Window owner,
            PosApiClient.SeparadoDetalleResponse initialDetail
    ) {
        if (initialDetail == null) {
            showError("Separados", "Selecciona un separado para visualizar sus abonos.");
            return;
        }

        Stage stage = createDialogStage("Abonos del separado");
        if (owner != null) {
            stage.initOwner(owner);
        }

        VBox root = createDialogRoot("Abonos", "Historial real de pagos parciales asociados al separado.");
        TableView<PosApiClient.AbonoSeparadoResponse> table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(340);
        table.getColumns().addAll(
                tableColumn("Fecha y hora", payment -> formatDateTime(payment.fechaAbono())),
                tableColumn("Valor", payment -> formatCurrency(payment.montoAbono())),
                tableColumn("Tipo", payment -> payment.abonoInicial() ? "Inicial" : "Abono"),
                tableColumn("Venta", payment -> payment.numeroVenta() == null ? "-" : payment.numeroVenta())
        );

        Label separatedValue = createMetaValueLabel(initialDetail.numeroSeparado());
        Label clientValue = createMetaValueLabel(initialDetail.cliente());
        Label totalValue = createMetaValueLabel(formatCurrency(initialDetail.valorTotal()));
        Label pendingValue = createMetaValueLabel(formatCurrency(initialDetail.saldoPendiente()));
        Label statusValue = createMetaValueLabel(formatLayawayStatus(initialDetail.estado()));
        table.setItems(FXCollections.observableArrayList(initialDetail.abonos()));

        root.getChildren().addAll(
                createKeyValue("Separado", separatedValue),
                createKeyValue("Cliente", clientValue),
                createKeyValue("Valor total", totalValue),
                createKeyValue("Saldo actual", pendingValue),
                createKeyValue("Estado", statusValue),
                table
        );

        Scene scene = new Scene(root, 820, 520);
        scene.getStylesheets().add(getClass().getResource("/com/posdesktop/pos/mockfx/mock-theme.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    private SplitPane createWorkspaceSplitPane(boolean vertical, double dividerPosition, Node... children) {
        SplitPane pane = new SplitPane(children);
        pane.getStyleClass().add("workspace-split");
        pane.setOrientation(vertical ? Orientation.VERTICAL : Orientation.HORIZONTAL);
        pane.setDividerPositions(dividerPosition);
        pane.setMaxWidth(Double.MAX_VALUE);
        pane.setMaxHeight(Double.MAX_VALUE);
        return pane;
    }

    private void applyResponsiveStageSize(
            Stage stage,
            double widthRatio,
            double heightRatio,
            double minWidth,
            double minHeight
    ) {
        Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
        double width = clamp(visualBounds.getWidth() * widthRatio, minWidth, visualBounds.getWidth() * 0.96);
        double height = clamp(visualBounds.getHeight() * heightRatio, minHeight, visualBounds.getHeight() * 0.94);
        stage.setMinWidth(minWidth);
        stage.setMinHeight(minHeight);
        stage.setWidth(width);
        stage.setHeight(height);
        stage.setMaxWidth(visualBounds.getWidth());
        stage.setMaxHeight(visualBounds.getHeight());
    }

    private Stage createDialogStage(String title) {
        Stage stage = new Stage();
        stage.initModality(Modality.NONE);
        stage.setTitle(title);
        stage.setMinWidth(460);
        stage.setMinHeight(360);
        return stage;
    }

    private VBox createDialogRoot(String title, String subtitle) {
        VBox root = new VBox(18);
        root.getStyleClass().addAll("dialog-root", "screen-container");
        root.setPadding(new Insets(24));
        root.getChildren().add(createScreenContainer(title, subtitle).getChildren().get(0));
        return root;
    }

    private VBox createCard(String title, String subtitle) {
        VBox card = new VBox(18);
        card.getStyleClass().add("surface-card");
        if (title != null && !title.isBlank()) {
            Label heading = new Label(title);
            heading.getStyleClass().add("card-title");
            VBox header = new VBox(4, heading);
            if (subtitle != null && !subtitle.isBlank()) {
                Label copy = new Label(subtitle);
                copy.getStyleClass().add("card-subtitle");
                header.getChildren().add(copy);
            }
            card.getChildren().add(header);
        }
        return card;
    }

    private VBox createMetricCard(String label, String value, String caption) {
        VBox card = new VBox(8);
        card.getStyleClass().add("metric-card");
        bindRegionWidthToScene(card, 0.17, 135, 190);
        Label overline = new Label(label);
        overline.getStyleClass().add("metric-label");
        Label number = createMetricValueLabel(value);
        Label helper = createMetricCaptionLabel(caption);
        card.getChildren().addAll(overline, number, helper);
        return card;
    }

    private VBox createMetricCard(String label, Label valueLabel, Label captionLabel) {
        VBox card = new VBox(8);
        card.getStyleClass().add("metric-card");
        bindRegionWidthToScene(card, 0.17, 135, 190);
        Label overline = new Label(label);
        overline.getStyleClass().add("metric-label");
        card.getChildren().addAll(overline, valueLabel, captionLabel);
        return card;
    }

    private Label createMetricValueLabel(String value) {
        Label label = new Label(value);
        label.getStyleClass().add("metric-value");
        return label;
    }

    private Label createMetricCaptionLabel(String value) {
        Label label = new Label(value);
        label.getStyleClass().add("metric-caption");
        return label;
    }

    private VBox createProgressCard(String title, double progress, String caption) {
        VBox box = new VBox(10);
        box.getStyleClass().add("progress-card");
        Label label = new Label(title);
        label.getStyleClass().add("progress-title");
        ProgressBar progressBar = new ProgressBar(progress);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.getStyleClass().add("accent-progress");
        Label helper = new Label(caption);
        helper.getStyleClass().add("progress-caption");
        box.getChildren().addAll(label, progressBar, helper);
        return box;
    }

    private VBox createProgressCard(String title, ProgressBar progressBar, Label captionLabel) {
        VBox box = new VBox(10);
        box.getStyleClass().add("progress-card");
        Label label = new Label(title);
        label.getStyleClass().add("progress-title");
        progressBar.setMaxWidth(Double.MAX_VALUE);
        if (!progressBar.getStyleClass().contains("accent-progress")) {
            progressBar.getStyleClass().add("accent-progress");
        }
        if (!captionLabel.getStyleClass().contains("progress-caption")) {
            captionLabel.getStyleClass().add("progress-caption");
        }
        box.getChildren().addAll(label, progressBar, captionLabel);
        return box;
    }

    private HBox createKeyValue(String key, String value) {
        Label left = new Label(key);
        left.getStyleClass().add("meta-key");
        Label right = new Label(value);
        right.getStyleClass().add("meta-value");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return new HBox(10, left, spacer, right);
    }

    private HBox createKeyValue(String key, Label valueLabel) {
        Label left = new Label(key);
        left.getStyleClass().add("meta-key");
        if (!valueLabel.getStyleClass().contains("meta-value")) {
            valueLabel.getStyleClass().add("meta-value");
        }
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return new HBox(10, left, spacer, valueLabel);
    }

    private VBox createCompactInfoBlock(String key, Label valueLabel) {
        Label title = new Label(key);
        title.getStyleClass().add("meta-key");
        if (!valueLabel.getStyleClass().contains("meta-value")) {
            valueLabel.getStyleClass().add("meta-value");
        }
        VBox box = new VBox(4, title, valueLabel);
        box.getStyleClass().add("invoice-info-block");
        return box;
    }

    private Label createMetaValueLabel(String value) {
        Label label = new Label(value);
        label.getStyleClass().add("meta-value");
        return label;
    }

    private Label createFormLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return label;
    }

    private TextField createField(String value) {
        TextField field = new TextField(value);
        field.getStyleClass().add("soft-field");
        return field;
    }

    private TextArea createArea(String value, int preferredRows) {
        TextArea area = new TextArea(value);
        area.setWrapText(true);
        area.setPrefRowCount(preferredRows);
        area.getStyleClass().add("soft-area");
        return area;
    }

    private void configureClosingFocusFlow(
            TextField baseField,
            TextField trabajadorasField,
            TextField ahorroField,
            Button saveButton
    ) {
        baseField.setOnAction(event -> trabajadorasField.requestFocus());
        trabajadorasField.setOnAction(event -> ahorroField.requestFocus());
        ahorroField.setOnAction(event -> saveButton.requestFocus());
    }

    private void configureSelectAllOnFocus(TextField field) {
        field.focusedProperty().addListener((obs, oldValue, focused) -> {
            if (focused) {
                Platform.runLater(field::selectAll);
            }
        });
        field.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (!field.isFocused()) {
                field.requestFocus();
                event.consume();
            }
        });
        field.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> field.selectAll());
    }

    private Button createActionButton(String text, String styleClass) {
        Button button = new Button(text);
        button.getStyleClass().add(styleClass);
        return button;
    }

    private Label createPill(String text) {
        Label pill = new Label(text);
        pill.getStyleClass().add("pill");
        return pill;
    }

    private FlowPane createResponsiveRow(Node... children) {
        FlowPane pane = new FlowPane();
        pane.setHgap(12);
        pane.setVgap(12);
        pane.setAlignment(Pos.TOP_LEFT);
        pane.setMaxWidth(Double.MAX_VALUE);
        pane.getStyleClass().add("responsive-row");
        pane.getChildren().addAll(children);
        return pane;
    }

    private HBox createAdaptivePanelRow(Node... children) {
        HBox pane = new HBox(12);
        pane.setAlignment(Pos.TOP_LEFT);
        pane.setFillHeight(true);
        pane.setMaxWidth(Double.MAX_VALUE);
        for (Node child : children) {
            if (child instanceof Region region) {
                region.setMinWidth(0);
                HBox.setHgrow(region, Priority.ALWAYS);
            }
            pane.getChildren().add(child);
        }
        return pane;
    }

    private VBox createFieldGroup(String label, Node control, double preferredWidth) {
        VBox group = new VBox(8);
        group.getStyleClass().add("field-group");
        group.setPrefWidth(preferredWidth);
        group.setMinWidth(Math.min(preferredWidth, 150));
        if (control instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
        group.getChildren().addAll(createFormLabel(label), control);
        return group;
    }

    private StackPane createIconBadge(String text, String sizeClass) {
        Label label = new Label(text);
        label.getStyleClass().add("badge-text");
        StackPane badge = new StackPane(label);
        badge.getStyleClass().addAll("icon-badge", sizeClass);
        return badge;
    }

    private <T> TableColumn<T, String> tableColumn(String title, java.util.function.Function<T, String> mapper) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(mapper.apply(cell.getValue())));
        return column;
    }

    private <T> void runAsync(
            java.util.concurrent.Callable<T> supplier,
            java.util.function.Consumer<T> onSuccess,
            java.util.function.Consumer<Throwable> onError
    ) {
        CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.call();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }).whenComplete((result, throwable) -> Platform.runLater(() -> {
            if (throwable != null) {
                onError.accept(unwrapCause(throwable));
                return;
            }
            onSuccess.accept(result);
        }));
    }

    private Throwable unwrapCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private BigDecimal parseRequiredPositive(String value, String errorMessage) {
        BigDecimal parsed = parseCurrencyOrZero(value);
        if (parsed.signum() <= 0) {
            throw new IllegalArgumentException(errorMessage);
        }
        return parsed;
    }

    private BigDecimal parseCurrencyOrZero(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }

        String normalized = value
                .replace("$", "")
                .replace("COP", "")
                .replace("\u00A0", "")
                .replace(" ", "")
                .trim();
        normalized = normalized.replaceAll("[^\\d,.-]", "");

        int commaCount = normalized.length() - normalized.replace(",", "").length();
        int dotCount = normalized.length() - normalized.replace(".", "").length();
        int lastComma = normalized.lastIndexOf(',');
        int lastDot = normalized.lastIndexOf('.');

        if (commaCount > 0 && dotCount > 0) {
            boolean commaIsDecimal = lastComma > lastDot;
            if (commaIsDecimal) {
                normalized = normalized.replace(".", "").replace(",", ".");
            } else {
                normalized = normalized.replace(",", "");
            }
        } else if (commaCount > 1) {
            normalized = normalized.replace(",", "");
        } else if (dotCount > 1) {
            normalized = normalized.replace(".", "");
        } else if (commaCount == 1) {
            int digitsAfterComma = normalized.length() - lastComma - 1;
            normalized = digitsAfterComma == 3
                    ? normalized.replace(",", "")
                    : normalized.replace(",", ".");
        } else if (dotCount == 1) {
            int digitsAfterDot = normalized.length() - lastDot - 1;
            if (digitsAfterDot == 3) {
                normalized = normalized.replace(".", "");
            }
        }
        return new BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP);
    }

    private String formatCurrency(BigDecimal value) {
        return currencyFormat.format(value == null ? BigDecimal.ZERO : value);
    }

    private String formatPlainNumber(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    private String formatNumber(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "" : DATE_TIME_FORMATTER.format(value);
    }

    private String formatReceiptTime(LocalDateTime value) {
        if (value == null) {
            return "";
        }
        return RECEIPT_TIME_FORMATTER.format(value)
                .replace("AM", "a. m.")
                .replace("PM", "p. m.");
    }

    private String formatReceiptAmount(BigDecimal value) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-CO"));
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(0);
        return format.format((value == null ? BigDecimal.ZERO : value).setScale(0, RoundingMode.HALF_UP));
    }

    private String formatReceiptQuantity(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        if (value.stripTrailingZeros().scale() <= 0) {
            return value.setScale(0, RoundingMode.HALF_UP).toPlainString();
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private void showFeedbackDialog(String title, String message, boolean error) {
        Stage stage = new Stage();
        Window owner = resolveActiveWindow();
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setTitle(title);

        StackPane root = new StackPane();
        root.getStyleClass().addAll("dialog-host", "feedback-overlay");
        root.setPadding(new Insets(26));

        VBox card = new VBox(18);
        card.getStyleClass().addAll("surface-card", "feedback-dialog", error ? "feedback-dialog-error" : "feedback-dialog-info");

        HBox hero = new HBox(16);
        hero.setAlignment(Pos.TOP_LEFT);

        StackPane badge = new StackPane();
        badge.getStyleClass().addAll("feedback-badge", error ? "feedback-badge-error" : "feedback-badge-info");
        Label symbol = new Label(error ? "!" : "i");
        symbol.getStyleClass().add("feedback-symbol");
        badge.getChildren().add(symbol);

        VBox copy = new VBox(6);
        copy.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(copy, Priority.ALWAYS);

        Label overline = new Label(error ? "Revisa este detalle" : "Operacion completada");
        overline.getStyleClass().add("feedback-overline");

        Label heading = new Label(safeText(title, error ? "Atencion" : "Mensaje"));
        heading.getStyleClass().add("feedback-title");
        heading.setWrapText(true);

        Label body = new Label(safeText(message, ""));
        body.getStyleClass().add("feedback-message");
        body.setWrapText(true);

        copy.getChildren().addAll(overline, heading, body);
        hero.getChildren().addAll(badge, copy);

        HBox actions = new HBox();
        actions.setAlignment(Pos.CENTER_RIGHT);
        Button closeButton = createActionButton(error ? "Entendido" : "Aceptar", error ? "ghost-button" : "primary-button");
        closeButton.setDefaultButton(true);
        closeButton.setCancelButton(true);
        closeButton.setOnAction(event -> stage.close());
        actions.getChildren().add(closeButton);

        card.getChildren().addAll(hero, actions);
        root.getChildren().add(card);

        Scene scene = new Scene(root, 480, 250);
        scene.getStylesheets().add(getClass().getResource("/com/posdesktop/pos/mockfx/mock-theme.css").toExternalForm());
        scene.setFill(Color.TRANSPARENT);
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE || event.getCode() == KeyCode.ENTER) {
                closeButton.fire();
                event.consume();
            }
        });
        stage.setScene(scene);
        stage.setOnShown(event -> Platform.runLater(closeButton::requestFocus));
        stage.showAndWait();
    }

    private Window resolveActiveWindow() {
        for (Window window : Window.getWindows()) {
            if (window != null && window.isShowing() && window.isFocused()) {
                return window;
            }
        }
        for (Window window : Window.getWindows()) {
            if (window != null && window.isShowing()) {
                return window;
            }
        }
        return null;
    }

    private void showInfo(String title, String message) {
        showFeedbackDialog(title, message, false);
    }

    private void showError(String title, String message) {
        showFeedbackDialog(title, message, true);
    }

    private String initials(String title) {
        String[] parts = title.split(" ");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (!part.isBlank()) {
                builder.append(Character.toUpperCase(part.charAt(0)));
            }
            if (builder.length() == 2) {
                break;
            }
        }
        return builder.toString();
    }

    private void updateResponsiveState(BorderPane shell, Scene scene) {
        boolean compact = scene.getWidth() < 1180 || scene.getHeight() < 760;
        shell.pseudoClassStateChanged(COMPACT, compact);
    }

    private void tuneCompactScreen(VBox root) {
        root.setSpacing(10);
        root.setPadding(new Insets(12, 16, 12, 16));
    }

    private void bindRegionWidthToScene(Region region, double ratio, double min, double max) {
        region.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                region.prefWidthProperty().bind(Bindings.createDoubleBinding(
                        () -> clamp(newScene.getWidth() * ratio, min, max),
                        newScene.widthProperty()
                ));
            }
        });
    }

    private void bindRegionHeightToScene(Region region, double offset, double minHeight) {
        region.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                region.prefHeightProperty().bind(Bindings.createDoubleBinding(
                        () -> Math.max(minHeight, newScene.getHeight() - offset),
                        newScene.heightProperty()
                ));
                region.minHeightProperty().bind(region.prefHeightProperty());
            }
        });
    }

    private void bindTableHeightToScene(TableView<?> table, double ratio, double min, double max) {
        table.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                table.prefHeightProperty().bind(Bindings.createDoubleBinding(
                        () -> clamp(newScene.getHeight() * ratio, min, max),
                        newScene.heightProperty()
                ));
            }
        });
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record SaleDraftRow(BigDecimal cantidad, BigDecimal valorUnitario) {

        private BigDecimal total() {
            return cantidad.multiply(valorUnitario).setScale(2, RoundingMode.HALF_UP);
        }
    }

    private record ClosingTotals(
            int cantidadCierres,
            int cantidadVentas,
            BigDecimal totalVentas,
            BigDecimal totalNetoCaja,
            BigDecimal totalBaseCaja,
            BigDecimal totalTrabajadoras,
            BigDecimal totalAhorro,
            BigDecimal totalFinal,
            BigDecimal promedioPorCierre,
            PosApiClient.CierreDiarioListadoResponse mayorCierre
    ) {
    }

    private record InvoiceDashboardData(
            List<PosApiClient.ProveedorResponse> proveedores,
            List<PosApiClient.FacturaProveedorListadoResponse> facturas
    ) {
    }

    private record InvoiceSupportRow(
            String tipo,
            String archivo,
            String origen,
            String cargadoEn,
            String rutaArchivo,
            String rutaRelativa
    ) {
    }
}
