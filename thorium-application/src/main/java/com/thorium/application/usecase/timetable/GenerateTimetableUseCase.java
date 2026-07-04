package com.thorium.application.usecase.timetable;

import com.thorium.application.dto.TimetableDto;
import com.thorium.application.dto.TimetableEntryDto;
import com.thorium.application.port.*;
import com.thorium.application.util.NameFormatter;
import com.thorium.domain.constraint.SoftConstraintScorer;
import com.thorium.domain.model.*;
import com.thorium.domain.scheduling.GenerationProgressCallback;
import com.thorium.domain.scheduling.SchedulingContext;
import com.thorium.domain.scheduling.TimetableGenerationResult;
import com.thorium.domain.scheduling.TimetableGenerator;
import com.thorium.domain.value.SubjectColorPalette;
import com.thorium.domain.value.TimetableStatus;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class GenerateTimetableUseCase {

    private static final Logger LOG = Logger.getLogger(GenerateTimetableUseCase.class.getName());

    private static final DateTimeFormatter NAME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final TimetableRepository timetableRepository;
    private final TeachingAssignmentRepository assignmentRepository;
    private final TeacherRepository teacherRepository;
    private final SubjectRepository subjectRepository;
    private final ClassStreamRepository classStreamRepository;
    private final TeacherAvailabilityRepository availabilityRepository;
    private final PeriodRepository periodRepository;
    private final ConstraintRepository constraintRepository;
    private final RoomRepository roomRepository;
    private final SchoolSettingsRepository schoolSettingsRepository;
    private final TimetableGenerator generator;

    public GenerateTimetableUseCase(TimetableRepository timetableRepository,
                                    TeachingAssignmentRepository assignmentRepository,
                                    TeacherRepository teacherRepository,
                                    SubjectRepository subjectRepository,
                                    ClassStreamRepository classStreamRepository,
                                    TeacherAvailabilityRepository availabilityRepository,
                                    PeriodRepository periodRepository,
                                    ConstraintRepository constraintRepository,
                                    RoomRepository roomRepository,
                                    SchoolSettingsRepository schoolSettingsRepository) {
        this.timetableRepository = timetableRepository;
        this.assignmentRepository = assignmentRepository;
        this.teacherRepository = teacherRepository;
        this.subjectRepository = subjectRepository;
        this.classStreamRepository = classStreamRepository;
        this.availabilityRepository = availabilityRepository;
        this.periodRepository = periodRepository;
        this.constraintRepository = constraintRepository;
        this.roomRepository = roomRepository;
        this.schoolSettingsRepository = schoolSettingsRepository;
        this.generator = new TimetableGenerator();
    }

    public TimetableDto execute(String name) {
        return execute(name, null);
    }

    public TimetableDto execute(String name, GenerationProgressCallback callback) {
        List<TeachingAssignment> assignments = assignmentRepository.findAll();
        if (assignments.isEmpty()) {
            throw new IllegalStateException("No teaching assignments defined");
        }

        List<Integer> lessonPeriodNumbers = periodRepository.findLessonPeriodNumbers();
        if (lessonPeriodNumbers.isEmpty()) {
            throw new IllegalStateException("No lesson periods configured");
        }

        SchoolSettings settings = schoolSettingsRepository.get();
        SoftConstraintScorer scorer = new SoftConstraintScorer(
                settings.getSpreadWeight(),
                settings.getConsecutiveWeight(),
                settings.getBalanceWeight()
        );

        SchedulingContext context = SchedulingContext.builder()
                .assignments(assignments)
                .teachers(teacherRepository.findAll())
                .subjects(subjectRepository.findAll())
                .classStreams(classStreamRepository.findAll())
                .teacherAvailability(availabilityRepository.findAll())
                .lessonPeriodNumbers(lessonPeriodNumbers)
                .constraints(constraintRepository.findAll())
                .softConstraintScorer(scorer)
                .build();

        List<String> preflight = generator.preflightChecks(context);
        if (!preflight.isEmpty()) {
            LOG.info("Preflight: " + String.join("; ", preflight));
            if (callback != null) {
                for (String issue : preflight) {
                    callback.log("WARN", issue);
                }
            }
        }

        TimetableGenerationResult result = generator.generate(context, callback);
        boolean complete = result.isComplete(context);

        if (!result.isSuccess() && !complete) {
            if (result.schedule().placedLessons().isEmpty()) {
                List<String> messages = new ArrayList<>(result.errors());
                messages.addAll(result.warnings());
                messages.addAll(preflight);
                throw new IllegalStateException("Timetable generation failed: " + String.join("; ", messages));
            }
        }

        Timetable timetable = new Timetable();
        timetable.setName(name != null && !name.isBlank() ? name : "Timetable " + LocalDateTime.now().format(NAME_FORMAT));
        timetable.setStatus(complete ? TimetableStatus.GENERATED : TimetableStatus.DRAFT);
        timetable.setCreatedAt(LocalDateTime.now());
        timetable.setQualityScore(result.qualityScore());

        List<TimetableEntry> entries = result.schedule().toEntries(null);
        Timetable saved = timetableRepository.saveWithEntries(timetable, entries);
        return toDto(timetableRepository.findByIdWithEntries(saved.getId())
                .orElseThrow(() -> new IllegalStateException("Failed to load saved timetable")));
    }

    public List<TimetableDto> findAll() {
        return timetableRepository.findAll().stream()
                .map(t -> timetableRepository.findByIdWithEntries(t.getId())
                        .map(this::toDto)
                        .orElse(toDtoWithoutEntries(t)))
                .toList();
    }

    public TimetableDto findById(Long id) {
        return timetableRepository.findByIdWithEntries(id)
                .map(this::toDto)
                .orElseThrow(() -> new IllegalArgumentException("Timetable not found: " + id));
    }

    private TimetableDto toDto(TimetableRepository.TimetableWithEntries data) {
        Map<Long, TeachingAssignment> assignmentMap = new HashMap<>();
        Map<Long, Teacher> teacherMap = new HashMap<>();
        Map<Long, Subject> subjectMap = new HashMap<>();
        Map<Long, ClassStream> classStreamMap = new HashMap<>();
        Map<Long, Room> roomMap = new HashMap<>();
        for (var a : assignmentRepository.findAll()) assignmentMap.put(a.getId(), a);
        for (var t : teacherRepository.findAll()) teacherMap.put(t.getId(), t);
        for (var s : subjectRepository.findAll()) subjectMap.put(s.getId(), s);
        for (var c : classStreamRepository.findAll()) classStreamMap.put(c.getId(), c);
        for (var r : roomRepository.findAll()) roomMap.put(r.getId(), r);

        List<TimetableEntryDto> entryDtos = data.entries().stream()
                .map(e -> toEntryDto(e, assignmentMap, teacherMap, subjectMap, classStreamMap, roomMap))
                .toList();
        Timetable t = data.timetable();
        return new TimetableDto(t.getId(), t.getName(), t.getStatus(), t.getCreatedAt(), t.getQualityScore(), entryDtos);
    }

    private TimetableDto toDtoWithoutEntries(Timetable t) {
        return new TimetableDto(t.getId(), t.getName(), t.getStatus(), t.getCreatedAt(), t.getQualityScore(), List.of());
    }

    private TimetableEntryDto toEntryDto(TimetableEntry entry, Map<Long, TeachingAssignment> assignmentMap,
                                          Map<Long, Teacher> teacherMap, Map<Long, Subject> subjectMap,
                                          Map<Long, ClassStream> classStreamMap, Map<Long, Room> roomMap) {
        TeachingAssignment assignment = assignmentMap.get(entry.getTeachingAssignmentId());
        if (assignment == null) throw new IllegalStateException("Assignment not found for entry " + entry.getId());
        Teacher teacher = teacherMap.get(assignment.getTeacherId());
        if (teacher == null) throw new IllegalStateException("Teacher not found for assignment " + assignment.getId());
        Subject subject = subjectMap.get(assignment.getSubjectId());
        if (subject == null) throw new IllegalStateException("Subject not found for assignment " + assignment.getId());
        ClassStream classStream = classStreamMap.get(assignment.getClassStreamId());
        if (classStream == null) throw new IllegalStateException("ClassStream not found for assignment " + assignment.getId());
        String roomCode = entry.getRoomId() != null
                ? roomMap.get(entry.getRoomId()).getCode()
                : null;
        return new TimetableEntryDto(
                entry.getId(),
                entry.getTeachingAssignmentId(),
                teacher.getName(),
                NameFormatter.initials(teacher.getName()),
                subject.getName(),
                subject.getCode(),
                SubjectColorPalette.resolveColor(subject.getId(), subject.getColor()),
                classStream.getDisplayName(),
                classStream.getId(),
                roomCode,
                entry.getRoomId(),
                entry.getDayOfWeek(),
                entry.getPeriodNumber()
        );
    }
}
