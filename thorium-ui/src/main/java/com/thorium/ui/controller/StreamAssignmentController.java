package com.thorium.ui.controller;

import com.thorium.application.dto.*;
import com.thorium.domain.model.LessonDuration;
import com.thorium.ui.di.AppContext;
import com.thorium.ui.util.IconUtil;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.*;
import java.util.stream.Collectors;

public class StreamAssignmentController {

    @FXML private ComboBox<TeacherDto> teacherCombo;
    @FXML private VBox assignmentContainer;
    @FXML private Label messageLabel;
    @FXML private Label noSubjectsLabel;
    @FXML private VBox actionCard;
    @FXML private Button assignBtn;
    @FXML private Button refreshBtn;

    private Long currentTeacherId;
    private List<SubjectDto> teacherSubjects = List.of();
    private List<ClassStreamDto> allClassStreams = List.of();
    private final Map<String, CheckBox> streamCheckBoxes = new HashMap<>();
    private String currentSubjectName = "";

    @FXML
    private void initialize() {
        IconUtil.addIcon(assignBtn, IconUtil.SAVE, "#ffffff");
        IconUtil.addIcon(refreshBtn, IconUtil.REFRESH, "#475569");

        allClassStreams = AppContext.get().classStreamManagementUseCase().findAll();

        var teachers = AppContext.get().teacherManagementUseCase().findAll();
        teacherCombo.setItems(FXCollections.observableArrayList(teachers));
        teacherCombo.setCellFactory(lv -> new ListCell<TeacherDto>() {
            @Override protected void updateItem(TeacherDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name() + " (" + item.code() + ")");
            }
        });

        teacherCombo.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null) {
                currentTeacherId = selected.id();
                loadTeacherSubjects(selected.id());
            } else {
                currentTeacherId = null;
                assignmentContainer.getChildren().clear();
                noSubjectsLabel.setVisible(true);
                actionCard.setVisible(false);
                actionCard.setManaged(false);
            }
        });

        noSubjectsLabel.setVisible(true);
        actionCard.setVisible(false);
        actionCard.setManaged(false);
    }

    private void loadTeacherSubjects(Long teacherId) {
        teacherSubjects = AppContext.get().teacherSubjectManagementUseCase().findSubjectsByTeacherId(teacherId);
        if (teacherSubjects.isEmpty()) {
            noSubjectsLabel.setText("This teacher has no subjects assigned. Go to Teacher Subjects view to assign subjects first.");
            noSubjectsLabel.setVisible(true);
            assignmentContainer.getChildren().clear();
            actionCard.setVisible(false);
            actionCard.setManaged(false);
            return;
        }
        noSubjectsLabel.setVisible(false);
        Map<Integer, List<ClassStreamDto>> streamsByForm = allClassStreams.stream()
                .collect(Collectors.groupingBy(ClassStreamDto::form));

        Set<String> existingAssignments = AppContext.get().assignmentManagementUseCase()
                .findByTeacherId(teacherId).stream()
                .map(a -> a.subjectId() + "|" + a.classStreamId())
                .collect(Collectors.toSet());

        assignmentContainer.getChildren().clear();
        streamCheckBoxes.clear();

        for (SubjectDto subject : teacherSubjects) {
            VBox subjectCard = createSubjectCard(subject, streamsByForm, existingAssignments);
            assignmentContainer.getChildren().add(subjectCard);
        }

        actionCard.setVisible(true);
        actionCard.setManaged(true);
    }

    private VBox createSubjectCard(SubjectDto subject,
                                    Map<Integer, List<ClassStreamDto>> streamsByForm,
                                    Set<String> existingAssignments) {
        VBox card = new VBox(0);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(0));

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

        List<Integer> sortedForms = new ArrayList<>(streamsByForm.keySet());
        Collections.sort(sortedForms);

        for (Integer form : sortedForms) {
            List<ClassStreamDto> streams = streamsByForm.get(form);
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
                    cb.setDisable(false);
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
        if (currentTeacherId == null) {
            showMessage("Select a teacher first", true);
            return;
        }

        try {
            Set<String> currentlyAssigned = AppContext.get().assignmentManagementUseCase()
                    .findByTeacherId(currentTeacherId).stream()
                    .map(a -> a.subjectId() + "|" + a.classStreamId())
                    .collect(Collectors.toSet());

            for (Map.Entry<String, CheckBox> entry : streamCheckBoxes.entrySet()) {
                String key = entry.getKey();
                CheckBox cb = entry.getValue();

                if (cb.isSelected() && !currentlyAssigned.contains(key)) {
                    Object[] data = (Object[]) cb.getUserData();
                    Long subjectId = (Long) data[0];
                    Long classStreamId = (Long) data[1];
                    SubjectDto subject = teacherSubjects.stream()
                            .filter(s -> s.id().equals(subjectId))
                            .findFirst().orElse(null);
                    int lessonsPerWeek = subject != null ? subject.cbcDefaultLessons() : 5;

                    TeachingAssignmentDto dto = new TeachingAssignmentDto(
                            null, currentTeacherId, "",
                            subjectId, "",
                            classStreamId, "",
                            lessonsPerWeek, LessonDuration.SINGLE
                    );
                    AppContext.get().assignmentManagementUseCase().create(dto);
                } else if (!cb.isSelected() && currentlyAssigned.contains(key)) {
                    Object[] data = (Object[]) cb.getUserData();
                    Long subjectId = (Long) data[0];
                    Long classStreamId = (Long) data[1];
                    var assignment = AppContext.get().assignmentManagementUseCase().findByTeacherId(currentTeacherId)
                            .stream()
                            .filter(a -> a.subjectId().equals(subjectId) && a.classStreamId().equals(classStreamId))
                            .findFirst();
                    assignment.ifPresent(a -> AppContext.get().assignmentManagementUseCase().delete(a.id()));
                }
            }

            showMessage("Assignments saved successfully", false);
            loadTeacherSubjects(currentTeacherId);
        } catch (IllegalArgumentException | IllegalStateException e) {
            showMessage(e.getMessage(), true);
        } catch (Exception e) {
            showMessage("An unexpected error occurred", true);
        }
    }

    @FXML
    private void onRefresh() {
        if (currentTeacherId != null) {
            loadTeacherSubjects(currentTeacherId);
        }
    }

    private void showMessage(String msg, boolean error) {
        messageLabel.setText(msg);
        messageLabel.getStyleClass().removeAll("msg-error", "msg-success");
        messageLabel.getStyleClass().add(error ? "msg-error" : "msg-success");
    }
}
