package com.thorium.ui.controller;

import com.thorium.application.dto.*;
import com.thorium.domain.model.LessonDuration;
import com.thorium.ui.di.AppContext;
import com.thorium.ui.util.IconUtil;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.*;
import java.util.stream.Collectors;

public class AssignmentManagementController {

    // Step 1: Teacher → Subject
    @FXML private ComboBox<TeacherDto> step1TeacherCombo;
    @FXML private FlowPane subjectCheckboxPane;
    @FXML private Label step1Message;
    @FXML private Label noTeacherStep1Label;
    @FXML private Button saveSubjectsBtn;

    // Step 2: Teacher + Subject → Streams
    @FXML private ComboBox<TeacherDto> step2TeacherCombo;
    @FXML private VBox streamAssignmentContainer;
    @FXML private Label step2Message;
    @FXML private Label noSubjectsStep2Label;
    @FXML private VBox step2ActionBar;
    @FXML private Button assignBtn;
    @FXML private Button refreshStep2Btn;

    private Long currentTeacherId;
    private final Map<Long, CheckBox> subjectCheckboxes = new HashMap<>();
    private List<SubjectDto> allSubjects = List.of();

    private Long step2TeacherId;
    private List<SubjectDto> step2Subjects = List.of();
    private List<ClassStreamDto> allClassStreams = List.of();
    private final Map<String, CheckBox> streamCheckBoxes = new HashMap<>();

    @FXML
    private void initialize() {
        IconUtil.addIcon(saveSubjectsBtn, IconUtil.SAVE, "#ffffff");
        IconUtil.addIcon(assignBtn, IconUtil.SAVE, "#ffffff");
        IconUtil.addIcon(refreshStep2Btn, IconUtil.REFRESH, "#475569");

        allSubjects = AppContext.get().subjectManagementUseCase().findAll();
        allClassStreams = AppContext.get().classStreamManagementUseCase().findAll();
        buildSubjectCheckboxes();

        var teachers = AppContext.get().teacherManagementUseCase().findAll();

        setupTeacherCombo(step1TeacherCombo, teachers, (obs, old, selected) -> {
            if (selected != null) {
                currentTeacherId = selected.id();
                loadAssignedSubjects(selected.id());
                noTeacherStep1Label.setVisible(false);
                subjectCheckboxPane.setVisible(true);
            } else {
                currentTeacherId = null;
                clearCheckboxes();
                noTeacherStep1Label.setVisible(true);
                subjectCheckboxPane.setVisible(false);
            }
        });

        setupTeacherCombo(step2TeacherCombo, teachers, (obs, old, selected) -> {
            if (selected != null) {
                step2TeacherId = selected.id();
                loadTeacherSubjectsForStep2(selected.id());
            } else {
                step2TeacherId = null;
                streamAssignmentContainer.getChildren().clear();
                noSubjectsStep2Label.setVisible(true);
                step2ActionBar.setVisible(false);
                step2ActionBar.setManaged(false);
            }
        });

        noTeacherStep1Label.setVisible(true);
        subjectCheckboxPane.setVisible(false);
        noSubjectsStep2Label.setVisible(true);
        step2ActionBar.setVisible(false);
        step2ActionBar.setManaged(false);
    }

    private void setupTeacherCombo(ComboBox<TeacherDto> combo, List<TeacherDto> teachers,
                                    javafx.beans.value.ChangeListener<TeacherDto> listener) {
        combo.setItems(FXCollections.observableArrayList(teachers));
        combo.setCellFactory(lv -> new ListCell<TeacherDto>() {
            @Override protected void updateItem(TeacherDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name() + " (" + item.code() + ")");
            }
        });
        combo.getSelectionModel().selectedItemProperty().addListener(listener);
    }

    // ========== STEP 1: Teacher → Subject ==========

    private void buildSubjectCheckboxes() {
        subjectCheckboxes.clear();
        subjectCheckboxPane.getChildren().clear();
        for (SubjectDto s : allSubjects) {
            CheckBox cb = new CheckBox(s.name() + " (" + s.code() + ")");
            cb.setUserData(s.id());
            cb.getStyleClass().add("subject-checkbox");
            subjectCheckboxes.put(s.id(), cb);
            subjectCheckboxPane.getChildren().add(cb);
        }
    }

    private void loadAssignedSubjects(Long teacherId) {
        clearCheckboxes();
        var assigned = AppContext.get().teacherSubjectManagementUseCase().findByTeacherId(teacherId);
        Set<Long> assignedIds = assigned.stream().map(ts -> ts.subjectId()).collect(Collectors.toSet());
        for (Map.Entry<Long, CheckBox> entry : subjectCheckboxes.entrySet()) {
            if (assignedIds.contains(entry.getKey())) {
                entry.getValue().setSelected(true);
            }
        }
    }

    private void clearCheckboxes() {
        for (CheckBox cb : subjectCheckboxes.values()) {
            cb.setSelected(false);
        }
    }

    @FXML
    private void onSaveSubjects() {
        if (currentTeacherId == null) {
            showStep1Message("Select a teacher first", true);
            return;
        }
        try {
            Set<Long> currentlyAssigned = AppContext.get().teacherSubjectManagementUseCase()
                    .findByTeacherId(currentTeacherId).stream()
                    .map(ts -> ts.subjectId())
                    .collect(Collectors.toSet());

            Set<Long> selected = new HashSet<>();
            for (CheckBox cb : subjectCheckboxes.values()) {
                if (cb.isSelected()) selected.add((Long) cb.getUserData());
            }

            for (Long subjectId : selected) {
                if (!currentlyAssigned.contains(subjectId)) {
                    AppContext.get().teacherSubjectManagementUseCase().assign(currentTeacherId, subjectId);
                }
            }
            for (Long subjectId : currentlyAssigned) {
                if (!selected.contains(subjectId)) {
                    AppContext.get().teacherSubjectManagementUseCase().unassign(currentTeacherId, subjectId);
                }
            }

            showStep1Message("Subjects saved for teacher", false);
            syncStep2Teacher();
        } catch (IllegalArgumentException | IllegalStateException e) {
            showStep1Message(e.getMessage(), true);
        } catch (Exception e) {
            showStep1Message("An unexpected error occurred", true);
        }
    }

    private void showStep1Message(String msg, boolean error) {
        step1Message.setText(msg);
        step1Message.getStyleClass().removeAll("msg-error", "msg-success");
        step1Message.getStyleClass().add(error ? "msg-error" : "msg-success");
    }

    // ========== STEP 2: Teacher + Subject → Streams ==========

    private void loadTeacherSubjectsForStep2(Long teacherId) {
        step2Subjects = AppContext.get().teacherSubjectManagementUseCase().findSubjectsByTeacherId(teacherId);
        if (step2Subjects.isEmpty()) {
            noSubjectsStep2Label.setText("This teacher has no subjects assigned. Use Step 1 to assign subjects first.");
            noSubjectsStep2Label.setVisible(true);
            streamAssignmentContainer.getChildren().clear();
            step2ActionBar.setVisible(false);
            step2ActionBar.setManaged(false);
            return;
        }
        noSubjectsStep2Label.setVisible(false);

        Map<Integer, List<ClassStreamDto>> streamsByForm = allClassStreams.stream()
                .collect(Collectors.groupingBy(ClassStreamDto::form));

        Set<String> existingAssignments = AppContext.get().assignmentManagementUseCase()
                .findByTeacherId(teacherId).stream()
                .map(a -> a.subjectId() + "|" + a.classStreamId())
                .collect(Collectors.toSet());

        streamAssignmentContainer.getChildren().clear();
        streamCheckBoxes.clear();

        List<Integer> sortedForms = new ArrayList<>(streamsByForm.keySet());
        Collections.sort(sortedForms);

        for (SubjectDto subject : step2Subjects) {
            VBox subjectCard = createSubjectCard(subject, streamsByForm, sortedForms, existingAssignments);
            streamAssignmentContainer.getChildren().add(subjectCard);
        }

        step2ActionBar.setVisible(true);
        step2ActionBar.setManaged(true);
    }

    private VBox createSubjectCard(SubjectDto subject,
                                    Map<Integer, List<ClassStreamDto>> streamsByForm,
                                    List<Integer> sortedForms,
                                    Set<String> existingAssignments) {
        VBox card = new VBox(0);
        card.getStyleClass().add("card");

        HBox header = new HBox(8);
        header.getStyleClass().add("card-header");
        Label subjectLabel = new Label(subject.name() + " (" + subject.code() + ")");
        subjectLabel.getStyleClass().add("card-title");
        Label lessonsLabel = new Label(subject.cbcDefaultLessons() + " lessons/week");
        lessonsLabel.getStyleClass().add("card-subtitle");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(subjectLabel, lessonsLabel, spacer);
        card.getChildren().add(header);

        VBox body = new VBox(12);
        body.getStyleClass().add("card-body");
        body.setPadding(new Insets(12));

        for (Integer form : sortedForms) {
            List<ClassStreamDto> streams = streamsByForm.get(form);
            if (streams == null) continue;

            VBox formGroup = new VBox(6);
            formGroup.getStyleClass().add("form-group");

            Label formLabel = new Label("Form " + form);
            formLabel.getStyleClass().add("form-group-title");
            formGroup.getChildren().add(formLabel);

            FlowPane streamPane = new FlowPane(10, 8);
            streamPane.getStyleClass().add("stream-flow-pane");

            for (ClassStreamDto cs : streams) {
                String key = subject.id() + "|" + cs.id();
                CheckBox cb = new CheckBox(cs.displayName());
                cb.setUserData(new Object[]{subject.id(), cs.id()});
                cb.getStyleClass().add("stream-checkbox");
                if (existingAssignments.contains(key)) {
                    cb.setSelected(true);
                }
                streamCheckBoxes.put(key, cb);
                streamPane.getChildren().add(cb);
            }
            formGroup.getChildren().add(streamPane);
            body.getChildren().add(formGroup);
        }
        card.getChildren().add(body);
        return card;
    }

    @FXML
    private void onAssign() {
        if (step2TeacherId == null) {
            showStep2Message("Select a teacher first", true);
            return;
        }
        try {
            Set<String> currentlyAssigned = AppContext.get().assignmentManagementUseCase()
                    .findByTeacherId(step2TeacherId).stream()
                    .map(a -> a.subjectId() + "|" + a.classStreamId())
                    .collect(Collectors.toSet());

            for (Map.Entry<String, CheckBox> entry : streamCheckBoxes.entrySet()) {
                String key = entry.getKey();
                CheckBox cb = entry.getValue();
                Object[] data = (Object[]) cb.getUserData();
                Long subjectId = (Long) data[0];
                Long classStreamId = (Long) data[1];

                if (cb.isSelected() && !currentlyAssigned.contains(key)) {
                    SubjectDto subject = step2Subjects.stream()
                            .filter(s -> s.id().equals(subjectId))
                            .findFirst().orElse(null);
                    int lessonsPerWeek = subject != null ? subject.cbcDefaultLessons() : 5;

                    TeachingAssignmentDto dto = new TeachingAssignmentDto(
                            null, step2TeacherId, "",
                            subjectId, "",
                            classStreamId, "",
                            lessonsPerWeek, LessonDuration.SINGLE
                    );
                    AppContext.get().assignmentManagementUseCase().create(dto);
                } else if (!cb.isSelected() && currentlyAssigned.contains(key)) {
                    var assignment = AppContext.get().assignmentManagementUseCase().findByTeacherId(step2TeacherId)
                            .stream()
                            .filter(a -> a.subjectId().equals(subjectId) && a.classStreamId().equals(classStreamId))
                            .findFirst();
                    assignment.ifPresent(a -> AppContext.get().assignmentManagementUseCase().delete(a.id()));
                }
            }

            showStep2Message("Assignments saved successfully", false);
            loadTeacherSubjectsForStep2(step2TeacherId);
        } catch (IllegalArgumentException | IllegalStateException e) {
            showStep2Message(e.getMessage(), true);
        } catch (Exception e) {
            showStep2Message("An unexpected error occurred", true);
        }
    }

    @FXML
    private void onRefreshStep2() {
        if (step2TeacherId != null) {
            loadTeacherSubjectsForStep2(step2TeacherId);
        }
    }

    private void syncStep2Teacher() {
        TeacherDto selected = step2TeacherCombo.getSelectionModel().getSelectedItem();
        if (selected != null) {
            loadTeacherSubjectsForStep2(selected.id());
        }
    }

    private void showStep2Message(String msg, boolean error) {
        step2Message.setText(msg);
        step2Message.getStyleClass().removeAll("msg-error", "msg-success");
        step2Message.getStyleClass().add(error ? "msg-error" : "msg-success");
    }
}
