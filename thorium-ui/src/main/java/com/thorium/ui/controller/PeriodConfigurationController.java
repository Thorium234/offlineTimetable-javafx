package com.thorium.ui.controller;

import com.thorium.application.dto.BreakDto;
import com.thorium.application.dto.PeriodDto;
import com.thorium.application.dto.SchoolSettingsDto;
import com.thorium.ui.di.AppContext;
import com.thorium.ui.util.IconUtil;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class PeriodConfigurationController {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    @FXML private TableView<PeriodDto> periodTable;
    @FXML private TableColumn<PeriodDto, Number> numberColumn;
    @FXML private TableColumn<PeriodDto, String> labelColumn;
    @FXML private TableColumn<PeriodDto, String> startColumn;
    @FXML private TableColumn<PeriodDto, String> endColumn;
    @FXML private Spinner<Integer> numberSpinner;
    @FXML private TextField labelField;
    @FXML private ComboBox<String> startCombo;
    @FXML private ComboBox<String> endCombo;
    @FXML private Label messageLabel;
    @FXML private Button saveBtn;
    @FXML private Button deleteBtn;
    @FXML private Button clearBtn;
    @FXML private Button generateBtn;

    private Long editingId;
    private SchoolSettingsDto settings;
    private boolean suppressingAlert;

    @FXML
    private void initialize() {
        IconUtil.addIcon(saveBtn, IconUtil.SAVE, "#16a34a");
        IconUtil.addIcon(deleteBtn, IconUtil.DELETE, "#dc2626");
        IconUtil.addIcon(clearBtn, IconUtil.CLEAR, "#64748b");
        IconUtil.addIcon(generateBtn, IconUtil.REFRESH, "#2563eb");
        numberColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().periodNumber()));
        labelColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().label()));
        startColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().startTime()));
        endColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().endTime()));
        numberSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 12, 1));
        populateTimeOptions();
        loadSettings();
        numberSpinner.valueProperty().addListener((obs, o, n) -> autoComputeTimes(n));
        startCombo.valueProperty().addListener((obs, o, n) -> checkOverlap(n, endCombo.getValue()));
        endCombo.valueProperty().addListener((obs, o, n) -> checkOverlap(startCombo.getValue(), n));
        refreshTable();
        periodTable.getSelectionModel().selectedItemProperty().addListener((obs, o, s) -> {
            if (s != null) populateForm(s);
        });
    }

    private void populateTimeOptions() {
        for (int hour = 6; hour <= 20; hour++) {
            for (int min = 0; min < 60; min += 5) {
                String t = String.format("%02d:%02d", hour, min);
                startCombo.getItems().add(t);
                endCombo.getItems().add(t);
            }
        }
    }

    private void loadSettings() {
        settings = AppContext.get().schoolSettingsUseCase().getSettings();
        if (settings != null && numberSpinner.getValue() != null) {
            autoComputeTimes(numberSpinner.getValue());
        }
    }

    private void autoComputeTimes(Integer periodNumber) {
        if (settings == null || periodNumber == null) return;

        List<PeriodDto> periods = AppContext.get().periodConfigurationUseCase().findAll();
        for (PeriodDto p : periods) {
            if (p.periodNumber() == periodNumber) {
                if (!startCombo.isFocused()) startCombo.setValue(p.startTime());
                if (!endCombo.isFocused()) endCombo.setValue(p.endTime());
                return;
            }
        }

        LocalTime cursor = LocalTime.parse(settings.startTime(), TIME_FMT);
        List<BreakDto> breaks = AppContext.get().breakConfigurationUseCase().findAll();
        List<BreakDto> beforeP1 = breaks.stream().filter(BreakDto::isBeforePeriodOne).toList();
        List<BreakDto> regular = breaks.stream().filter(b -> !b.isBeforePeriodOne()).toList();

        if (periodNumber == 1) {
            BreakDto slotableP1 = beforeP1.stream().filter(BreakDto::slotable).findFirst().orElse(null);
            if (slotableP1 != null) {
                if (!startCombo.isFocused()) startCombo.setValue(cursor.format(TIME_FMT));
                if (!endCombo.isFocused()) endCombo.setValue(cursor.plusMinutes(slotableP1.durationMinutes()).format(TIME_FMT));
                return;
            }
            if (!startCombo.isFocused()) startCombo.setValue(cursor.format(TIME_FMT));
            if (!endCombo.isFocused()) endCombo.setValue(cursor.plusMinutes(settings.periodDurationMinutes()).format(TIME_FMT));
            return;
        }

        for (BreakDto b : beforeP1) {
            cursor = cursor.plusMinutes(b.durationMinutes());
        }

        for (int i = 1; i < periodNumber - 1; i++) {
            cursor = cursor.plusMinutes(settings.periodDurationMinutes());
            for (BreakDto b : regular) {
                if (b.afterPeriod() == i) {
                    cursor = cursor.plusMinutes(b.durationMinutes());
                }
            }
        }

        String start = cursor.format(TIME_FMT);
        String end = cursor.plusMinutes(settings.periodDurationMinutes()).format(TIME_FMT);
        if (!startCombo.isFocused()) startCombo.setValue(start);
        if (!endCombo.isFocused()) endCombo.setValue(end);
    }

    private void checkOverlap(String startTime, String endTime) {
        if (suppressingAlert || startTime == null || endTime == null) return;
        String conflict = findBreakConflict(startTime, endTime);
        if (conflict != null) {
            suppressingAlert = true;
            Alert alert = new Alert(Alert.AlertType.ERROR, conflict, ButtonType.OK);
            alert.setTitle("Time Overlap Detected");
            alert.setHeaderText("This period overlaps with a break");
            alert.showAndWait().ifPresent(r -> {
                if (r == ButtonType.OK) {
                    autoComputeTimes(numberSpinner.getValue());
                }
            });
            suppressingAlert = false;
        }
    }

    @FXML private void onSave() {
        try {
            String start = startCombo.getValue();
            String end = endCombo.getValue();
            String conflict = findBreakConflict(start, end);
            if (conflict != null) {
                showMessage(conflict, true);
                return;
            }
            int periodNumber = numberSpinner.getValue();
            PeriodDto dto = new PeriodDto(editingId, periodNumber, start, end, labelField.getText().trim());
            if (editingId == null) AppContext.get().periodConfigurationUseCase().create(dto);
            else AppContext.get().periodConfigurationUseCase().update(dto);
            clearForm(); refreshTable(); showMessage("Saved", false);
        } catch (IllegalArgumentException | IllegalStateException e) { showMessage(e.getMessage(), true); }
        catch (Exception e) { showMessage("An unexpected error occurred", true); }
    }

    private String findBreakConflict(String startTime, String endTime) {
        if (startTime == null || endTime == null) return "Select start and end times";
        LocalTime start = LocalTime.parse(startTime, TIME_FMT);
        LocalTime end = LocalTime.parse(endTime, TIME_FMT);
        List<BreakDto> breaks = AppContext.get().breakConfigurationUseCase().findAll();
        for (BreakDto b : breaks) {
            if (b.startTime() == null || b.endTime() == null) continue;
            LocalTime breakStart = LocalTime.parse(b.startTime(), TIME_FMT);
            LocalTime breakEnd = LocalTime.parse(b.endTime(), TIME_FMT);
            if (start.isBefore(breakEnd) && end.isAfter(breakStart)) {
                return b.name() + " (" + b.startTime() + "-" + b.endTime() + ") conflicts with selected time";
            }
        }
        return null;
    }

    @FXML private void onDelete() {
        PeriodDto selected = periodTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showMessage("Select a period", true); return; }
        try {
            AppContext.get().periodConfigurationUseCase().delete(selected.id());
            clearForm(); refreshTable(); showMessage("Deleted", false);
        } catch (IllegalArgumentException | IllegalStateException e) { showMessage(e.getMessage(), true); }
        catch (Exception e) { showMessage("An unexpected error occurred", true); }
    }

    @FXML private void onClear() { clearForm(); }

    @FXML private void onGenerateFromSettings() {
        try {
            SchoolSettingsDto s = AppContext.get().schoolSettingsUseCase().getSettings();
            AppContext.get().periodConfigurationUseCase().recalculateMasterTimeline(s);
            clearForm();
            refreshTable();
            showMessage("Generated " + s.totalPeriods() + " periods from settings", false);
        } catch (Exception e) {
            showMessage("Failed to generate periods: " + e.getMessage(), true);
        }
    }

    private void refreshTable() {
        periodTable.setItems(FXCollections.observableArrayList(AppContext.get().periodConfigurationUseCase().findAll()));
    }

    private void populateForm(PeriodDto dto) {
        editingId = dto.id();
        numberSpinner.getValueFactory().setValue(dto.periodNumber());
        labelField.setText(dto.label());
        startCombo.setValue(dto.startTime());
        endCombo.setValue(dto.endTime());
    }

    private void clearForm() {
        editingId = null;
        numberSpinner.getValueFactory().setValue(1);
        labelField.clear();
        startCombo.setValue(null);
        endCombo.setValue(null);
        periodTable.getSelectionModel().clearSelection();
    }

    private void showMessage(String msg, boolean error) {
        messageLabel.setText(msg);
        messageLabel.getStyleClass().removeAll("error", "success");
        messageLabel.getStyleClass().add(error ? "error" : "success");
    }
}
