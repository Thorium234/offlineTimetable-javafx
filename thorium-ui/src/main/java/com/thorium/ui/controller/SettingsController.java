package com.thorium.ui.controller;

import com.thorium.application.dto.SchoolSettingsDto;
import com.thorium.ui.di.AppContext;
import com.thorium.ui.util.IconUtil;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class SettingsController {

    @FXML private Spinner<Integer> totalPeriodsSpinner;
    @FXML private ComboBox<String> startTimeCombo;
    @FXML private ComboBox<String> endTimeCombo;
    @FXML private Spinner<Integer> durationSpinner;
    @FXML private Label messageLabel;
    @FXML private Label dbPathLabel;
    @FXML private Button saveBtn;

    @FXML
    private void initialize() {
        IconUtil.addIcon(saveBtn, IconUtil.SAVE, "#2563eb");
        totalPeriodsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 12, 8));
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
        totalPeriodsSpinner.getValueFactory().setValue(s.totalPeriods());
        startTimeCombo.setValue(s.startTime());
        endTimeCombo.setValue(s.endTime());
        durationSpinner.getValueFactory().setValue(s.periodDurationMinutes());
    }

    @FXML
    private void onSave() {
        try {
            SchoolSettingsDto dto = new SchoolSettingsDto(
                    1L,
                    totalPeriodsSpinner.getValue(),
                    startTimeCombo.getValue(),
                    endTimeCombo.getValue(),
                    durationSpinner.getValue()
            );
            AppContext.get().schoolSettingsUseCase().updateSettings(dto);
            showMessage("Settings saved", false);
        } catch (IllegalArgumentException e) {
            showMessage(e.getMessage(), true);
        } catch (Exception e) {
            showMessage("Unexpected error saving settings", true);
        }
    }

    private void showMessage(String msg, boolean error) {
        messageLabel.setText(msg);
        messageLabel.getStyleClass().removeAll("error", "success");
        messageLabel.getStyleClass().add(error ? "error" : "success");
    }
}
