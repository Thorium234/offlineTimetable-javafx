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

public class BreakConfigurationController {

    @FXML private TableView<BreakDto> breakTable;
    @FXML private TableColumn<BreakDto, String> nameColumn;
    @FXML private TableColumn<BreakDto, Number> afterColumn;
    @FXML private TableColumn<BreakDto, Number> durationColumn;
    @FXML private TextField nameField;
    @FXML private Spinner<Integer> afterSpinner;
    @FXML private Spinner<Integer> durationSpinner;
    @FXML private Spinner<Integer> sortSpinner;
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
        afterSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 12, 0));
        durationSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(5, 120, 20));
        sortSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 20, 0));
        refreshTable();
        breakTable.getSelectionModel().selectedItemProperty().addListener((obs, o, s) -> {
            if (s != null) populateForm(s);
        });
    }

    @FXML private void onSave() {
        try {
            BreakDto dto = new BreakDto(editingId, nameField.getText().trim(),
                    afterSpinner.getValue(), durationSpinner.getValue(), sortSpinner.getValue());
            if (editingId == null) AppContext.get().breakConfigurationUseCase().create(dto);
            else AppContext.get().breakConfigurationUseCase().update(dto);
            clearForm(); refreshTable(); showMessage("Saved", false);
        } catch (IllegalArgumentException | IllegalStateException e) { showMessage(e.getMessage(), true); }
        catch (Exception e) { showMessage("An unexpected error occurred", true); }
    }

    @FXML private void onDelete() {
        BreakDto selected = breakTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showMessage("Select a break", true); return; }
        try {
            AppContext.get().breakConfigurationUseCase().delete(selected.id());
            clearForm(); refreshTable(); showMessage("Deleted", false);
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
            int sort = 1;
            if (tp >= 3) {
                AppContext.get().breakConfigurationUseCase().create(
                        new BreakDto(null, "Morning Break", 2, 15, sort++));
            }
            if (tp >= 5) {
                AppContext.get().breakConfigurationUseCase().create(
                        new BreakDto(null, "Lunch Break", 4, 40, sort++));
            }
            if (tp >= 7) {
                AppContext.get().breakConfigurationUseCase().create(
                        new BreakDto(null, "Afternoon Break", tp - 2, 15, sort++));
            }
            clearForm();
            refreshTable();
            showMessage("Generated default breaks for " + tp + " periods", false);
        } catch (Exception e) {
            showMessage("Failed to generate breaks: " + e.getMessage(), true);
        }
    }

    private void refreshTable() {
        breakTable.setItems(FXCollections.observableArrayList(AppContext.get().breakConfigurationUseCase().findAll()));
    }

    private void populateForm(BreakDto dto) {
        editingId = dto.id();
        nameField.setText(dto.name());
        afterSpinner.getValueFactory().setValue(dto.afterPeriod());
        durationSpinner.getValueFactory().setValue(dto.durationMinutes());
        sortSpinner.getValueFactory().setValue(dto.sortOrder());
    }

    private void clearForm() {
        editingId = null; nameField.clear();
        afterSpinner.getValueFactory().setValue(0);
        durationSpinner.getValueFactory().setValue(20);
        sortSpinner.getValueFactory().setValue(0);
        breakTable.getSelectionModel().clearSelection();
    }

    private void showMessage(String msg, boolean error) {
        messageLabel.setText(msg);
        messageLabel.getStyleClass().removeAll("error", "success");
        messageLabel.getStyleClass().add(error ? "error" : "success");
    }
}
