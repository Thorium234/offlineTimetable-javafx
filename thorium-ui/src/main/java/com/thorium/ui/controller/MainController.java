package com.thorium.ui.controller;

import com.thorium.ui.di.AppContext;
import com.thorium.ui.util.IconUtil;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

import javafx.application.Platform;

import java.io.IOException;
import java.util.prefs.Preferences;

public class MainController {

    @FXML
    private ListView<NavItem> navigationList;

    @FXML
    private StackPane contentPane;

    @FXML
    private Label statusLabel;

    @FXML
    private Button themeToggleBtn;

    private final AppContext appContext = AppContext.get();
    private boolean darkMode;
    private final Preferences prefs = Preferences.userNodeForPackage(MainController.class);

    private static final String ICON_DASHBOARD = "M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z M9 22V12h6v10";
    private static final String ICON_TEACHERS = "M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2 M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z";
    private static final String ICON_SUBJECTS = "M4 19.5v-15a2.5 2.5 0 0 1 2.5-2.5H20v20H6.5a2.5 2.5 0 0 1-2.5-2.5z";
    private static final String ICON_CLASSES = "M22 10v6M2 10l10-5 10 5-10 5z M6 12v5c0 2 2 3 6 3s6-1 6-3v-5";
    private static final String ICON_ROOMS = "M3 20V4a2 2 0 0 1 2-2h7a2 2 0 0 1 2 2v16 M14 4h4a2 2 0 0 1 2 2v14 M2 20h20 M14 4l-8 2v12l8 2";
    private static final String ICON_ASSIGNMENTS = "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z M14 2v6h6 M16 13H8 M16 17H8 M10 9H8";
    private static final String ICON_PERIODS = "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2z M12 6v6l4 2";
    private static final String ICON_BREAKS = "M18 8h1a4 4 0 0 1 0 8h-1 M2 8h16v9a4 4 0 0 1-4 4H6a4 4 0 0 1-4-4V8z M6 1v3 M10 1v3 M14 1v3";
    private static final String ICON_AVAILABILITY = "M19 4H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2z M16 2v4 M8 2v4 M3 10h18 M10 14l2 2 4-4";
    private static final String ICON_GENERATE = "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z M14 2v6h6 M12 18v-6 M9 15l3-3 3 3";
    private static final String ICON_VIEWER = "M3 3h18v18H3z M21 9H3 M3 15h18 M9 3v18 M15 3v18";
    private static final String ICON_EXPORT = "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4 M7 10l5 5 5-5 M12 15V3";
    private static final String ICON_SETTINGS = "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z";

    private record NavItem(String label, String svgPath, String svgColor) {
        @Override
        public String toString() {
            return label;
        }
    }

    @FXML
    private void initialize() {
        navigationList.getItems().addAll(
                new NavItem("Dashboard", ICON_DASHBOARD, "#3b82f6"),
                new NavItem("Teachers", ICON_TEACHERS, "#10b981"),
                new NavItem("Subjects", ICON_SUBJECTS, "#f59e0b"),
                new NavItem("Classes", ICON_CLASSES, "#8b5cf6"),
                new NavItem("Rooms", ICON_ROOMS, "#ef4444"),
                new NavItem("Assignments", ICON_ASSIGNMENTS, "#06b6d4"),
                new NavItem("Periods", ICON_PERIODS, "#f97316"),
                new NavItem("Breaks", ICON_BREAKS, "#14b8a6"),
                new NavItem("Availability", ICON_AVAILABILITY, "#e11d48"),
                new NavItem("Generate Timetable", ICON_GENERATE, "#6366f1"),
                new NavItem("View Timetable", ICON_VIEWER, "#2563eb"),
                new NavItem("Export Center", ICON_EXPORT, "#16a34a"),
                new NavItem("Settings", ICON_SETTINGS, "#64748b")
        );

        navigationList.setCellFactory(lv -> new ListCell<NavItem>() {
            @Override
            protected void updateItem(NavItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    SVGPath path = new SVGPath();
                    path.setContent(item.svgPath());
                    path.setFill(Color.TRANSPARENT);
                    path.setStroke(Color.web(item.svgColor()));
                    path.setStrokeWidth(1.8);
                    StackPane iconContainer = new StackPane(path);
                    iconContainer.setPrefSize(18, 18);
                    iconContainer.setPadding(new Insets(0, 4, 0, 0));

                    Label label = new Label(item.label());
                    label.setStyle("-fx-text-fill: #cbd5e1; -fx-font-size: 13px;");

                    HBox box = new HBox(iconContainer, label);
                    box.setSpacing(6);
                    box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    HBox.setHgrow(label, Priority.ALWAYS);

                    setGraphic(box);
                    setText(null);
                }
            }
        });

        navigationList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadView(newVal.label());
            }
        });
        navigationList.getSelectionModel().selectFirst();

        darkMode = prefs.getBoolean("darkMode", false);
        Platform.runLater(this::applyTheme);
    }

    @FXML
    private void onToggleTheme() {
        darkMode = !darkMode;
        prefs.putBoolean("darkMode", darkMode);
        applyTheme();
    }

    private void applyTheme() {
        Scene scene = themeToggleBtn.getScene();
        if (scene == null) return;

        var stylesheets = scene.getStylesheets();
        String darkCss = getClass().getResource("/css/dark.css").toExternalForm();

        if (darkMode) {
            if (!stylesheets.contains(darkCss)) {
                stylesheets.add(darkCss);
            }
            updateThemeIcon(true);
        } else {
            stylesheets.remove(darkCss);
            updateThemeIcon(false);
        }
    }

    private void updateThemeIcon(boolean isDark) {
        Node icon = IconUtil.createIcon(isDark ? IconUtil.SUN : IconUtil.MOON,
                isDark ? "#fbbf24" : "#64748b", 18);
        themeToggleBtn.setGraphic(icon);
        themeToggleBtn.setTooltip(new javafx.scene.control.Tooltip(
                isDark ? "Switch to Light Mode" : "Switch to Dark Mode"));
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
