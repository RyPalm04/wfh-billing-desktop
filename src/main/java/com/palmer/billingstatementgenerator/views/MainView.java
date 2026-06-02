package com.palmer.billingstatementgenerator.views;

import com.palmer.billingstatementgenerator.AppInfo;
import com.palmer.billingstatementgenerator.client.VersionClient;
import com.palmer.billingstatementgenerator.logging.WorkflowEventTracker;
import com.palmer.billingstatementgenerator.models.statement.Statement;
import com.palmer.billingstatementgenerator.models.statement.StatementCalculator;
import com.palmer.billingstatementgenerator.models.statement.StatementContext;
import com.palmer.billingstatementgenerator.services.PdfService;
import com.palmer.billingstatementgenerator.services.StatementService;
import com.palmer.billingstatementgenerator.views.controllers.BaseController;
import com.palmer.billingstatementgenerator.views.controllers.CashAdvanceController;
import com.palmer.billingstatementgenerator.views.controllers.InstructionsTabController;
import com.palmer.billingstatementgenerator.views.controllers.MerchandiseController;
import com.palmer.billingstatementgenerator.views.controllers.ServicesController;
import com.palmer.billingstatementgenerator.views.controllers.SpecialChargesController;
import com.palmer.billingstatementgenerator.views.controllers.SummaryTabController;
import com.palmer.billingstatementgenerator.views.dialogs.AppDialog;
import com.palmer.billingstatementgenerator.views.dialogs.FeedbackDialog;
import com.palmer.billingstatementgenerator.views.dialogs.IncompleteAlertDialog;
import com.palmer.billingstatementgenerator.views.dialogs.MessageDialog;
import com.palmer.billingstatementgenerator.views.dialogs.OpenStatementDialog;
import com.palmer.billingstatementgenerator.views.dialogs.PdfDialog;
import com.palmer.billingstatementgenerator.views.dialogs.ResetStatementDialog;
import com.palmer.billingstatementgenerator.views.dialogs.SettingsDialog;
import com.palmer.billingstatementgenerator.views.dialogs.StartupDialog;
import com.palmer.billingstatementgenerator.views.dialogs.UnsavedChangesDialog;
import com.palmer.billingstatementgenerator.views.tabs.GeneratorTabs;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The main application view, responsible for assembling the full UI layout.
 * Manages the tab pane, header, button bar, and navigation between tabs.
 * Owns the shared Previous, Clear, and Next buttons and rewires them
 * reactively as the user navigates between tabs.
 */
public class MainView {
    private static final Logger log = LoggerFactory.getLogger(MainView.class);
    private static final String FXML_BASE = "/views/";
    private final StatementService statementService = StatementService.getInstance();
    private final Label versionLabel = new Label("API Version: " + AppInfo.VERSION);
    private StackPane rootPane;
    private BorderPane root;
    private TabPane tabPane;
    private Button prevButton;
    private Button nextButton;
    private Button clearButton;
    private Button saveButton;
    private Button resetButton;
    /**
     * Controller for the summary tab, held for refresh and reset operations.
     */
    private SummaryTabController summaryController;
    /**
     * Event tracker used to extend logging to modal dialog scenes.
     */
    private WorkflowEventTracker eventTracker;

    /**
     * Constructs the main view by creating tabs, the button bar, the layout,
     * and wiring tab selection behavior.
     */
    public MainView() {
        createTabs();
        createButtonBar();
        createLayout();
        wireTabs();
        wireTabDisabling();
    }

    /**
     * Returns the root node of the main view for embedding in a {@link javafx.scene.Scene}.
     *
     * @return the root {@link Parent} node
     */
    public Parent asParent() {
        return rootPane;
    }

    /**
     * Returns the workflow tab pane, used by external components to observe
     * or pass tab context to loggers and other coordinators.
     *
     * @return the main {@link TabPane}
     */
    public TabPane getTabPane() {
        return tabPane;
    }

    /**
     * Sets the event tracker so that modal dialog scenes created by this view
     * are also covered by UI activity logging.
     *
     * @param eventTracker
     *         the active {@link WorkflowEventTracker}
     */
    public void setEventTracker(WorkflowEventTracker eventTracker) {
        this.eventTracker = eventTracker;
        AppDialog.configure(root.getScene().getWindow(), eventTracker);
    }

    /**
     * Creates and configures all tabs, wiring total suppliers to the grid tab controllers
     * and assembling the tab pane.
     */
    private void createTabs() {
        InstructionsTabController instructionsController = new InstructionsTabController();
        ScrollPane instructionsView = instructionsController.buildView(
                i -> tabPane.getSelectionModel().select(i)
        );
        GeneratorTabs instructionsTab = GeneratorTabs.fromController(
                "INSTRUCTIONS", instructionsController, instructionsView);

        GeneratorTabs serviceInformationTab = GeneratorTabs.fromFxml("SERVICE INFORMATION", FXML_BASE + "service_information.fxml");

        ServicesController servicesController = new ServicesController();
        servicesController.setTotalSupplier(() -> StatementCalculator.servicesTotal(StatementContext.current()));

        MerchandiseController merchandiseController = new MerchandiseController();
        merchandiseController.setTotalSupplier(() -> StatementCalculator.merchandiseTotal(StatementContext.current()));

        SpecialChargesController specialChargesController = new SpecialChargesController();
        specialChargesController.setTotalSupplier(() -> StatementCalculator.specialChargesTotal(StatementContext.current()));

        CashAdvanceController cashAdvanceController = new CashAdvanceController();
        cashAdvanceController.setTotalSupplier(() -> StatementCalculator.cashAdvancesTotal(StatementContext.current()));

        GeneratorTabs servicesTab = GeneratorTabs.fromController("SERVICES, FACILITIES & TRANSPORTATION", servicesController);
        GeneratorTabs merchandiseTab = GeneratorTabs.fromController("MERCHANDISE", merchandiseController);
        GeneratorTabs specialChargesTab = GeneratorTabs.fromController("SPECIAL CHARGES", specialChargesController);
        GeneratorTabs cashAdvanceTab = GeneratorTabs.fromController("CASH ADVANCE ITEMS", cashAdvanceController);

        summaryController = new SummaryTabController();
        ScrollPane summaryView = summaryController.buildView(i -> tabPane.getSelectionModel().select(i));
        GeneratorTabs summaryTab = GeneratorTabs.fromController("SUMMARY", summaryController, summaryView);

        tabPane = new TabPane(instructionsTab, serviceInformationTab, servicesTab, merchandiseTab, specialChargesTab, cashAdvanceTab, summaryTab);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
    }

    /**
     * Creates the shared navigation buttons and applies their base style classes.
     */
    private void createButtonBar() {
        prevButton = createButton("Previous", "prevButton", "");
        clearButton = createButton("Clear Selections", "clearButton", "button-clear");
        saveButton = createButton("Save", "saveButton", "button-save");
        nextButton = createButton("Next", "nextButton", "");
        resetButton = createButton("Reset", "resetButton",  "button-reset");
        resetButton.setVisible(false);
    }

    /**
     * Builds the bottom button bar HBox with Previous, Clear, and Next buttons
     * separated by flexible spacers.
     *
     * @return the configured button bar {@link HBox}
     */
    private HBox buildButtonBar() {
        HBox rightGroup = new HBox(8, resetButton, saveButton, nextButton);
        HBox bar = new HBox(prevButton, spacer(), clearButton, spacer(), rightGroup);
        bar.setPadding(new Insets(12, 24, 12, 24));
        bar.setAlignment(Pos.CENTER);
        bar.getStyleClass().add("button-bar");
        return bar;
    }

    /**
     * Creates a horizontally growing spacer region for use in the button bar.
     *
     * @return a {@link Region} with horizontal grow priority set to ALWAYS
     */
    private Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    /**
     * Assembles the main layout with the logo header at the top,
     * tab pane in the center, and button bar at the bottom.
     */
    private void createLayout() {
        Image logo = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/img/app-icon.png")));
        ImageView logoView = new ImageView(logo);
        logoView.setFitWidth(80);
        logoView.setPreserveRatio(true);
        logoView.setOpacity(0.9);

        Button closeBtn = createButton(new FontIcon(FontAwesomeSolid.TIMES), "closeButton", "button-close", e -> closeAppAction());
        Button settingsBtn = createButton(new FontIcon(FontAwesomeSolid.COG), "settingsButton", "button-close", e -> new SettingsDialog().open());
        Button feedbackBtn = createButton(new FontIcon(FontAwesomeSolid.COMMENT_ALT), "feedbackButton", "button-close", e -> openFeedbackDialogAction());

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        HBox header = new HBox(logoView, headerSpacer, feedbackBtn, settingsBtn, closeBtn);
        header.setPadding(new Insets(10, 16, 10, 16));
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("app-header");

        root = new BorderPane();
        root.setTop(header);
        root.setCenter(tabPane);
        root.setBottom(buildButtonBar());

        BorderPane outerPane = generateOuterPane();
        outerPane.setCenter(root);

        StackPane overlay = generateOverlay();
        rootPane = new StackPane(outerPane, overlay);
    }

    private BorderPane generateOuterPane() {
        versionLabel.getStyleClass().add("version-label");

        HBox footer = new HBox(versionLabel);
        footer.getStyleClass().add("app-footer");
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(2, 8, 2, 8));

        BorderPane outerPane = new BorderPane();
        outerPane.setBottom(footer);

        return outerPane;
    }

    private StackPane generateOverlay() {
        StackPane overlay = new StackPane();
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(100, 100);
        spinner.setStyle("-fx-accent: -sm-navy;");
        overlay.getChildren().add(spinner);
        overlay.setBackground(new Background(new BackgroundFill(
                Color.rgb(0, 0, 0, 0.35),
                CornerRadii.EMPTY,
                Insets.EMPTY
        )));
        overlay.visibleProperty().bind(statementService.runningProperty().or(PdfService.getInstance().runningProperty()));
        return overlay;
    }

    private Button createButton(String name, String btnId, String styleClass) {
        Button button = createButton(null, btnId, styleClass, e -> {});
        button.setText(name);
        return button;
    }

    private Button createButton(Node graphic, String btnId, String styleClass, EventHandler<ActionEvent> onAction) {
        Button button = new Button();
        button.setGraphic(graphic);
        button.setId(btnId);
        button.getStyleClass().add(styleClass);
        button.setOnAction(onAction);

        return button;
    }

    private void closeAppAction() {
        Stage stage = (Stage) root.getScene().getWindow();
        if (StatementContext.isDirty()) {
            showUnsavedChangesOnClose(stage);
        } else {
            stage.close();
        }
    }

    private void openFeedbackDialogAction() {
        String tab = tabPane.getSelectionModel().getSelectedItem().getText();
        new FeedbackDialog(tab).open();
    }

    /**
     * Wires the tab selection listener to update the button bar whenever
     * the selected tab changes, and sets the initial button bar state.
     */
    private void wireTabs() {
        updateButtonBar((GeneratorTabs) tabPane.getTabs().get(0));
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab instanceof GeneratorTabs) {
                updateButtonBar((GeneratorTabs) newTab);
            }
        });
    }

    /**
     * Updates the button bar state for the given tab. Hides the button bar on the
     * instructions tab, disables Previous on the first data tab, and switches the
     * Next button to "Generate PDF" and the Clear button to "Reset" on the last tab.
     *
     * @param tab
     *         the currently selected {@link GeneratorTabs}
     */
    private void updateButtonBar(GeneratorTabs tab) {
        int index = tabPane.getTabs().indexOf(tab);
        boolean isFirst = index == 0;
        boolean isLast = index == tabPane.getTabs().size() - 1;

        root.setBottom(isFirst ? null : buildButtonBar());

        unbindButtonDisable(prevButton, nextButton, saveButton);

        prevButton.setDisable(index == 1);
        saveButton.disableProperty().bind(StatementContext.dirtyProperty().not());
        saveButton.setOnAction(e -> statementService.save(null));

        BooleanBinding invalid = tab.getController().hasInvalidSelections();

        prevButton.setOnAction(e -> {
            if (invalid.get()) {
                new IncompleteAlertDialog().open();
            } else {
                tabPane.getSelectionModel().selectPrevious();
            }
        });

        if (isLast) {
            nextButton.setText("Generate PDF");
            nextButton.setOnAction(e -> {
                if (invalid.get()) {
                    new IncompleteAlertDialog().open();
                } else if (StatementContext.isDirty()) {
                    new MessageDialog("Save Required", "The statement must be saved before a PDF can be generated.").open();
                } else {
                    new PdfDialog().open();
                }
            });
            clearButton.disableProperty().unbind();
            clearButton.setVisible(false);
            resetButton.setVisible(true);
            resetButton.setOnAction(e -> showResetDialog());
        } else {
            nextButton.setText("Next");
            nextButton.setOnAction(e -> {
                if (invalid.get()) {
                    new IncompleteAlertDialog().open();
                } else {
                    tabPane.getSelectionModel().selectNext();
                }
            });
            resetButton.setVisible(false);
            clearButton.setVisible(true);
            tab.getController().setClearButton(clearButton);
        }
    }

    private void unbindButtonDisable(Button... buttons) {
        Arrays.stream(buttons).forEach(b -> b.disableProperty().unbind());
    }

    /**
     * Skips the Instructions tab and selects the Service Information tab directly.
     * Called on non-first launches.
     */
    public void skipInstructions() {
        tabPane.getSelectionModel().select(1);
    }

    /**
     * Wires Alt+Left / Alt+Right to navigate between tabs at the scene level,
     * so the shortcuts work regardless of which control has focus.
     *
     * @param scene
     *         the main application {@link Scene}
     */
    public void wireKeyNav(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.isShortcutDown() && e.getCode() == KeyCode.COMMA) {
                new SettingsDialog().open();
                e.consume();
            } else if (e.isAltDown()) {
                if (e.getCode() == KeyCode.RIGHT) {
                    tabPane.getSelectionModel().selectNext();
                    e.consume();
                } else if (e.getCode() == KeyCode.LEFT) {
                    tabPane.getSelectionModel().selectPrevious();
                    e.consume();
                } else if (e.getCode() == KeyCode.F) {
                    openFeedbackDialogAction();
                    e.consume();
                }
            }
        });
    }

    /**
     * Sizes the application window to fit the largest tab by iterating through
     * all tabs, measuring their rendered size, and setting the stage dimensions
     * to the maximum observed width and height.
     */
    public void fitWindowToLargestTab() {
        if (root.getScene() == null) {
            return;
        }
        Window window = root.getScene().getWindow();
        if (!(window instanceof Stage stage)) {
            return;
        }

        int originalIndex = tabPane.getSelectionModel().getSelectedIndex();
        double maxWidth = 0;
        double maxHeight = 0;

        for (int i = 0; i < tabPane.getTabs().size(); i++) {
            tabPane.getSelectionModel().select(i);
            root.getScene().getRoot().applyCss();
            root.getScene().getRoot().layout();
            stage.sizeToScene();
            maxWidth = Math.max(maxWidth, stage.getWidth());
            maxHeight = Math.max(maxHeight, stage.getHeight());
        }

        tabPane.getSelectionModel().select(originalIndex);
        stage.setMinWidth(maxWidth);
        stage.setMinHeight(maxHeight);
        stage.setWidth(maxWidth);
        stage.setHeight(maxHeight);
    }

    /**
     * Shows the reset dialog in a loop until the user makes a terminal choice.
     */
    private void showResetDialog() {
        switch (new ResetStatementDialog().open()) {
            case NEW -> {
                Runnable doNew = () -> {
                    StatementContext.init();
                    rebuildView();
                    tabPane.getSelectionModel().select(1);
                };
                if (StatementContext.isDirty()) {
                    showUnsavedChangesDialog(doNew);
                } else {
                    doNew.run();
                }
            }
            case OPEN -> {
                if (StatementContext.isDirty()) {
                    showUnsavedChangesDialog(() -> showOpenDialog(result -> {
                        if (!result) {
                            showResetDialog();
                        }
                    }));
                } else {
                    showOpenDialog(result -> {
                        if (!result) {
                            showResetDialog();
                        }
                    });
                }
            }
            case CLEAR -> {
                clearAllSelections();
                tabPane.getSelectionModel().select(2);
            }
        }
    }

    /**
     * Shows the open-statement dialog and returns {@code true} if a statement was loaded.
     */
    private void showOpenDialog(Consumer<Boolean> onResult) {
        log.debug("showOpenDialog called");
        statementService.fetchAll(summaries -> {
            log.debug("fetchAll callback fired, summaries={}", summaries.size());
            if (summaries.isEmpty()) {
                onResult.accept(false);
                return;
            }
            try {
                Integer id = new OpenStatementDialog(summaries).open();
                if (id == null) {
                    onResult.accept(false);
                    return;
                }
                statementService.load(id, () -> {
                    rebuildView();
                    tabPane.getSelectionModel().select(1);
                    onResult.accept(true);
                });
            } catch (Exception e) {
                log.error("OpenStatementDialog failed", e);
            }
        });
    }

    /**
     * Displays a modal "unsaved changes" warning and runs {@code onDiscard} if the
     * user confirms they want to discard changes.
     *
     * @param onDiscard
     *         the action to run if the user chooses to discard
     */
    private void showUnsavedChangesDialog(Runnable onDiscard) {
        new UnsavedChangesDialog(
                () -> statementService.save(onDiscard),
                onDiscard
        ).open();
    }

    /**
     * Called by the application after startup completes. Shows the launch dialog
     * and navigates based on the user's choice.
     *
     */
    public void onAppReady() {
        statementService.fetchAll(saved -> {
            if (Objects.requireNonNull(new StartupDialog(!saved.isEmpty()).open()) != StartupDialog.Choice.OPEN) {
                return;
            }

            showOpenDialog(result -> {
                if (!result) {
                    onAppReady();
                }
            });
        });
    }

    /**
     * Resets all tab controllers except the summary, then refreshes the summary.
     * Used by the Clear Selections option in the reset dialog.
     */
    private void clearAllSelections() {
        log.info("Clearing all selections");
        tabPane.getTabs().stream()
               .filter(t -> t instanceof GeneratorTabs)
               .map(t -> ((GeneratorTabs) t).getController())
               .filter(Objects::nonNull)
               .filter(c -> c != summaryController)
               .forEach(BaseController::reset);
        summaryController.refresh();
    }

    /**
     * Shows the unsaved changes dialog when the application window is closing.
     * If the user saves or discards, the stage is closed. Cancel leaves the app open.
     *
     * @param stage
     *         the primary application stage to close after confirmation
     */
    public void showUnsavedChangesOnClose(Stage stage) {
        showUnsavedChangesDialog(() -> {
            stage.setOnCloseRequest(null);
            stage.close();
        });
    }

    /**
     * Rebuilds the entire view after a full reset by recreating tabs,
     * the layout, and rewiring tab selection behavior.
     */
    private void rebuildView() {
        log.info("Rebuilding view after full reset");
        createTabs();
        root.setCenter(tabPane);
        wireTabs();
        wireTabDisabling();
        if (eventTracker != null) {
            eventTracker.onTabPaneReplaced(tabPane);
        }
    }

    /**
     * Binds the disabled state of all data tabs (indices 2–6) to whether the
     * Service Information tab has valid control number and services-for name.
     * Tabs re-enable automatically as soon as both fields are filled.
     */
    private void wireTabDisabling() {
        Statement stmt = StatementContext.current();
        BooleanBinding infoIncomplete = Bindings.createBooleanBinding(
                () -> stmt.getServicesForName().trim().isEmpty(),
                stmt.servicesForNameProperty()
        );
        for (int i = 2; i < tabPane.getTabs().size(); i++) {
            tabPane.getTabs().get(i).disableProperty().bind(infoIncomplete);
        }
    }

    public void fetchAndDisplayVersions() {
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                return new VersionClient().fetchApiVersion();
            }
        };

        task.setOnSucceeded(e -> versionLabel.setText("Desktop: v" + AppInfo.VERSION + " | API: v" + task.getValue()));

        new Thread(task).start();
    }
}