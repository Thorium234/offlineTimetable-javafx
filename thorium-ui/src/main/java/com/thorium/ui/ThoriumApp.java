package com.thorium.ui;

import com.thorium.infrastructure.ApplicationBootstrap;
import com.thorium.ui.di.AppContext;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.nio.file.Paths;

public class ThoriumApp extends Application {

    private static final String APP_ICON_SVG = "M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z M9 22V12h6v10";

    @Override
    public void start(Stage stage) throws Exception {
        Path dbPath = Paths.get(System.getProperty("user.home"), ".thorium", "timetable.db");
        dbPath.getParent().toFile().mkdirs();
        AppContext.initialize(ApplicationBootstrap.create(dbPath));

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        Scene scene = new Scene(loader.load(), 1200, 800);
        scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());

        stage.setTitle("Thorium Timetable Generator");
        stage.getIcons().add(createAppIcon());
        stage.setScene(scene);
        stage.setMinWidth(960);
        stage.setMinHeight(640);
        stage.show();
    }

    private Image createAppIcon() {
        SVGPath path = new SVGPath();
        path.setContent(APP_ICON_SVG);
        path.setFill(Color.TRANSPARENT);
        path.setStroke(Color.web("#1e293b"));
        path.setStrokeWidth(2.5);

        StackPane pane = new StackPane(path);
        pane.setPrefSize(64, 64);
        pane.setMinSize(64, 64);
        pane.setMaxSize(64, 64);
        pane.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 8; -fx-border-color: #cbd5e1; -fx-border-radius: 8; -fx-padding: 8;");

        Scene temp = new Scene(pane);
        temp.snapshot(null);
        WritableImage img = new WritableImage(64, 64);
        return pane.snapshot(null, img);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
