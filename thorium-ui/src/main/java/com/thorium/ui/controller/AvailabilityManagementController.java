package com.thorium.ui.controller;

import com.thorium.application.dto.PeriodDto;
import com.thorium.application.dto.TeacherAvailabilityDto;
import com.thorium.application.dto.TeacherDto;
import com.thorium.domain.value.DayOfWeek;
import com.thorium.ui.di.AppContext;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.*;

public class AvailabilityManagementController {

    @FXML private ComboBox<TeacherDto> teacherCombo;
    @FXML private HBox quickActionsBox;
    @FXML private Label gridTitleLabel;
    @FXML private Label messageLabel;
    @FXML private GridPane availabilityGrid;

    private final List<DayOfWeek> days = List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
    private List<PeriodDto> periods = new ArrayList<>();
    private final Map<String, TeacherAvailabilityDto> currentAvailability = new HashMap<>();

    private Long selectedTeacherId = null;
    private String selectedTeacherName = "";

    @FXML
    private void initialize() {
        periods = AppContext.get().periodConfigurationUseCase().findAll();

        var teachers = AppContext.get().teacherManagementUseCase().findAll();
        teacherCombo.setItems(FXCollections.observableArrayList(teachers));
        teacherCombo.setCellFactory(lv -> new ListCell<TeacherDto>() {
            @Override
            protected void updateItem(TeacherDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name() + " (" + item.code() + ")");
            }
        });

        teacherCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedTeacherId = newVal.id();
                selectedTeacherName = newVal.name();
                quickActionsBox.setDisable(false);
                gridTitleLabel.setText("Availability Grid for " + selectedTeacherName);
                loadAvailabilityData();
            } else {
                clearWorkspace();
            }
        });

        clearWorkspace();
    }

    private void clearWorkspace() {
        selectedTeacherId = null;
        selectedTeacherName = "";
        quickActionsBox.setDisable(true);
        gridTitleLabel.setText("Availability Grid (Please select a teacher)");
        messageLabel.setText("");
        currentAvailability.clear();
        availabilityGrid.getChildren().clear();
    }

    private void loadAvailabilityData() {
        if (selectedTeacherId == null) return;
        try {
            currentAvailability.clear();
            var list = AppContext.get().availabilityManagementUseCase().findByTeacher(selectedTeacherId);
            for (var entry : list) {
                currentAvailability.put(entry.dayOfWeek().name() + "|" + entry.periodNumber(), entry);
            }
            renderGrid();
        } catch (Exception e) {
            showStatus("Failed to load availability: " + e.getMessage(), true);
        }
    }

    private void renderGrid() {
        availabilityGrid.getChildren().clear();
        availabilityGrid.getColumnConstraints().clear();
        availabilityGrid.getRowConstraints().clear();

        if (selectedTeacherId == null) return;

        ColumnConstraints col0 = new ColumnConstraints(120);
        availabilityGrid.getColumnConstraints().add(col0);
        for (int i = 0; i < 5; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(18);
            availabilityGrid.getColumnConstraints().add(col);
        }

        for (int c = 0; c < days.size(); c++) {
            Label dayLabel = new Label(days.get(c).displayName());
            dayLabel.getStyleClass().add("availability-header-label");
            StackPane header = new StackPane(dayLabel);
            header.getStyleClass().add("avail-day-header");
            availabilityGrid.add(header, c + 1, 0);
        }

        for (int r = 0; r < periods.size(); r++) {
            PeriodDto period = periods.get(r);
            final int rowNum = r + 1;

            Label pLabel = new Label(period.label());
            pLabel.getStyleClass().add("avail-period-label");
            Label timeLabel = new Label(period.startTime() + " - " + period.endTime());
            timeLabel.getStyleClass().add("avail-period-time");

            VBox pBox = new VBox(pLabel, timeLabel);
            pBox.setSpacing(2);
            pBox.setPadding(new Insets(6));
            pBox.getStyleClass().add("avail-period-header");
            availabilityGrid.add(pBox, 0, rowNum);

            for (int c = 0; c < days.size(); c++) {
                DayOfWeek day = days.get(c);
                final int periodNum = period.periodNumber();
                StackPane cell = createCellNode(day, periodNum);
                availabilityGrid.add(cell, c + 1, rowNum);
            }
        }
    }

    private StackPane createCellNode(DayOfWeek day, int periodNum) {
        StackPane cell = new StackPane();
        cell.setPrefSize(100, 50);

        String key = day.name() + "|" + periodNum;
        boolean isBlocked = currentAvailability.containsKey(key);

        Label label = new Label();
        cell.getStyleClass().clear();
        cell.getStyleClass().add("avail-cell");

        if (isBlocked) {
            cell.getStyleClass().add("avail-cell-blocked");
            label.setText("✕ Blocked");
            label.getStyleClass().add("avail-cell-label-blocked");
        } else {
            cell.getStyleClass().add("avail-cell-available");
            label.setText("✓ Available");
            label.getStyleClass().add("avail-cell-label-available");
        }

        cell.getChildren().add(label);

        cell.setOnMouseClicked(event -> {
            try {
                if (isBlocked) {
                    TeacherAvailabilityDto entry = currentAvailability.get(key);
                    AppContext.get().availabilityManagementUseCase().delete(entry.id());
                    showStatus("Set " + day.displayName() + " " + periodNum + " to Available", false);
                } else {
                    TeacherAvailabilityDto entry = new TeacherAvailabilityDto(
                            null, selectedTeacherId, selectedTeacherName, day, periodNum, false
                    );
                    AppContext.get().availabilityManagementUseCase().save(entry);
                    showStatus("Blocked " + day.displayName() + " " + periodNum, false);
                }
                loadAvailabilityData();
            } catch (Exception e) {
                showStatus("Error toggling cell: " + e.getMessage(), true);
            }
        });

        cell.setOnMouseEntered(e -> {
            cell.getStyleClass().removeAll("avail-cell-blocked", "avail-cell-available",
                    "avail-cell-blocked-hover", "avail-cell-available-hover");
            if (isBlocked) {
                cell.getStyleClass().add("avail-cell-blocked-hover");
            } else {
                cell.getStyleClass().add("avail-cell-available-hover");
            }
        });
        cell.setOnMouseExited(e -> {
            cell.getStyleClass().removeAll("avail-cell-blocked", "avail-cell-available",
                    "avail-cell-blocked-hover", "avail-cell-available-hover");
            if (isBlocked) {
                cell.getStyleClass().add("avail-cell-blocked");
            } else {
                cell.getStyleClass().add("avail-cell-available");
            }
        });

        return cell;
    }

    @FXML
    private void onBlockMornings() {
        if (selectedTeacherId == null) return;
        try {
            boolean changed = false;
            for (DayOfWeek day : days) {
                for (int pNum = 1; pNum <= 2; pNum++) {
                    String key = day.name() + "|" + pNum;
                    if (!currentAvailability.containsKey(key)) {
                        TeacherAvailabilityDto entry = new TeacherAvailabilityDto(
                                null, selectedTeacherId, selectedTeacherName, day, pNum, false
                        );
                        AppContext.get().availabilityManagementUseCase().save(entry);
                        changed = true;
                    }
                }
            }
            if (changed) {
                showStatus("Morning periods blocked for all days", false);
                loadAvailabilityData();
            } else {
                showStatus("Mornings are already blocked", false);
            }
        } catch (Exception e) {
            showStatus("Failed to block mornings: " + e.getMessage(), true);
        }
    }

    @FXML
    private void onBlockEvenings() {
        if (selectedTeacherId == null || periods.isEmpty()) return;
        try {
            int maxPeriod = periods.stream()
                    .mapToInt(PeriodDto::periodNumber)
                    .max()
                    .orElse(8);

            boolean changed = false;
            for (DayOfWeek day : days) {
                for (int pNum = maxPeriod - 1; pNum <= maxPeriod; pNum++) {
                    if (pNum <= 0) continue;
                    String key = day.name() + "|" + pNum;
                    if (!currentAvailability.containsKey(key)) {
                        TeacherAvailabilityDto entry = new TeacherAvailabilityDto(
                                null, selectedTeacherId, selectedTeacherName, day, pNum, false
                        );
                        AppContext.get().availabilityManagementUseCase().save(entry);
                        changed = true;
                    }
                }
            }
            if (changed) {
                showStatus("Evening periods blocked for all days", false);
                loadAvailabilityData();
            } else {
                showStatus("Evenings are already blocked", false);
            }
        } catch (Exception e) {
            showStatus("Failed to block evenings: " + e.getMessage(), true);
        }
    }

    @FXML
    private void onClearAllBlocks() {
        if (selectedTeacherId == null) return;
        if (currentAvailability.isEmpty()) {
            showStatus("No availability blocks to clear", false);
            return;
        }
        try {
            for (var entry : currentAvailability.values()) {
                AppContext.get().availabilityManagementUseCase().delete(entry.id());
            }
            showStatus("Cleared all availability blocks", false);
            loadAvailabilityData();
        } catch (Exception e) {
            showStatus("Failed to clear blocks: " + e.getMessage(), true);
        }
    }

    private void showStatus(String msg, boolean error) {
        messageLabel.setText(msg);
        messageLabel.getStyleClass().removeAll("msg-error", "msg-success");
        messageLabel.getStyleClass().add(error ? "msg-error" : "msg-success");
    }
}
