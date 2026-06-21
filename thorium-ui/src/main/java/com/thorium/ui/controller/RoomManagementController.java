package com.thorium.ui.controller;

import com.thorium.application.dto.RoomDto;
import com.thorium.domain.model.RoomType;
import com.thorium.ui.di.AppContext;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class RoomManagementController {

    @FXML private TableView<RoomDto> roomTable;
    @FXML private TableColumn<RoomDto, String> codeColumn;
    @FXML private TableColumn<RoomDto, String> nameColumn;
    @FXML private TableColumn<RoomDto, String> typeColumn;
    @FXML private TableColumn<RoomDto, Number> capacityColumn;
    @FXML private TextField codeField;
    @FXML private TextField nameField;
    @FXML private ComboBox<RoomType> typeCombo;
    @FXML private Spinner<Integer> capacitySpinner;
    @FXML private Label messageLabel;

    private Long editingId;

    @FXML
    private void initialize() {
        codeColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().code()));
        nameColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().name()));
        typeColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().type().name()));
        capacityColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().capacity()));
        typeCombo.setItems(FXCollections.observableArrayList(RoomType.values()));
        capacitySpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 200, 30));
        refreshTable();
        roomTable.getSelectionModel().selectedItemProperty().addListener((obs, o, s) -> {
            if (s != null) populateForm(s);
        });
    }

    @FXML private void onSave() {
        try {
            RoomDto dto = new RoomDto(editingId, codeField.getText().trim(), nameField.getText().trim(),
                    typeCombo.getValue(), capacitySpinner.getValue());
            if (editingId == null) AppContext.get().roomManagementUseCase().create(dto);
            else AppContext.get().roomManagementUseCase().update(dto);
            clearForm(); refreshTable(); showMessage("Saved", false);
        } catch (IllegalArgumentException | IllegalStateException e) { showMessage(e.getMessage(), true); }
        catch (Exception e) { showMessage("An unexpected error occurred", true); }
    }

    @FXML private void onDelete() {
        RoomDto selected = roomTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showMessage("Select a room", true); return; }
        try {
            AppContext.get().roomManagementUseCase().delete(selected.id());
            clearForm(); refreshTable(); showMessage("Deleted", false);
        } catch (IllegalArgumentException | IllegalStateException e) { showMessage(e.getMessage(), true); }
        catch (Exception e) { showMessage("An unexpected error occurred", true); }
    }

    @FXML private void onClear() { clearForm(); }

    private void refreshTable() {
        roomTable.setItems(FXCollections.observableArrayList(AppContext.get().roomManagementUseCase().findAll()));
    }

    private void populateForm(RoomDto dto) {
        editingId = dto.id();
        codeField.setText(dto.code()); nameField.setText(dto.name());
        typeCombo.getSelectionModel().select(dto.type());
        capacitySpinner.getValueFactory().setValue(dto.capacity());
    }

    private void clearForm() {
        editingId = null; codeField.clear(); nameField.clear();
        typeCombo.getSelectionModel().clearSelection();
        capacitySpinner.getValueFactory().setValue(30);
        roomTable.getSelectionModel().clearSelection();
    }

    private void showMessage(String msg, boolean error) {
        messageLabel.setText(msg);
        messageLabel.getStyleClass().removeAll("error", "success");
        messageLabel.getStyleClass().add(error ? "error" : "success");
    }
}
