package com.thorium.ui.controller;

import com.thorium.application.dto.SubjectDto;
import com.thorium.ui.di.AppContext;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class SubjectManagementController {

    @FXML private TableView<SubjectDto> subjectTable;
    @FXML private TableColumn<SubjectDto, String> codeColumn;
    @FXML private TableColumn<SubjectDto, String> nameColumn;
    @FXML private TableColumn<SubjectDto, Boolean> examinableColumn;
    @FXML private TextField codeField;
    @FXML private TextField nameField;
    @FXML private CheckBox examinableCheck;
    @FXML private Spinner<Integer> cbcLessonsSpinner;
    @FXML private CheckBox doublePeriodCheck;
    @FXML private CheckBox requiresDoubleCheck;
    @FXML private Label messageLabel;

    private Long editingId;

    @FXML
    private void initialize() {
        codeColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().code()));
        nameColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().name()));
        examinableColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().examinable()));
        cbcLessonsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10, 5));
        refreshTable();
        subjectTable.getSelectionModel().selectedItemProperty().addListener((obs, o, s) -> {
            if (s != null) populateForm(s);
        });
    }

    @FXML private void onSave() {
        try {
            SubjectDto dto = new SubjectDto(editingId, codeField.getText().trim(), nameField.getText().trim(),
                    examinableCheck.isSelected(), cbcLessonsSpinner.getValue(), doublePeriodCheck.isSelected(),
                    requiresDoubleCheck.isSelected());
            if (editingId == null) AppContext.get().subjectManagementUseCase().create(dto);
            else AppContext.get().subjectManagementUseCase().update(dto);
            clearForm(); refreshTable(); showMessage("Saved", false);
        } catch (IllegalArgumentException | IllegalStateException e) { showMessage(e.getMessage(), true); }
        catch (Exception e) { showMessage("An unexpected error occurred", true); }
    }

    @FXML private void onDelete() {
        SubjectDto selected = subjectTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showMessage("Select a subject", true); return; }
        try {
            AppContext.get().subjectManagementUseCase().delete(selected.id());
            clearForm(); refreshTable(); showMessage("Deleted", false);
        } catch (IllegalArgumentException | IllegalStateException e) { showMessage(e.getMessage(), true); }
        catch (Exception e) { showMessage("An unexpected error occurred", true); }
    }

    @FXML private void onClear() { clearForm(); }

    private void refreshTable() {
        subjectTable.setItems(FXCollections.observableArrayList(AppContext.get().subjectManagementUseCase().findAll()));
    }

    private void populateForm(SubjectDto dto) {
        editingId = dto.id();
        codeField.setText(dto.code()); nameField.setText(dto.name());
        examinableCheck.setSelected(dto.examinable());
        cbcLessonsSpinner.getValueFactory().setValue(dto.cbcDefaultLessons());
        doublePeriodCheck.setSelected(dto.allowsDoublePeriod());
        requiresDoubleCheck.setSelected(dto.requiresDoublePeriod());
    }

    private void clearForm() {
        editingId = null; codeField.clear(); nameField.clear();
        examinableCheck.setSelected(false); cbcLessonsSpinner.getValueFactory().setValue(5);
        doublePeriodCheck.setSelected(false); requiresDoubleCheck.setSelected(false);
        subjectTable.getSelectionModel().clearSelection();
    }

    private void showMessage(String msg, boolean error) {
        messageLabel.setText(msg);
        messageLabel.getStyleClass().removeAll("error", "success");
        messageLabel.getStyleClass().add(error ? "error" : "success");
    }
}
