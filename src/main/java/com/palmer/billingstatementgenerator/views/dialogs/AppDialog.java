package com.palmer.billingstatementgenerator.views.dialogs;

import com.palmer.billingstatementgenerator.logging.WorkflowEventTracker;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/**
 * Abstract base for application modal dialogs. Subclasses define their content
 * via {@link #buildContent()} and display themselves by calling {@link #open()}.
 *
 * <p>The owner window and event tracker are registered once via
 * {@link #configure(Window, WorkflowEventTracker)} at startup and shared by all
 * dialogs, so call sites need no knowledge of the UI hierarchy.</p>
 *
 * <p>The type parameter {@code R} is the result type returned by {@link #open()}.
 * Subclasses assign {@link #result} before calling {@link #close()}. Use {@code Void}
 * for dialogs that carry no meaningful return value.</p>
 *
 * @param <R>
 *         the result type returned by {@link #open()}
 */
public abstract class AppDialog<R> extends Stage {

    private static final String STYLESHEET =
            "/css/style.css";

    private static Window defaultOwner;
    private static WorkflowEventTracker defaultTracker;

    /**
     * The value returned by {@link #open()}. Subclasses assign this before calling
     * {@link #close()}. Remains {@code null} if the dialog is dismissed without a
     * button press.
     */
    protected R result;

    /**
     * Registers the application owner window and event tracker. Must be called once
     * before any dialog is opened, typically when the main stage becomes visible.
     *
     * @param owner
     *         the primary application {@link Window}
     * @param tracker
     *         the active {@link WorkflowEventTracker}, or {@code null}
     */
    public static void configure(Window owner, WorkflowEventTracker tracker) {
        defaultOwner = owner;
        defaultTracker = tracker;
    }

    /**
     * Builds the standard dialog layout: a dark draggable header band containing
     * {@code title}, followed by a body {@link VBox} with the provided children.
     * Dragging the header moves the dialog stage.
     *
     * @param title
     *         the dialog heading text shown in the header
     * @param children
     *         body nodes to display below the header
     *
     * @return the assembled content {@link VBox}
     */
    protected VBox contentBox(String title, Node... children) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("dialog-header-title");

        HBox header = new HBox(titleLabel);
        header.getStyleClass().add("dialog-header");
        header.setAlignment(Pos.CENTER);

        final double[] dragDelta = {0, 0};
        header.setOnMousePressed(e -> {
            dragDelta[0] = getX() - e.getScreenX();
            dragDelta[1] = getY() - e.getScreenY();
        });
        header.setOnMouseDragged(e -> {
            setX(e.getScreenX() + dragDelta[0]);
            setY(e.getScreenY() + dragDelta[1]);
        });

        VBox body = new VBox(20, children);
        body.setPadding(new Insets(24, 32, 32, 32));
        body.setAlignment(Pos.CENTER);

        VBox container = new VBox(header, body);
        container.getStyleClass().add("dialog-container");
        return container;
    }

    /**
     * Builds and returns the dialog's content node. Called once by {@link #open()}
     * immediately before the dialog is shown, so button handlers may safely
     * reference {@link #close()} via {@code this}.
     *
     * @return the root content {@link VBox} for this dialog
     */
    protected abstract VBox buildContent();

    /**
     * Configures the stage, attaches the stylesheet and event tracker, shows the
     * dialog, blocks until it is closed, then returns {@link #result}.
     *
     * @return the result set by the subclass, or {@code null} if the dialog was
     *         dismissed without a button press
     */
    public R open() {
        initStyle(StageStyle.TRANSPARENT);
        initModality(Modality.APPLICATION_MODAL);
        initOwner(defaultOwner);
        setResizable(false);

        StackPane wrapper = new StackPane(buildContent());
        wrapper.setPadding(new Insets(12));
        wrapper.setStyle("-fx-background-color: transparent;");

        Scene scene = new Scene(wrapper);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(AppDialog.class.getResource(STYLESHEET).toExternalForm());
        if (defaultTracker != null) {
            defaultTracker.attachTo(scene);
        }
        setScene(scene);
        showAndWait();
        return result;
    }

    protected Label buildErrorLabel(String labelText) {
        Label errorLabel = new Label(labelText);
        errorLabel.getStyleClass().add("splash-subtitle");
        errorLabel.setVisible(false);

        return errorLabel;
    }
}