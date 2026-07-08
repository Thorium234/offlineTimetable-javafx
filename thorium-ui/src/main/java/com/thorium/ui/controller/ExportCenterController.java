package com.thorium.ui.controller;

import com.thorium.application.dto.TeacherDto;
import com.thorium.application.dto.TimetableDto;
import com.thorium.ui.di.AppContext;
import com.thorium.ui.util.IconUtil;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ExportCenterController {

    private static final Logger LOG = Logger.getLogger(ExportCenterController.class.getName());

    @FXML private ComboBox<TimetableDto> timetableCombo;
    @FXML private Label messageLabel;
    @FXML private Button exportPdfBtn;
    @FXML private Button previewPdfBtn;
    @FXML private Button exportExcelBtn;

    @FXML private Button previewAllTeachersBtn;
    @FXML private Button exportAllTeachersBtn;

    @FXML private Button previewAllClassesBtn;
    @FXML private Button exportAllClassesBtn;

    @FXML private ComboBox<TeacherDto> teacherCombo;
    @FXML private Button previewTeacherBtn;
    @FXML private Button exportTeacherBtn;

    @FXML private ComboBox<Integer> formCombo;
    @FXML private ComboBox<String> streamCombo;
    @FXML private Button previewClassBtn;
    @FXML private Button exportClassBtn;

    @FXML private ComboBox<TeacherDto> ascTeacherCombo;
    @FXML private Button previewAscTeacherBtn;
    @FXML private Button exportAscTeacherBtn;
    @FXML private Button previewAscAllTeachersBtn;
    @FXML private Button exportAscAllTeachersBtn;

    @FXML
    private void initialize() {
        IconUtil.addIcon(exportPdfBtn, IconUtil.EXPORT, "#ffffff");
        IconUtil.addIcon(previewPdfBtn, IconUtil.PREVIEW, "#2563eb");
        IconUtil.addIcon(exportExcelBtn, IconUtil.EXPORT, "#16a34a");
        IconUtil.addIcon(previewAllTeachersBtn, IconUtil.PREVIEW, "#2563eb");
        IconUtil.addIcon(exportAllTeachersBtn, IconUtil.EXPORT, "#ffffff");
        IconUtil.addIcon(previewAllClassesBtn, IconUtil.PREVIEW, "#2563eb");
        IconUtil.addIcon(exportAllClassesBtn, IconUtil.EXPORT, "#ffffff");
        IconUtil.addIcon(previewTeacherBtn, IconUtil.PREVIEW, "#2563eb");
        IconUtil.addIcon(exportTeacherBtn, IconUtil.EXPORT, "#ffffff");
        IconUtil.addIcon(previewClassBtn, IconUtil.PREVIEW, "#2563eb");
        IconUtil.addIcon(exportClassBtn, IconUtil.EXPORT, "#ffffff");

        IconUtil.addIcon(previewAscTeacherBtn, IconUtil.PREVIEW, "#2563eb");
        IconUtil.addIcon(exportAscTeacherBtn, IconUtil.EXPORT, "#ffffff");
        IconUtil.addIcon(previewAscAllTeachersBtn, IconUtil.PREVIEW, "#2563eb");
        IconUtil.addIcon(exportAscAllTeachersBtn, IconUtil.EXPORT, "#ffffff");

        refreshTimetables();
        refreshTeachers();
        refreshAscTeachers();
        refreshForms();
        refreshStreams();

        timetableCombo.setCellFactory(lv -> new ListCell<TimetableDto>() {
            @Override protected void updateItem(TimetableDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name() + " (" + item.status() + ")");
            }
        });
        teacherCombo.setCellFactory(lv -> new ListCell<TeacherDto>() {
            @Override protected void updateItem(TeacherDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name());
            }
        });
        ascTeacherCombo.setCellFactory(lv -> new ListCell<TeacherDto>() {
            @Override protected void updateItem(TeacherDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name());
            }
        });
    }

    @FXML
    private void onExportPdf() { export(); }

    @FXML
    private void onPreviewPdf() {
        TimetableDto selected = timetableCombo.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showMessage("Select a timetable", true);
            return;
        }
        try {
            byte[] pdfBytes = AppContext.get().exportTimetableUseCase().previewPdf(selected.id());
            new PdfPreviewDialog(getStage(), pdfBytes, selected.name(), selected.id()).show();
            showMessage("Preview closed", false);
        } catch (IllegalArgumentException | IllegalStateException e) {
            showMessage(e.getMessage(), true);
            LOG.log(Level.WARNING, "Timetable preview failed", e);
        } catch (Exception e) {
            showMessage("An unexpected error occurred", true);
            LOG.log(Level.SEVERE, "Unexpected error during timetable preview", e);
        }
    }

    @FXML
    private void onPreviewAllTeachers() {
        TimetableDto tt = timetableCombo.getSelectionModel().getSelectedItem();
        if (tt == null) {
            showMessage("Select a timetable", true);
            return;
        }
        try {
            byte[] pdfBytes = AppContext.get().exportTimetableUseCase().previewAllTeachersPdf(tt.id());
            new PdfPreviewDialog(getStage(), pdfBytes, tt.name() + " - All Teachers", tt.id()).show();
            showMessage("Preview closed", false);
        } catch (IllegalArgumentException | IllegalStateException e) {
            showMessage(e.getMessage(), true);
            LOG.log(Level.WARNING, "All teachers timetable preview failed", e);
        } catch (Exception e) {
            showMessage("An unexpected error occurred", true);
            LOG.log(Level.SEVERE, "Unexpected error during all teachers timetable preview", e);
        }
    }

    @FXML
    private void onExportAllTeachers() {
        TimetableDto tt = timetableCombo.getSelectionModel().getSelectedItem();
        if (tt == null) {
            showMessage("Select a timetable", true);
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save All Teachers Timetable PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        chooser.setInitialFileName(tt.name() + " - All Teachers.pdf");
        File file = chooser.showSaveDialog(getStage());
        if (file == null) return;
        try {
            AppContext.get().exportTimetableUseCase().exportAllTeachersPdf(tt.id(), file.toPath());
            showMessage("Exported to " + file.getAbsolutePath(), false);
        } catch (IllegalArgumentException | IllegalStateException e) {
            showMessage(e.getMessage(), true);
        }
    }

    @FXML
    private void onPreviewAllClasses() {
        TimetableDto tt = timetableCombo.getSelectionModel().getSelectedItem();
        if (tt == null) {
            showMessage("Select a timetable", true);
            return;
        }
        try {
            byte[] pdfBytes = AppContext.get().exportTimetableUseCase().previewAllClassesPdf(tt.id());
            new PdfPreviewDialog(getStage(), pdfBytes, tt.name() + " - All Classes", tt.id()).show();
            showMessage("Preview closed", false);
        } catch (IllegalArgumentException | IllegalStateException e) {
            showMessage(e.getMessage(), true);
            LOG.log(Level.WARNING, "All classes timetable preview failed", e);
        } catch (Exception e) {
            showMessage("An unexpected error occurred", true);
            LOG.log(Level.SEVERE, "Unexpected error during all classes timetable preview", e);
        }
    }

    @FXML
    private void onExportAllClasses() {
        TimetableDto tt = timetableCombo.getSelectionModel().getSelectedItem();
        if (tt == null) {
            showMessage("Select a timetable", true);
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save All Classes Timetable PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        chooser.setInitialFileName(tt.name() + " - All Classes.pdf");
        File file = chooser.showSaveDialog(getStage());
        if (file == null) return;
        try {
            AppContext.get().exportTimetableUseCase().exportAllClassesPdf(tt.id(), file.toPath());
            showMessage("Exported to " + file.getAbsolutePath(), false);
        } catch (IllegalArgumentException | IllegalStateException e) {
            showMessage(e.getMessage(), true);
        }
    }

    @FXML
    private void onPreviewTeacher() {
        TimetableDto tt = timetableCombo.getSelectionModel().getSelectedItem();
        TeacherDto teacher = teacherCombo.getSelectionModel().getSelectedItem();
        if (tt == null || teacher == null) {
            showMessage("Select a timetable and a teacher", true);
            return;
        }
        try {
            byte[] pdfBytes = AppContext.get().exportTimetableUseCase().previewTeacherPdf(tt.id(), teacher.id());
            new PdfPreviewDialog(getStage(), pdfBytes, tt.name() + " - " + teacher.name(), tt.id()).show();
            showMessage("Preview closed", false);
        } catch (IllegalArgumentException | IllegalStateException e) {
            showMessage(e.getMessage(), true);
            LOG.log(Level.WARNING, "Teacher timetable preview failed", e);
        } catch (Exception e) {
            showMessage("An unexpected error occurred", true);
            LOG.log(Level.SEVERE, "Unexpected error during teacher timetable preview", e);
        }
    }

    @FXML
    private void onExportTeacher() {
        TimetableDto tt = timetableCombo.getSelectionModel().getSelectedItem();
        TeacherDto teacher = teacherCombo.getSelectionModel().getSelectedItem();
        if (tt == null || teacher == null) {
            showMessage("Select a timetable and a teacher", true);
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Teacher Timetable PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        chooser.setInitialFileName(tt.name() + " - " + teacher.name() + ".pdf");
        File file = chooser.showSaveDialog(getStage());
        if (file == null) return;
        try {
            AppContext.get().exportTimetableUseCase().exportTeacherPdf(tt.id(), file.toPath(), teacher.id());
            showMessage("Exported to " + file.getAbsolutePath(), false);
        } catch (IllegalArgumentException | IllegalStateException e) {
            showMessage(e.getMessage(), true);
        }
    }

    @FXML
    private void onPreviewClass() {
        TimetableDto tt = timetableCombo.getSelectionModel().getSelectedItem();
        Integer form = formCombo.getSelectionModel().getSelectedItem();
        String stream = streamCombo.getSelectionModel().getSelectedItem();
        if (tt == null || form == null || stream == null) {
            showMessage("Select a timetable, form, and stream", true);
            return;
        }
        try {
            byte[] pdfBytes = AppContext.get().exportTimetableUseCase().previewClassPdf(tt.id(), form, stream);
            new PdfPreviewDialog(getStage(), pdfBytes, tt.name() + " - Form " + form + " " + stream, tt.id()).show();
            showMessage("Preview closed", false);
        } catch (IllegalArgumentException | IllegalStateException e) {
            showMessage(e.getMessage(), true);
            LOG.log(Level.WARNING, "Class timetable preview failed", e);
        } catch (Exception e) {
            showMessage("An unexpected error occurred", true);
            LOG.log(Level.SEVERE, "Unexpected error during class timetable preview", e);
        }
    }

    @FXML
    private void onExportClass() {
        TimetableDto tt = timetableCombo.getSelectionModel().getSelectedItem();
        Integer form = formCombo.getSelectionModel().getSelectedItem();
        String stream = streamCombo.getSelectionModel().getSelectedItem();
        if (tt == null || form == null || stream == null) {
            showMessage("Select a timetable, form, and stream", true);
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Class Timetable PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        chooser.setInitialFileName(tt.name() + " - Form " + form + " " + stream + ".pdf");
        File file = chooser.showSaveDialog(getStage());
        if (file == null) return;
        try {
            AppContext.get().exportTimetableUseCase().exportClassPdf(tt.id(), file.toPath(), form, stream);
            showMessage("Exported to " + file.getAbsolutePath(), false);
        } catch (IllegalArgumentException | IllegalStateException e) {
            showMessage(e.getMessage(), true);
        }
    }

    @FXML
    private void onExportAscTeacher() {
        TimetableDto tt = timetableCombo.getSelectionModel().getSelectedItem();
        TeacherDto teacher = ascTeacherCombo.getSelectionModel().getSelectedItem();
        if (tt == null || teacher == null) {
            showMessage("Select a timetable and a teacher", true);
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save aSc-Style Teacher Timetable PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        chooser.setInitialFileName(tt.name() + " - " + teacher.name() + " (aSc).pdf");
        File file = chooser.showSaveDialog(getStage());
        if (file == null) return;
        try {
            AppContext.get().exportTimetableUseCase().exportAscTeacherPdf(tt.id(), file.toPath(), teacher.id());
            showMessage("Exported to " + file.getAbsolutePath(), false);
        } catch (IllegalArgumentException | IllegalStateException e) {
            showMessage(e.getMessage(), true);
        }
    }

    @FXML
    private void onPreviewAscTeacher() {
        TimetableDto tt = timetableCombo.getSelectionModel().getSelectedItem();
        TeacherDto teacher = ascTeacherCombo.getSelectionModel().getSelectedItem();
        if (tt == null || teacher == null) {
            showMessage("Select a timetable and a teacher", true);
            return;
        }
        try {
            byte[] pdfBytes = AppContext.get().exportTimetableUseCase().previewAscTeacherPdf(tt.id(), teacher.id());
            new PdfPreviewDialog(getStage(), pdfBytes, tt.name() + " - " + teacher.name() + " (aSc)", tt.id()).show();
            showMessage("Preview closed", false);
        } catch (IllegalArgumentException | IllegalStateException e) {
            showMessage(e.getMessage(), true);
            LOG.log(Level.WARNING, "aSc teacher timetable preview failed", e);
        } catch (Exception e) {
            showMessage("An unexpected error occurred", true);
            LOG.log(Level.SEVERE, "Unexpected error during aSc teacher timetable preview", e);
        }
    }

    @FXML
    private void onExportAscAllTeachers() {
        TimetableDto tt = timetableCombo.getSelectionModel().getSelectedItem();
        if (tt == null) {
            showMessage("Select a timetable", true);
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save aSc-Style All Teachers Timetable PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        chooser.setInitialFileName(tt.name() + " - All Teachers (aSc).pdf");
        File file = chooser.showSaveDialog(getStage());
        if (file == null) return;
        try {
            AppContext.get().exportTimetableUseCase().exportAscAllTeachersPdf(tt.id(), file.toPath());
            showMessage("Exported to " + file.getAbsolutePath(), false);
        } catch (IllegalArgumentException | IllegalStateException e) {
            showMessage(e.getMessage(), true);
        }
    }

    @FXML
    private void onPreviewAscAllTeachers() {
        TimetableDto tt = timetableCombo.getSelectionModel().getSelectedItem();
        if (tt == null) {
            showMessage("Select a timetable", true);
            return;
        }
        try {
            byte[] pdfBytes = AppContext.get().exportTimetableUseCase().previewAscAllTeachersPdf(tt.id());
            new PdfPreviewDialog(getStage(), pdfBytes, tt.name() + " - All Teachers (aSc)", tt.id()).show();
            showMessage("Preview closed", false);
        } catch (IllegalArgumentException | IllegalStateException e) {
            showMessage(e.getMessage(), true);
            LOG.log(Level.WARNING, "aSc all teachers timetable preview failed", e);
        } catch (Exception e) {
            showMessage("An unexpected error occurred", true);
            LOG.log(Level.SEVERE, "Unexpected error during aSc all teachers timetable preview", e);
        }
    }

    private void export() {
        TimetableDto selected = timetableCombo.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showMessage("Select a timetable", true);
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        chooser.setInitialFileName(selected.name() + ".pdf");
        File file = chooser.showSaveDialog(getStage());
        if (file == null) return;
        try {
            AppContext.get().exportTimetableUseCase().exportPdf(selected.id(), file.toPath());
            showMessage("Exported to " + file.getAbsolutePath(), false);
        } catch (IllegalArgumentException | IllegalStateException e) {
            showMessage(e.getMessage(), true);
            LOG.log(Level.WARNING, "PDF export failed", e);
        } catch (Exception e) {
            showMessage("An unexpected error occurred", true);
            LOG.log(Level.SEVERE, "Unexpected error during PDF export", e);
        }
    }

    @FXML
    private void onExportExcel() {
        TimetableDto selected = timetableCombo.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showMessage("Select a timetable", true);
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Excel");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel", "*.xlsx"));
        chooser.setInitialFileName(selected.name() + ".xlsx");
        File file = chooser.showSaveDialog(getStage());
        if (file == null) return;
        try {
            AppContext.get().exportTimetableUseCase().exportExcel(selected.id(), file.toPath());
            showMessage("Exported to " + file.getAbsolutePath(), false);
        } catch (IllegalArgumentException | IllegalStateException e) {
            showMessage(e.getMessage(), true);
            LOG.log(Level.WARNING, "Excel export failed", e);
        } catch (Exception e) {
            showMessage("An unexpected error occurred", true);
            LOG.log(Level.SEVERE, "Unexpected error during Excel export", e);
        }
    }

    private void refreshTimetables() {
        timetableCombo.setItems(FXCollections.observableArrayList(
                AppContext.get().generateTimetableUseCase().findAll()));
    }

    private void refreshAscTeachers() {
        ascTeacherCombo.setItems(FXCollections.observableArrayList(
                AppContext.get().teacherManagementUseCase().findAll()));
    }

    private void refreshTeachers() {
        teacherCombo.setItems(FXCollections.observableArrayList(
                AppContext.get().teacherManagementUseCase().findAll()));
    }

    private void refreshForms() {
        var forms = AppContext.get().classStreamManagementUseCase().findAll().stream()
                .map(c -> c.form())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        formCombo.setItems(FXCollections.observableArrayList(forms));
    }

    private void refreshStreams() {
        var streams = AppContext.get().classStreamManagementUseCase().findAll().stream()
                .map(c -> c.stream())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        streamCombo.setItems(FXCollections.observableArrayList(streams));
    }

    private Stage getStage() {
        return (Stage) timetableCombo.getScene().getWindow();
    }

    private void showMessage(String msg, boolean error) {
        messageLabel.setText(msg);
        messageLabel.getStyleClass().removeAll("error", "success");
        messageLabel.getStyleClass().add(error ? "error" : "success");
    }
}
