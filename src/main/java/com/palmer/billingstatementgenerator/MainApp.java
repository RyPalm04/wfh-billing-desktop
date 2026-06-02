package com.palmer.billingstatementgenerator;

import com.palmer.billingstatementgenerator.client.ApiConfig;
import com.palmer.billingstatementgenerator.client.VersionClient;
import com.palmer.billingstatementgenerator.logging.WorkflowEventTracker;
import com.palmer.billingstatementgenerator.models.statement.StatementContext;
import com.palmer.billingstatementgenerator.util.AppLock;
import com.palmer.billingstatementgenerator.util.AppPreferences;
import com.palmer.billingstatementgenerator.views.MainView;
import com.palmer.billingstatementgenerator.views.SplashView;
import com.palmer.billingstatementgenerator.views.dialogs.MessageDialog;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class MainApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(MainApp.class);

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        log.info("Application starting");
        if (!AppLock.acquire()) {
            new MessageDialog("Already Running",
                    "Statement Manager is already open.\n" +
                    "Only one instance can run at a time.").open();
            Platform.exit();
            return;
        }
        loadFonts();

        var iconStream = getClass().getResourceAsStream(
                "/img/app-icon.png");
        if (iconStream != null) {
            primaryStage.getIcons().add(new javafx.scene.image.Image(iconStream));
        }

        SplashView splashView = new SplashView();
        Scene splashScene = new Scene(splashView.asParent());
        splashScene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        primaryStage.setScene(splashScene);
        primaryStage.initStyle(StageStyle.UNDECORATED);
        primaryStage.show();

        Task<Void> initTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Connecting to database...");
                updateProgress(1, 4);
                ApiConfig.init();
                Thread.sleep(new Random().nextInt(601) + 900);

                updateMessage("Loading statement context...");
                updateProgress(2, 4);
                StatementContext.init();
                Thread.sleep(new Random().nextInt(601) + 900);

                updateMessage("Preparing UI...");
                updateProgress(3, 4);
                Thread.sleep(new Random().nextInt(601) + 900);

                updateMessage("Ready!");
                updateProgress(4, 4);
                Thread.sleep(new Random().nextInt(401) + 700);

                return null;
            }
        };

        splashView.getStatusLabel().textProperty().bind(initTask.messageProperty());

        initTask.progressProperty().addListener((obs, oldVal, newVal) -> Platform.runLater(() -> {
            Timeline tl = new Timeline(new KeyFrame(
                    Duration.millis(400),
                    new KeyValue(splashView.getProgressBar().progressProperty(), newVal.doubleValue(), Interpolator.EASE_BOTH)
            ));
            tl.play();
        }));

        initTask.setOnSucceeded(e -> {
            boolean firstLaunch = !AppPreferences.hasLaunched();
            AppPreferences.setHasLaunched(true);
            log.info("Startup complete — firstLaunch={}", firstLaunch);

            MainView mainView = new MainView();
            BorderPane root = new BorderPane(mainView.asParent());
            Scene mainScene = new Scene(root);
            mainScene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

            Stage mainStage = new Stage();
            mainStage.setScene(mainScene);
            mainStage.setTitle("Statement Manager");
            mainStage.getIcons().addAll(primaryStage.getIcons());
            mainStage.show();

            mainView.wireKeyNav(mainScene);
            mainView.setEventTracker(new WorkflowEventTracker(mainStage, mainView.getTabPane()));
            mainView.fitWindowToLargestTab();
            mainView.onAppReady();
            mainView.fetchAndDisplayVersions();

            mainStage.setOnCloseRequest(event -> {
                if (StatementContext.isDirty()) {
                    event.consume();
                    mainView.showUnsavedChangesOnClose(mainStage);
                }
            });

            primaryStage.close();
        });

        initTask.setOnFailed(e -> {
            Throwable ex = initTask.getException();
            log.error("Startup failed", ex);
            primaryStage.close();
            new MessageDialog("Startup Error",
                    "The application failed to start.\n" +
                    (ex != null ? ex.getMessage() : "Unknown error")).open();
            Platform.exit();
        });

        new Thread(initTask).start();
    }

    private void loadFonts() {
        String base = "/fonts/";
        Font.loadFont(getClass().getResourceAsStream(base + "PlayfairDisplay-Regular.ttf"), 14);
        Font.loadFont(getClass().getResourceAsStream(base + "PlayfairDisplay-Bold.ttf"), 14);
        Font.loadFont(getClass().getResourceAsStream(base + "Lato-Regular.ttf"), 14);
        Font.loadFont(getClass().getResourceAsStream(base + "Lato-Bold.ttf"), 14);
        Font.loadFont(getClass().getResourceAsStream(base + "Lato-Light.ttf"), 14);
    }
}
