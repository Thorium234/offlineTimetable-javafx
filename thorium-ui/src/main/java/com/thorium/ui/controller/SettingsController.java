package com.thorium.ui.controller;

import com.thorium.application.dto.SchoolSettingsDto;
import com.thorium.ui.di.AppContext;
import com.thorium.ui.util.IconUtil;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.Optional;

public class SettingsController {

    @FXML private Spinner<Integer> totalPeriodsSpinner;
    @FXML private ComboBox<String> startTimeCombo;
    @FXML private ComboBox<String> endTimeCombo;
    @FXML private Spinner<Integer> durationSpinner;
    @FXML private Label messageLabel;
    @FXML private Label dbPathLabel;
    @FXML private TextField schoolNameField;
    @FXML private Button saveBtn;
    @FXML private Button generateSampleDataBtn;
    @FXML private Button clearDbBtn;

    @FXML
    private void initialize() {
        IconUtil.addIcon(saveBtn, IconUtil.SAVE, "#2563eb");
        IconUtil.addIcon(generateSampleDataBtn, IconUtil.REFRESH, "#ffffff");
        IconUtil.addIcon(clearDbBtn, IconUtil.DELETE, "#ffffff");
        totalPeriodsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 20, 15));
        durationSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(20, 60, 40));
        populateTimeOptions(startTimeCombo);
        populateTimeOptions(endTimeCombo);
        loadSettings();
    }

    private void populateTimeOptions(ComboBox<String> combo) {
        for (int hour = 6; hour <= 20; hour++) {
            for (int min = 0; min < 60; min += 5) {
                combo.getItems().add(String.format("%02d:%02d", hour, min));
            }
        }
    }

    private void loadSettings() {
        SchoolSettingsDto s = AppContext.get().schoolSettingsUseCase().getSettings();
        schoolNameField.setText(s.schoolName());
        totalPeriodsSpinner.getValueFactory().setValue(s.totalPeriods());
        startTimeCombo.setValue(s.startTime());
        endTimeCombo.setValue(s.endTime());
        durationSpinner.getValueFactory().setValue(s.periodDurationMinutes());
    }

    @FXML
    private void onClearDatabase() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Clear All Data");
        confirm.setHeaderText("Delete all teachers, subjects, classes, and timetable data?");
        confirm.setContentText("This cannot be undone. Seed configuration (periods, breaks, settings) will be preserved.");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        try {
            AppContext.get().dataManagementUseCase().clearAllData();
            showMessage("All user data cleared successfully", false);
        } catch (Exception e) {
            showMessage("Failed to clear data: " + e.getMessage(), true);
        }
    }

    @FXML
    private void onGenerateSampleData() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Generate Sample Data");
        confirm.setHeaderText("Generate sample teachers, subjects, classes, and assignments?");
        confirm.setContentText("This will replace any existing user data with 8 classes, 10 subjects, 16 teachers, 80 assignments, 5 breaks, 15 periods, and availability for all teachers.");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        try {
            int count = AppContext.get().dataManagementUseCase().generateSampleData();
            showMessage("Sample data generated: " + count + " assignments", false);
        } catch (Exception e) {
            showMessage("Failed to generate sample data: " + e.getMessage(), true);
        }
    }

    @FXML
    private void onSave() {
        try {
            SchoolSettingsDto dto = new SchoolSettingsDto(
                    1L,
                    schoolNameField.getText(),
                    totalPeriodsSpinner.getValue(),
                    startTimeCombo.getValue(),
                    endTimeCombo.getValue(),
                    durationSpinner.getValue(),
                    0.50, 0.40, 0.10
            );
            AppContext.get().schoolSettingsUseCase().updateSettings(dto);
            recalcPeriods();
            showMessage("Settings saved — periods recalculated", false);
        } catch (IllegalArgumentException e) {
            showMessage(e.getMessage(), true);
        } catch (Exception e) {
            showMessage("Unexpected error saving settings", true);
        }
    }

    private void recalcPeriods() {
        try {
            var s = AppContext.get().schoolSettingsUseCase().getSettings();
            AppContext.get().periodConfigurationUseCase().recalculateMasterTimeline(s);
        } catch (Exception ignored) {}
    }

    private void showMessage(String msg, boolean error) {
        messageLabel.setText(msg);
        messageLabel.getStyleClass().removeAll("error", "success");
        messageLabel.getStyleClass().add(error ? "error" : "success");
    }
}
