package com.thorium.ui.util;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

public final class IconUtil {

    private IconUtil() {}

    public static final String SAVE = "M17 21v-2a4 4 0 0 0-4-4H7a4 4 0 0 0-4 4v2 M7 7V3h10l4 4v10a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2z M7 3v4h8";
    public static final String DELETE = "M3 6h18 M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6 M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2 M10 11v6 M14 11v6";
    public static final String CLEAR = "M18 6L6 18 M6 6l12 12";
    public static final String REFRESH = "M23 4v6h-6 M1 20v-6h6 M3.51 9a9 9 0 0 1 14.85-3.36L23 10 M1 14l4.64 4.36A9 9 0 0 0 20.49 15";
    public static final String GENERATE = "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z M14 2v6h6 M12 18v-6 M9 15l3-3 3 3";
    public static final String SETTINGS = "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z";
    public static final String BLOCK = "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2z M4.93 4.93l14.14 14.14";
    public static final String CHECK = "M9 16l-4-4 2-2 2 2 5-5 2 2z";
    public static final String SUN = "M12 1v2 M12 21v2 M4.22 4.22l1.42 1.42 M18.36 18.36l1.42 1.42 M1 12h2 M21 12h2 M4.22 19.78l1.42-1.42 M18.36 5.64l1.42-1.42 M12 17a5 5 0 1 0 0-10 5 5 0 0 0 0 10z";
    public static final String MOON = "M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z";
    public static final String PRINT = "M6 9V3h12v6 M6 13h12 M6 17h12 M4 21h16a2 2 0 0 0 2-2v-8a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2z";

    public static Node createIcon(String svgPath, String color, double size) {
        SVGPath path = new SVGPath();
        path.setContent(svgPath);
        path.setFill(Color.TRANSPARENT);
        path.setStroke(Color.web(color));
        path.setStrokeWidth(2.0);
        path.setScaleX(size / 24.0);
        path.setScaleY(size / 24.0);
        StackPane container = new StackPane(path);
        container.setPrefSize(size, size);
        container.setMinSize(size, size);
        container.setMaxSize(size, size);
        return container;
    }

    public static void addIcon(Button button, String svgPath, String color) {
        button.setGraphic(createIcon(svgPath, color, 16));
        button.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
    }
}
