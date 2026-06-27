package com.thorium.ui.controller;

import com.thorium.ui.di.AppContext;
import com.thorium.ui.util.IconUtil;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

import javafx.application.Platform;

import java.io.IOException;
import java.util.prefs.Preferences;

public class MainController {

    private enum NavItem {
        DASHBOARD("Dashboard", "M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z M9 22V12h6v10", "#3b82f6", "/fxml/dashboard.fxml"),
        TEACHERS("Teachers", "M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2 M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z", "#10b981", "/fxml/teachers.fxml"),
        ASSIGNMENTS("Assign", "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z M14 2v6h6 M16 13H8 M16 17H8 M10 9H8", "#f97316", "/fxml/assignments.fxml"),
        SUBJECTS("Subjects", "M4 19.5v-15a2.5 2.5 0 0 1 2.5-2.5H20v20H6.5a2.5 2.5 0 0 1-2.5-2.5z", "#f59e0b", "/fxml/subjects.fxml"),
        CLASSES("Classes", "M22 10v6M2 10l10-5 10 5-10 5z M6 12v5c0 2 2 3 6 3s6-1 6-3v-5", "#8b5cf6", "/fxml/classes.fxml"),
        ROOMS("Rooms", "M3 20V4a2 2 0 0 1 2-2h7a2 2 0 0 1 2 2v16 M14 4h4a2 2 0 0 1 2 2v14 M2 20h20", "#ef4444", "/fxml/rooms.fxml"),
        PERIODS("Periods", "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2z M12 6v6l4 2", "#f97316", "/fxml/periods.fxml"),
        BREAKS("Breaks", "M18 8h1a4 4 0 0 1 0 8h-1 M2 8h16v9a4 4 0 0 1-4 4H6a4 4 0 0 1-4-4V8z", "#14b8a6", "/fxml/breaks.fxml"),
        AVAILABILITY("Avail.", "M19 4H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2z M16 2v4 M8 2v4 M3 10h18", "#e11d48", "/fxml/availability.fxml"),
        GENERATE("Generate", "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z M14 2v6h6 M12 18v-6 M9 15l3-3 3 3", "#6366f1", "/fxml/generate.fxml"),
        TIMETABLE("Timetable", "M3 3h18v18H3z M21 9H3 M3 15h18 M9 3v18 M15 3v18", "#2563eb", "/fxml/viewer.fxml"),
        EXPORT("Export", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4 M7 10l5 5 5-5 M12 15V3", "#16a34a", "/fxml/export.fxml"),
        SETTINGS("Settings", "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z", "#64748b", "/fxml/settings.fxml");

        final String label;
        final String svgPath;
        final String svgColor;
        final String fxml;

        NavItem(String label, String svgPath, String svgColor, String fxml) {
            this.label = label;
            this.svgPath = svgPath;
            this.svgColor = svgColor;
            this.fxml = fxml;
        }
    }

    @FXML
    private ToolBar navToolbar;

    @FXML
    private StackPane contentPane;

    @FXML
    private Label statusLabel;

    @FXML
    private Button themeToggleBtn;

    @FXML
    private Button generateBtn;

    private final AppContext appContext = AppContext.get();
    private boolean darkMode;
    private final Preferences prefs = Preferences.userNodeForPackage(MainController.class);
    private NavItem currentView = null;

    @FXML
    private void initialize() {
        navToolbar.getItems().add(createSeparator(4));
        for (NavItem nav : NavItem.values()) {
            navToolbar.getItems().add(createNavButton(nav));
            navToolbar.getItems().add(createSeparator(2));
        }

        darkMode = prefs.getBoolean("darkMode", false);
        Platform.runLater(() -> {
            applyTheme();
            if (!navToolbar.getItems().isEmpty()) {
                for (var item : navToolbar.getItems()) {
                    if (item instanceof ToggleButton btn && btn.getUserData() == NavItem.DASHBOARD) {
                        btn.setSelected(true);
                        loadView(NavItem.DASHBOARD);
                        break;
                    }
                }
            }
        });
    }

    private Node createSeparator(double width) {
        var sep = new javafx.scene.shape.Line(0, 0, 0, 32);
        sep.setStroke(Color.web("#e2e8f0"));
        sep.setStrokeWidth(1);
        var box = new HBox(sep);
        box.setPadding(new javafx.geometry.Insets(0, width, 0, width));
        box.getStyleClass().add("nav-separator");
        return box;
    }

    private ToggleButton createNavButton(NavItem nav) {
        SVGPath path = new SVGPath();
        path.setContent(nav.svgPath);
        path.setFill(Color.TRANSPARENT);
        path.setStroke(Color.web(nav.svgColor));
        path.setStrokeWidth(1.8);

        StackPane iconBox = new StackPane(path);
        iconBox.setPrefSize(22, 22);
        iconBox.setMinSize(22, 22);
        iconBox.setMaxSize(22, 22);

        Label l = new Label(nav.label);
        l.getStyleClass().add("nav-label");

        VBox content = new VBox(2, iconBox, l);
        content.setAlignment(javafx.geometry.Pos.CENTER);
        content.setPrefWidth(68);
        content.setMinWidth(68);

        ToggleButton btn = new ToggleButton();
        btn.setGraphic(content);
        btn.setUserData(nav);
        btn.getStyleClass().addAll("toggle-button", "nav-btn");
        btn.setTooltip(new Tooltip(nav.label));

        btn.selectedProperty().addListener((obs, was, is) -> {
            if (is) {
                btn.getStyleClass().add("nav-btn-selected");
                l.getStyleClass().add("nav-label-selected");
            } else {
                btn.getStyleClass().remove("nav-btn-selected");
                l.getStyleClass().remove("nav-label-selected");
            }
        });

        btn.setOnAction(e -> {
            loadView(nav);
            for (var item : navToolbar.getItems()) {
                if (item instanceof ToggleButton other && other != btn) {
                    other.setSelected(false);
                }
            }
        });

        return btn;
    }

    @FXML
    private void onToggleTheme() {
        darkMode = !darkMode;
        prefs.putBoolean("darkMode", darkMode);
        applyTheme();
    }

    @FXML
    private void onGenerate() {
        for (var item : navToolbar.getItems()) {
            if (item instanceof ToggleButton btn && btn.getUserData() == NavItem.GENERATE) {
                btn.setSelected(true);
                loadView(NavItem.GENERATE);
                break;
            }
        }
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
                isDark ? "#fbbf24" : "#94a3b8", 16);
        themeToggleBtn.setGraphic(icon);
        themeToggleBtn.setTooltip(new javafx.scene.control.Tooltip(
                isDark ? "Switch to Light Mode" : "Switch to Dark Mode"));
    }

    private void loadView(NavItem nav) {
        if (nav == currentView) return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(nav.fxml));
            javafx.scene.Parent root = loader.load();
            contentPane.getChildren().setAll(root);
            statusLabel.setText(nav.label);
            currentView = nav;
        } catch (IOException e) {
            statusLabel.setText("Failed to load: " + nav.label);
        }
    }
}
