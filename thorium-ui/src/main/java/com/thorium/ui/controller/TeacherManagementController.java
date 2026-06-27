package com.thorium.ui.controller;

import com.thorium.application.dto.ClassStreamDto;
import com.thorium.application.dto.SubjectDto;
import com.thorium.application.dto.TeacherDto;
import com.thorium.application.dto.TeachingAssignmentDto;
import com.thorium.domain.model.LessonDuration;
import com.thorium.ui.di.AppContext;
import com.thorium.ui.util.IconUtil;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;

public class TeacherManagementController {

    @FXML private TableView<TeacherDto> teacherTable;
    @FXML private TextField searchField;
    @FXML private TableColumn<TeacherDto, String> codeColumn;
    @FXML private TableColumn<TeacherDto, String> nameColumn;
    @FXML private TextField codeField;
    @FXML private TextField nameField;
    @FXML private CheckBox activeCheck;
    @FXML private Label messageLabel;
    @FXML private Button saveBtn;
    @FXML private Button deleteBtn;
    @FXML private Button clearBtn;
    @FXML private Button newLessonBtn;
    @FXML private TableView<TeachingAssignmentDto> lessonTable;
    @FXML private TableColumn<TeachingAssignmentDto, String> lessonSubjectColumn;
    @FXML private TableColumn<TeachingAssignmentDto, String> lessonClassColumn;
    @FXML private TableColumn<TeachingAssignmentDto, Number> lessonCountColumn;
    @FXML private TableColumn<TeachingAssignmentDto, String> lessonDurationColumn;
    @FXML private TableColumn<TeachingAssignmentDto, Void> lessonDeleteColumn;

    private Long editingId;
    private final javafx.collections.ObservableList<TeacherDto> masterData = FXCollections.observableArrayList();
    private FilteredList<TeacherDto> filteredItems;
    private final javafx.collections.ObservableList<TeachingAssignmentDto> lessonData = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        IconUtil.addIcon(saveBtn, IconUtil.SAVE, "#16a34a");
        IconUtil.addIcon(deleteBtn, IconUtil.DELETE, "#dc2626");
        IconUtil.addIcon(clearBtn, IconUtil.CLEAR, "#64748b");
        IconUtil.addIcon(newLessonBtn, IconUtil.CHECK, "#ffffff");

        codeColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().code()));
        nameColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().name()));
        activeCheck.setSelected(true);

        lessonSubjectColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().subjectName()));
        lessonClassColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().classStreamName()));
        lessonCountColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().lessonsPerWeek()));
        lessonDurationColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().duration().name()));

        lessonDeleteColumn.setCellFactory(col -> new TableCell<>() {
            private final Button delBtn = new Button("X");
            {
                delBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #dc2626; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 2 6;");
                delBtn.setOnAction(e -> {
                    TeachingAssignmentDto item = getTableView().getItems().get(getIndex());
                    if (item != null) deleteLesson(item);
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : delBtn);
            }
        });

        filteredItems = new FilteredList<>(masterData, p -> true);
        teacherTable.setItems(filteredItems);
        lessonTable.setItems(lessonData);
        refreshTable();
        teacherTable.getSelectionModel().selectedItemProperty().addListener((obs, o, selected) -> {
            if (selected != null) {
                populateForm(selected);
                loadLessons(selected.id());
            } else {
                lessonData.clear();
            }
        });
        searchField.textProperty().addListener((obs, old, search) -> {
            if (filteredItems != null) {
                filteredItems.setPredicate(dto -> {
                    if (search == null || search.isBlank()) return true;
                    String q = search.toLowerCase();
                    return (dto.name() != null && dto.name().toLowerCase().contains(q))
                        || (dto.code() != null && dto.code().toLowerCase().contains(q));
                });
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
            lessonData.clear();
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

    @FXML
    private void onNewLesson() {
        TeacherDto teacher = teacherTable.getSelectionModel().getSelectedItem();
        if (teacher == null) {
            showMessage("Select a teacher first", true);
            return;
        }
        showLessonDialog(null, teacher.id(), teacher.name());
    }

    private void deleteLesson(TeachingAssignmentDto lesson) {
        try {
            AppContext.get().assignmentManagementUseCase().delete(lesson.id());
            loadLessons(lesson.teacherId());
            showMessage("Lesson deleted", false);
        } catch (Exception e) {
            showMessage("Failed to delete lesson: " + e.getMessage(), true);
        }
    }

    private void loadLessons(Long teacherId) {
        lessonData.setAll(AppContext.get().assignmentManagementUseCase().findByTeacherId(teacherId));
    }

    private void showLessonDialog(TeachingAssignmentDto existing, Long teacherId, String teacherName) {
        Dialog<TeachingAssignmentDto> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "New Lesson" : "Edit Lesson");
        dialog.initModality(Modality.APPLICATION_MODAL);

        ComboBox<SubjectDto> subjectCombo = new ComboBox<>();
        subjectCombo.setPrefWidth(250);
        var teacherSubjects = AppContext.get().teacherSubjectManagementUseCase().findSubjectsByTeacherId(teacherId);
        if (teacherSubjects.isEmpty()) {
            teacherSubjects = AppContext.get().subjectManagementUseCase().findAll();
        }
        subjectCombo.setItems(FXCollections.observableArrayList(teacherSubjects));
        subjectCombo.setCellFactory(lv -> new ListCell<SubjectDto>() {
            @Override protected void updateItem(SubjectDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name() + " (" + item.code() + ")");
            }
        });
        subjectCombo.setButtonCell(new ListCell<SubjectDto>() {
            @Override protected void updateItem(SubjectDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name() + " (" + item.code() + ")");
            }
        });

        ComboBox<ClassStreamDto> classCombo = new ComboBox<>();
        classCombo.setPrefWidth(250);
        classCombo.setItems(FXCollections.observableArrayList(AppContext.get().classStreamManagementUseCase().findAll()));
        classCombo.setCellFactory(lv -> new ListCell<ClassStreamDto>() {
            @Override protected void updateItem(ClassStreamDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayName());
            }
        });
        classCombo.setButtonCell(new ListCell<ClassStreamDto>() {
            @Override protected void updateItem(ClassStreamDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayName());
            }
        });

        Spinner<Integer> lessonsSpinner = new Spinner<>(1, 10, 5);

        ComboBox<String> durationCombo = new ComboBox<>();
        durationCombo.getItems().addAll("SINGLE", "DOUBLE");
        durationCombo.getSelectionModel().select("SINGLE");

        if (existing != null) {
            for (SubjectDto s : subjectCombo.getItems()) {
                if (s.id().equals(existing.subjectId())) { subjectCombo.getSelectionModel().select(s); break; }
            }
            for (ClassStreamDto c : classCombo.getItems()) {
                if (c.id().equals(existing.classStreamId())) { classCombo.getSelectionModel().select(c); break; }
            }
            lessonsSpinner.getValueFactory().setValue(existing.lessonsPerWeek());
            durationCombo.getSelectionModel().select(existing.duration().name());
        }

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.add(new Label("Subject:"), 0, 0); grid.add(subjectCombo, 1, 0);
        grid.add(new Label("Class:"), 0, 1); grid.add(classCombo, 1, 1);
        grid.add(new Label("Lessons/Week:"), 0, 2); grid.add(lessonsSpinner, 1, 2);
        grid.add(new Label("Duration:"), 0, 3); grid.add(durationCombo, 1, 3);
        Label teacherLabel = new Label("Teacher: " + teacherName);
        teacherLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569;");
        grid.add(teacherLabel, 0, 4, 2, 1);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                SubjectDto subj = subjectCombo.getValue();
                ClassStreamDto cls = classCombo.getValue();
                if (subj == null || cls == null) return null;
                LessonDuration dur = "DOUBLE".equals(durationCombo.getValue()) ? LessonDuration.DOUBLE : LessonDuration.SINGLE;
                return new TeachingAssignmentDto(
                        existing != null ? existing.id() : null,
                        teacherId, teacherName,
                        subj.id(), subj.name(),
                        cls.id(), cls.displayName(),
                        lessonsSpinner.getValue(), dur
                );
            }
            return null;
        });

        dialog.showAndWait().ifPresent(dto -> {
            try {
                if (dto.id() == null) {
                    AppContext.get().assignmentManagementUseCase().create(dto);
                    showMessage("Lesson added", false);
                } else {
                    AppContext.get().assignmentManagementUseCase().update(dto);
                    showMessage("Lesson updated", false);
                }
                loadLessons(teacherId);
            } catch (Exception e) {
                showMessage("Failed to save lesson: " + e.getMessage(), true);
            }
        });
    }

    private void refreshTable() {
        masterData.setAll(AppContext.get().teacherManagementUseCase().findAll());
    }

    private void populateForm(TeacherDto dto) {
        editingId = dto.id();
        codeField.setText(dto.code());
        nameField.setText(dto.name());
        activeCheck.setSelected(dto.active());
    }

    private void clearForm() {
        editingId = null;
        codeField.clear();
        nameField.clear();
        activeCheck.setSelected(true);
        teacherTable.getSelectionModel().clearSelection();
        lessonData.clear();
    }

    private void showMessage(String message, boolean error) {
        messageLabel.setText(message);
        messageLabel.getStyleClass().removeAll("error", "success");
        messageLabel.getStyleClass().add(error ? "error" : "success");
    }
}
