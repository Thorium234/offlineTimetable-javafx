package com.thorium.infrastructure;

import com.thorium.application.port.*;
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
import com.thorium.application.usecase.timetable.TimetableEditorUseCase;
import com.thorium.infrastructure.export.CompositeTimetableExporter;
import com.thorium.infrastructure.export.ExcelTimetableExporter;
import com.thorium.infrastructure.export.PdfTimetableExporter;
import com.thorium.infrastructure.persistence.DatabaseInitializer;
import com.thorium.infrastructure.persistence.SQLiteConnectionProvider;
import com.thorium.infrastructure.persistence.repository.*;

import java.nio.file.Path;

public final class ApplicationBootstrap implements Bootstrap {

    private final SQLiteConnectionProvider connectionProvider;
    private final TeacherRepository teacherRepository;
    private final SubjectRepository subjectRepository;
    private final ClassStreamRepository classStreamRepository;
    private final TeachingAssignmentRepository assignmentRepository;
    private final TeacherSubjectRepository teacherSubjectRepository;
    private final TeacherAvailabilityRepository availabilityRepository;
    private final PeriodRepository periodRepository;
    private final BreakRepository breakRepository;
    private final ConstraintRepository constraintRepository;
    private final TimetableRepository timetableRepository;
    private final RoomRepository roomRepository;
    private final SchoolSettingsRepository schoolSettingsRepository;
    private final SchoolSettingsUseCase schoolSettingsUseCase;
    private final TimetableExporter timetableExporter;

    private ApplicationBootstrap(Path databasePath) {
        this.connectionProvider = new SQLiteConnectionProvider(databasePath);
        new DatabaseInitializer(connectionProvider).initialize();

        this.teacherRepository = new SqliteTeacherRepository(connectionProvider);
        this.subjectRepository = new SqliteSubjectRepository(connectionProvider);
        this.classStreamRepository = new SqliteClassStreamRepository(connectionProvider);
        this.assignmentRepository = new SqliteTeachingAssignmentRepository(connectionProvider);
        this.teacherSubjectRepository = new SqliteTeacherSubjectRepository(connectionProvider);
        this.availabilityRepository = new SqliteTeacherAvailabilityRepository(connectionProvider);
        this.periodRepository = new SqlitePeriodRepository(connectionProvider);
        this.breakRepository = new SqliteBreakRepository(connectionProvider);
        this.constraintRepository = new SqliteConstraintRepository(connectionProvider);
        this.timetableRepository = new SqliteTimetableRepository(connectionProvider);
        this.roomRepository = new SqliteRoomRepository(connectionProvider);
        this.schoolSettingsRepository = new SqliteSchoolSettingsRepository(connectionProvider);
        this.schoolSettingsUseCase = new SchoolSettingsUseCase(schoolSettingsRepository);

        PdfTimetableExporter pdfExporter = new PdfTimetableExporter(assignmentRepository, subjectRepository, classStreamRepository, teacherRepository, periodRepository, roomRepository, breakRepository);
        ExcelTimetableExporter excelExporter = new ExcelTimetableExporter(
                assignmentRepository, subjectRepository, teacherRepository, classStreamRepository);
        this.timetableExporter = new CompositeTimetableExporter(pdfExporter, excelExporter);
    }

    public static ApplicationBootstrap create(Path databasePath) {
        return new ApplicationBootstrap(databasePath);
    }

    public TeacherManagementUseCase teacherManagementUseCase() {
        return new TeacherManagementUseCase(teacherRepository);
    }

    public SubjectManagementUseCase subjectManagementUseCase() {
        return new SubjectManagementUseCase(subjectRepository);
    }

    public ClassStreamManagementUseCase classStreamManagementUseCase() {
        return new ClassStreamManagementUseCase(classStreamRepository);
    }

    public AssignmentManagementUseCase assignmentManagementUseCase() {
        return new AssignmentManagementUseCase(
                assignmentRepository, teacherRepository, subjectRepository, classStreamRepository, periodRepository);
    }

    public TeacherSubjectManagementUseCase teacherSubjectManagementUseCase() {
        return new TeacherSubjectManagementUseCase(teacherSubjectRepository, teacherRepository, subjectRepository);
    }

    public PeriodConfigurationUseCase periodConfigurationUseCase() {
        return new PeriodConfigurationUseCase(periodRepository, breakRepository);
    }

    public BreakConfigurationUseCase breakConfigurationUseCase() {
        return new BreakConfigurationUseCase(breakRepository);
    }

    public AvailabilityManagementUseCase availabilityManagementUseCase() {
        return new AvailabilityManagementUseCase(availabilityRepository, teacherRepository);
    }

    public GenerateTimetableUseCase generateTimetableUseCase() {
        return new GenerateTimetableUseCase(
                timetableRepository, assignmentRepository, teacherRepository, subjectRepository,
                classStreamRepository, availabilityRepository, periodRepository, constraintRepository,
                roomRepository);
    }

    public TimetableEditorUseCase timetableEditorUseCase() {
        return new TimetableEditorUseCase(
                timetableRepository, assignmentRepository, teacherRepository, subjectRepository,
                classStreamRepository, availabilityRepository, periodRepository, constraintRepository,
                roomRepository);
    }

    public RoomManagementUseCase roomManagementUseCase() {
        return new RoomManagementUseCase(roomRepository);
    }

    public ExportTimetableUseCase exportTimetableUseCase() {
        return new ExportTimetableUseCase(timetableRepository, timetableExporter);
    }

    public DashboardUseCase dashboardUseCase() {
        return new DashboardUseCase(
                teacherRepository, subjectRepository, classStreamRepository,
                assignmentRepository, timetableRepository, roomRepository);
    }

    public PeriodRepository periodRepository() {
        return periodRepository;
    }

    public ConstraintRepository constraintRepository() {
        return constraintRepository;
    }

    public RoomRepository roomRepository() {
        return roomRepository;
    }

    public SchoolSettingsUseCase schoolSettingsUseCase() {
        return schoolSettingsUseCase;
    }
}
