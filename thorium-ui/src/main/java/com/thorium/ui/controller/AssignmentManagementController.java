package com.thorium.ui.controller;

import com.thorium.application.dto.*;
import com.thorium.ui.di.AppContext;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class AssignmentManagementController {

    @FXML private TableView<TeachingAssignmentDto> assignmentTable;
    @FXML private TableColumn<TeachingAssignmentDto, String> teacherColumn;
    @FXML private TableColumn<TeachingAssignmentDto, String> subjectColumn;
    @FXML private TableColumn<TeachingAssignmentDto, String> classColumn;
    @FXML private TableColumn<TeachingAssignmentDto, Number> lessonsColumn;
    @FXML private ComboBox<TeacherDto> teacherCombo;
    @FXML private ComboBox<SubjectDto> subjectCombo;
    @FXML private ComboBox<ClassStreamDto> classCombo;
    @FXML private Spinner<Integer> lessonsSpinner;
    @FXML private Label messageLabel;

    private Long editingId;

    @FXML
    private void initialize() {
        teacherColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().teacherName()));
        subjectColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().subjectName()));
        classColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().classStreamName()));
        lessonsColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().lessonsPerWeek()));
        lessonsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10, 5));
        loadCombos();
        refreshTable();
        assignmentTable.getSelectionModel().selectedItemProperty().addListener((obs, o, s) -> {
            if (s != null) populateForm(s);
        });
    }

    @FXML private void onSave() {
        try {
            TeachingAssignmentDto dto = new TeachingAssignmentDto(editingId,
                    teacherCombo.getValue().id(), teacherCombo.getValue().name(),
                    subjectCombo.getValue().id(), subjectCombo.getValue().name(),
                    classCombo.getValue().id(), classCombo.getValue().displayName(),
                    lessonsSpinner.getValue());
            if (editingId == null) AppContext.get().assignmentManagementUseCase().create(dto);
            else AppContext.get().assignmentManagementUseCase().update(dto);
            clearForm(); refreshTable(); showMessage("Saved", false);
        } catch (IllegalArgumentException | IllegalStateException e) { showMessage(e.getMessage(), true); }
        catch (Exception e) { showMessage("An unexpected error occurred", true); }
    }

    @FXML private void onDelete() {
        TeachingAssignmentDto selected = assignmentTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showMessage("Select an assignment", true); return; }
        try {
            AppContext.get().assignmentManagementUseCase().delete(selected.id());
            clearForm(); refreshTable(); showMessage("Deleted", false);
        } catch (IllegalArgumentException | IllegalStateException e) { showMessage(e.getMessage(), true); }
        catch (Exception e) { showMessage("An unexpected error occurred", true); }
    }

    @FXML private void onClear() { clearForm(); }

    private void loadCombos() {
        teacherCombo.setItems(FXCollections.observableArrayList(AppContext.get().teacherManagementUseCase().findAll()));
        teacherCombo.setCellFactory(lv -> new ListCell<TeacherDto>() {
            @Override protected void updateItem(TeacherDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name() + " (" + item.code() + ")");
            }
        });
        subjectCombo.setItems(FXCollections.observableArrayList(AppContext.get().subjectManagementUseCase().findAll()));
        subjectCombo.setCellFactory(lv -> new ListCell<SubjectDto>() {
            @Override protected void updateItem(SubjectDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name() + " (" + item.code() + ")");
            }
        });
        classCombo.setItems(FXCollections.observableArrayList(AppContext.get().classStreamManagementUseCase().findAll()));
        classCombo.setCellFactory(lv -> new ListCell<ClassStreamDto>() {
            @Override protected void updateItem(ClassStreamDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayName());
            }
        });
    }

    private void refreshTable() {
        assignmentTable.setItems(FXCollections.observableArrayList(AppContext.get().assignmentManagementUseCase().findAll()));
    }

    private void populateForm(TeachingAssignmentDto dto) {
        editingId = dto.id();
        selectCombo(teacherCombo, dto.teacherId());
        selectCombo(subjectCombo, dto.subjectId());
        selectCombo(classCombo, dto.classStreamId());
        lessonsSpinner.getValueFactory().setValue(dto.lessonsPerWeek());
    }

    private <T> void selectCombo(ComboBox<T> combo, Long id) {
        for (T item : combo.getItems()) {
            Long itemId = switch (item) {
                case TeacherDto t -> t.id();
                case SubjectDto s -> s.id();
                case ClassStreamDto c -> c.id();
                default -> null;
            };
            if (id.equals(itemId)) { combo.getSelectionModel().select(item); return; }
        }
    }

    private void clearForm() {
        editingId = null;
        teacherCombo.getSelectionModel().clearSelection();
        subjectCombo.getSelectionModel().clearSelection();
        classCombo.getSelectionModel().clearSelection();
        lessonsSpinner.getValueFactory().setValue(5);
        assignmentTable.getSelectionModel().clearSelection();
    }

    private void showMessage(String msg, boolean error) {
        messageLabel.setText(msg);
        messageLabel.getStyleClass().removeAll("error", "success");
        messageLabel.getStyleClass().add(error ? "error" : "success");
    }
}
