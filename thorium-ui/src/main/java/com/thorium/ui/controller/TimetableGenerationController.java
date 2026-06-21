package com.thorium.ui.controller;

import com.thorium.application.dto.TimetableDto;
import com.thorium.ui.di.AppContext;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public class TimetableGenerationController {

    @FXML private TextField nameField;
    @FXML private Label resultLabel;
    @FXML private Label qualityLabel;

    @FXML
    private void onGenerate() {
        try {
            TimetableDto result = AppContext.get().generateTimetableUseCase()
                    .execute(nameField.getText().trim());
            resultLabel.setText("Generated: " + result.name() + " (ID: " + result.id() + ")");
            qualityLabel.setText(String.format("Quality score: %.2f | Entries: %d",
                    result.qualityScore(), result.entries().size()));
            resultLabel.getStyleClass().removeAll("error");
            resultLabel.getStyleClass().add("success");
        } catch (IllegalArgumentException | IllegalStateException e) {
            resultLabel.setText(e.getMessage());
            resultLabel.getStyleClass().removeAll("success");
            resultLabel.getStyleClass().add("error");
            qualityLabel.setText("");
        } catch (Exception e) {
            resultLabel.setText("An unexpected error occurred");
            resultLabel.getStyleClass().removeAll("success");
            resultLabel.getStyleClass().add("error");
            qualityLabel.setText("");
        }
    }
}
