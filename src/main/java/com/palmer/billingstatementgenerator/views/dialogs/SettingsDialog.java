package com.palmer.billingstatementgenerator.views.dialogs;

import com.palmer.billingstatementgenerator.models.statement.StatementContext;
import com.palmer.billingstatementgenerator.util.AppPreferences;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/**
 * Modal settings dialog for configuring the sales tax rate and resetting help prompts.
 */
public class SettingsDialog extends AppDialog<Void> {

    private static final Logger log = LoggerFactory.getLogger(SettingsDialog.class);

    /**
     * {@inheritDoc}
     */
    @Override
    protected VBox buildContent() {
        Label taxRateLabel = new Label("Sales Tax Rate (%)");
        taxRateLabel.getStyleClass().add("splash-subtitle");

        String displayRate = AppPreferences.getSalesTaxRate()
                .movePointRight(2).stripTrailingZeros().toPlainString();

        TextField taxRateField = buildTaxRateField(displayRate);
        Label taxRateError = buildErrorLabel("Please enter a valid number (e.g. 8.25)");

        HBox taxRateRow = new HBox(12, taxRateLabel, taxRateField);
        taxRateRow.setAlignment(Pos.CENTER);

        Button resetPromptsButton = buildResetPromptButton();

        Label resetNote = new Label("Shows the instructions tab on next launch");
        resetNote.getStyleClass().add("splash-subtitle");

        VBox resetSection = new VBox(6, resetPromptsButton, resetNote);
        resetSection.setAlignment(Pos.CENTER);

        Button saveBtn = buildSaveButton(taxRateField, taxRateError);
        Button cancelButton = buildCancelButton();

        HBox buttons = new HBox(12, saveBtn, cancelButton);
        buttons.setAlignment(Pos.CENTER);

        return contentBox("Settings", taxRateRow, taxRateError, resetSection, buttons);
    }

    private TextField buildTaxRateField(String displayRate) {
        TextField taxRateField = new TextField(displayRate);
        taxRateField.setMaxWidth(100);
        taxRateField.setId("taxRateField");
        return taxRateField;
    }

    private Button buildResetPromptButton() {
        Button resetPromptsButton = new Button("Reset Help Prompts");
        resetPromptsButton.setId("resetPromptsButton");
        resetPromptsButton.setOnAction(e -> {
            AppPreferences.setHasLaunched(false);
            resetPromptsButton.setText("✓ Takes effect on next launch");
            resetPromptsButton.setDisable(true);
        });
        return resetPromptsButton;
    }

    private Button buildCancelButton() {
        Button cancelButton = new Button("Cancel");
        cancelButton.setId("settingsCancelButton");
        cancelButton.getStyleClass().add("button-clear");
        cancelButton.setOnAction(e -> close());
        return cancelButton;
    }

    private Button buildSaveButton(TextField taxRateField, Label taxRateError) {
        Button saveBtn = new Button("Save");
        saveBtn.setId("settingsSaveButton");
        saveBtn.setOnAction(e -> {
            try {
                BigDecimal rate = new BigDecimal(taxRateField.getText().trim()).movePointLeft(2);
                AppPreferences.setSalesTaxRate(rate);
                StatementContext.current().setSalesTaxRate(rate);
                log.info("Tax rate updated to {}", rate.toPlainString());
                close();
            } catch (NumberFormatException ex) {
                taxRateError.setVisible(true);
            }
        });
        return saveBtn;
    }
}