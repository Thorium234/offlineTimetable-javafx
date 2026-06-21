package com.thorium.ui.controller;

import com.thorium.ui.di.AppContext;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.StackPane;

import java.io.IOException;

public class MainController {

    @FXML
    private ListView<String> navigationList;

    @FXML
    private StackPane contentPane;

    @FXML
    private Label statusLabel;

    private final AppContext appContext = AppContext.get();

    @FXML
    private void initialize() {
        navigationList.getItems().addAll(
                "Dashboard",
                "Teachers",
                "Subjects",
                "Classes",
                "Rooms",
                "Assignments",
                "Assignments",
                "Periods",
                "Breaks",
                "Availability",
                "Generate Timetable",
                "View Timetable",
                "Export Center",
                "Settings"
        );
        navigationList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadView(newVal);
            }
        });
        navigationList.getSelectionModel().selectFirst();
    }

    private void loadView(String viewName) {
        try {
            String fxml = switch (viewName) {
                case "Dashboard" -> "/fxml/dashboard.fxml";
                case "Teachers" -> "/fxml/teachers.fxml";
                case "Subjects" -> "/fxml/subjects.fxml";
                case "Classes" -> "/fxml/classes.fxml";
                case "Rooms" -> "/fxml/rooms.fxml";
                case "Assignments" -> "/fxml/assignments.fxml";
                case "Periods" -> "/fxml/periods.fxml";
                case "Breaks" -> "/fxml/breaks.fxml";
                case "Availability" -> "/fxml/availability.fxml";
                case "Generate Timetable" -> "/fxml/generate.fxml";
                case "View Timetable" -> "/fxml/viewer.fxml";
                case "Export Center" -> "/fxml/export.fxml";
                case "Settings" -> "/fxml/settings.fxml";
                default -> throw new IllegalArgumentException("Unknown view: " + viewName);
            };
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            javafx.scene.Parent root = loader.load();
            contentPane.getChildren().setAll(root);
            statusLabel.setText(viewName);
        } catch (IOException e) {
            statusLabel.setText("Failed to load: " + viewName);
        }
    }
}
