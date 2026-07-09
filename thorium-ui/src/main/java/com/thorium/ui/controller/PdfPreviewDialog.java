package com.thorium.ui.controller;

import com.thorium.ui.di.AppContext;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToolBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PdfPreviewDialog {

    private static final Logger LOG = Logger.getLogger(PdfPreviewDialog.class.getName());

    private final Stage parent;
    private final byte[] pdfBytes;
    private final String timetableName;
    private final Long timetableId;

    public PdfPreviewDialog(Stage parent, byte[] pdfBytes, String timetableName, Long timetableId) {
        this.parent = parent;
        this.pdfBytes = pdfBytes;
        this.timetableName = timetableName;
        this.timetableId = timetableId;
    }

    public void show() {
        Stage stage = new Stage();
        stage.initOwner(parent);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Preview — " + timetableName);

        Button exportBtn = new Button("Export PDF");
        exportBtn.setStyle("-fx-background-color: #333333; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 4; -fx-padding: 8 16; -fx-cursor: hand;");
        exportBtn.setOnAction(e -> {
            exportPdf();
            stage.close();
        });

        Button closeBtn = new Button("Close");
        closeBtn.setStyle("-fx-background-color: #e0e0e0; -fx-text-fill: #333333; -fx-font-weight: bold; -fx-background-radius: 4; -fx-padding: 8 16; -fx-cursor: hand;");
        closeBtn.setOnAction(e -> stage.close());

        Label pageCount = new Label("Pages: " + countPages());
        pageCount.setStyle("-fx-font-weight: bold; -fx-text-fill: #333333;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        ToolBar toolBar = new ToolBar(pageCount, spacer, exportBtn, closeBtn);
        toolBar.setStyle("-fx-background-color: #f5f5f5; -fx-padding: 8 12; -fx-border-color: #d0d0d0; -fx-border-width: 0 0 1 0;");

        VBox pagesBox = new VBox(12);
        pagesBox.setPadding(new Insets(16));
        pagesBox.setStyle("-fx-background-color: #f5f5f5;");
        renderPages(pagesBox);

        ScrollPane scrollPane = new ScrollPane(pagesBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: #f5f5f5;");

        VBox root = new VBox(toolBar, scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        Scene scene = new Scene(root, 900, 700);
        stage.setScene(scene);
        stage.showAndWait();
    }

    private int countPages() {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            return doc.getNumberOfPages();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to count PDF pages", e);
            return 0;
        }
    }

    private void renderPages(VBox container) {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                var bufferedImage = renderer.renderImageWithDPI(i, 120, ImageType.RGB);
                ByteArrayOutputStream baos = new ByteArrayOutputStream(65536);
                ImageIO.write(bufferedImage, "png", baos);
                Image fxImage = new Image(new ByteArrayInputStream(baos.toByteArray()));
                ImageView imageView = new ImageView(fxImage);
                imageView.setPreserveRatio(true);
                imageView.setFitWidth(850);
                container.getChildren().add(imageView);
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to render PDF preview", e);
            container.getChildren().add(new Label("Failed to render preview: " + e.getMessage()));
        }
    }

    private void exportPdf() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        chooser.setInitialFileName(timetableName + ".pdf");
        File file = chooser.showSaveDialog(parent);
        if (file == null) return;
        try {
            java.nio.file.Files.write(file.toPath(), pdfBytes);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save PDF", e);
        }
    }
}
