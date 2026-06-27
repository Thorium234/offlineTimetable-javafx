package com.thorium.ui.controller;

import com.thorium.application.dto.SubjectDto;
import com.thorium.application.dto.TeacherDto;
import com.thorium.ui.di.AppContext;
import com.thorium.ui.util.IconUtil;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;

import java.util.*;
import java.util.stream.Collectors;

public class TeacherSubjectController {

    @FXML private ComboBox<TeacherDto> teacherCombo;
    @FXML private FlowPane subjectCheckboxPane;
    @FXML private Label messageLabel;
    @FXML private Label noTeacherLabel;
    @FXML private Button saveBtn;

    private Long currentTeacherId;
    private final Map<Long, CheckBox> subjectCheckboxes = new HashMap<>();
    private List<SubjectDto> allSubjects = List.of();

    @FXML
    private void initialize() {
        IconUtil.addIcon(saveBtn, IconUtil.SAVE, "#ffffff");

        allSubjects = AppContext.get().subjectManagementUseCase().findAll();
        buildSubjectCheckboxes();

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
                loadAssignedSubjects(selected.id());
                noTeacherLabel.setVisible(false);
                subjectCheckboxPane.setVisible(true);
            } else {
                currentTeacherId = null;
                clearCheckboxes();
                noTeacherLabel.setVisible(true);
                subjectCheckboxPane.setVisible(false);
            }
        });

        noTeacherLabel.setVisible(true);
        subjectCheckboxPane.setVisible(false);
    }

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
    private void onSave() {
        if (currentTeacherId == null) {
            showMessage("Select a teacher first", true);
            return;
        }
        try {
            Set<Long> currentlyAssigned = AppContext.get().teacherSubjectManagementUseCase()
                    .findByTeacherId(currentTeacherId).stream()
                    .map(ts -> ts.subjectId())
                    .collect(Collectors.toSet());

            Set<Long> selected = new HashSet<>();
            for (CheckBox cb : subjectCheckboxes.values()) {
                if (cb.isSelected()) {
                    selected.add((Long) cb.getUserData());
                }
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

            showMessage("Subjects saved for teacher", false);
        } catch (IllegalArgumentException | IllegalStateException e) {
            showMessage(e.getMessage(), true);
        } catch (Exception e) {
            showMessage("An unexpected error occurred", true);
        }
    }

    private void showMessage(String msg, boolean error) {
        messageLabel.setText(msg);
        messageLabel.getStyleClass().removeAll("msg-error", "msg-success");
        messageLabel.getStyleClass().add(error ? "msg-error" : "msg-success");
    }
}
