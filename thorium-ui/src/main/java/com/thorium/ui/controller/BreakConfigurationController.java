package com.thorium.ui.controller;

import com.thorium.application.dto.BreakDto;
import com.thorium.application.dto.SchoolSettingsDto;
import com.thorium.ui.di.AppContext;
import com.thorium.ui.util.IconUtil;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.Region;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class BreakConfigurationController {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    @FXML private TableView<BreakDto> breakTable;
    @FXML private TableColumn<BreakDto, String> nameColumn;
    @FXML private TableColumn<BreakDto, Number> afterColumn;
    @FXML private TableColumn<BreakDto, Number> durationColumn;
    @FXML private TableColumn<BreakDto, String> beforeColumn;
    @FXML private TableColumn<BreakDto, String> startColumn;
    @FXML private TableColumn<BreakDto, String> endColumn;
    @FXML private TextField nameField;
    @FXML private Spinner<Integer> afterSpinner;
    @FXML private Spinner<Integer> durationSpinner;
    @FXML private Spinner<Integer> sortSpinner;
    @FXML private CheckBox beforeP1Checkbox;
    @FXML private ComboBox<String> startCombo;
    @FXML private ComboBox<String> endCombo;
    @FXML private Label messageLabel;
    @FXML private Button saveBtn;
    @FXML private Button deleteBtn;
    @FXML private Button clearBtn;
    @FXML private Button generateBtn;

    private Long editingId;

    @FXML
    private void initialize() {
        IconUtil.addIcon(saveBtn, IconUtil.SAVE, "#16a34a");
        IconUtil.addIcon(deleteBtn, IconUtil.DELETE, "#dc2626");
        IconUtil.addIcon(clearBtn, IconUtil.CLEAR, "#64748b");
        IconUtil.addIcon(generateBtn, IconUtil.REFRESH, "#2563eb");
        nameColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().name()));
        afterColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().afterPeriod()));
        durationColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().durationMinutes()));
        beforeColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().isBeforePeriodOne() ? "Yes" : ""));
        startColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().startTime()));
        endColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().endTime()));
        afterSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 12, 0));
        durationSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(5, 120, 20));
        sortSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 20, 0));
        populateTimeOptions();
        refreshTable();
        breakTable.getSelectionModel().selectedItemProperty().addListener((obs, o, s) -> {
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

    @FXML private void onSave() {
        try {
            BreakDto dto = new BreakDto(editingId, nameField.getText().trim(),
                    afterSpinner.getValue(), durationSpinner.getValue(), sortSpinner.getValue(),
                    beforeP1Checkbox.isSelected(), false,
                    startCombo.getValue(), endCombo.getValue());
            if (editingId == null) AppContext.get().breakConfigurationUseCase().create(dto);
            else AppContext.get().breakConfigurationUseCase().update(dto);
            clearForm(); refreshTable(); showMessage("Saved", false);
            recalcPeriods();
        } catch (IllegalArgumentException | IllegalStateException e) { showMessage(e.getMessage(), true); }
        catch (Exception e) { showMessage("An unexpected error occurred", true); }
    }

    @FXML private void onDelete() {
        BreakDto selected = breakTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showMessage("Select a break", true); return; }
        try {
            AppContext.get().breakConfigurationUseCase().delete(selected.id());
            clearForm(); refreshTable(); showMessage("Deleted", false);
            recalcPeriods();
        } catch (IllegalArgumentException | IllegalStateException e) { showMessage(e.getMessage(), true); }
        catch (Exception e) { showMessage("An unexpected error occurred", true); }
    }

    @FXML private void onClear() { clearForm(); }

    @FXML private void onGenerateDefaults() {
        try {
            SchoolSettingsDto s = AppContext.get().schoolSettingsUseCase().getSettings();
            int tp = s.totalPeriods();
            for (BreakDto b : AppContext.get().breakConfigurationUseCase().findAll()) {
                AppContext.get().breakConfigurationUseCase().delete(b.id());
            }
            List<BreakSpec> specs = new ArrayList<>();
            specs.add(new BreakSpec("Assembly", 0, 50, true, false));
            if (tp >= 4) specs.add(new BreakSpec("Tea Break", 3, 20, false, false));
            if (tp >= 6) specs.add(new BreakSpec("Short Break", 5, 10, false, false));
            if (tp >= 8) specs.add(new BreakSpec("Lunch Break", 7, 50, false, false));
            int gamesAfter = Math.min(tp, 10);
            if (tp >= 11) {
                specs.add(new BreakSpec("Games Time", 10, 40, false, false));
            } else if (tp >= gamesAfter) {
                specs.add(new BreakSpec("Games Time", gamesAfter, 40, false, false));
            }
            int sort = 1;
            for (BreakSpec spec : specs) {
                AppContext.get().breakConfigurationUseCase().create(
                        new BreakDto(null, spec.name, spec.afterPeriod, spec.duration, sort++, spec.beforeP1, spec.slotable, null, null));
            }
            clearForm(); refreshTable();
            recalcPeriods();
            showMessage("Generated standard school breaks for " + tp + " periods", false);
        } catch (Exception e) {
            showMessage("Failed to generate breaks: " + e.getMessage(), true);
        }
    }

        private record BreakSpec(String name, int afterPeriod, int duration, boolean beforeP1, boolean slotable) {}

    private void recalcPeriods() {
        try {
            SchoolSettingsDto s = AppContext.get().schoolSettingsUseCase().getSettings();
            AppContext.get().periodConfigurationUseCase().recalculateMasterTimeline(s);
        } catch (Exception ignored) {}
    }

    private void refreshTable() {
        breakTable.setItems(FXCollections.observableArrayList(
                AppContext.get().breakConfigurationUseCase().findAll()));
    }

    private void populateForm(BreakDto dto) {
        editingId = dto.id();
        nameField.setText(dto.name());
        afterSpinner.getValueFactory().setValue(dto.afterPeriod());
        durationSpinner.getValueFactory().setValue(dto.durationMinutes());
        sortSpinner.getValueFactory().setValue(dto.sortOrder());
        beforeP1Checkbox.setSelected(dto.isBeforePeriodOne());
        startCombo.setValue(dto.startTime());
        endCombo.setValue(dto.endTime());
    }

    private void clearForm() {
        editingId = null; nameField.clear();
        afterSpinner.getValueFactory().setValue(0);
        durationSpinner.getValueFactory().setValue(20);
        sortSpinner.getValueFactory().setValue(0);
        beforeP1Checkbox.setSelected(false);
        startCombo.setValue(null);
        endCombo.setValue(null);
        breakTable.getSelectionModel().clearSelection();
    }

    private void showMessage(String msg, boolean error) {
        messageLabel.setText(msg);
        messageLabel.getStyleClass().removeAll("error", "success");
        messageLabel.getStyleClass().add(error ? "error" : "success");
    }
}
