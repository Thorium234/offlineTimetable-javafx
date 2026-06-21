package com.thorium.ui.controller;

import com.thorium.application.dto.PeriodDto;
import com.thorium.ui.di.AppContext;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class PeriodConfigurationController {

    @FXML private TableView<PeriodDto> periodTable;
    @FXML private TableColumn<PeriodDto, Number> numberColumn;
    @FXML private TableColumn<PeriodDto, String> labelColumn;
    @FXML private TableColumn<PeriodDto, String> startColumn;
    @FXML private TableColumn<PeriodDto, String> endColumn;
    @FXML private Spinner<Integer> numberSpinner;
    @FXML private TextField labelField;
    @FXML private TextField startField;
    @FXML private TextField endField;
    @FXML private Label messageLabel;

    private Long editingId;

    @FXML
    private void initialize() {
        numberColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().periodNumber()));
        labelColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().label()));
        startColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().startTime()));
        endColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().endTime()));
        numberSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 12, 1));
        refreshTable();
        periodTable.getSelectionModel().selectedItemProperty().addListener((obs, o, s) -> {
            if (s != null) populateForm(s);
        });
    }

    @FXML private void onSave() {
        try {
            PeriodDto dto = new PeriodDto(editingId, numberSpinner.getValue(),
                    startField.getText().trim(), endField.getText().trim(), labelField.getText().trim());
            if (editingId == null) AppContext.get().periodConfigurationUseCase().create(dto);
            else AppContext.get().periodConfigurationUseCase().update(dto);
            clearForm(); refreshTable(); showMessage("Saved", false);
        } catch (IllegalArgumentException | IllegalStateException e) { showMessage(e.getMessage(), true); }
        catch (Exception e) { showMessage("An unexpected error occurred", true); }
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

    private void refreshTable() {
        periodTable.setItems(FXCollections.observableArrayList(AppContext.get().periodConfigurationUseCase().findAll()));
    }

    private void populateForm(PeriodDto dto) {
        editingId = dto.id();
        numberSpinner.getValueFactory().setValue(dto.periodNumber());
        labelField.setText(dto.label());
        startField.setText(dto.startTime());
        endField.setText(dto.endTime());
    }

    private void clearForm() {
        editingId = null;
        numberSpinner.getValueFactory().setValue(1);
        labelField.clear(); startField.clear(); endField.clear();
        periodTable.getSelectionModel().clearSelection();
    }

    private void showMessage(String msg, boolean error) {
        messageLabel.setText(msg);
        messageLabel.getStyleClass().removeAll("error", "success");
        messageLabel.getStyleClass().add(error ? "error" : "success");
    }
}
