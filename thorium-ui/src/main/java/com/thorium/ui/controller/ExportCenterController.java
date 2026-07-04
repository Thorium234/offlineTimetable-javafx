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
import java.util.Comparator;
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

    @FXML private ComboBox<TeacherDto> teacherCombo;
    @FXML private Button previewTeacherBtn;
    @FXML private Button exportTeacherBtn;

    @FXML private ComboBox<String> streamCombo;
    @FXML private Button previewStreamBtn;
    @FXML private Button exportStreamBtn;

    @FXML private ComboBox<Integer> gradeCombo;
    @FXML private Button previewGradeBtn;
    @FXML private Button exportGradeBtn;

    @FXML
    private void initialize() {
        IconUtil.addIcon(exportPdfBtn, IconUtil.EXPORT, "#ffffff");
        IconUtil.addIcon(previewPdfBtn, IconUtil.PREVIEW, "#2563eb");
        IconUtil.addIcon(exportExcelBtn, IconUtil.EXPORT, "#16a34a");
        IconUtil.addIcon(previewTeacherBtn, IconUtil.PREVIEW, "#2563eb");
        IconUtil.addIcon(exportTeacherBtn, IconUtil.EXPORT, "#ffffff");
        IconUtil.addIcon(previewStreamBtn, IconUtil.PREVIEW, "#2563eb");
        IconUtil.addIcon(exportStreamBtn, IconUtil.EXPORT, "#ffffff");
        IconUtil.addIcon(previewGradeBtn, IconUtil.PREVIEW, "#2563eb");
        IconUtil.addIcon(exportGradeBtn, IconUtil.EXPORT, "#ffffff");

        refreshTimetables();
        refreshTeachers();
        refreshStreams();
        refreshGrades();

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
    private void onPreviewStream() {
        TimetableDto tt = timetableCombo.getSelectionModel().getSelectedItem();
        String stream = streamCombo.getSelectionModel().getSelectedItem();
        if (tt == null || stream == null) {
            showMessage("Select a timetable and a stream", true);
            return;
        }
        try {
            byte[] pdfBytes = AppContext.get().exportTimetableUseCase().previewStreamPdf(tt.id(), stream);
            new PdfPreviewDialog(getStage(), pdfBytes, tt.name() + " - Stream " + stream, tt.id()).show();
            showMessage("Preview closed", false);
        } catch (IllegalArgumentException | IllegalStateException e) {
            showMessage(e.getMessage(), true);
            LOG.log(Level.WARNING, "Stream timetable preview failed", e);
        } catch (Exception e) {
            showMessage("An unexpected error occurred", true);
            LOG.log(Level.SEVERE, "Unexpected error during stream timetable preview", e);
        }
    }

    @FXML
    private void onExportStream() {
        TimetableDto tt = timetableCombo.getSelectionModel().getSelectedItem();
        String stream = streamCombo.getSelectionModel().getSelectedItem();
        if (tt == null || stream == null) {
            showMessage("Select a timetable and a stream", true);
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Stream-Wise Timetable PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        chooser.setInitialFileName(tt.name() + " - Stream " + stream + ".pdf");
        File file = chooser.showSaveDialog(getStage());
        if (file == null) return;
        try {
            AppContext.get().exportTimetableUseCase().exportStreamPdf(tt.id(), file.toPath(), stream);
            showMessage("Exported to " + file.getAbsolutePath(), false);
        } catch (IllegalArgumentException | IllegalStateException e) {
            showMessage(e.getMessage(), true);
        }
    }

    @FXML
    private void onPreviewGrade() {
        TimetableDto tt = timetableCombo.getSelectionModel().getSelectedItem();
        Integer form = gradeCombo.getSelectionModel().getSelectedItem();
        if (tt == null || form == null) {
            showMessage("Select a timetable and a grade", true);
            return;
        }
        try {
            byte[] pdfBytes = AppContext.get().exportTimetableUseCase().previewGradePdf(tt.id(), form);
            new PdfPreviewDialog(getStage(), pdfBytes, tt.name() + " - Form " + form, tt.id()).show();
            showMessage("Preview closed", false);
        } catch (IllegalArgumentException | IllegalStateException e) {
            showMessage(e.getMessage(), true);
            LOG.log(Level.WARNING, "Grade timetable preview failed", e);
        } catch (Exception e) {
            showMessage("An unexpected error occurred", true);
            LOG.log(Level.SEVERE, "Unexpected error during grade timetable preview", e);
        }
    }

    @FXML
    private void onExportGrade() {
        TimetableDto tt = timetableCombo.getSelectionModel().getSelectedItem();
        Integer form = gradeCombo.getSelectionModel().getSelectedItem();
        if (tt == null || form == null) {
            showMessage("Select a timetable and a grade", true);
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Grade-Wise Timetable PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        chooser.setInitialFileName(tt.name() + " - Form " + form + ".pdf");
        File file = chooser.showSaveDialog(getStage());
        if (file == null) return;
        try {
            AppContext.get().exportTimetableUseCase().exportGradePdf(tt.id(), file.toPath(), form);
            showMessage("Exported to " + file.getAbsolutePath(), false);
        } catch (IllegalArgumentException | IllegalStateException e) {
            showMessage(e.getMessage(), true);
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

    private void refreshTeachers() {
        teacherCombo.setItems(FXCollections.observableArrayList(
                AppContext.get().teacherManagementUseCase().findAll()));
    }

    private void refreshStreams() {
        var streams = AppContext.get().classStreamManagementUseCase().findAll().stream()
                .map(c -> c.stream())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        streamCombo.setItems(FXCollections.observableArrayList(streams));
    }

    private void refreshGrades() {
        var forms = AppContext.get().classStreamManagementUseCase().findAll().stream()
                .map(c -> c.form())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        gradeCombo.setItems(FXCollections.observableArrayList(forms));
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
