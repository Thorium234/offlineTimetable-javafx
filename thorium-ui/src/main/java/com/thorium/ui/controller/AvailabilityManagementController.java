package com.thorium.ui.controller;

import com.thorium.application.dto.TeacherAvailabilityDto;
import com.thorium.application.dto.TeacherDto;
import com.thorium.domain.value.DayOfWeek;
import com.thorium.ui.di.AppContext;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class AvailabilityManagementController {

    @FXML private TableView<TeacherAvailabilityDto> availabilityTable;
    @FXML private TableColumn<TeacherAvailabilityDto, String> teacherColumn;
    @FXML private TableColumn<TeacherAvailabilityDto, String> dayColumn;
    @FXML private TableColumn<TeacherAvailabilityDto, Number> periodColumn;
    @FXML private TableColumn<TeacherAvailabilityDto, Boolean> availableColumn;
    @FXML private ComboBox<TeacherDto> teacherCombo;
    @FXML private ComboBox<DayOfWeek> dayCombo;
    @FXML private Spinner<Integer> periodSpinner;
    @FXML private CheckBox availableCheck;
    @FXML private Label messageLabel;

    private Long editingId;

    @FXML
    private void initialize() {
        teacherColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().teacherName()));
        dayColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().dayOfWeek().displayName()));
        periodColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().periodNumber()));
        availableColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().available()));
        dayCombo.setItems(FXCollections.observableArrayList(DayOfWeek.values()));
        periodSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 12, 1));
        availableCheck.setSelected(true);
        teacherCombo.setItems(FXCollections.observableArrayList(AppContext.get().teacherManagementUseCase().findAll()));
        teacherCombo.setCellFactory(lv -> new ListCell<TeacherDto>() {
            @Override protected void updateItem(TeacherDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name() + " (" + item.code() + ")");
            }
        });
        refreshTable();
        availabilityTable.getSelectionModel().selectedItemProperty().addListener((obs, o, s) -> {
            if (s != null) populateForm(s);
        });
    }

    @FXML private void onSave() {
        try {
            TeacherAvailabilityDto dto = new TeacherAvailabilityDto(editingId,
                    teacherCombo.getValue().id(), teacherCombo.getValue().name(),
                    dayCombo.getValue(), periodSpinner.getValue(), availableCheck.isSelected());
            AppContext.get().availabilityManagementUseCase().save(dto);
            clearForm(); refreshTable(); showMessage("Saved", false);
        } catch (IllegalArgumentException | IllegalStateException e) { showMessage(e.getMessage(), true); }
        catch (Exception e) { showMessage("An unexpected error occurred", true); }
    }

    @FXML private void onDelete() {
        TeacherAvailabilityDto selected = availabilityTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showMessage("Select a record", true); return; }
        try {
            AppContext.get().availabilityManagementUseCase().delete(selected.id());
            clearForm(); refreshTable(); showMessage("Deleted", false);
        } catch (IllegalArgumentException | IllegalStateException e) { showMessage(e.getMessage(), true); }
        catch (Exception e) { showMessage("An unexpected error occurred", true); }
    }

    @FXML private void onClear() { clearForm(); }

    private void refreshTable() {
        availabilityTable.setItems(FXCollections.observableArrayList(
                AppContext.get().availabilityManagementUseCase().findAll()));
    }

    private void populateForm(TeacherAvailabilityDto dto) {
        editingId = dto.id();
        teacherCombo.getItems().stream().filter(t -> t.id().equals(dto.teacherId()))
                .findFirst().ifPresent(t -> teacherCombo.getSelectionModel().select(t));
        dayCombo.getSelectionModel().select(dto.dayOfWeek());
        periodSpinner.getValueFactory().setValue(dto.periodNumber());
        availableCheck.setSelected(dto.available());
    }

    private void clearForm() {
        editingId = null;
        teacherCombo.getSelectionModel().clearSelection();
        dayCombo.getSelectionModel().clearSelection();
        periodSpinner.getValueFactory().setValue(1);
        availableCheck.setSelected(true);
        availabilityTable.getSelectionModel().clearSelection();
    }

    private void showMessage(String msg, boolean error) {
        messageLabel.setText(msg);
        messageLabel.getStyleClass().removeAll("error", "success");
        messageLabel.getStyleClass().add(error ? "error" : "success");
    }
}
