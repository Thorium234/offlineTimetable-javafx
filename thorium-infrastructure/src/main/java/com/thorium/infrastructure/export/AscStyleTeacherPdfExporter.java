package com.thorium.infrastructure.export;

import com.thorium.application.port.*;
import com.thorium.domain.model.*;
import com.thorium.domain.value.DayOfWeek;
import com.thorium.domain.value.SubjectColorPalette;
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
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * aSc Timetables-style teacher PDF exporter.
 * Column layout is built dynamically from DB periods.
 */
public class AscStyleTeacherPdfExporter implements TimetableExporter {

    private static final DateTimeFormatter TIME_FMT_NO_LEAD = DateTimeFormatter.ofPattern("H:mm");

    private enum ColType { DAY_LABEL, LESSON_COL, BREAK_COL }

    private static final class ColLayout {
        final int totalColumns;
        final ColType[] colTypes;
        final String[] colLabels;
        final String[] colBreakFullLabels;
        final int[] colPeriodNumbers;
        final boolean[] colIsBreak;

        ColLayout(int total, ColType[] types, String[] labels, String[] breakFull,
                   int[] pns, boolean[] breaks) {
            this.totalColumns = total;
            this.colTypes = types;
            this.colLabels = labels;
            this.colBreakFullLabels = breakFull;
            this.colPeriodNumbers = pns;
            this.colIsBreak = breaks;
        }
    }

    private static final String[] DAY_ABBREVIATIONS = {"Mon", "Tue", "Wed", "Thu", "Fri"};

    // ---- Repositories ----

    private final TeachingAssignmentRepository assignmentRepository;
    private final SubjectRepository subjectRepository;
    private final ClassStreamRepository classStreamRepository;
    private final TeacherRepository teacherRepository;
    private final RoomRepository roomRepository;
    private final PeriodRepository periodRepository;
    private final BreakRepository breakRepository;
    private final SchoolSettingsRepository schoolSettingsRepository;

    public AscStyleTeacherPdfExporter(TeachingAssignmentRepository assignmentRepository,
                                      SubjectRepository subjectRepository,
                                      ClassStreamRepository classStreamRepository,
                                      TeacherRepository teacherRepository,
                                      RoomRepository roomRepository,
                                      PeriodRepository periodRepository,
                                      BreakRepository breakRepository,
                                      SchoolSettingsRepository schoolSettingsRepository) {
        this.assignmentRepository = assignmentRepository;
        this.subjectRepository = subjectRepository;
        this.classStreamRepository = classStreamRepository;
        this.teacherRepository = teacherRepository;
        this.roomRepository = roomRepository;
        this.periodRepository = periodRepository;
        this.breakRepository = breakRepository;
        this.schoolSettingsRepository = schoolSettingsRepository;
    }

    // ---- Unsupported TimetableExporter methods ----

    @Override
    public void exportPdf(TimetableRepository.TimetableWithEntries data, Path outputPath) {
        throw new UnsupportedOperationException("Use PdfTimetableExporter for class timetables");
    }

    @Override
    public void exportExcel(TimetableRepository.TimetableWithEntries data, Path outputPath) {
        throw new UnsupportedOperationException("Use ExcelTimetableExporter");
    }

    @Override
    public byte[] renderPdfToBytes(TimetableRepository.TimetableWithEntries data) {
        return renderAscAllTeachersPdfToBytes(data);
    }

    @Override
    public byte[] renderTeacherPdfToBytes(TimetableRepository.TimetableWithEntries data, Long teacherId) {
        return renderAscTeacherPdfToBytes(data, teacherId);
    }

    @Override
    public byte[] renderStreamPdfToBytes(TimetableRepository.TimetableWithEntries data, String stream) {
        throw new UnsupportedOperationException("Use PdfTimetableExporter for stream timetables");
    }

    @Override
    public byte[] renderGradePdfToBytes(TimetableRepository.TimetableWithEntries data, int form) {
        throw new UnsupportedOperationException("Use PdfTimetableExporter for grade timetables");
    }

    @Override
    public byte[] renderAllTeachersPdfToBytes(TimetableRepository.TimetableWithEntries data) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(65536);
        writeAscAllTeachersPdf(data, bos);
        return bos.toByteArray();
    }

    @Override
    public byte[] renderAllClassesPdfToBytes(TimetableRepository.TimetableWithEntries data) {
        throw new UnsupportedOperationException("Use PdfTimetableExporter for class timetables");
    }

    @Override
    public byte[] renderAscTeacherPdfToBytes(TimetableRepository.TimetableWithEntries data, Long teacherId) {
        return renderTeacherPdfToBytes(data, teacherId);
    }

    @Override
    public byte[] renderAscAllTeachersPdfToBytes(TimetableRepository.TimetableWithEntries data) {
        return renderAllTeachersPdfToBytes(data);
    }

    // ---- Layout ----

    private static final float MM_TO_PT = 2.83465f;
    private static final float MARGIN_TOP = 15 * MM_TO_PT;
    private static final float MARGIN_BOTTOM = 15 * MM_TO_PT;
    private static final float MARGIN_LEFT = 12 * MM_TO_PT;
    private static final float MARGIN_RIGHT = 12 * MM_TO_PT;

    private static final float DAY_COL_W = 48;
    private static final float BREAK_COL_W = 52;
    private static final float LESSON_COL_W = 46;

    private static final float SCHOOL_NAME_H = 16;
    private static final float TEACHER_NAME_H = 26;
    private static final float HEADER_ROW_H = 38;
    private static final float DAY_ROW_H = 44;

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
    private static final float[] CLASS_TEXT_C = hex("64748b");
    private static final float[] TITLE_TEXT_C = hex("1e293b");
    private static final float[] SCHOOL_NAME_C = hex("475569");

    // Pastel subject color cache: subjectId -> float[]
    private final Map<Long, float[]> subjectColorCache = new HashMap<>();

    private float[] subjectBg(Long subjectId) {
        return subjectColorCache.computeIfAbsent(subjectId, id -> {
            Subject subject = subjectRepository.findById(id).orElse(null);
            if (subject == null) return EVEN_ROW;
            String hex = SubjectColorPalette.resolveColor(id, subject.getColor());
            return hex(hex);
        });
    }

    private static float[] hex(String h) {
        int c = Integer.parseInt(h.substring(1), 16);
        return new float[]{((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f};
    }

    // ---- Drawing helpers ----

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

    // ---- DB-driven column layout ----

    private volatile ColLayout layout = null;

    private ColLayout layout() {
        if (layout == null) {
            synchronized (this) {
                if (layout == null) {
                    layout = loadLayout();
                }
            }
        }
        return layout;
    }

    private ColLayout loadLayout() {
        List<Period> periods;
        try {
            periods = periodRepository.findAll().stream()
                    .sorted(Comparator.comparingInt(Period::getPeriodNumber))
                    .toList();
        } catch (Exception e) {
            periods = List.of();
        }
        if (periods.isEmpty()) {
            return buildFallbackLayout();
        }

        int total = 1 + periods.size();
        ColType[] types = new ColType[total];
        String[] labels = new String[total];
        String[] breakFull = new String[total];
        int[] pns = new int[total];
        boolean[] breaks = new boolean[total];

        types[0] = ColType.DAY_LABEL;
        labels[0] = "";
        breakFull[0] = "";
        pns[0] = -1;
        breaks[0] = false;

        int lessonNum = 0;
        for (int i = 0; i < periods.size(); i++) {
            Period p = periods.get(i);
            int colIdx = i + 1;
            if (p.isBreak()) {
                types[colIdx] = ColType.BREAK_COL;
                labels[colIdx] = shortLabel(p.getLabel());
                breakFull[colIdx] = p.getLabel() != null ? p.getLabel().toUpperCase() : "BREAK";
                pns[colIdx] = -1;
                breaks[colIdx] = true;
            } else {
                lessonNum++;
                types[colIdx] = ColType.LESSON_COL;
                labels[colIdx] = shortLessonLabel(lessonNum, p.getLabel());
                breakFull[colIdx] = "";
                pns[colIdx] = p.getPeriodNumber();
                breaks[colIdx] = false;
            }
        }
        return new ColLayout(total, types, labels, breakFull, pns, breaks);
    }

    private ColLayout buildFallbackLayout() {
        int total = 17;
        ColType[] types = new ColType[total];
        String[] labels = new String[total];
        String[] breakFull = new String[total];
        int[] pns = new int[total];
        boolean[] breaks = new boolean[total];

        types[0] = ColType.DAY_LABEL; labels[0] = ""; breakFull[0] = ""; pns[0] = -1; breaks[0] = false;

        // colIdx, type(0=break,1=lesson), label, fullLabel, periodNumber(DB)
        Object[][] def = {
                {1, 0, "Ass.", "ASSEMBLY/PREPS", -1},
                {2, 1, "1", "", 2},
                {3, 1, "2", "", 3},
                {4, 1, "3", "", 4},
                {5, 0, "Tea", "TEA BREAK", -1},
                {6, 1, "4", "", 6},
                {7, 1, "5", "", 7},
                {8, 0, "SB", "SHORT BREAK", -1},
                {9, 1, "6", "", 9},
                {10, 1, "7", "", 10},
                {11, 0, "Lunch", "LUNCH BREAK", -1},
                {12, 1, "8", "", 12},
                {13, 1, "9", "", 13},
                {14, 1, "10", "", 14},
                {15, 0, "Games", "GAMES", -1},
                {16, 1, "11", "", 15},
        };

        for (Object[] d : def) {
            int idx = (int) d[0];
            boolean isBreak = (int) d[1] == 0;
            types[idx] = isBreak ? ColType.BREAK_COL : ColType.LESSON_COL;
            labels[idx] = (String) d[2];
            breakFull[idx] = (String) d[3];
            pns[idx] = (int) d[4];
            breaks[idx] = isBreak;
        }
        return new ColLayout(total, types, labels, breakFull, pns, breaks);
    }

    private String shortLabel(String periodLabel) {
        if (periodLabel == null) return "";
        return switch (periodLabel.toLowerCase()) {
            case "assembly" -> "Ass.";
            case "tea break" -> "Tea";
            case "short break" -> "SB";
            case "lunch break", "lunch" -> "Lunch";
            case "games time", "games" -> "Games";
            default -> periodLabel.length() <= 5 ? periodLabel : periodLabel.substring(0, 4) + ".";
        };
    }

    private String shortLessonLabel(int lessonNum, String periodLabel) {
        if (periodLabel != null && !periodLabel.isBlank()) {
            String u = periodLabel.toUpperCase();
            if (u.matches("P\\d+")) return u.substring(1);
            return u.length() <= 4 ? u : u.substring(0, 3);
        }
        return String.valueOf(lessonNum);
    }

    private float[] computeColStarts(float tableLeft) {
        ColLayout ly = layout();
        float[] starts = new float[ly.totalColumns];
        starts[0] = tableLeft;
        float cx = tableLeft + DAY_COL_W;
        for (int i = 1; i < ly.totalColumns; i++) {
            starts[i] = cx;
            cx += colWidth(ly, i);
        }
        return starts;
    }

    private float colWidth(ColLayout ly, int colIdx) {
        if (colIdx == 0) return DAY_COL_W;
        return ly.colTypes[colIdx] == ColType.BREAK_COL ? BREAK_COL_W : LESSON_COL_W;
    }

    private float tableWidth() {
        ColLayout ly = layout();
        float w = DAY_COL_W;
        for (int i = 1; i < ly.totalColumns; i++) {
            w += colWidth(ly, i);
        }
        return w;
    }

    // ---- Lookup helpers ----

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

    private String classDisplayName(Long classStreamId) {
        return classStreamRepository.findById(classStreamId)
                .map(ClassStream::getDisplayName)
                .orElse("Class #" + classStreamId);
    }

    private String schoolName() {
        if (schoolSettingsRepository == null) return "";
        try {
            return schoolSettingsRepository.get().getSchoolName();
        } catch (Exception e) {
            return "";
        }
    }

    // ---- DB-backed time lookup ----

    private volatile Map<Integer, String> periodTimeCache = null;

    private String timeForPeriod(int periodNumber) {
        if (periodTimeCache == null) {
            periodTimeCache = loadPeriodTimes();
        }
        return periodTimeCache.getOrDefault(periodNumber, "");
    }

    private Map<Integer, String> loadPeriodTimes() {
        Map<Integer, String> map = new HashMap<>();
        try {
            List<Period> periods = periodRepository.findAll();
            for (Period p : periods) {
                if (Period.TYPE_LESSON.equals(p.getType())) {
                    map.put(p.getPeriodNumber(),
                            formatTimeNoLeading(p.getStartTime()) + " - " + formatTimeNoLeading(p.getEndTime()));
                } else {
                    map.put(p.getPeriodNumber(),
                            formatTimeNoLeading(p.getStartTime()) + " - " + formatTimeNoLeading(p.getEndTime()));
                }
            }
        } catch (Exception e) {
            map.putAll(fallbackPeriodTimes());
        }
        if (map.isEmpty()) {
            map.putAll(fallbackPeriodTimes());
        }
        return Collections.unmodifiableMap(map);
    }

    private String formatTimeNoLeading(LocalTime t) {
        int hour = t.getHour();
        int min = t.getMinute();
        return hour + ":" + (min < 10 ? "0" + min : String.valueOf(min));
    }

    private static Map<Integer, String> fallbackPeriodTimes() {
        Map<Integer, String> m = new HashMap<>();
        m.put(1, "8:00 - 8:40");
        m.put(2, "8:40 - 9:20");
        m.put(3, "9:20 - 10:00");
        m.put(4, "10:20 - 11:00");
        m.put(5, "11:00 - 11:40");
        m.put(6, "11:50 - 12:30");
        m.put(7, "12:30 - 13:10");
        m.put(8, "14:00 - 14:40");
        m.put(9, "14:40 - 15:20");
        m.put(10, "15:20 - 16:00");
        m.put(11, "16:40 - 17:20");
        return m;
    }

    // ---- Page rendering ----

    private void writeAscTeacherPdf(TimetableRepository.TimetableWithEntries data, Long teacherId, OutputStream output) {
        PDType1Font fb = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDType1Font f = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font fi = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

        Teacher teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found: " + teacherId));
        Map<Long, TeachingAssignment> aMap = assignmentMap();
        List<TimetableEntry> teacherEntries = data.entries().stream()
                .filter(e -> {
                    TeachingAssignment a = aMap.get(e.getTeachingAssignmentId());
                    return a != null && a.getTeacherId().equals(teacherId);
                })
                .toList();

        try (PDDocument doc = new PDDocument()) {
            float pw = PDRectangle.A4.getHeight();
            float ph = PDRectangle.A4.getWidth();
            PDPage pg = new PDPage(new PDRectangle(pw, ph));
            doc.addPage(pg);

            try (PDPageContentStream cs = new PDPageContentStream(doc, pg)) {
                renderAscPage(cs, pg.getMediaBox(), teacher.getName(), teacherEntries, aMap, fb, f, fi);
            }
            doc.save(output);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render aSc-style teacher PDF", e);
        }
    }

    private void writeAscAllTeachersPdf(TimetableRepository.TimetableWithEntries data, OutputStream output) {
        PDType1Font fb = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDType1Font f = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font fi = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

        Map<Long, TeachingAssignment> aMap = assignmentMap();
        List<Teacher> allTeachers = teacherRepository.findAll();

        try (PDDocument doc = new PDDocument()) {
            float pw = PDRectangle.A4.getHeight();
            float ph = PDRectangle.A4.getWidth();

            for (Teacher teacher : allTeachers) {
                List<TimetableEntry> teacherEntries = data.entries().stream()
                        .filter(e -> {
                            TeachingAssignment a = aMap.get(e.getTeachingAssignmentId());
                            return a != null && a.getTeacherId().equals(teacher.getId());
                        })
                        .toList();

                PDPage pg = new PDPage(new PDRectangle(pw, ph));
                doc.addPage(pg);

                try (PDPageContentStream cs = new PDPageContentStream(doc, pg)) {
                    renderAscPage(cs, pg.getMediaBox(), teacher.getName(), teacherEntries, aMap, fb, f, fi);
                }
            }
            doc.save(output);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render aSc-style all teachers PDF", e);
        }
    }

    private void renderAscPage(PDPageContentStream cs, PDRectangle pageSize,
                                String teacherName, List<TimetableEntry> entries,
                                Map<Long, TeachingAssignment> aMap,
                                PDType1Font fb, PDType1Font f, PDType1Font fi) throws IOException {
        float pw = pageSize.getWidth();
        float ph = pageSize.getHeight();
        float tableW = tableWidth();

        float tableLeft = (pw - tableW) / 2f;
        float yTop = ph - MARGIN_TOP;

        float[] colStarts = computeColStarts(tableLeft);
        String school = schoolName();
        float y = yTop;

        if (!school.isBlank()) {
            setFill(cs, SCHOOL_NAME_C);
            txt(cs, tableLeft, y, school.toUpperCase(), fb, 9);
            y -= SCHOOL_NAME_H;
        }

        String teacherHeading = "Teacher " + teacherName.toUpperCase();
        setFill(cs, TITLE_TEXT_C);
        txc(cs, tableLeft, tableLeft + tableW, y, teacherHeading, fb, 17);
        y -= TEACHER_NAME_H;

        setStroke(cs, GRID_LINE, 0.8f);
        hline(cs, tableLeft, tableLeft + tableW, y);

        renderAscHeaders(cs, y, colStarts, fb, f);
        y -= HEADER_ROW_H;

        renderAscDayRows(cs, y, colStarts, entries, aMap, fb, f, fi);
    }

    private void renderAscHeaders(PDPageContentStream cs, float y, float[] colStarts,
                                   PDType1Font fb, PDType1Font f) throws IOException {
        ColLayout ly = layout();
        dr(cs, colStarts[0], y, DAY_COL_W, HEADER_ROW_H, HEADER_BG, null, 0);

        for (int i = 1; i < ly.totalColumns; i++) {
            float cx = colStarts[i];
            float cw = colWidth(ly, i);
            dr(cs, cx, y, cw, HEADER_ROW_H, HEADER_BG, null, 0);
            setFill(cs, HEADER_TEXT);

            boolean isBreak = ly.colTypes[i] == ColType.BREAK_COL;
            int pn = ly.colPeriodNumbers[i];

            String timeLabel;
            if (isBreak) {
                timeLabel = breakTimeForColumn(pn);
            } else {
                timeLabel = timeForPeriod(pn);
            }
            if (!timeLabel.isBlank()) {
                txc(cs, cx, cx + cw, y + HEADER_ROW_H - 8, timeLabel, f, 7);
            }

            txc(cs, cx, cx + cw, y + 6, ly.colLabels[i], fb, 10);
        }

        setStroke(cs, GRID_LINE, 0.8f);
        hline(cs, colStarts[0], colStarts[0] + tableWidth(), y);
    }

    private String breakTimeForColumn(int periodNumber) {
        if (periodNumber > 0) {
            String t = timeForPeriod(periodNumber);
            if (!t.isBlank()) return t;
        }
        return "";
    }

    private void renderAscDayRows(PDPageContentStream cs, float y, float[] colStarts,
                                   List<TimetableEntry> entries, Map<Long, TeachingAssignment> aMap,
                                   PDType1Font fb, PDType1Font f, PDType1Font fi) throws IOException {
        ColLayout ly = layout();
        Map<String, Map<Integer, TimetableEntry>> dayLookup = new HashMap<>();
        for (TimetableEntry e : entries) {
            dayLookup.computeIfAbsent(e.getDayOfWeek().name(), _k -> new HashMap<>())
                    .put(e.getPeriodNumber(), e);
        }

        float rowsTotalH = 5 * DAY_ROW_H;
        float tableLeft = colStarts[0];
        float tableRight = tableLeft + tableWidth();

        for (int i = 1; i < ly.totalColumns; i++) {
            if (ly.colTypes[i] != ColType.BREAK_COL) continue;
            float cx = colStarts[i];
            float cw = colWidth(ly, i);
            dr(cs, cx, y, cw, rowsTotalH, BREAK_BG, BREAK_BORDER_C, 0.8f);

            String fullLabel = ly.colBreakFullLabels[i];
            cs.saveGraphicsState();
            float centerX = cx + cw / 2f;
            float centerY = y - rowsTotalH / 2f;
            cs.transform(new org.apache.pdfbox.util.Matrix(0, -1, 1, 0, centerX, centerY));
            setFill(cs, BREAK_BORDER_C);
            float tw = fb.getStringWidth(fullLabel) / 1000f * 10;
            txc(cs, -tw / 2f, tw / 2f, -cw / 2f + 3, fullLabel, fb, 10);
            cs.restoreGraphicsState();
        }

        for (int ri = 0; ri < 5; ri++) {
            DayOfWeek day = DayOfWeek.workingDays().get(ri);
            float[] bg = ri % 2 == 1 ? ODD_ROW : EVEN_ROW;

            dr(cs, colStarts[0], y, DAY_COL_W, DAY_ROW_H, bg, GRID_LINE, 0.5f);
            setFill(cs, DAY_TEXT);
            txc(cs, colStarts[0], colStarts[0] + DAY_COL_W, y - DAY_ROW_H / 2f + 5,
                    DAY_ABBREVIATIONS[ri], fb, 12);

            Map<Integer, TimetableEntry> dayEntries = dayLookup.getOrDefault(day.name(), new HashMap<>());
            float midY = y - DAY_ROW_H / 2f;

            for (int i = 1; i < ly.totalColumns; i++) {
                float cx = colStarts[i];
                float cw = colWidth(ly, i);

                if (ly.colTypes[i] == ColType.LESSON_COL) {
                    int pn = ly.colPeriodNumbers[i];
                    TimetableEntry match = dayEntries.get(pn);

                    dr(cs, cx, y, cw, DAY_ROW_H, bg, GRID_LINE, 0.5f);

                    if (match != null) {
                        TeachingAssignment a = aMap.get(match.getTeachingAssignmentId());
                        if (a != null) {
                            String sc = subjectCode(a.getSubjectId());
                            String cn = classDisplayName(a.getClassStreamId());
                            float[] subBg = subjectBg(a.getSubjectId());
                            float pad = 1f;
                            dr(cs, cx + pad, y - pad, cw - 2 * pad, DAY_ROW_H - 2 * pad, subBg, null, 0);
                            setFill(cs, SUBJECT_TEXT_C);
                            txc(cs, cx, cx + cw, midY + 10, sc, fb, 9);
                            setFill(cs, CLASS_TEXT_C);
                            txc(cs, cx, cx + cw, midY - 6, cn, f, 7);
                        }
                    }
                } else {
                    setStroke(cs, GRID_LINE, 0.4f);
                    vline(cs, cx + cw, y, y - DAY_ROW_H);
                }
            }

            setStroke(cs, GRID_LINE, 0.4f);
            hline(cs, tableLeft, tableRight, y - DAY_ROW_H);
            y -= DAY_ROW_H;
        }

        setStroke(cs, GRID_LINE, 1.2f);
        strokeR(cs, tableLeft, y + rowsTotalH, tableWidth(), rowsTotalH);
    }
}
