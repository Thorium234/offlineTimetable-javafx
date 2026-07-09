package com.thorium.ui.util;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PdfPreviewDialog {

    private static final Logger LOG = Logger.getLogger(PdfPreviewDialog.class.getName());

    private final Stage stage;
    private final VBox content;

    public PdfPreviewDialog(Window owner, String title) {
        this.stage = new Stage();
        if (owner instanceof Stage s) stage.initOwner(s);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(title);
        stage.setWidth(900);
        stage.setHeight(700);

        content = new VBox(8);
        content.setStyle("-fx-padding: 16; -fx-background-color: #f5f5f5;");

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: #f5f5f5;");

        Scene scene = new Scene(scroll);
        stage.setScene(scene);
    }

    public void show(byte[] pdfBytes) {
        content.getChildren().clear();
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            int pageCount = doc.getNumberOfPages();
            for (int i = 0; i < pageCount; i++) {
                var bufferedImage = renderer.renderImageWithDPI(i, 120);
                Image fxImage = SwingFXUtils.toFXImage(bufferedImage, null);
                ImageView iv = new ImageView(fxImage);
                iv.setPreserveRatio(true);
                iv.setFitWidth(840);
                content.getChildren().add(iv);
            }
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to render PDF preview", e);
            return;
        }
        stage.showAndWait();
    }
}
