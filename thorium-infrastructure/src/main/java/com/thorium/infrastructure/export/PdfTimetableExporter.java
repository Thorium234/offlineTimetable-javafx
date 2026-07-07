package com.thorium.infrastructure.export;

import com.thorium.application.port.*;
import com.thorium.application.util.NameFormatter;
import com.thorium.domain.model.*;
import com.thorium.domain.model.timeblock.BlockType;
import com.thorium.domain.model.timeblock.LessonBlock;
import com.thorium.domain.model.timeblock.TimeBlock;
import com.thorium.domain.scheduling.DailyTimelineGenerator;
import com.thorium.domain.value.DayOfWeek;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class PdfTimetableExporter implements TimetableExporter {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final TeachingAssignmentRepository assignmentRepository;
    private final SubjectRepository subjectRepository;
    private final ClassStreamRepository classStreamRepository;
    private final TeacherRepository teacherRepository;
    private final PeriodRepository periodRepository;
    private final RoomRepository roomRepository;
    private final BreakRepository breakRepository;
    private final SchoolSettingsRepository schoolSettingsRepository;

    public PdfTimetableExporter(TeachingAssignmentRepository assignmentRepository,
                                SubjectRepository subjectRepository,
                                ClassStreamRepository classStreamRepository,
                                TeacherRepository teacherRepository,
                                PeriodRepository periodRepository,
                                RoomRepository roomRepository,
                                BreakRepository breakRepository) {
        this(assignmentRepository, subjectRepository, classStreamRepository,
                teacherRepository, periodRepository, roomRepository, breakRepository, null);
    }

    public PdfTimetableExporter(TeachingAssignmentRepository assignmentRepository,
                                SubjectRepository subjectRepository,
                                ClassStreamRepository classStreamRepository,
                                TeacherRepository teacherRepository,
                                PeriodRepository periodRepository,
                                RoomRepository roomRepository,
                                BreakRepository breakRepository,
                                SchoolSettingsRepository schoolSettingsRepository) {
        this.assignmentRepository = assignmentRepository;
        this.subjectRepository = subjectRepository;
        this.classStreamRepository = classStreamRepository;
        this.teacherRepository = teacherRepository;
        this.periodRepository = periodRepository;
        this.roomRepository = roomRepository;
        this.breakRepository = breakRepository;
        this.schoolSettingsRepository = schoolSettingsRepository;
    }

    @Override
    public void exportPdf(TimetableRepository.TimetableWithEntries data, Path outputPath) {
        byte[] pdfBytes = renderPdfToBytes(data);
        try {
            java.nio.file.Files.write(outputPath, pdfBytes);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write PDF file", e);
        }
    }

    @Override
    public byte[] renderPdfToBytes(TimetableRepository.TimetableWithEntries data) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(65536);
        writeAllClassesPdf(data, bos, schoolName(), data.timetable().getName());
        return bos.toByteArray();
    }

    @Override
    public void exportExcel(TimetableRepository.TimetableWithEntries data, Path outputPath) {
        throw new UnsupportedOperationException("Use ExcelTimetableExporter");
    }

    @Override
    public byte[] renderTeacherPdfToBytes(TimetableRepository.TimetableWithEntries data, Long teacherId) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(65536);
        writeTeacherPdf(data, teacherId, bos);
        return bos.toByteArray();
    }

    @Override
    public byte[] renderStreamPdfToBytes(TimetableRepository.TimetableWithEntries data, String stream) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(65536);
        writeFilteredPdf(data, "Stream " + stream, entries -> {
            List<TimetableEntry> filtered = new ArrayList<>();
            for (TimetableEntry e : entries) {
                TeachingAssignment a = assignmentMap().get(e.getTeachingAssignmentId());
                if (a != null) {
                    ClassStream cs = classStreamRepository.findById(a.getClassStreamId()).orElse(null);
                    if (cs != null && stream.equals(cs.getStream())) {
                        filtered.add(e);
                    }
                }
            }
            return filtered;
        }, bos);
        return bos.toByteArray();
    }

    @Override
    public byte[] renderGradePdfToBytes(TimetableRepository.TimetableWithEntries data, int form) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(65536);
        writeFilteredPdf(data, "Form " + form, entries -> {
            List<TimetableEntry> filtered = new ArrayList<>();
            for (TimetableEntry e : entries) {
                TeachingAssignment a = assignmentMap().get(e.getTeachingAssignmentId());
                if (a != null) {
                    ClassStream cs = classStreamRepository.findById(a.getClassStreamId()).orElse(null);
                    if (cs != null && cs.getForm() == form) {
                        filtered.add(e);
                    }
                }
            }
            return filtered;
        }, bos);
        return bos.toByteArray();
    }

    @Override
    public byte[] renderAllTeachersPdfToBytes(TimetableRepository.TimetableWithEntries data) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(65536);
        writeAllTeachersPdf(data, bos);
        return bos.toByteArray();
    }

    @Override
    public byte[] renderAllClassesPdfToBytes(TimetableRepository.TimetableWithEntries data) {
        return renderPdfToBytes(data);
    }

    // ---- Private helpers ----

    private Map<Long, TeachingAssignment> assignmentMap() {
        return assignmentRepository.findAll().stream()
                .collect(Collectors.toMap(TeachingAssignment::getId, a -> a));
    }

    private String subjectCode(Long subjectId) {
        return subjectRepository.findById(subjectId)
                .map(s -> {
                    if (s.getCode() != null && !s.getCode().isBlank()) {
                        return s.getCode().toUpperCase();
                    }
                    String n = s.getName();
                    return n.substring(0, Math.min(4, n.length())).toUpperCase();
                })
                .orElse("?");
    }

    private String teacherInitials(Long teacherId) {
        return teacherRepository.findById(teacherId)
                .map(t -> NameFormatter.initials(t.getName()))
                .orElse("?");
    }

    private String roomCodeIfAny(Long roomId) {
        if (roomId == null) return null;
        try {
            return roomRepository.findById(roomId).map(Room::getCode).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private String classDisplayName(Long classStreamId) {
        return classStreamRepository.findById(classStreamId)
                .map(ClassStream::getDisplayName)
                .orElse("Class #" + classStreamId);
    }

    private String schoolName() {
        if (schoolSettingsRepository == null) return "Thorium Timetable";
        try {
            return schoolSettingsRepository.get().getSchoolName();
        } catch (Exception e) {
            return "Thorium Timetable";
        }
    }

    // ---- Layout ----

    private record Layout(float margin, float tableLeft, float tableWidth,
                          float dayColW, float nonLessonColW, float lessonColW,
                          float headerH, float rowH,
                          float[] colWidths, float[] colStarts) {}

    private Layout computeLayout(PDRectangle pageSize, List<TimeBlock> timeline) {
        float margin = 28;
        float tableLeft = margin;
        float tableWidth = pageSize.getWidth() - 2 * margin;
        float dayColW = 65;
        float nonLessonColW = 46;
        int numLessons = (int) timeline.stream().filter(t -> t.blockType() == BlockType.LESSON).count();
        int numNonLessons = timeline.size() - numLessons;
        float remaining = tableWidth - dayColW - numNonLessons * nonLessonColW;
        float lessonColW = numLessons > 0 ? Math.max(remaining / numLessons, 55) : 0;
        float headerH = 36;
        float rowH = 46;

        float[] colWidths = new float[timeline.size() + 1];
        float[] colStarts = new float[timeline.size() + 1];
        colWidths[0] = dayColW;
        colStarts[0] = tableLeft;
        float cx = tableLeft + dayColW;
        for (int i = 0; i < timeline.size(); i++) {
            float cw = timeline.get(i).blockType() == BlockType.LESSON ? lessonColW : nonLessonColW;
            colWidths[i + 1] = cw;
            colStarts[i + 1] = cx;
            cx += cw;
        }
        return new Layout(margin, tableLeft, tableWidth, dayColW, nonLessonColW, lessonColW,
                headerH, rowH, colWidths, colStarts);
    }

    // ---- Colors ----

    private static final float[] HEADER_BG = hex("1e40af");
    private static final float[] HEADER_TEXT = {1, 1, 1};
    private static final float[] EVEN_ROW = hex("ffffff");
    private static final float[] ODD_ROW = hex("f1f5f9");
    private static final float[] DAY_TEXT = hex("1e293b");
    private static final float[] BREAK_BG = hex("fef3c7");
    private static final float[] BREAK_BORDER_C = hex("d97706");
    private static final float[] GRID_LINE = hex("cbd5e1");
    private static final float[] SUBJECT_TEXT_C = hex("1e3a5f");
    private static final float[] TEACHER_TEXT_C = hex("64748b");
    private static final float[] TITLE_TEXT_C = hex("1e293b");
    private static final float[] SUBTITLE_TEXT_C = hex("64748b");

    private static float[] hex(String h) {
        int c = Integer.parseInt(h.substring(1), 16);
        return new float[]{((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f};
    }

    // ---- Drawing ----

    private void setFill(PDPageContentStream cs, float[] rgb) throws IOException {
        cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);
    }

    private void setStroke(PDPageContentStream cs, float[] rgb, float w) throws IOException {
        cs.setStrokingColor(rgb[0], rgb[1], rgb[2]);
        cs.setLineWidth(w);
    }

    private void fillR(PDPageContentStream cs, float x, float y, float w, float h) throws IOException {
        cs.addRect(x, y - h, w, h);
        cs.fill();
    }

    private void strokeR(PDPageContentStream cs, float x, float y, float w, float h) throws IOException {
        cs.addRect(x, y - h, w, h);
        cs.stroke();
    }

    private void dr(PDPageContentStream cs, float x, float y, float w, float h,
                     float[] bg, float[] border, float bw) throws IOException {
        if (bg != null) { setFill(cs, bg); fillR(cs, x, y, w, h); }
        if (border != null) { setStroke(cs, border, bw); strokeR(cs, x, y, w, h); }
    }

    private void txt(PDPageContentStream cs, float x, float y, String text,
                      PDType1Font f, float sz) throws IOException {
        cs.beginText(); cs.setFont(f, sz); cs.newLineAtOffset(x, y); cs.showText(text); cs.endText();
    }

    private void txc(PDPageContentStream cs, float x1, float x2, float y, String text,
                      PDType1Font f, float sz) throws IOException {
        float tw = f.getStringWidth(text) / 1000f * sz;
        txt(cs, (x1 + x2) / 2f - tw / 2f, y, text, f, sz);
    }

    private void hline(PDPageContentStream cs, float x1, float x2, float y) throws IOException {
        cs.moveTo(x1, y); cs.lineTo(x2, y); cs.stroke();
    }

    private void vline(PDPageContentStream cs, float x, float y1, float y2) throws IOException {
        cs.moveTo(x, y1); cs.lineTo(x, y2); cs.stroke();
    }

    private List<TimeBlock> loadTimeline() {
        List<Period> periods = periodRepository.findAll().stream()
                .sorted(Comparator.comparingInt(Period::getPeriodNumber)).toList();
        List<BreakPeriod> breaks = breakRepository.findAll().stream()
                .sorted(Comparator.comparingInt(BreakPeriod::getSortOrder)).toList();
        return DailyTimelineGenerator.generate(periods, breaks);
    }

    private TimetableEntry findEntry(List<TimetableEntry> entries, int pn) {
        for (TimetableEntry e : entries) if (e.getPeriodNumber() == pn) return e;
        return null;
    }

    private Map<String, List<TimetableEntry>> groupByClass(List<TimetableEntry> entries) {
        Map<Long, TeachingAssignment> am = assignmentRepository.findAll().stream()
                .collect(Collectors.toMap(TeachingAssignment::getId, a -> a));
        Map<String, List<TimetableEntry>> r = new LinkedHashMap<>();
        for (TimetableEntry e : entries) {
            TeachingAssignment a = am.get(e.getTeachingAssignmentId());
            String k = a != null ? classStreamRepository.findById(a.getClassStreamId())
                    .map(ClassStream::getDisplayName).orElse("Class #" + a.getClassStreamId()) : "Unknown";
            r.computeIfAbsent(k, _k -> new ArrayList<>()).add(e);
        }
        return r;
    }

    // ---- Header ----

    private float renderHeader(PDPageContentStream cs, Layout ly, float yTop, String school,
                                String className, String ttName,
                                PDType1Font b, PDType1Font r, PDType1Font i) throws IOException {
        float y = yTop;
        setFill(cs, TITLE_TEXT_C);
        txt(cs, ly.tableLeft, y, school, b, 16);
        setFill(cs, SUBTITLE_TEXT_C);
        txt(cs, ly.tableLeft, y - 18, className + " \u2014 " + ttName, i, 10);
        float divY = y - 30;
        setStroke(cs, GRID_LINE, 0.8f);
        hline(cs, ly.tableLeft, ly.tableLeft + ly.tableWidth, divY);
        return 36;
    }

    // ---- Column headers ----

    private void renderColumnHeaders(PDPageContentStream cs, Layout ly, float y,
                                      List<TimeBlock> timeline, PDType1Font b, PDType1Font r) throws IOException {
        dr(cs, ly.colStarts[0], y, ly.colWidths[0], ly.headerH, HEADER_BG, null, 0);
        setFill(cs, HEADER_TEXT);
        txc(cs, ly.colStarts[0], ly.colStarts[0] + ly.colWidths[0], y + ly.headerH / 2f - 5,
                "Day", b, 11);

        for (int i = 0; i < timeline.size(); i++) {
            TimeBlock blk = timeline.get(i);
            float cx = ly.colStarts[i + 1], cw = ly.colWidths[i + 1];
            dr(cs, cx, y, cw, ly.headerH, HEADER_BG, null, 0);
            if (blk instanceof LessonBlock lb) {
                setFill(cs, HEADER_TEXT);
                txc(cs, cx, cx + cw, y + ly.headerH - 12, "P" + lb.periodNumber(), b, 11);
                txc(cs, cx, cx + cw, y + 8,
                        lb.startTime().format(TIME_FMT) + " - " + lb.endTime().format(TIME_FMT), r, 7);
            } else {
                setFill(cs, HEADER_TEXT);
                txc(cs, cx, cx + cw, y + ly.headerH - 13, blk.label().toUpperCase(), b, 9);
                txc(cs, cx, cx + cw, y + 8,
                        blk.startTime().format(TIME_FMT) + " - " + blk.endTime().format(TIME_FMT), r, 6);
            }
        }
        setStroke(cs, GRID_LINE, 0.6f);
        hline(cs, ly.tableLeft, ly.tableLeft + ly.tableWidth, y);
    }

    // ---- Break column backgrounds ----

    private void renderBreakCols(PDPageContentStream cs, Layout ly, float y,
                                  List<TimeBlock> timeline, PDType1Font b) throws IOException {
        float th = 5 * ly.rowH;
        for (int i = 0; i < timeline.size(); i++) {
            TimeBlock blk = timeline.get(i);
            if (blk.blockType() == BlockType.LESSON) continue;
            float cx = ly.colStarts[i + 1], cw = ly.colWidths[i + 1];
            dr(cs, cx, y, cw, th, BREAK_BG, BREAK_BORDER_C, 0.8f);
            String label = blk.label().toUpperCase();
            cs.saveGraphicsState();
            cs.beginText();
            cs.setFont(b, 10);
            float tw = b.getStringWidth(label) / 1000f * 10;
            cs.newLineAtOffset(cx + cw / 2f - tw / 2f, y - th / 2f - 3);
            cs.showText(label);
            cs.endText();
            cs.restoreGraphicsState();
        }
    }

    // ---- Day rows ----

    private float renderDayRows(PDPageContentStream cs, Layout ly, float y,
                                 List<DayOfWeek> days, List<TimetableEntry> entries,
                                 List<TimeBlock> timeline, Map<Long, TeachingAssignment> aMap,
                                 PDType1Font b, PDType1Font r, PDType1Font s) throws IOException {
        Map<String, List<TimetableEntry>> lookup = new HashMap<>();
        for (TimetableEntry e : entries) lookup.computeIfAbsent(e.getDayOfWeek().name(), _k -> new ArrayList<>()).add(e);

        for (int ri = 0; ri < days.size(); ri++) {
            DayOfWeek day = days.get(ri);
            float[] bg = ri % 2 == 1 ? ODD_ROW : EVEN_ROW;

            dr(cs, ly.colStarts[0], y, ly.colWidths[0], ly.rowH, bg, GRID_LINE, 0.5f);
            setFill(cs, DAY_TEXT);
            txc(cs, ly.colStarts[0], ly.colStarts[0] + ly.colWidths[0], y + ly.rowH / 2f - 4,
                    day.displayName(), b, 11);

            List<TimetableEntry> de = lookup.getOrDefault(day.name(), List.of());
            float midY = y + ly.rowH / 2f;
            for (int i = 0; i < timeline.size(); i++) {
                TimeBlock blk = timeline.get(i);
                float cx = ly.colStarts[i + 1], cw = ly.colWidths[i + 1];
                if (blk instanceof LessonBlock lb) {
                    dr(cs, cx, y, cw, ly.rowH, bg, GRID_LINE, 0.5f);
                    TimetableEntry match = findEntry(de, lb.periodNumber());
                    if (match != null) {
                        TeachingAssignment a = aMap.get(match.getTeachingAssignmentId());
                        if (a != null) {
                            String sc = subjectCode(a.getSubjectId());
                            String ti = teacherInitials(a.getTeacherId());
                            String rm = roomCodeIfAny(match.getRoomId());
                            setFill(cs, SUBJECT_TEXT_C);
                            txc(cs, cx, cx + cw, midY + 8, sc, b, 10);
                            setFill(cs, TEACHER_TEXT_C);
                            txt(cs, cx + 5, y + 7, ti, s, 7);
                            if (rm != null) {
                                float rw = s.getStringWidth(rm) / 1000f * 7;
                                txt(cs, cx + cw - 5 - rw, y + 7, rm, s, 7);
                            }
                        }
                    }
                } else {
                    setStroke(cs, GRID_LINE, 0.4f);
                    vline(cs, cx + cw, y, y - ly.rowH);
                }
            }
            setStroke(cs, GRID_LINE, 0.4f);
            hline(cs, ly.tableLeft, ly.tableLeft + ly.tableWidth, y - ly.rowH);
            y -= ly.rowH;
        }
        return y;
    }

    // ---- PDF writers ----

    private void writeAllClassesPdf(TimetableRepository.TimetableWithEntries data, OutputStream output,
                                     String school, String ttName) {
        PDType1Font f = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font fb = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDType1Font fi = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);
        PDType1Font fs = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

        try (PDDocument doc = new PDDocument()) {
            List<TimeBlock> timeline = loadTimeline();
            Map<Long, TeachingAssignment> aMap = assignmentMap();
            Map<String, List<TimetableEntry>> byClass = groupByClass(data.entries());
            float ph = PDRectangle.A4.getHeight(), pw = PDRectangle.A4.getWidth();

            for (var ce : byClass.entrySet()) {
                PDPage pg = new PDPage(new PDRectangle(pw, ph));
                doc.addPage(pg);
                PDPageContentStream cs = new PDPageContentStream(doc, pg);
                Layout ly = computeLayout(pg.getMediaBox(), timeline);
                float yTop = ph - 28;
                float used = renderHeader(cs, ly, yTop, school, ce.getKey(), ttName, fb, f, fi);
                float y = yTop - used;
                renderColumnHeaders(cs, ly, y, timeline, fb, f);
                y -= ly.headerH;
                renderBreakCols(cs, ly, y, timeline, fb);
                y = renderDayRows(cs, ly, y, DayOfWeek.workingDays(), ce.getValue(), timeline, aMap, fb, f, fs);
                cs.close();
            }
            doc.save(output);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render PDF", e);
        }
    }

    private void writeFilteredPdf(TimetableRepository.TimetableWithEntries data, String groupLabel,
                                   Filter filter, OutputStream output) {
        PDType1Font f = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font fb = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDType1Font fi = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);
        PDType1Font fs = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

        try (PDDocument doc = new PDDocument()) {
            List<TimeBlock> timeline = loadTimeline();
            Map<Long, TeachingAssignment> aMap = assignmentMap();
            List<TimetableEntry> filtered = filter.filter(data.entries());
            Map<String, List<TimetableEntry>> byClass = groupByClass(filtered);
            String school = schoolName();
            float ph = PDRectangle.A4.getHeight(), pw = PDRectangle.A4.getWidth();

            if (byClass.isEmpty()) {
                PDPage pg = new PDPage(PDRectangle.A4);
                doc.addPage(pg);
                try (PDPageContentStream cs = new PDPageContentStream(doc, pg)) {
                    txt(cs, 28, ph - 28, "No entries found for " + groupLabel, fi, 12);
                }
                doc.save(output);
                return;
            }

            for (var ce : byClass.entrySet()) {
                PDPage pg = new PDPage(new PDRectangle(pw, ph));
                doc.addPage(pg);
                PDPageContentStream cs = new PDPageContentStream(doc, pg);
                Layout ly = computeLayout(pg.getMediaBox(), timeline);
                float yTop = ph - 28;
                float used = renderHeader(cs, ly, yTop, school, ce.getKey(), data.timetable().getName(), fb, f, fi);
                float y = yTop - used;
                renderColumnHeaders(cs, ly, y, timeline, fb, f);
                y -= ly.headerH;
                renderBreakCols(cs, ly, y, timeline, fb);
                y = renderDayRows(cs, ly, y, DayOfWeek.workingDays(), ce.getValue(), timeline, aMap, fb, f, fs);
                cs.close();
            }
            doc.save(output);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render PDF", e);
        }
    }

    private void writeTeacherPdf(TimetableRepository.TimetableWithEntries data, Long teacherId, OutputStream output) {
        PDType1Font f = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font fb = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDType1Font fi = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);
        PDType1Font fs = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

        try (PDDocument doc = new PDDocument()) {
            List<TimeBlock> timeline = loadTimeline();
            Map<Long, TeachingAssignment> aMap = assignmentMap();
            Teacher teacher = teacherRepository.findById(teacherId)
                    .orElseThrow(() -> new IllegalArgumentException("Teacher not found: " + teacherId));
            List<TimetableEntry> teacherEntries = data.entries().stream()
                    .filter(e -> { TeachingAssignment a = aMap.get(e.getTeachingAssignmentId()); return a != null && a.getTeacherId().equals(teacherId); })
                    .toList();
            String school = schoolName();
            float ph = PDRectangle.A4.getHeight(), pw = PDRectangle.A4.getWidth();

            PDPage pg = new PDPage(new PDRectangle(pw, ph));
            doc.addPage(pg);
            PDPageContentStream cs = new PDPageContentStream(doc, pg);
            Layout ly = computeLayout(pg.getMediaBox(), timeline);
            float yTop = ph - 28;
            float used = renderHeader(cs, ly, yTop, school, teacher.getName(), data.timetable().getName(), fb, f, fi);
            float y = yTop - used;
            renderColumnHeaders(cs, ly, y, timeline, fb, f);
            y -= ly.headerH;
            renderBreakCols(cs, ly, y, timeline, fb);

            Map<String, List<TimetableEntry>> lookup = new HashMap<>();
            for (TimetableEntry e : teacherEntries) lookup.computeIfAbsent(e.getDayOfWeek().name(), _k -> new ArrayList<>()).add(e);
            for (int ri = 0; ri < DayOfWeek.workingDays().size(); ri++) {
                DayOfWeek day = DayOfWeek.workingDays().get(ri);
                float[] bg = ri % 2 == 1 ? ODD_ROW : EVEN_ROW;
                dr(cs, ly.colStarts[0], y, ly.colWidths[0], ly.rowH, bg, GRID_LINE, 0.5f);
                setFill(cs, DAY_TEXT);
                txc(cs, ly.colStarts[0], ly.colStarts[0] + ly.colWidths[0], y + ly.rowH / 2f - 4, day.displayName(), fb, 11);
                List<TimetableEntry> de = lookup.getOrDefault(day.name(), List.of());
                for (int i = 0; i < timeline.size(); i++) {
                    TimeBlock blk = timeline.get(i);
                    float cx = ly.colStarts[i + 1], cw = ly.colWidths[i + 1];
                    if (blk instanceof LessonBlock lb) {
                        dr(cs, cx, y, cw, ly.rowH, bg, GRID_LINE, 0.5f);
                        TimetableEntry m = findEntry(de, lb.periodNumber());
                        if (m != null) {
                            TeachingAssignment a = aMap.get(m.getTeachingAssignmentId());
                            if (a != null) {
                                String sc = subjectCode(a.getSubjectId()), cn = classDisplayName(a.getClassStreamId()), rm = roomCodeIfAny(m.getRoomId());
                                setFill(cs, SUBJECT_TEXT_C);
                                txc(cs, cx, cx + cw, y + ly.rowH / 2f + 8, sc, fb, 10);
                                setFill(cs, TEACHER_TEXT_C);
                                txt(cs, cx + 5, y + 7, cn, fs, 7);
                                if (rm != null) { float rw = fs.getStringWidth(rm) / 1000f * 7; txt(cs, cx + cw - 5 - rw, y + 7, rm, fs, 7); }
                            }
                        }
                    } else { setStroke(cs, GRID_LINE, 0.4f); vline(cs, cx + cw, y, y - ly.rowH); }
                }
                setStroke(cs, GRID_LINE, 0.4f);
                hline(cs, ly.tableLeft, ly.tableLeft + ly.tableWidth, y - ly.rowH);
                y -= ly.rowH;
            }
            cs.close();
            doc.save(output);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render teacher PDF", e);
        }
    }

    private void writeAllTeachersPdf(TimetableRepository.TimetableWithEntries data, OutputStream output) {
        PDType1Font f = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font fb = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDType1Font fi = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);
        PDType1Font fs = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

        try (PDDocument doc = new PDDocument()) {
            List<TimeBlock> timeline = loadTimeline();
            Map<Long, TeachingAssignment> aMap = assignmentMap();
            List<Teacher> allTeachers = teacherRepository.findAll();
            String school = schoolName();
            float ph = PDRectangle.A4.getHeight(), pw = PDRectangle.A4.getWidth();

            for (Teacher teacher : allTeachers) {
                List<TimetableEntry> teacherEntries = data.entries().stream()
                        .filter(e -> { TeachingAssignment a = aMap.get(e.getTeachingAssignmentId()); return a != null && a.getTeacherId().equals(teacher.getId()); })
                        .toList();
                PDPage pg = new PDPage(new PDRectangle(pw, ph));
                doc.addPage(pg);
                PDPageContentStream cs = new PDPageContentStream(doc, pg);
                Layout ly = computeLayout(pg.getMediaBox(), timeline);
                float yTop = ph - 28;
                float used = renderHeader(cs, ly, yTop, school, teacher.getName(), data.timetable().getName(), fb, f, fi);
                float y = yTop - used;
                renderColumnHeaders(cs, ly, y, timeline, fb, f);
                y -= ly.headerH;
                renderBreakCols(cs, ly, y, timeline, fb);

                Map<String, List<TimetableEntry>> lookup = new HashMap<>();
                for (TimetableEntry e : teacherEntries) lookup.computeIfAbsent(e.getDayOfWeek().name(), _k -> new ArrayList<>()).add(e);
                for (int ri = 0; ri < DayOfWeek.workingDays().size(); ri++) {
                    DayOfWeek day = DayOfWeek.workingDays().get(ri);
                    float[] bg = ri % 2 == 1 ? ODD_ROW : EVEN_ROW;
                    dr(cs, ly.colStarts[0], y, ly.colWidths[0], ly.rowH, bg, GRID_LINE, 0.5f);
                    setFill(cs, DAY_TEXT);
                    txc(cs, ly.colStarts[0], ly.colStarts[0] + ly.colWidths[0], y + ly.rowH / 2f - 4, day.displayName(), fb, 11);
                    List<TimetableEntry> de = lookup.getOrDefault(day.name(), List.of());
                    for (int i = 0; i < timeline.size(); i++) {
                        TimeBlock blk = timeline.get(i);
                        float cx = ly.colStarts[i + 1], cw = ly.colWidths[i + 1];
                        if (blk instanceof LessonBlock lb) {
                            dr(cs, cx, y, cw, ly.rowH, bg, GRID_LINE, 0.5f);
                            TimetableEntry m = findEntry(de, lb.periodNumber());
                            if (m != null) {
                                TeachingAssignment a = aMap.get(m.getTeachingAssignmentId());
                                if (a != null) {
                                    String sc = subjectCode(a.getSubjectId()), cn = classDisplayName(a.getClassStreamId()), rm = roomCodeIfAny(m.getRoomId());
                                    setFill(cs, SUBJECT_TEXT_C);
                                    txc(cs, cx, cx + cw, y + ly.rowH / 2f + 8, sc, fb, 10);
                                    setFill(cs, TEACHER_TEXT_C);
                                    txt(cs, cx + 5, y + 7, cn, fs, 7);
                                    if (rm != null) { float rw = fs.getStringWidth(rm) / 1000f * 7; txt(cs, cx + cw - 5 - rw, y + 7, rm, fs, 7); }
                                }
                            }
                        } else { setStroke(cs, GRID_LINE, 0.4f); vline(cs, cx + cw, y, y - ly.rowH); }
                    }
                    setStroke(cs, GRID_LINE, 0.4f);
                    hline(cs, ly.tableLeft, ly.tableLeft + ly.tableWidth, y - ly.rowH);
                    y -= ly.rowH;
                }
                cs.close();
            }
            doc.save(output);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render all teachers PDF", e);
        }
    }

    @FunctionalInterface
    private interface Filter {
        List<TimetableEntry> filter(List<TimetableEntry> entries);
    }
}
