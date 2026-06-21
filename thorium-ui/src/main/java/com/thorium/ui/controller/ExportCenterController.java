package com.thorium.ui.controller;

import com.thorium.application.dto.TimetableDto;
import com.thorium.ui.di.AppContext;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;

public class ExportCenterController {

    @FXML private ComboBox<TimetableDto> timetableCombo;
    @FXML private Label messageLabel;

    @FXML
    private void initialize() {
        refreshTimetables();
        timetableCombo.setCellFactory(lv -> new ListCell<TimetableDto>() {
            @Override protected void updateItem(TimetableDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name() + " (" + item.status() + ")");
            }
        });
    }

    @FXML
    private void onExportPdf() {
        export(true);
    }

    @FXML
    private void onExportExcel() {
        export(false);
    }

    private void export(boolean pdf) {
        TimetableDto selected = timetableCombo.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showMessage("Select a timetable", true);
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(pdf ? "Save PDF" : "Save Excel");
        if (pdf) {
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
            chooser.setInitialFileName(selected.name() + ".pdf");
        } else {
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel", "*.xlsx"));
            chooser.setInitialFileName(selected.name() + ".xlsx");
        }
        File file = chooser.showSaveDialog(getStage());
        if (file == null) return;
        try {
            if (pdf) {
                AppContext.get().exportTimetableUseCase().exportPdf(selected.id(), file.toPath());
            } else {
                AppContext.get().exportTimetableUseCase().exportExcel(selected.id(), file.toPath());
            }
            showMessage("Exported to " + file.getAbsolutePath(), false);
        } catch (IllegalArgumentException | IllegalStateException e) {
            showMessage(e.getMessage(), true);
        } catch (Exception e) {
            showMessage("An unexpected error occurred", true);
        }
    }

    private void refreshTimetables() {
        timetableCombo.setItems(FXCollections.observableArrayList(
                AppContext.get().generateTimetableUseCase().findAll()));
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
