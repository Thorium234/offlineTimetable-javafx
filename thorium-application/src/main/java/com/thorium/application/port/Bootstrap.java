package com.thorium.application.port;

import com.thorium.application.usecase.assignment.AssignmentManagementUseCase;
import com.thorium.application.usecase.assignment.TeacherSubjectManagementUseCase;
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
import com.thorium.application.usecase.data.DataManagementUseCase;
import com.thorium.application.usecase.timetable.TimetableEditorUseCase;

public interface Bootstrap {
    DashboardUseCase dashboardUseCase();
    TeacherManagementUseCase teacherManagementUseCase();
    SubjectManagementUseCase subjectManagementUseCase();
    ClassStreamManagementUseCase classStreamManagementUseCase();
    AssignmentManagementUseCase assignmentManagementUseCase();
    TeacherSubjectManagementUseCase teacherSubjectManagementUseCase();
    PeriodConfigurationUseCase periodConfigurationUseCase();
    BreakConfigurationUseCase breakConfigurationUseCase();
    AvailabilityManagementUseCase availabilityManagementUseCase();
    GenerateTimetableUseCase generateTimetableUseCase();
    TimetableEditorUseCase timetableEditorUseCase();
    RoomManagementUseCase roomManagementUseCase();
    SchoolSettingsUseCase schoolSettingsUseCase();
    ExportTimetableUseCase exportTimetableUseCase();
    DataManagementUseCase dataManagementUseCase();
}
