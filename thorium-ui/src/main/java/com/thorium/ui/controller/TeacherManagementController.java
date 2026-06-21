package com.thorium.ui.controller;

import com.thorium.application.dto.TeacherDto;
import com.thorium.ui.di.AppContext;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class TeacherManagementController {

    @FXML
    private TableView<TeacherDto> teacherTable;
    @FXML
    private TableColumn<TeacherDto, String> codeColumn;
    @FXML
    private TableColumn<TeacherDto, String> nameColumn;
    @FXML
    private TableColumn<TeacherDto, Number> maxDayColumn;
    @FXML
    private TableColumn<TeacherDto, Number> maxWeekColumn;
    @FXML
    private TextField codeField;
    @FXML
    private TextField nameField;
    @FXML
    private Spinner<Integer> maxDaySpinner;
    @FXML
    private Spinner<Integer> maxWeekSpinner;
    @FXML
    private CheckBox activeCheck;
    @FXML
    private Label messageLabel;

    private Long editingId;

    @FXML
    private void initialize() {
        codeColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().code()));
        nameColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().name()));
        maxDayColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().maxLessonsPerDay()));
        maxWeekColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().maxLessonsPerWeek()));
        maxDaySpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 12, 6));
        maxWeekSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 50, 30));
        activeCheck.setSelected(true);
        refreshTable();
        teacherTable.getSelectionModel().selectedItemProperty().addListener((obs, o, selected) -> {
            if (selected != null) {
                populateForm(selected);
            }
        });
    }

    @FXML
    private void onSave() {
        try {
            TeacherDto dto = new TeacherDto(
                    editingId,
                    codeField.getText().trim(),
                    nameField.getText().trim(),
                    maxDaySpinner.getValue(),
                    maxWeekSpinner.getValue(),
                    activeCheck.isSelected()
            );
            if (editingId == null) {
                AppContext.get().teacherManagementUseCase().create(dto);
                showMessage("Teacher created", false);
            } else {
                AppContext.get().teacherManagementUseCase().update(dto);
                showMessage("Teacher updated", false);
            }
            clearForm();
            refreshTable();
        } catch (IllegalArgumentException | IllegalStateException e) {
            showMessage(e.getMessage(), true);
        } catch (Exception e) {
            showMessage("An unexpected error occurred", true);
        }
    }

    @FXML
    private void onDelete() {
        TeacherDto selected = teacherTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showMessage("Select a teacher to delete", true);
            return;
        }
        try {
            AppContext.get().teacherManagementUseCase().delete(selected.id());
            clearForm();
            refreshTable();
            showMessage("Teacher deleted", false);
        } catch (IllegalArgumentException | IllegalStateException e) {
            showMessage(e.getMessage(), true);
        } catch (Exception e) {
            showMessage("An unexpected error occurred", true);
        }
    }

    @FXML
    private void onClear() {
        clearForm();
    }

    private void refreshTable() {
        teacherTable.setItems(FXCollections.observableArrayList(
                AppContext.get().teacherManagementUseCase().findAll()));
    }

    private void populateForm(TeacherDto dto) {
        editingId = dto.id();
        codeField.setText(dto.code());
        nameField.setText(dto.name());
        maxDaySpinner.getValueFactory().setValue(dto.maxLessonsPerDay());
        maxWeekSpinner.getValueFactory().setValue(dto.maxLessonsPerWeek());
        activeCheck.setSelected(dto.active());
    }

    private void clearForm() {
        editingId = null;
        codeField.clear();
        nameField.clear();
        maxDaySpinner.getValueFactory().setValue(6);
        maxWeekSpinner.getValueFactory().setValue(30);
        activeCheck.setSelected(true);
        teacherTable.getSelectionModel().clearSelection();
    }

    private void showMessage(String message, boolean error) {
        messageLabel.setText(message);
        messageLabel.getStyleClass().removeAll("error", "success");
        messageLabel.getStyleClass().add(error ? "error" : "success");
    }
}
