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
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;

import java.util.*;
import java.util.stream.Collectors;

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

    @FXML private Button viewProfileBtn;
    @FXML private VBox normalContent;
    @FXML private VBox profileCard;
    @FXML private Label profileNameLabel;
    @FXML private FlowPane profileSubjectContainer;
    @FXML private ComboBox<SubjectDto> profileSubjectCombo;
    @FXML private VBox profileStreamSection;
    @FXML private ComboBox<Integer> profileFormCombo;
    @FXML private FlowPane profileStreamContainer;

    private Long editingId;
    private final javafx.collections.ObservableList<TeacherDto> masterData = FXCollections.observableArrayList();
    private FilteredList<TeacherDto> filteredItems;
    private final javafx.collections.ObservableList<TeachingAssignmentDto> lessonData = FXCollections.observableArrayList();

    private TeacherDto profileTeacher;
    private SubjectDto profileSelectedSubject;
    private List<SubjectDto> profileAllSubjects = List.of();
    private List<ClassStreamDto> profileAllStreams = List.of();
    private final Map<Long, ToggleButton> profileSubjectToggles = new HashMap<>();
    private final Map<String, ToggleButton> profileStreamToggles = new HashMap<>();

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
        viewProfileBtn.setDisable(true);
        refreshTable();

        teacherTable.getSelectionModel().selectedItemProperty().addListener((obs, o, selected) -> {
            if (selected != null) {
                populateForm(selected);
                loadLessons(selected.id());
                viewProfileBtn.setDisable(false);
            } else {
                lessonData.clear();
                viewProfileBtn.setDisable(true);
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

        profileSubjectCombo.setCellFactory(lv -> new ListCell<SubjectDto>() {
            @Override protected void updateItem(SubjectDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name() + " (" + item.code() + ")");
            }
        });
        profileSubjectCombo.setButtonCell(new ListCell<SubjectDto>() {
            @Override protected void updateItem(SubjectDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name() + " (" + item.code() + ")");
            }
        });
        profileSubjectCombo.getSelectionModel().selectedItemProperty().addListener((obs, old, s) -> {
            if (s != null) {
                profileSelectedSubject = s;
                profileStreamSection.setVisible(true);
                profileStreamSection.setManaged(true);
                loadProfileStreams(s);
            } else {
                profileStreamSection.setVisible(false);
                profileStreamSection.setManaged(false);
            }
        });

        profileFormCombo.getSelectionModel().selectedItemProperty().addListener((obs, old, f) -> {
            if (f != null && profileSelectedSubject != null) {
                Set<String> assigned = AppContext.get().assignmentManagementUseCase()
                        .findByTeacherId(profileTeacher.id()).stream()
                        .filter(a -> a.subjectId().equals(profileSelectedSubject.id()))
                        .map(a -> a.classStreamId().toString())
                        .collect(Collectors.toSet());
                renderProfileStreams(profileAllStreams.stream()
                        .filter(cs -> cs.form() == f)
                        .collect(Collectors.toList()), assigned);
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

    @FXML
    private void onViewProfile() {
        TeacherDto selected = teacherTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showMessage("Select a teacher first", true);
            return;
        }
        profileTeacher = selected;
        profileAllSubjects = AppContext.get().subjectManagementUseCase().findAll();
        profileAllStreams = AppContext.get().classStreamManagementUseCase().findAll();
        profileNameLabel.setText("Profile: " + profileTeacher.name() + " (" + profileTeacher.code() + ")");
        profileSubjectCombo.getSelectionModel().clearSelection();
        profileStreamSection.setVisible(false);
        profileStreamSection.setManaged(false);
        profileSelectedSubject = null;
        renderProfileSubjects();
        normalContent.setVisible(false);
        normalContent.setManaged(false);
        profileCard.setVisible(true);
        profileCard.setManaged(true);
    }

    @FXML
    private void onBackFromProfile() {
        profileCard.setVisible(false);
        profileCard.setManaged(false);
        normalContent.setVisible(true);
        normalContent.setManaged(true);
        refreshTable();
    }

    private void renderProfileSubjects() {
        profileSubjectContainer.getChildren().clear();
        profileSubjectToggles.clear();

        var assigned = AppContext.get().teacherSubjectManagementUseCase()
                .findByTeacherId(profileTeacher.id());
        Set<Long> assignedIds = new HashSet<>();
        for (var ts : assigned) {
            assignedIds.add(ts.subjectId());
        }

        for (SubjectDto s : profileAllSubjects) {
            ToggleButton tb = createProfileSubjectToggle(s, assignedIds.contains(s.id()));
            profileSubjectToggles.put(s.id(), tb);
            profileSubjectContainer.getChildren().add(tb);
        }
        updateProfileSubjectCombo();
    }

    private ToggleButton createProfileSubjectToggle(SubjectDto s, boolean assigned) {
        ToggleButton tb = new ToggleButton(s.name() + " (" + s.code() + ")");
        tb.setPrefSize(180, 70);
        tb.setMinSize(180, 70);
        tb.getStyleClass().addAll("toggle-card", "subject-toggle");
        tb.setSelected(assigned);
        tb.setUserData(s.id());
        tb.setOnAction(e -> onProfileSubjectToggled(s, tb.isSelected()));
        return tb;
    }

    private void onProfileSubjectToggled(SubjectDto subject, boolean selected) {
        try {
            if (selected) {
                AppContext.get().teacherSubjectManagementUseCase()
                        .assign(profileTeacher.id(), subject.id());
            } else {
                AppContext.get().teacherSubjectManagementUseCase()
                        .unassign(profileTeacher.id(), subject.id());
            }
        } catch (Exception e) {
            ToggleButton tb = profileSubjectToggles.get(subject.id());
            if (tb != null) tb.setSelected(!selected);
        }
        updateProfileSubjectCombo();
        if (!selected && profileSelectedSubject != null
                && profileSelectedSubject.id().equals(subject.id())) {
            profileSubjectCombo.getSelectionModel().clearSelection();
            profileSelectedSubject = null;
            profileStreamSection.setVisible(false);
            profileStreamSection.setManaged(false);
        }
    }

    private void updateProfileSubjectCombo() {
        List<SubjectDto> assigned = new ArrayList<>();
        for (SubjectDto s : profileAllSubjects) {
            ToggleButton tb = profileSubjectToggles.get(s.id());
            if (tb != null && tb.isSelected()) {
                assigned.add(s);
            }
        }
        profileSubjectCombo.setItems(FXCollections.observableArrayList(assigned));
        if (profileSelectedSubject != null && !assigned.contains(profileSelectedSubject)) {
            profileSubjectCombo.getSelectionModel().clearSelection();
        }
    }

    private void loadProfileStreams(SubjectDto subject) {
        Map<Integer, List<ClassStreamDto>> byForm = profileAllStreams.stream()
                .collect(Collectors.groupingBy(ClassStreamDto::form));
        List<Integer> forms = new ArrayList<>(byForm.keySet());
        Collections.sort(forms);
        profileFormCombo.setItems(FXCollections.observableArrayList(forms));
        if (!forms.isEmpty()) {
            profileFormCombo.getSelectionModel().select(0);
        }
    }

    private void renderProfileStreams(List<ClassStreamDto> streams, Set<String> assigned) {
        profileStreamContainer.getChildren().clear();
        profileStreamToggles.clear();

        for (ClassStreamDto cs : streams) {
            ToggleButton tb = new ToggleButton(cs.displayName());
            tb.setPrefSize(160, 60);
            tb.setMinSize(160, 60);
            tb.getStyleClass().addAll("toggle-card", "stream-toggle");
            String key = cs.id().toString();
            tb.setSelected(assigned.contains(key));
            tb.setUserData(cs.id());
            tb.setOnAction(e -> onProfileStreamToggled(cs, tb.isSelected()));
            profileStreamToggles.put(key, tb);
            profileStreamContainer.getChildren().add(tb);
        }
    }

    private void onProfileStreamToggled(ClassStreamDto cs, boolean selected) {
        try {
            if (selected) {
                TeachingAssignmentDto dto = new TeachingAssignmentDto(
                        null, profileTeacher.id(), "",
                        profileSelectedSubject.id(), "",
                        cs.id(), "",
                        profileSelectedSubject.cbcDefaultLessons(), LessonDuration.SINGLE
                );
                AppContext.get().assignmentManagementUseCase().create(dto);
            } else {
                var list = AppContext.get().assignmentManagementUseCase()
                        .findByTeacherId(profileTeacher.id()).stream()
                        .filter(a -> a.subjectId().equals(profileSelectedSubject.id())
                                && a.classStreamId().equals(cs.id()))
                        .toList();
                for (var a : list) {
                    AppContext.get().assignmentManagementUseCase().delete(a.id());
                }
            }
        } catch (Exception e) {
            ToggleButton tb = profileStreamToggles.get(cs.id().toString());
            if (tb != null) tb.setSelected(!selected);
        }
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
