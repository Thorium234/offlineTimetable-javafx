package com.thorium.ui.controller;

import com.thorium.application.dto.*;
import com.thorium.domain.model.LessonDuration;
import com.thorium.ui.di.AppContext;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;
import java.util.stream.Collectors;

public class AssignmentManagementController {

    @FXML private VBox stepTeachers;
    @FXML private FlowPane teacherCardContainer;
    @FXML private VBox stepSubjects;
    @FXML private Label selectedTeacherLabel;
    @FXML private FlowPane subjectToggleContainer;
    @FXML private Button continueToStreamsBtn;
    @FXML private VBox stepStreamSubjects;
    @FXML private Label streamSubjectTeacherLabel;
    @FXML private FlowPane subjectCardContainer;
    @FXML private VBox stepStreams;
    @FXML private Label streamSubjectNameLabel;
    @FXML private ComboBox<Integer> formCombo;
    @FXML private FlowPane streamToggleContainer;
    @FXML private Button backToTeachersBtn;
    @FXML private Label messageLabel;

    private TeacherDto selectedTeacher;
    private SubjectDto selectedSubject;
    private List<TeacherDto> allTeachers = List.of();
    private List<SubjectDto> allSubjects = List.of();
    private List<ClassStreamDto> allClassStreams = List.of();
    private final Map<Long, ToggleButton> subjectToggles = new HashMap<>();
    private final Map<String, ToggleButton> streamToggles = new HashMap<>();

    @FXML
    private void initialize() {
        allTeachers = AppContext.get().teacherManagementUseCase().findAll();
        allSubjects = AppContext.get().subjectManagementUseCase().findAll();
        allClassStreams = AppContext.get().classStreamManagementUseCase().findAll();

        formCombo.getSelectionModel().selectedItemProperty().addListener((obs, old, f) -> {
            if (f != null && selectedSubject != null) {
                Set<String> assigned = AppContext.get().assignmentManagementUseCase()
                        .findByTeacherId(selectedTeacher.id()).stream()
                        .filter(a -> a.subjectId().equals(selectedSubject.id()))
                        .map(a -> a.classStreamId().toString())
                        .collect(Collectors.toSet());
                renderStreamToggles(allClassStreams.stream()
                        .filter(cs -> cs.form() == f)
                        .collect(Collectors.toList()), assigned);
            }
        });

        renderTeacherCards();
        showStep(stepTeachers);
    }

    private void renderTeacherCards() {
        teacherCardContainer.getChildren().clear();
        for (TeacherDto t : allTeachers) {
            teacherCardContainer.getChildren().add(createTeacherCard(t));
        }
    }

    private VBox createTeacherCard(TeacherDto t) {
        VBox card = new VBox(8);
        card.setPrefSize(200, 110);
        card.setMinSize(200, 110);
        card.getStyleClass().addAll("card", "teacher-card");
        card.setUserData(t);

        Label name = new Label(t.name());
        name.getStyleClass().add("card-title");
        Label code = new Label(t.code());
        code.getStyleClass().add("card-subtitle");

        card.getChildren().addAll(name, code);
        card.setOnMouseClicked(e -> onTeacherSelected(t));
        return card;
    }

    private void onTeacherSelected(TeacherDto t) {
        selectedTeacher = t;
        selectedTeacherLabel.setText("Subjects for: " + t.name() + " (" + t.code() + ")");
        streamSubjectTeacherLabel.setText("Streams for: " + t.name());
        renderSubjectToggles();
        showStep(stepSubjects);
    }

    private void renderSubjectToggles() {
        subjectToggleContainer.getChildren().clear();
        subjectToggles.clear();

        var assigned = AppContext.get().teacherSubjectManagementUseCase()
                .findByTeacherId(selectedTeacher.id());
        Set<Long> assignedIds = new HashSet<>();
        for (var ts : assigned) {
            assignedIds.add(ts.subjectId());
        }

        for (SubjectDto s : allSubjects) {
            ToggleButton tb = createSubjectToggle(s, assignedIds.contains(s.id()));
            subjectToggles.put(s.id(), tb);
            subjectToggleContainer.getChildren().add(tb);
        }
    }

    private ToggleButton createSubjectToggle(SubjectDto s, boolean assigned) {
        ToggleButton tb = new ToggleButton(s.name() + " (" + s.code() + ")");
        tb.setPrefSize(180, 70);
        tb.setMinSize(180, 70);
        tb.getStyleClass().addAll("toggle-card", "subject-toggle");
        tb.setSelected(assigned);
        tb.setUserData(s.id());
        tb.setOnAction(e -> onSubjectToggled(s, tb.isSelected()));
        return tb;
    }

    private void onSubjectToggled(SubjectDto subject, boolean selected) {
        try {
            if (selected) {
                AppContext.get().teacherSubjectManagementUseCase()
                        .assign(selectedTeacher.id(), subject.id());
            } else {
                AppContext.get().teacherSubjectManagementUseCase()
                        .unassign(selectedTeacher.id(), subject.id());
            }
        } catch (Exception e) {
            showMessage(e.getMessage(), true);
            ToggleButton tb = subjectToggles.get(subject.id());
            if (tb != null) tb.setSelected(!selected);
        }
    }

    @FXML
    private void onContinueToStreams() {
        boolean hasAssigned = subjectToggles.values().stream().anyMatch(ToggleButton::isSelected);
        if (!hasAssigned) {
            showMessage("Assign at least one subject first", true);
            return;
        }
        renderSubjectCards();
        showStep(stepStreamSubjects);
    }

    private void renderSubjectCards() {
        subjectCardContainer.getChildren().clear();
        for (SubjectDto s : allSubjects) {
            ToggleButton tb = subjectToggles.get(s.id());
            if (tb == null || !tb.isSelected()) continue;
            subjectCardContainer.getChildren().add(createSubjectCard(s));
        }
    }

    private VBox createSubjectCard(SubjectDto s) {
        VBox card = new VBox(8);
        card.setPrefSize(200, 110);
        card.setMinSize(200, 110);
        card.getStyleClass().addAll("card", "subject-card");
        card.setUserData(s);

        Label name = new Label(s.name());
        name.getStyleClass().add("card-title");
        Label code = new Label(s.code());
        code.getStyleClass().add("card-subtitle");

        card.getChildren().addAll(name, code);
        card.setOnMouseClicked(e -> onSubjectCardClicked(s));
        return card;
    }

    private void onSubjectCardClicked(SubjectDto s) {
        selectedSubject = s;
        streamSubjectNameLabel.setText("Streams for: " + s.name() + " (" + s.code() + ")");
        loadStreams(s);
        showStep(stepStreams);
    }

    private void loadStreams(SubjectDto subject) {
        Map<Integer, List<ClassStreamDto>> byForm = allClassStreams.stream()
                .collect(Collectors.groupingBy(ClassStreamDto::form));

        List<Integer> forms = new ArrayList<>(byForm.keySet());
        Collections.sort(forms);

        formCombo.setItems(FXCollections.observableArrayList(forms));
        if (!forms.isEmpty()) {
            formCombo.getSelectionModel().select(0);
        }
    }

    private void renderStreamToggles(List<ClassStreamDto> streams, Set<String> assigned) {
        streamToggleContainer.getChildren().clear();
        streamToggles.clear();

        for (ClassStreamDto cs : streams) {
            ToggleButton tb = new ToggleButton(cs.displayName());
            tb.setPrefSize(160, 60);
            tb.setMinSize(160, 60);
            tb.getStyleClass().addAll("toggle-card", "stream-toggle");
            String key = cs.id().toString();
            tb.setSelected(assigned.contains(key));
            tb.setUserData(cs.id());
            tb.setOnAction(e -> onStreamToggled(cs, tb.isSelected()));
            streamToggles.put(key, tb);
            streamToggleContainer.getChildren().add(tb);
        }
    }

    private void onStreamToggled(ClassStreamDto cs, boolean selected) {
        try {
            if (selected) {
                TeachingAssignmentDto dto = new TeachingAssignmentDto(
                        null, selectedTeacher.id(), "",
                        selectedSubject.id(), "",
                        cs.id(), "",
                        selectedSubject.cbcDefaultLessons(), LessonDuration.SINGLE
                );
                AppContext.get().assignmentManagementUseCase().create(dto);
            } else {
                var list = AppContext.get().assignmentManagementUseCase()
                        .findByTeacherId(selectedTeacher.id()).stream()
                        .filter(a -> a.subjectId().equals(selectedSubject.id())
                                && a.classStreamId().equals(cs.id()))
                        .toList();
                for (var a : list) {
                    AppContext.get().assignmentManagementUseCase().delete(a.id());
                }
            }
        } catch (Exception e) {
            showMessage(e.getMessage(), true);
            ToggleButton tb = streamToggles.get(cs.id().toString());
            if (tb != null) tb.setSelected(!selected);
        }
    }

    @FXML
    private void onBackToTeachers() {
        showStep(stepTeachers);
    }

    @FXML
    private void onBackToSubjects() {
        renderSubjectToggles();
        showStep(stepSubjects);
    }

    @FXML
    private void onBackToStreamSubjects() {
        renderSubjectCards();
        showStep(stepStreamSubjects);
    }

    private void showStep(VBox step) {
        stepTeachers.setVisible(false);
        stepTeachers.setManaged(false);
        stepSubjects.setVisible(false);
        stepSubjects.setManaged(false);
        stepStreamSubjects.setVisible(false);
        stepStreamSubjects.setManaged(false);
        stepStreams.setVisible(false);
        stepStreams.setManaged(false);
        step.setVisible(true);
        step.setManaged(true);
    }

    private void showMessage(String msg, boolean error) {
        messageLabel.setText(msg);
        messageLabel.getStyleClass().removeAll("msg-error", "msg-success");
        messageLabel.getStyleClass().add(error ? "msg-error" : "msg-success");
        messageLabel.setManaged(true);
    }
}
