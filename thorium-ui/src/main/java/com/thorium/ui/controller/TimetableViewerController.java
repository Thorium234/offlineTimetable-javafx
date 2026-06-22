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

        // Set Headers
        gridTitleLabel.setText(selectedLabel + " Timetable");
        filterStatusLabel.setText("Viewing scheduled lessons for " + selectedType.name().toLowerCase() + ": " + selectedLabel);

        List<DayOfWeek> days = List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

        // Column Constraints
        ColumnConstraints col0 = new ColumnConstraints(110);
        timetableGrid.getColumnConstraints().add(col0);
        for (int i = 0; i < 5; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(18); // equally space column widths
            timetableGrid.getColumnConstraints().add(col);
        }

        // Row 0 Day Headers
        for (int c = 0; c < days.size(); c++) {
            Label dayLabel = new Label(days.get(c).displayName());
            dayLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #334155; -fx-font-size: 13px;");
            StackPane headerPane = new StackPane(dayLabel);
            headerPane.setStyle("-fx-background-color: #f1f5f9; -fx-padding: 8; -fx-alignment: center; -fx-border-color: #e2e8f0; -fx-border-width: 0 0 2 0;");
            timetableGrid.add(headerPane, c + 1, 0);
        }

        // Set up mapping of periods and breaks to grid rows
        int currentRow = 1;
        Map<Integer, Integer> periodToRowMap = new HashMap<>();
        Map<Integer, BreakDto> breakRows = new HashMap<>(); // row index -> BreakDto

        List<PeriodDto> periods = currentState.periods();
        List<BreakDto> breaks = AppContext.get().breakConfigurationUseCase().findAll();

        for (PeriodDto period : periods) {
            periodToRowMap.put(period.periodNumber(), currentRow);
            final int pNum = period.periodNumber();

            Optional<BreakDto> activeBreak = breaks.stream()
                    .filter(b -> !b.isBeforePeriodOne() && b.afterPeriod() == pNum - 1)
                    .findFirst();

            currentRow++;
            if (activeBreak.isPresent()) {
                breakRows.put(currentRow, activeBreak.get());
                currentRow++;
            }
        }

        // Draw Period Rows
        for (PeriodDto period : periods) {
            int r = periodToRowMap.get(period.periodNumber());

            // Left Header (Column 0)
            Label pLabel = new Label(period.label());
            pLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e293b; -fx-font-size: 12px;");
            Label timeLabel = new Label(period.startTime() + " - " + period.endTime());
            timeLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748b;");

            VBox pBox = new VBox(pLabel, timeLabel);
            pBox.setSpacing(2);
            pBox.setPadding(new Insets(6));
            pBox.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-width: 0 1 1 0; -fx-alignment: center;");
            timetableGrid.add(pBox, 0, r);

            // Columns 1 to 5 (Monday to Friday)
            for (int c = 0; c < days.size(); c++) {
                DayOfWeek day = days.get(c);
                LessonCardDto matchedLesson = findMatchedLesson(day, period.periodNumber());

                if (matchedLesson != null) {
                    VBox cardNode = createCardNode(matchedLesson, false);
                    timetableGrid.add(cardNode, c + 1, r);
                } else {
                    Pane emptySlot = createEmptySlotNode(day, period.periodNumber());
                    timetableGrid.add(emptySlot, c + 1, r);
                }
            }
        }

        // Draw Break Rows (horizontal dividers)
        for (var entry : breakRows.entrySet()) {
            int r = entry.getKey();
            BreakDto brk = entry.getValue();

            Label breakLabel = new Label("☕ " + brk.name() + " (" + brk.durationMinutes() + " mins)");
            breakLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569; -fx-font-size: 11px;");
            StackPane breakPane = new StackPane(breakLabel);
            breakPane.setStyle("-fx-background-color: #f1f5f9; -fx-background-radius: 4; -fx-padding: 6; -fx-alignment: center;");
            
            // Span break cell across whole row (0 to 5)
            timetableGrid.add(breakPane, 0, r, 6, 1);
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
                VBox cardNode = createCardNode(card, true);
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
    private VBox createCardNode(LessonCardDto card, boolean isPoolCard) {
        VBox node = new VBox();
        node.getStyleClass().add("lesson-card");
        if (isPoolCard) {
            node.getStyleClass().add("unassigned-card");
        } else {
            node.getStyleClass().add("grid-card");
        }

        // Visual Layout Styles & Colors
        String baseColor = card.subjectColor();
        if (card.roomId() == null && !isPoolCard) {
            // White stripes indicator for no-room placement
            node.setStyle(
                    "-fx-background-color: repeating-linear-gradient(45deg, " +
                    baseColor + ", " + baseColor + " 8px, #ffffff 8px, #ffffff 16px); " +
                    "-fx-border-color: derive(" + baseColor + ", -25%); " +
                    "-fx-border-width: 1.5px; -fx-border-radius: 6px; -fx-background-radius: 6px;"
            );
        } else {
            node.setStyle(
                    "-fx-background-color: " + baseColor + "; " +
                    "-fx-border-color: derive(" + baseColor + ", -25%); " +
                    "-fx-border-width: 1.5px; -fx-border-radius: 6px; -fx-background-radius: 6px;"
            );
        }

        // Core card labels and icons rows
        HBox subjectRow = new HBox(4);
        subjectRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label subjectLabel = new Label(card.subjectCode());
        subjectLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #0f172a;");
        subjectRow.getChildren().addAll(
                createSmallIcon(SUBJECT_ICON, "#1e293b", 10, 1.5),
                subjectLabel
        );

        HBox teacherOrClassRow = new HBox(4);
        teacherOrClassRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label secondaryLabel = new Label();
        secondaryLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #334155; -fx-font-weight: 500;");

        HBox roomRow = new HBox(4);
        roomRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label roomLabel = new Label();
        roomRow.getChildren().add(roomLabel);

        // Adapt contents and icons dynamically to the active Resource view
        if (selectedType == SelectionType.CLASS) {
            secondaryLabel.setText(card.teacherInitials() != null ? card.teacherInitials() : "No Teacher");
            teacherOrClassRow.getChildren().addAll(
                    createSmallIcon(TEACHER_ICON, "#334155", 10, 1.5),
                    secondaryLabel
            );
            
            if (card.roomCode() != null) {
                roomLabel.setText(card.roomCode());
                roomLabel.setStyle("-fx-font-size: 9px; -fx-font-style: italic; -fx-text-fill: #475569;");
                roomRow.getChildren().add(0, createSmallIcon(ROOM_ICON, "#475569", 10, 1.5));
            } else {
                roomLabel.setText("No Room");
                roomLabel.setStyle("-fx-font-size: 9px; -fx-font-style: italic; -fx-text-fill: #94a3b8;");
            }
        } else if (selectedType == SelectionType.TEACHER) {
            secondaryLabel.setText(card.classStreamName());
            teacherOrClassRow.getChildren().addAll(
                    createSmallIcon(CLASS_ICON, "#334155", 10, 1.5),
                    secondaryLabel
            );
            
            if (card.roomCode() != null) {
                roomLabel.setText(card.roomCode());
                roomLabel.setStyle("-fx-font-size: 9px; -fx-font-style: italic; -fx-text-fill: #475569;");
                roomRow.getChildren().add(0, createSmallIcon(ROOM_ICON, "#475569", 10, 1.5));
            } else {
                roomLabel.setText("No Room");
                roomLabel.setStyle("-fx-font-size: 9px; -fx-font-style: italic; -fx-text-fill: #94a3b8;");
            }
        } else if (selectedType == SelectionType.ROOM) {
            secondaryLabel.setText(card.classStreamName());
            teacherOrClassRow.getChildren().addAll(
                    createSmallIcon(CLASS_ICON, "#334155", 10, 1.5),
                    secondaryLabel
            );
            
            if (card.teacherInitials() != null) {
                roomLabel.setText(card.teacherInitials());
                roomLabel.setStyle("-fx-font-size: 9px; -fx-text-fill: #475569;");
                roomRow.getChildren().add(0, createSmallIcon(TEACHER_ICON, "#475569", 10, 1.5));
            } else {
                roomLabel.setText("No Teacher");
                roomLabel.setStyle("-fx-font-size: 9px; -fx-text-fill: #94a3b8;");
            }
        } else {
            secondaryLabel.setText(card.classStreamName());
            teacherOrClassRow.getChildren().addAll(
                    createSmallIcon(CLASS_ICON, "#334155", 10, 1.5),
                    secondaryLabel
            );
            
            if (card.teacherInitials() != null) {
                roomLabel.setText(card.teacherInitials());
                roomLabel.setStyle("-fx-font-size: 9px; -fx-text-fill: #475569;");
                roomRow.getChildren().add(0, createSmallIcon(TEACHER_ICON, "#475569", 10, 1.5));
            } else {
                roomLabel.setText("No Teacher");
                roomLabel.setStyle("-fx-font-size: 9px; -fx-text-fill: #94a3b8;");
            }
        }

        HBox topRow = new HBox(subjectRow);
        topRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // Grid Close button (only displayed on Hover to allow quick unassigning)
        if (!isPoolCard) {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Button closeBtn = new Button("×");
            closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ef4444; -fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 0 2 0 2; -fx-cursor: hand;");
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

            topRow.getChildren().addAll(spacer, closeBtn);
        }

        node.getChildren().addAll(topRow, teacherOrClassRow, roomRow);
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
                                node.setStyle("-fx-background-color: #fef3c7; -fx-border-color: #d97706; -fx-border-width: 2px; -fx-border-style: solid;");
                                event.acceptTransferModes(javafx.scene.input.TransferMode.MOVE);
                                conflictWarningLabel.setText("");
                            } else {
                                node.setStyle("-fx-background-color: #fef2f2; -fx-border-color: #ef4444; -fx-border-width: 2px; -fx-border-style: solid;");
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
                String origColor = card.subjectColor();
                if (card.roomId() == null) {
                    node.setStyle(
                            "-fx-background-color: repeating-linear-gradient(45deg, " +
                            origColor + ", " + origColor + " 8px, #ffffff 8px, #ffffff 16px); " +
                            "-fx-border-color: derive(" + origColor + ", -25%); " +
                            "-fx-border-width: 1.5px; -fx-border-radius: 6px; -fx-background-radius: 6px;"
                    );
                } else {
                    node.setStyle(
                            "-fx-background-color: " + origColor + "; " +
                            "-fx-border-color: derive(" + origColor + ", -25%); " +
                            "-fx-border-width: 1.5px; -fx-border-radius: 6px; -fx-background-radius: 6px;"
                    );
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
    private Pane createEmptySlotNode(DayOfWeek day, int periodNum) {
        StackPane slot = new StackPane();
        slot.getStyleClass().add("grid-cell");
        slot.setStyle("-fx-background-color: transparent; -fx-border-color: #cbd5e1; -fx-border-width: 1px; -fx-border-style: dashed; -fx-border-radius: 6px; -fx-background-radius: 6px;");
        slot.setPrefSize(105, 60);

        // Hover Highlight
        slot.setOnMouseEntered(e -> {
            if (slot.getChildren().isEmpty()) {
                slot.setStyle("-fx-background-color: #f1f5f9; -fx-border-color: #94a3b8; -fx-border-width: 1.5px; -fx-border-style: dashed; -fx-border-radius: 6px; -fx-background-radius: 6px;");
            }
        });
        slot.setOnMouseExited(e -> {
            if (slot.getChildren().isEmpty()) {
                slot.setStyle("-fx-background-color: transparent; -fx-border-color: #cbd5e1; -fx-border-width: 1px; -fx-border-style: dashed; -fx-border-radius: 6px; -fx-background-radius: 6px;");
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
                        // Green highlight for valid drop slot
                        slot.setStyle("-fx-background-color: #f0fdf4; -fx-border-color: #22c55e; -fx-border-width: 2px; -fx-border-style: solid; -fx-border-radius: 6px; -fx-background-radius: 6px;");
                        event.acceptTransferModes(javafx.scene.input.TransferMode.MOVE);
                        conflictWarningLabel.setText("");
                    } else {
                        // Red highlight for conflict slot
                        slot.setStyle("-fx-background-color: #fef2f2; -fx-border-color: #ef4444; -fx-border-width: 2px; -fx-border-style: solid; -fx-border-radius: 6px; -fx-background-radius: 6px;");
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
            slot.setStyle("-fx-background-color: transparent; -fx-border-color: #cbd5e1; -fx-border-width: 1px; -fx-border-style: dashed; -fx-border-radius: 6px; -fx-background-radius: 6px;");
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
