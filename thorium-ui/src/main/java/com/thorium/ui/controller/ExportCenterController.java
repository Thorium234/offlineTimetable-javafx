package com.thorium.ui.controller;

import com.thorium.application.dto.ClassStreamDto;
import com.thorium.application.dto.TeacherDto;
import com.thorium.application.usecase.export.ExportTimetableUseCase;
import com.thorium.application.mapper.EntityMapper;
import com.thorium.ui.di.AppContext;
import com.thorium.ui.util.PdfPreviewDialog;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ExportCenterController {

    private static final Logger LOG = Logger.getLogger(ExportCenterController.class.getName());

    @FXML
    private ComboBox<TeacherDto> teacherCombo;
    @FXML
    private ComboBox<ClassStreamDto> classCombo;
    @FXML
    private Button previewTeacherBtn;
    @FXML
    private Button exportTeacherPdfBtn;
    @FXML
    private Button previewClassBtn;
    @FXML
    private Button exportClassPdfBtn;
    @FXML
    private Button previewAllClassesBtn;
    @FXML
    private Button exportAllClassesPdfBtn;
    @FXML
    private Label teacherStatusLabel;
    @FXML
    private Label classStatusLabel;
    @FXML
    private Label allClassesStatusLabel;

    private final ExportTimetableUseCase useCase = AppContext.get().exportTimetableUseCase();

    @FXML
    private void initialize() {
        loadTeachers();
        loadClasses();

        teacherCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(TeacherDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name() + " (" + item.code() + ")");
            }
        });
        teacherCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(TeacherDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name() + " (" + item.code() + ")");
            }
        });

        classCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ClassStreamDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayName());
            }
        });
        classCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(ClassStreamDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayName());
            }
        });

        previewTeacherBtn.setOnAction(e -> onPreviewTeacher());
        exportTeacherPdfBtn.setOnAction(e -> onExportTeacherPdf());
        previewClassBtn.setOnAction(e -> onPreviewClass());
        exportClassPdfBtn.setOnAction(e -> onExportClassPdf());
        previewAllClassesBtn.setOnAction(e -> onPreviewAllClasses());
        exportAllClassesPdfBtn.setOnAction(e -> onExportAllClassesPdf());
    }

    private void loadTeachers() {
        var teachers = useCase.findAllTeachers().stream()
                .map(EntityMapper::toDto)
                .toList();
        teacherCombo.setItems(FXCollections.observableArrayList(teachers));
    }

    private void loadClasses() {
        var classes = useCase.findAllClasses().stream()
                .map(EntityMapper::toDto)
                .toList();
        classCombo.setItems(FXCollections.observableArrayList(classes));
    }

    private Long requireTimetable(Label status) {
        var id = useCase.findLatestTimetableId();
        if (id.isEmpty()) {
            status.setText("No timetable found. Generate one first.");
            return null;
        }
        return id.get();
    }

    private TeacherDto requireTeacher(Label status) {
        TeacherDto s = teacherCombo.getSelectionModel().getSelectedItem();
        if (s == null) status.setText("Please select a teacher first.");
        return s;
    }

    private ClassStreamDto requireClass(Label status) {
        ClassStreamDto s = classCombo.getSelectionModel().getSelectedItem();
        if (s == null) status.setText("Please select a class first.");
        return s;
    }

    private void onPreviewTeacher() {
        TeacherDto selected = requireTeacher(teacherStatusLabel);
        if (selected == null) return;
        Long tid = requireTimetable(teacherStatusLabel);
        if (tid == null) return;
        try {
            var pdf = useCase.previewTeacherPdf(tid, selected.id());
            new PdfPreviewDialog(exportTeacherPdfBtn.getScene().getWindow(), "Teacher Timetable — " + selected.name())
                    .show(pdf);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Preview failed", e);
            teacherStatusLabel.setText("Preview failed: " + e.getMessage());
        }
    }

    private void onExportTeacherPdf() {
        TeacherDto selected = requireTeacher(teacherStatusLabel);
        if (selected == null) return;
        Long tid = requireTimetable(teacherStatusLabel);
        if (tid == null) return;

        FileChooser fc = new FileChooser();
        fc.setTitle("Export Teacher Timetable");
        fc.setInitialFileName("timetable_" + selected.name().replace(" ", "_") + ".pdf");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
        File file = fc.showSaveDialog(exportTeacherPdfBtn.getScene().getWindow());
        if (file == null) return;

        try {
            useCase.exportTeacherPdf(tid, file.toPath(), selected.id());
            teacherStatusLabel.setText("PDF exported: " + file.getName());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Export failed", e);
            teacherStatusLabel.setText("Export failed: " + e.getMessage());
        }
    }

    private void onPreviewClass() {
        ClassStreamDto selected = requireClass(classStatusLabel);
        if (selected == null) return;
        Long tid = requireTimetable(classStatusLabel);
        if (tid == null) return;
        try {
            var pdf = useCase.previewClassPdf(tid, selected.id());
            new PdfPreviewDialog(exportClassPdfBtn.getScene().getWindow(), "Class Timetable — " + selected.displayName())
                    .show(pdf);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Preview failed", e);
            classStatusLabel.setText("Preview failed: " + e.getMessage());
        }
    }

    private void onExportClassPdf() {
        ClassStreamDto selected = requireClass(classStatusLabel);
        if (selected == null) return;
        Long tid = requireTimetable(classStatusLabel);
        if (tid == null) return;

        FileChooser fc = new FileChooser();
        fc.setTitle("Export Class Timetable");
        fc.setInitialFileName("timetable_" + selected.displayName().replace(" ", "_") + ".pdf");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
        File file = fc.showSaveDialog(exportClassPdfBtn.getScene().getWindow());
        if (file == null) return;

        try {
            useCase.exportClassPdf(tid, file.toPath(), selected.id());
            classStatusLabel.setText("PDF exported: " + file.getName());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Export failed", e);
            classStatusLabel.setText("Export failed: " + e.getMessage());
        }
    }

    private void onPreviewAllClasses() {
        Long tid = requireTimetable(allClassesStatusLabel);
        if (tid == null) return;
        try {
            var pdf = useCase.previewAllClassesPdf(tid);
            new PdfPreviewDialog(exportAllClassesPdfBtn.getScene().getWindow(), "School Timetable — All Classes")
                    .show(pdf);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Preview failed", e);
            allClassesStatusLabel.setText("Preview failed: " + e.getMessage());
        }
    }

    private void onExportAllClassesPdf() {
        Long tid = requireTimetable(allClassesStatusLabel);
        if (tid == null) return;

        FileChooser fc = new FileChooser();
        fc.setTitle("Export School Timetable");
        fc.setInitialFileName("timetable_all_classes.pdf");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
        File file = fc.showSaveDialog(exportAllClassesPdfBtn.getScene().getWindow());
        if (file == null) return;

        try {
            useCase.exportAllClassesPdf(tid, file.toPath());
            allClassesStatusLabel.setText("PDF exported: " + file.getName());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Export failed", e);
            allClassesStatusLabel.setText("Export failed: " + e.getMessage());
        }
    }
}
