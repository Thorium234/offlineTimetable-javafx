package com.thorium.ui.controller;

import com.thorium.application.dto.*;
import com.thorium.application.usecase.timetable.TimetableEditorUseCase;
import com.thorium.domain.value.DayOfWeek;
import com.thorium.ui.di.AppContext;
import com.thorium.ui.util.IconUtil;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

import java.util.*;

/**
 * Controller for the aSc Timetables-inspired interactive scheduling editor and workspace.
 * Manages the TreeView sidebar resource list, the Weekly grid (showing scheduled items),
 * and the Unplaced Cards pool (supporting drag-and-drop and real-time validation feedback).
 */
public class TimetableViewerController {

    // FXML Visual Node Injections
    @FXML private ComboBox<TimetableDto> timetableCombo;
    @FXML private TreeView<TreeItemValue> entityTreeView;
    @FXML private GridPane timetableGrid;
    @FXML private FlowPane unassignedPool;
    @FXML private Label filterStatusLabel;
    @FXML private Label gridTitleLabel;
    @FXML private Label conflictWarningLabel;
    @FXML private VBox poolContainer;
    @FXML private Button refreshBtn;
    @FXML private Button printBtn;
    private static final String[] DAY_CODES = {"Mo", "Tu", "We", "Th", "Fr"};

    // SVG Outline Paths for Resource Tree Nodes
    private static final String CLASS_ICON = "M22 10v6M2 10l10-5 10 5-10 5z M6 12v5c0 2 2 3 6 3s6-1 6-3v-5";
    private static final String TEACHER_ICON = "M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2 M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z";
    private static final String SUBJECT_ICON = "M4 19.5v-15a2.5 2.5 0 0 1 2.5-2.5H20v20H6.5a2.5 2.5 0 0 1-2.5-2.5z";
    private static final String ROOM_ICON = "M3 20V4a2 2 0 0 1 2-2h7a2 2 0 0 1 2 2v16 M14 4h4a2 2 0 0 1 2 2v14 M2 20h20 M14 4l-8 2v12l8 2";

    // Application Use Cases
    private final TimetableEditorUseCase timetableEditorUseCase = AppContext.get().timetableEditorUseCase();

    // Editor State tracking variables
    private Long currentTimetableId;
    private TimetableEditorStateDto currentState;
    private List<RoomDto> allRooms = new ArrayList<>();

    // Sidebar selected node tracking
    private SelectionType selectedType = SelectionType.NONE;
    private Long selectedId = null;
    private String selectedLabel = "";
    private String selectedDetail = "";

    // Active drag-and-drop node reference
    private LessonCardDto activeDragCard;

    private enum SelectionType {
        NONE, CLASS, TEACHER, SUBJECT, ROOM
    }

    /**
     * DTO Wrapper for TreeView items to allow clean label rendering and metadata lookup.
     */
    private record TreeItemValue(SelectionType type, Long id, String label, String detail) {
        @Override
        public String toString() {
            if (type == SelectionType.NONE) {
                return label;
            }
            if (detail != null && !detail.isBlank()) {
                return label + " (" + detail + ")";
            }
            return label;
        }
    }

    @FXML
    private void initialize() {
        IconUtil.addIcon(refreshBtn, IconUtil.REFRESH, "#ffffff");
        IconUtil.addIcon(printBtn, IconUtil.PRINT, "#334155");

        // Setup Timetable list listener
        timetableCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                currentTimetableId = newVal.id();
                loadWorkspaceState();
            }
        });

        // Setup TreeView selection listener
        entityTreeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.getValue() != null) {
                TreeItemValue val = newVal.getValue();
                selectedType = val.type();
                selectedId = val.id();
                selectedLabel = val.label();
                selectedDetail = val.detail();
                renderGrid();
                renderPool();
            } else {
                clearWorkspaceGrid();
            }
        });

        // Setup drag drop on pool container (dragging from grid back to pool unassigns it)
        unassignedPool.setOnDragOver(event -> {
            if (event.getDragboard().hasString() && activeDragCard != null && activeDragCard.entryId() != null) {
                event.acceptTransferModes(javafx.scene.input.TransferMode.MOVE);
            }
            event.consume();
        });

        unassignedPool.setOnDragDropped(event -> {
            boolean success = false;
            if (activeDragCard != null && activeDragCard.entryId() != null) {
                try {
                    timetableEditorUseCase.unassignEntry(currentTimetableId, activeDragCard.entryId());
                    success = true;
                } catch (Exception ex) {
                    showError("Failed to unassign: " + ex.getMessage());
                }
            }
            event.setDropCompleted(success);
            event.consume();
            if (success) {
                refreshWorkspace();
            }
        });

        // Initial loading of lists and databases
        loadRoomsDatabase();
        refreshTimetablesList();
    }

    @FXML
    private void onRefresh() {
        refreshTimetablesList();
    }

    @FXML
    private void onPrint() {
        javafx.scene.Node node = timetableGrid;
        if (node.getScene() == null) return;
        javafx.print.PrinterJob job = javafx.print.PrinterJob.createPrinterJob();
        if (job != null && job.showPrintDialog(node.getScene().getWindow())) {
            boolean printed = job.printPage(node);
            if (printed) {
                job.endJob();
            }
        }
    }

    /**
     * Refreshes the timetables dropdown from DB and retains the active selection.
     */
    private void refreshTimetablesList() {
        var timetables = AppContext.get().generateTimetableUseCase().findAll();
        timetableCombo.setItems(FXCollections.observableArrayList(timetables));
        timetableCombo.setCellFactory(lv -> new ListCell<TimetableDto>() {
            @Override
            protected void updateItem(TimetableDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name() + " (" + item.status() + ")");
            }
        });
        if (!timetables.isEmpty()) {
            timetableCombo.getSelectionModel().selectLast();
        }
    }

    private void loadRoomsDatabase() {
        allRooms = AppContext.get().roomManagementUseCase().findAll();
    }

    /**
     * Loads the state for the active timetable from the editor use case and builds the resource tree.
     */
    private void loadWorkspaceState() {
        if (currentTimetableId == null) {
            return;
        }
        try {
            currentState = timetableEditorUseCase.loadState(currentTimetableId, null);
            populateTreeView(currentState.entityTree());
            renderGrid();
            renderPool();
        } catch (Exception e) {
            showError("Failed to load workspace state: " + e.getMessage());
        }
    }

    /**
     * Forces a state reload and redraws grid/pool while preserving the active sidebar selections.
     */
    private void refreshWorkspace() {
        if (currentTimetableId == null) {
            return;
        }
        try {
            currentState = timetableEditorUseCase.loadState(currentTimetableId, null);
            // Reload rooms too in case they changed
            loadRoomsDatabase();
            renderGrid();
            renderPool();
        } catch (Exception e) {
            showError("Failed to refresh workspace: " + e.getMessage());
        }
    }

    private void clearWorkspaceGrid() {
        selectedType = SelectionType.NONE;
        selectedId = null;
        selectedLabel = "";
        selectedDetail = "";
        gridTitleLabel.setText("Weekly Timetable Grid");
        filterStatusLabel.setText("Select a resource from the explorer sidebar to view its timetable.");
        timetableGrid.getChildren().clear();
        unassignedPool.getChildren().clear();
        conflictWarningLabel.setText("");
    }

    /**
     * Populates the sidebar resource TreeView hierarchy with SVG icons.
     */
    private void populateTreeView(EntityTreeDto treeDto) {
        TreeItem<TreeItemValue> root = new TreeItem<>(new TreeItemValue(SelectionType.NONE, null, "Resources", null));
        root.setExpanded(true);

        // Classes Branch
        TreeItem<TreeItemValue> classesBranch = new TreeItem<>(
                new TreeItemValue(SelectionType.NONE, null, "Classes", null),
                createIcon(CLASS_ICON, "#3b82f6")
        );
        classesBranch.setExpanded(true);
        for (var c : treeDto.classes()) {
            classesBranch.getChildren().add(new TreeItem<>(
                    new TreeItemValue(SelectionType.CLASS, c.id(), c.label(), c.detail()),
                    createIcon(CLASS_ICON, "#3b82f6")
            ));
        }
        root.getChildren().add(classesBranch);

        // Teachers Branch
        TreeItem<TreeItemValue> teachersBranch = new TreeItem<>(
                new TreeItemValue(SelectionType.NONE, null, "Teachers", null),
                createIcon(TEACHER_ICON, "#10b981")
        );
        teachersBranch.setExpanded(true);
        for (var t : treeDto.teachers()) {
            teachersBranch.getChildren().add(new TreeItem<>(
                    new TreeItemValue(SelectionType.TEACHER, t.id(), t.label(), t.detail()),
                    createIcon(TEACHER_ICON, "#10b981")
            ));
        }
        root.getChildren().add(teachersBranch);

        // Subjects Branch
        TreeItem<TreeItemValue> subjectsBranch = new TreeItem<>(
                new TreeItemValue(SelectionType.NONE, null, "Subjects", null),
                createIcon(SUBJECT_ICON, "#f59e0b")
        );
        subjectsBranch.setExpanded(true);
        for (var s : treeDto.subjects()) {
            subjectsBranch.getChildren().add(new TreeItem<>(
                    new TreeItemValue(SelectionType.SUBJECT, s.id(), s.label(), s.detail()),
                    createIcon(SUBJECT_ICON, "#f59e0b")
            ));
        }
        root.getChildren().add(subjectsBranch);

        // Rooms Branch
        TreeItem<TreeItemValue> roomsBranch = new TreeItem<>(
                new TreeItemValue(SelectionType.NONE, null, "Rooms / Classrooms", null),
                createIcon(ROOM_ICON, "#8b5cf6")
        );
        roomsBranch.setExpanded(true);
        for (var r : treeDto.rooms()) {
            roomsBranch.getChildren().add(new TreeItem<>(
                    new TreeItemValue(SelectionType.ROOM, r.id(), r.label(), r.detail()),
                    createIcon(ROOM_ICON, "#8b5cf6")
            ));
        }
        root.getChildren().add(roomsBranch);

        entityTreeView.setRoot(root);
        entityTreeView.setShowRoot(false);
    }

    /**
     * Renders the master timetable grid depending on selection filters and configures period / break rows.
     */
    private void renderGrid() {
        timetableGrid.getChildren().clear();
        timetableGrid.getRowConstraints().clear();
        timetableGrid.getColumnConstraints().clear();

        if (currentState == null || selectedType == SelectionType.NONE) {
            return;
        }

        gridTitleLabel.setText(selectedLabel + " Timetable");
        filterStatusLabel.setText("Viewing scheduled lessons for " + selectedType.name().toLowerCase() + ": " + selectedLabel);

        List<PeriodDto> periods = currentState.periods();
        List<DayOfWeek> days = DayOfWeek.workingDays();

        // Column 0: day label; remaining: one per period
        timetableGrid.getColumnConstraints().add(new ColumnConstraints(80));
        for (int i = 0; i < periods.size(); i++) {
            timetableGrid.getColumnConstraints().add(new ColumnConstraints(90));
        }

        // 6 rows: header + 5 days
        timetableGrid.getRowConstraints().add(new RowConstraints(38));
        for (int i = 0; i < 5; i++) {
            timetableGrid.getRowConstraints().add(new RowConstraints(44));
        }

        // ---- Row 0: Header row (period names + times) ----
        for (int pi = 0; pi < periods.size(); pi++) {
            PeriodDto p = periods.get(pi);
            int col = pi + 1;

            VBox headerCell = new VBox(2);
            Label periodLabel = new Label(p.label());
            periodLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: #ffffff;");
            Label timeLabel = new Label(p.startTime() + " - " + p.endTime());
            timeLabel.setStyle("-fx-font-size: 9px; -fx-text-fill: #d0d0d0;");
            headerCell.getChildren().addAll(periodLabel, timeLabel);
            headerCell.setAlignment(javafx.geometry.Pos.CENTER);
            headerCell.setPadding(new Insets(4));
            headerCell.setStyle("-fx-background-color: #333333; -fx-border-color: #000000; -fx-border-width: 0.5px;");
            timetableGrid.add(headerCell, col, 0);
        }

        // ---- Rows 1-5: Day rows ----
        for (int di = 0; di < 5; di++) {
            DayOfWeek day = days.get(di);
            int row = di + 1;
            boolean isMonday = di == 0;

            // Column 0: day label
            Label dayLabel = new Label(day.displayName());
            dayLabel.setMaxWidth(Double.MAX_VALUE);
            dayLabel.setMaxHeight(Double.MAX_VALUE);
            dayLabel.setAlignment(javafx.geometry.Pos.CENTER);
            if (isMonday) {
                dayLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #ffffff; -fx-background-color: #333333; -fx-padding: 8; -fx-border-color: #000000; -fx-border-width: 0.5px;");
            } else {
                dayLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #000000; -fx-background-color: #f5f5f5; -fx-padding: 8; -fx-border-color: #000000; -fx-border-width: 0.5px;");
            }
            timetableGrid.add(dayLabel, 0, row);

            // Lesson cells per period column
            for (int pi = 0; pi < periods.size(); pi++) {
                PeriodDto p = periods.get(pi);
                int col = pi + 1;

                if ("BREAK".equals(p.type())) continue;

                LessonCardDto lesson = findMatchedLesson(day, p.periodNumber());
                if (lesson != null) {
                    VBox card = createCardNode(lesson, false, isMonday);
                    timetableGrid.add(card, col, row);
                } else {
                    Pane empty = createEmptySlotNode(day, p.periodNumber(), isMonday);
                    timetableGrid.add(empty, col, row);
                }
            }
        }

        // ---- Merged break columns (vertical across all 5 day rows) ----
        for (int pi = 0; pi < periods.size(); pi++) {
            PeriodDto p = periods.get(pi);
            if (!"BREAK".equals(p.type())) continue;
            int col = pi + 1;

            String breakText = "\u2615 " + p.label().toUpperCase() + "\n" + p.startTime() + " - " + p.endTime();
            Label breakLabel = new Label(breakText);
            breakLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: #333333;");
            breakLabel.setAlignment(javafx.geometry.Pos.CENTER);
            breakLabel.setMaxWidth(Double.MAX_VALUE);
            breakLabel.setMaxHeight(Double.MAX_VALUE);

            StackPane breakPane = new StackPane(breakLabel);
            breakPane.setStyle("-fx-background-color: #e0e0e0; -fx-border-color: #000000; -fx-border-width: 0.5px; -fx-padding: 6; -fx-alignment: center;");
            timetableGrid.add(breakPane, col, 1, 1, 5);
        }
    }

    /**
     * Renders cards in the unassigned pool area.
     */
    private void renderPool() {
        unassignedPool.getChildren().clear();
        if (currentState == null || selectedType == SelectionType.NONE) {
            return;
        }

        for (LessonCardDto card : currentState.unassignedLessons()) {
            boolean match = false;

            switch (selectedType) {
                case CLASS:
                    if (card.classStreamId().equals(selectedId)) {
                        match = true;
                    }
                    break;
                case TEACHER:
                    if (card.teacherName().equals(selectedLabel)) {
                        match = true;
                    }
                    break;
                case SUBJECT:
                    if (card.subjectCode().equals(selectedDetail)) {
                        match = true;
                    }
                    break;
                case ROOM:
                    // Show all unassigned cards in room view
                    match = true;
                    break;
                default:
                    break;
            }

            if (match) {
                VBox cardNode = createCardNode(card, true, false);
                unassignedPool.getChildren().add(cardNode);
            }
        }
    }

    private LessonCardDto findMatchedLesson(DayOfWeek day, int periodNumber) {
        if (currentState == null) return null;

        for (LessonCardDto card : currentState.gridLessons()) {
            if (card.dayOfWeek() != day || card.periodNumber() != periodNumber) {
                continue;
            }

            switch (selectedType) {
                case CLASS:
                    if (card.classStreamId().equals(selectedId)) {
                        return card;
                    }
                    break;
                case TEACHER:
                    if (card.teacherName().equals(selectedLabel)) {
                        return card;
                    }
                    break;
                case ROOM:
                    if (card.roomId() != null && card.roomId().equals(selectedId)) {
                        return card;
                    }
                    break;
                case SUBJECT:
                    if (card.subjectCode().equals(selectedDetail)) {
                        return card;
                    }
                    break;
                default:
                    break;
            }
        }
        return null;
    }

    /**
     * Creates custom lesson card node with hover effects, context menus, tooltips,
     * and drag-and-drop triggers.
     */
    private VBox createCardNode(LessonCardDto card, boolean isPoolCard, boolean isMonday) {
        VBox node = new VBox();
        node.getStyleClass().add("lesson-card");
        if (isPoolCard) {
            node.getStyleClass().add("unassigned-card");
        } else {
            node.getStyleClass().add("grid-card");
        }

        // Black & White theme
        if (isMonday) {
            node.setStyle("-fx-background-color: #333333; -fx-border-color: #000000; -fx-border-width: 0.5px; -fx-padding: 4;");
        } else {
            node.setStyle("-fx-background-color: #ffffff; -fx-border-color: #000000; -fx-border-width: 0.5px; -fx-padding: 4;");
        }

        // Subject code (bold, centered, large)
        Label subjectLabel = new Label(card.subjectCode());
        subjectLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: " + (isMonday ? "#ffffff" : "#000000") + ";");
        subjectLabel.setAlignment(javafx.geometry.Pos.CENTER);

        // Teacher initials below (smaller, lighter, centered)
        Label teacherLabel = new Label(card.teacherInitials() != null ? card.teacherInitials() : "");
        teacherLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: " + (isMonday ? "#d0d0d0" : "#666666") + ";");
        teacherLabel.setAlignment(javafx.geometry.Pos.CENTER);

        VBox contentBox = new VBox(subjectLabel, teacherLabel);
        contentBox.setSpacing(1);
        contentBox.setAlignment(javafx.geometry.Pos.CENTER);
        contentBox.setPadding(new Insets(4, 2, 4, 2));
        HBox.setHgrow(contentBox, Priority.ALWAYS);

        // Grid Close button (only displayed on Hover to allow quick unassigning)
        if (!isPoolCard) {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Button closeBtn = new Button("×");
            closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + (isMonday ? "#ffffff" : "#333333") + "; -fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 0 2 0 2; -fx-cursor: hand;");
            closeBtn.setOnAction(e -> {
                try {
                    timetableEditorUseCase.unassignEntry(currentTimetableId, card.entryId());
                    refreshWorkspace();
                } catch (Exception ex) {
                    showError("Failed to unassign: " + ex.getMessage());
                }
            });

            closeBtn.setVisible(false);
            node.setOnMouseEntered(e -> closeBtn.setVisible(true));
            node.setOnMouseExited(e -> closeBtn.setVisible(false));

            // Close button overlaid on top-right via a wrapper
            StackPane wrapper = new StackPane(contentBox, closeBtn);
            StackPane.setAlignment(closeBtn, javafx.geometry.Pos.TOP_RIGHT);
            StackPane.setMargin(closeBtn, new Insets(0, 2, 0, 0));
            node.getChildren().add(wrapper);
        } else {
            node.getChildren().add(contentBox);
        }
        node.setSpacing(1);
        node.setPadding(new Insets(6));

        // Display beautiful ToolTip on hover
        Tooltip tooltip = new Tooltip(
                card.subjectName() + " (" + card.subjectCode() + ")\n" +
                "Teacher: " + card.teacherName() + "\n" +
                "Class: " + card.classStreamName() + 
                (card.roomCode() != null ? "\nRoom: " + card.roomCode() : "\nRoom: Unassigned")
        );
        Tooltip.install(node, tooltip);

        // Grid Right-Click context menus
        ContextMenu contextMenu = new ContextMenu();

        // Submenu to pick a Room
        Menu assignRoomMenu = new Menu("Assign Room");
        MenuItem noRoomItem = new MenuItem("None (No Room)");
        noRoomItem.setOnAction(e -> {
            try {
                if (isPoolCard) return;
                timetableEditorUseCase.removeRoom(currentTimetableId, card.entryId());
                refreshWorkspace();
            } catch (Exception ex) {
                showError("Failed to remove room: " + ex.getMessage());
            }
        });
        assignRoomMenu.getItems().add(noRoomItem);
        assignRoomMenu.getItems().add(new SeparatorMenuItem());

        for (var rm : allRooms) {
            MenuItem roomItem = new MenuItem(rm.name() + " (" + rm.code() + ")");
            roomItem.setOnAction(e -> {
                try {
                    if (isPoolCard) return;
                    timetableEditorUseCase.assignRoom(currentTimetableId, card.entryId(), rm.id());
                    refreshWorkspace();
                } catch (Exception ex) {
                    showError("Failed to assign room: " + ex.getMessage());
                }
            });
            assignRoomMenu.getItems().add(roomItem);
        }
        contextMenu.getItems().add(assignRoomMenu);

        // Swap drop functionality (only grid-to-grid)
        if (!isPoolCard) {
            MenuItem unassignItem = new MenuItem("Unassign Lesson");
            unassignItem.setOnAction(e -> {
                try {
                    timetableEditorUseCase.unassignEntry(currentTimetableId, card.entryId());
                    refreshWorkspace();
                } catch (Exception ex) {
                    showError("Failed to unassign: " + ex.getMessage());
                }
            });
            contextMenu.getItems().addAll(new SeparatorMenuItem(), unassignItem);
        }

        node.setOnContextMenuRequested(e -> {
            if (!isPoolCard) {
                contextMenu.show(node, e.getScreenX(), e.getScreenY());
            }
        });

        // Setup Drag detection
        node.setOnDragDetected(event -> {
            activeDragCard = card;
            javafx.scene.input.Dragboard db = node.startDragAndDrop(javafx.scene.input.TransferMode.MOVE);
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString("LESSON_CARD:" + card.teachingAssignmentId() + ":" + (card.entryId() != null ? card.entryId() : "null"));
            db.setContent(content);

            db.setDragView(node.snapshot(null, null));
            event.consume();
        });

        // Set drag over / drop swap handlers (Grid-to-Grid only)
        if (!isPoolCard) {
            node.setOnDragOver(event -> {
                if (event.getGestureSource() != node && event.getDragboard().hasString() && activeDragCard != null) {
                    if (activeDragCard.entryId() != null) {
                        try {
                            var v1 = timetableEditorUseCase.validatePlacement(
                                    currentTimetableId,
                                    activeDragCard.teachingAssignmentId(),
                                    card.dayOfWeek(),
                                    card.periodNumber(),
                                    activeDragCard.entryId(),
                                    activeDragCard.roomId()
                            );
                            var v2 = timetableEditorUseCase.validatePlacement(
                                    currentTimetableId,
                                    card.teachingAssignmentId(),
                                    activeDragCard.dayOfWeek(),
                                    activeDragCard.periodNumber(),
                                    card.entryId(),
                                    card.roomId()
                            );

                            if (v1.valid() && v2.valid()) {
                                node.setStyle("-fx-background-color: #e0e0e0; -fx-border-color: #000000; -fx-border-width: 2px; -fx-border-style: solid;");
                                event.acceptTransferModes(javafx.scene.input.TransferMode.MOVE);
                                conflictWarningLabel.setText("");
                            } else {
                                node.setStyle("-fx-background-color: #cccccc; -fx-border-color: #000000; -fx-border-width: 2px; -fx-border-style: solid;");
                                conflictWarningLabel.setText(!v1.valid() ? v1.reason() : v2.reason());
                            }
                        } catch (Exception ex) {
                            // Suppress exceptions
                        }
                    }
                }
                event.consume();
            });

            node.setOnDragExited(event -> {
                // Restore visual card state
                if (isMonday) {
                    node.setStyle("-fx-background-color: #333333; -fx-border-color: #000000; -fx-border-width: 0.5px; -fx-padding: 4;");
                } else {
                    node.setStyle("-fx-background-color: #ffffff; -fx-border-color: #000000; -fx-border-width: 0.5px; -fx-padding: 4;");
                }
                conflictWarningLabel.setText("");
                event.consume();
            });

            node.setOnDragDropped(event -> {
                boolean success = false;
                if (activeDragCard != null && activeDragCard.entryId() != null) {
                    try {
                        timetableEditorUseCase.swapEntries(
                                currentTimetableId,
                                activeDragCard.entryId(),
                                card.entryId()
                        );
                        success = true;
                    } catch (Exception ex) {
                        showError("Swap failed: " + ex.getMessage());
                    }
                }
                event.setDropCompleted(success);
                event.consume();
                if (success) {
                    refreshWorkspace();
                }
            });
        }

        return node;
    }

    /**
     * Builds an empty grid slot ready to receive cards with real-time green/red visual validation feedback.
     */
    private Pane createEmptySlotNode(DayOfWeek day, int periodNum, boolean isMonday) {
        StackPane slot = new StackPane();
        slot.getStyleClass().add("grid-cell");
        String defaultBg = isMonday ? "#333333" : "#ffffff";
        String defaultBorder = "#000000";
        slot.setStyle("-fx-background-color: " + defaultBg + "; -fx-border-color: " + defaultBorder + "; -fx-border-width: 0.5px;");
        slot.setPrefSize(90, 44);

        slot.setOnMouseEntered(e -> {
            if (slot.getChildren().isEmpty()) {
                slot.setStyle("-fx-background-color: #e0e0e0; -fx-border-color: #000000; -fx-border-width: 1px;");
            }
        });
        slot.setOnMouseExited(e -> {
            if (slot.getChildren().isEmpty()) {
                slot.setStyle("-fx-background-color: " + defaultBg + "; -fx-border-color: " + defaultBorder + "; -fx-border-width: 0.5px;");
            }
        });

        // Drag Over listener
        slot.setOnDragOver(event -> {
            if (event.getGestureSource() != slot && event.getDragboard().hasString() && activeDragCard != null) {
                try {
                    // Determine room assignment based on target view
                    Long targetRoomId = (selectedType == SelectionType.ROOM) ? selectedId : activeDragCard.roomId();

                    var validation = timetableEditorUseCase.validatePlacement(
                            currentTimetableId,
                            activeDragCard.teachingAssignmentId(),
                            day,
                            periodNum,
                            activeDragCard.entryId(),
                            targetRoomId
                    );

                    if (validation.valid()) {
                        // Dark border for valid drop slot
                        slot.setStyle("-fx-background-color: " + defaultBg + "; -fx-border-color: #000000; -fx-border-width: 2px; -fx-border-style: solid;");
                        event.acceptTransferModes(javafx.scene.input.TransferMode.MOVE);
                        conflictWarningLabel.setText("");
                    } else {
                        // Dashed border for conflict slot
                        slot.setStyle("-fx-background-color: " + defaultBg + "; -fx-border-color: #000000; -fx-border-width: 1px; -fx-border-style: dashed;");
                        conflictWarningLabel.setText(validation.reason());
                    }
                } catch (Exception ex) {
                    // Ignore errors during drag validation
                }
            }
            event.consume();
        });

        // Drag Exited listener
        slot.setOnDragExited(event -> {
            slot.setStyle("-fx-background-color: " + defaultBg + "; -fx-border-color: " + defaultBorder + "; -fx-border-width: 0.5px;");
            conflictWarningLabel.setText("");
            event.consume();
        });

        // Drag Dropped listener
        slot.setOnDragDropped(event -> {
            boolean success = false;
            if (activeDragCard != null) {
                try {
                    Long targetRoomId = (selectedType == SelectionType.ROOM) ? selectedId : activeDragCard.roomId();

                    if (activeDragCard.entryId() == null) {
                        // Place unassigned card
                        timetableEditorUseCase.placeLesson(
                                currentTimetableId,
                                activeDragCard.teachingAssignmentId(),
                                day,
                                periodNum,
                                targetRoomId
                        );
                    } else {
                        // Move existing card on grid
                        timetableEditorUseCase.moveEntry(
                                currentTimetableId,
                                activeDragCard.entryId(),
                                day,
                                periodNum,
                                targetRoomId
                        );
                    }
                    success = true;
                } catch (Exception ex) {
                    showError("Placement failed: " + ex.getMessage());
                }
            }
            event.setDropCompleted(success);
            event.consume();
            if (success) {
                refreshWorkspace();
            }
        });

        return slot;
    }

    /**
     * Builds custom SVG outline shapes inside a StackPane to serve as high-quality Explorer node icons.
     */
    private javafx.scene.Node createIcon(String svgPath, String strokeColor) {
        SVGPath path = new SVGPath();
        path.setContent(svgPath);
        path.setFill(Color.TRANSPARENT);
        path.setStroke(Color.web(strokeColor));
        path.setStrokeWidth(1.8);
        
        StackPane container = new StackPane(path);
        container.setPadding(new Insets(2, 4, 2, 2));
        container.setPrefSize(18, 18);
        return container;
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Timetable Workspace Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Builds small scaled-down SVG outline shapes inside a StackPane to serve as mini icons on lesson cards.
     */
    private javafx.scene.Node createSmallIcon(String svgPath, String strokeColor, double size, double strokeWidth) {
        SVGPath path = new SVGPath();
        path.setContent(svgPath);
        path.setFill(Color.TRANSPARENT);
        path.setStroke(Color.web(strokeColor));
        path.setStrokeWidth(strokeWidth);
        
        // Scale 24x24 standard SVG paths to fit the layout
        path.setScaleX(size / 24.0);
        path.setScaleY(size / 24.0);
        
        StackPane container = new StackPane(path);
        container.setPrefSize(size, size);
        container.setMinSize(size, size);
        container.setMaxSize(size, size);
        return container;
    }
}
