package com.palmer.billingstatementgenerator.views.dialogs;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;

public class LicenseKeyDialog extends AppDialog<String> {

    private TextField keyField;
    private Label errorLabel;

    @Override
    protected VBox buildContent() {
        Label instructionLabel = buildInstructionLabel();
        keyField = buildKeyTextField();
        errorLabel = buildErrorLabel("Invalid license key format");
        Button activateButton = buildActivateButton();
        Button cancelButton = buildCancelButton();

        HBox buttons = new HBox(12, activateButton, cancelButton);
        buttons.setAlignment(Pos.CENTER);

        return contentBox("Activate Desktop App", instructionLabel, keyField, errorLabel, buttons);
    }

    private Label buildInstructionLabel() {
        Label instructions = new Label("""
                                      Enter the license key from your Eternatel web portal
                                      Settings page to activate the desktop app.
                                      """);
        instructions.getStyleClass().add("splash-subtitle");
        instructions.setTextAlignment(TextAlignment.CENTER);
        return instructions;
    }

    private TextField buildKeyTextField() {
        TextField keyField = new TextField();
        keyField.setPromptText("EDC-XXXXX-XXXXX-XXXXX-XXXXX");
        keyField.setMaxWidth(280);
        keyField.setId("licenseKeyField");

        return keyField;
    }

    private Button buildActivateButton() {
        Button button = new Button("Activate");
        button.setId("activateButton");
        button.setOnAction(event -> {
            String key = keyField.getText().trim().toUpperCase();

            if (key.matches("EDC(-[A-Z2-9]{4}){5}")) {
                result = key;
                close();
            } else {
                errorLabel.setVisible(true);
            }
        });

        return button;
    }

    private Button buildCancelButton() {
        Button button = new Button("Cancel");
        button.setId("cancelButton");
        button.getStyleClass().add("button-clear");
        button.setOnAction(event -> close());

        return button;
    }
}
