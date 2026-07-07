package com.thorium.ui.controller;

import com.thorium.application.dto.TimetableDto;
import com.thorium.domain.scheduling.GenerationProgressCallback;
import com.thorium.domain.value.TimetableStatus;
import com.thorium.ui.di.AppContext;
import com.thorium.ui.util.IconUtil;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

public class TimetableGenerationController {

    @FXML private TextField nameField;
    @FXML private Label resultLabel;
    @FXML private Label qualityLabel;
    @FXML private Button generateBtn;
    @FXML private Button stopBtn;
    @FXML private ProgressIndicator progressIndicator;

    @FXML private VBox logSection;
    @FXML private TextArea logArea;
    @FXML private Label logToggleIcon;

    @FXML private VBox advisorSection;
    @FXML private VBox advisorContent;
    @FXML private Label advisorToggleIcon;

    @FXML private VBox solverSection;
    @FXML private Label solverToggleIcon;

    private final StringBuilder logBuffer = new StringBuilder();
    private final List<String> capturedWarnings = new ArrayList<>();
    private Task<Void> generationTask;

    @FXML
    private void initialize() {
        IconUtil.addIcon(generateBtn, IconUtil.GENERATE, "#ffffff");
    }

    @FXML
    private void onGenerate() {
        if (generationTask != null && generationTask.isRunning()) return;

        resultLabel.getStyleClass().removeAll("success", "warning", "error");
        resultLabel.setText("Generating...");
        qualityLabel.setText("");
        logBuffer.setLength(0);
        capturedWarnings.clear();
        logArea.clear();

        String name = nameField.getText().trim();

        generationTask = new Task<>() {
            @Override
            protected Void call() {
                var ctx = AppContext.get();
                var useCase = ctx.generateTimetableUseCase();
                try {
                    TimetableDto dto = useCase.execute(name, callback);
                    Platform.runLater(() -> onGenerationComplete(dto));
                } catch (Exception e) {
                    Platform.runLater(() -> onGenerationError(e));
                }
                return null;
            }
        };

        generateBtn.setDisable(true);
        stopBtn.setVisible(true);
        progressIndicator.setVisible(true);

        Thread thread = new Thread(generationTask, "timetable-gen");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onStop() {
        if (generationTask != null && generationTask.isRunning()) {
            generationTask.cancel(true);
            onGenerationCancelled();
        }
    }

    private final GenerationProgressCallback callback = new GenerationProgressCallback() {
        @Override
        public boolean isCancelled() {
            return generationTask != null && generationTask.isCancelled();
        }

        @Override
        public void log(String level, String message) {
            String line = "[" + level + "] " + message + "\n";
            logBuffer.append(line);
            if (level.equals("WARN")) capturedWarnings.add(message);
            Platform.runLater(() -> {
                logArea.appendText(line);
                logArea.setScrollTop(Double.MAX_VALUE);
            });
        }

        @Override
        public void progress(int placed, int total) {
            Platform.runLater(() ->
                    qualityLabel.setText(String.format("Progress: %d / %d lessons placed", placed, total)));
        }

        @Override
        public void itemRejected(String summary, String reason) {
            Platform.runLater(() -> {
                logArea.appendText("[REJECTED] " + summary + " — " + reason + "\n");
                logArea.setScrollTop(Double.MAX_VALUE);
            });
        }

        @Override
        public void tierChange(String tier) {
            Platform.runLater(() -> {
                logArea.appendText("=== Tier: " + tier + " ===\n");
                logArea.setScrollTop(Double.MAX_VALUE);
            });
        }

        @Override
        public void complete(boolean success, int placed, int total, double quality) {
        }
    };

    private void onGenerationComplete(TimetableDto result) {
        generateBtn.setDisable(false);
        stopBtn.setVisible(false);
        progressIndicator.setVisible(false);

        String coverage = String.format("%.2f", result.qualityScore());
        boolean isDraft = result.status() == TimetableStatus.DRAFT;

        resultLabel.getStyleClass().removeAll("error", "success", "warning");
        if (isDraft) {
            resultLabel.setText("Partial: " + result.name() + " (saved as DRAFT)");
            qualityLabel.setText(String.format("Quality: %s | Entries: %d / ??? (incomplete)",
                    coverage, result.entries().size()));
            resultLabel.getStyleClass().add("warning");
        } else {
            resultLabel.setText("Generated: " + result.name());
            qualityLabel.setText(String.format("Quality: %s | Entries: %d", coverage, result.entries().size()));
            resultLabel.getStyleClass().add("success");
        }

        showAdvisor(result, isDraft);
    }

    private void onGenerationError(Exception e) {
        generateBtn.setDisable(false);
        stopBtn.setVisible(false);
        progressIndicator.setVisible(false);

        resultLabel.setText(e.getMessage());
        resultLabel.getStyleClass().removeAll("success", "warning");
        resultLabel.getStyleClass().add("error");
        qualityLabel.setText("");

        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String stacktrace = sw.toString();
        e.printStackTrace();
        logArea.appendText("[ERROR] " + stacktrace + "\n");
        logArea.setScrollTop(Double.MAX_VALUE);
    }

    private void onGenerationCancelled() {
        generateBtn.setDisable(false);
        stopBtn.setVisible(false);
        progressIndicator.setVisible(false);

        resultLabel.setText("Generation cancelled");
        resultLabel.getStyleClass().removeAll("success", "warning", "error");
        qualityLabel.setText("");
        logArea.appendText("[INFO] Generation cancelled by user\n");
        logArea.setScrollTop(Double.MAX_VALUE);
    }

    private void showAdvisor(TimetableDto result, boolean isDraft) {
        advisorContent.getChildren().clear();

        if (!isDraft) {
            Label ok = new Label("✓ All " + result.entries().size() + " lessons placed successfully.");
            ok.setWrapText(true);
            ok.getStyleClass().add("msg-label");
            advisorContent.getChildren().add(ok);
            return;
        }

        List<String> advice = analyzeIssues(result);
        if (advice.isEmpty()) {
            advice.add("Generation is incomplete. Check the Live Log for details on what went wrong.");
        }
        for (String tip : advice) {
            Label label = new Label("• " + tip);
            label.setWrapText(true);
            label.getStyleClass().add("msg-label");
            advisorContent.getChildren().add(label);
        }
    }

    private List<String> analyzeIssues(TimetableDto result) {
        List<String> advice = new ArrayList<>();

        for (String w : capturedWarnings) {
            if (w.contains("exceeds total available slots")) {
                advice.add("Total lessons exceed available capacity. Try reducing lesson counts "
                        + "per week in the Assignments page.");
            } else if (w.contains("exceeding available slots")) {
                int start = w.indexOf("Teacher ");
                int end = w.indexOf(" has ");
                String teacher = (start >= 0 && end > start) ? w.substring(start + 8, end) : "A teacher";
                advice.add(teacher + " is overloaded — more lessons than time slots available. "
                        + "Distribute lessons across more teachers or reduce assignments.");
            } else if (w.contains("not placed in consecutive pairs")) {
                advice.add("Some subjects require double periods but couldn't find adjacent free slots. "
                        + "Ensure enough consecutive slots are free on the same day.");
            } else if (w.contains("odd lesson count")) {
                advice.add("Subjects requiring double periods need even lesson counts. "
                        + "Adjust lesson counts in the Assignments page.");
            } else if (w.contains("empty domain") || w.contains("MRV returned null")) {
                advice.add("Scheduler got stuck — some assignments have no available slots. "
                        + "Check teacher/class availability and reduce lesson counts.");
            } else if (w.contains("no valid slot")) {
                advice.add("Some lessons couldn't be placed at all. "
                        + "Check if all required teachers and classes are available.");
            }
        }

        if (advice.isEmpty()) {
            advice.add("Check the Live Log for details on why generation was incomplete.");
        }

        return advice;
    }

    @FXML
    private void onClearLog() {
        logArea.clear();
        logBuffer.setLength(0);
        capturedWarnings.clear();
    }

    @FXML
    private void onCopyLog() {
        ClipboardContent content = new ClipboardContent();
        content.putString(logArea.getText());
        Clipboard.getSystemClipboard().setContent(content);
    }

    @FXML
    private void toggleLogSection() {
        boolean shown = logSection.isVisible();
        logSection.setVisible(!shown);
        logSection.setManaged(!shown);
        logToggleIcon.setText(shown ? "▸" : "▾");
    }

    @FXML
    private void toggleAdvisorSection() {
        boolean shown = advisorSection.isVisible();
        advisorSection.setVisible(!shown);
        advisorSection.setManaged(!shown);
        advisorToggleIcon.setText(shown ? "▸" : "▾");
    }

    @FXML
    private void toggleSolverSection() {
        boolean shown = solverSection.isVisible();
        solverSection.setVisible(!shown);
        solverSection.setManaged(!shown);
        solverToggleIcon.setText(shown ? "▸" : "▾");
    }

    @FXML
    private void onReduceTeacherLoad() {
        logArea.appendText("[SOLVER] Analyzing teacher loads...\n");
        boolean found = false;
        for (String w : capturedWarnings) {
            if (w.contains("exceeding available slots")) {
                logArea.appendText("[SOLVER]   → " + w + "\n");
                found = true;
            }
        }
        if (!found) {
            logArea.appendText("[SOLVER] No overloaded teachers detected in last run.\n");
        }
        logArea.appendText("[SOLVER] To fix: go to Teachers → View Profile → unassign subjects "
                + "or reduce lesson counts in the Assignments page.\n");
        logArea.setScrollTop(Double.MAX_VALUE);
    }

    @FXML
    private void onRelaxConstraints() {
        logArea.appendText("[SOLVER] Constraint relaxation is handled automatically.\n");
        logArea.appendText("[SOLVER] The system tries STRICT then RELAXED tiers.\n");
        logArea.appendText("[SOLVER] If generation still fails, the data may be over-constrained.\n");
        logArea.setScrollTop(Double.MAX_VALUE);
    }

    @FXML
    private void onBalanceSubjects() {
        logArea.appendText("[SOLVER] Subject distribution check:\n");
        logArea.appendText("[SOLVER] • Ensure odd-count subjects with double-period requirement have even counts.\n");
        logArea.appendText("[SOLVER] • Spread heavy subjects across different days.\n");
        logArea.appendText("[SOLVER] • Check each teacher's total lesson load doesn't exceed available slots.\n");
        logArea.setScrollTop(Double.MAX_VALUE);
    }
}
