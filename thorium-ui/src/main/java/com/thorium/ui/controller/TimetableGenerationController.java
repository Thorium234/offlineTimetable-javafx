package com.thorium.ui.controller;

import com.thorium.application.dto.TimetableDto;
import com.thorium.ui.di.AppContext;
import com.thorium.ui.util.IconUtil;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public class TimetableGenerationController {

    @FXML private TextField nameField;
    @FXML private Label resultLabel;
    @FXML private Label qualityLabel;
    @FXML private Button generateBtn;

    @FXML
    private void initialize() {
        IconUtil.addIcon(generateBtn, IconUtil.GENERATE, "#ffffff");
    }

    @FXML
    private void onGenerate() {
        try {
            TimetableDto result = AppContext.get().generateTimetableUseCase()
                    .execute(nameField.getText().trim());
            String coverage = String.format("%.2f", result.qualityScore());
            qualityLabel.setText(String.format("Quality: %s | Entries: %d",
                    coverage, result.entries().size()));
            resultLabel.getStyleClass().removeAll("error", "success");
            if (result.status() == com.thorium.domain.value.TimetableStatus.DRAFT) {
                resultLabel.setText("Partial: " + result.name() + " (saved as DRAFT)");
                qualityLabel.setText(String.format("Quality: %s | Entries: %d (incomplete)",
                        coverage, result.entries().size()));
                resultLabel.getStyleClass().add("warning");
            } else {
                resultLabel.setText("Generated: " + result.name());
                resultLabel.getStyleClass().add("success");
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            resultLabel.setText(e.getMessage());
            resultLabel.getStyleClass().removeAll("success", "warning");
            resultLabel.getStyleClass().add("error");
            qualityLabel.setText("");
        } catch (Exception e) {
            resultLabel.setText("An unexpected error occurred");
            resultLabel.getStyleClass().removeAll("success", "warning");
            resultLabel.getStyleClass().add("error");
            qualityLabel.setText("");
        }
    }
}
