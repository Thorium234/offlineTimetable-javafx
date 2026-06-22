package com.thorium.ui.di;

import com.thorium.application.usecase.assignment.AssignmentManagementUseCase;
import com.thorium.application.usecase.availability.AvailabilityManagementUseCase;
import com.thorium.application.usecase.breaks.BreakConfigurationUseCase;
import com.thorium.application.usecase.classstream.ClassStreamManagementUseCase;
import com.thorium.application.usecase.dashboard.DashboardUseCase;
import com.thorium.application.usecase.export.ExportTimetableUseCase;
import com.thorium.application.usecase.period.PeriodConfigurationUseCase;
import com.thorium.application.usecase.room.RoomManagementUseCase;
import com.thorium.application.usecase.settings.SchoolSettingsUseCase;
import com.thorium.application.usecase.subject.SubjectManagementUseCase;
import com.thorium.application.usecase.teacher.TeacherManagementUseCase;
import com.thorium.application.usecase.timetable.GenerateTimetableUseCase;
import com.thorium.application.usecase.timetable.TimetableEditorUseCase;
import com.thorium.infrastructure.ApplicationBootstrap;

import java.nio.file.Path;

public final class AppContext {

    private static AppContext instance;

    private final ApplicationBootstrap bootstrap;

    private AppContext(Path databasePath) {
        this.bootstrap = ApplicationBootstrap.create(databasePath);
    }

    public static synchronized AppContext initialize(Path databasePath) {
        if (instance == null) {
            instance = new AppContext(databasePath);
        }
        return instance;
    }

    public static AppContext get() {
        if (instance == null) {
            throw new IllegalStateException("AppContext not initialized");
        }
        return instance;
    }

    public DashboardUseCase dashboardUseCase() {
        return bootstrap.dashboardUseCase();
    }

    public TeacherManagementUseCase teacherManagementUseCase() {
        return bootstrap.teacherManagementUseCase();
    }

    public SubjectManagementUseCase subjectManagementUseCase() {
        return bootstrap.subjectManagementUseCase();
    }

    public ClassStreamManagementUseCase classStreamManagementUseCase() {
        return bootstrap.classStreamManagementUseCase();
    }

    public AssignmentManagementUseCase assignmentManagementUseCase() {
        return bootstrap.assignmentManagementUseCase();
    }

    public PeriodConfigurationUseCase periodConfigurationUseCase() {
        return bootstrap.periodConfigurationUseCase();
    }

    public BreakConfigurationUseCase breakConfigurationUseCase() {
        return bootstrap.breakConfigurationUseCase();
    }

    public AvailabilityManagementUseCase availabilityManagementUseCase() {
        return bootstrap.availabilityManagementUseCase();
    }

    public GenerateTimetableUseCase generateTimetableUseCase() {
        return bootstrap.generateTimetableUseCase();
    }

    public TimetableEditorUseCase timetableEditorUseCase() {
        return bootstrap.timetableEditorUseCase();
    }

    public RoomManagementUseCase roomManagementUseCase() {
        return bootstrap.roomManagementUseCase();
    }

    public SchoolSettingsUseCase schoolSettingsUseCase() {
        return bootstrap.schoolSettingsUseCase();
    }

    public ExportTimetableUseCase exportTimetableUseCase() {
        return bootstrap.exportTimetableUseCase();
    }
}
