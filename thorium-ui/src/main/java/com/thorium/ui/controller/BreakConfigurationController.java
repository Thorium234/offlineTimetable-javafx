package com.thorium.ui.controller;

import com.thorium.application.dto.BreakDto;
import com.thorium.ui.di.AppContext;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;

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

    private Long editingId;

    @FXML
    private void initialize() {
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
