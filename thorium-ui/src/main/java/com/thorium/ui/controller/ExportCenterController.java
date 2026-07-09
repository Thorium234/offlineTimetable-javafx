package com.thorium.ui.controller;

import com.thorium.application.dto.TeacherDto;
import com.thorium.application.usecase.export.ExportTimetableUseCase;
import com.thorium.application.mapper.EntityMapper;
import com.thorium.ui.di.AppContext;
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
    private Button exportPdfBtn;

    @FXML
    private Label statusLabel;

    private final ExportTimetableUseCase useCase = AppContext.get().exportTimetableUseCase();
    private final AppContext appContext = AppContext.get();

    @FXML
    private void initialize() {
        loadTeachers();

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

        exportPdfBtn.setOnAction(e -> onExportPdf());
    }

    private void loadTeachers() {
        var teachers = useCase.findAllTeachers().stream()
                .map(EntityMapper::toDto)
                .toList();
        teacherCombo.setItems(FXCollections.observableArrayList(teachers));
    }

    private void onExportPdf() {
        TeacherDto selected = teacherCombo.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Please select a teacher first.");
            return;
        }

        var timetableId = useCase.findLatestTimetableId();
        if (timetableId.isEmpty()) {
            statusLabel.setText("No timetable found. Generate one first.");
            return;
        }

        FileChooser fc = new FileChooser();
        fc.setTitle("Export Teacher Timetable");
        fc.setInitialFileName("timetable_" + selected.name().replace(" ", "_") + ".pdf");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
        File file = fc.showSaveDialog(exportPdfBtn.getScene().getWindow());
        if (file == null) return;

        try {
            useCase.exportTeacherPdf(timetableId.get(), file.toPath(), selected.id());
            statusLabel.setText("PDF exported: " + file.getName());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Export failed", e);
            statusLabel.setText("Export failed: " + e.getMessage());
        }
    }
}
