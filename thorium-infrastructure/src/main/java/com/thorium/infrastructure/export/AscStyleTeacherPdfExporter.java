package com.thorium.infrastructure.export;

import com.thorium.application.port.*;
import com.thorium.application.util.NameFormatter;
import com.thorium.domain.model.*;
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
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

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

    private static final String[] FULL_DAY_NAMES = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday"};

    private static final float MM_TO_PT = 2.83465f;
    private static final float MARGIN_TOP = 18 * MM_TO_PT;
    private static final float MARGIN_BOTTOM = 15 * MM_TO_PT;
    private static final float MARGIN_LEFT = 15 * MM_TO_PT;
    private static final float MARGIN_RIGHT = 15 * MM_TO_PT;

    private static final float DAY_COL_W = 80;
    private static final float BREAK_COL_W = 56;
    private static final float LESSON_COL_W = 62;

    private static final float HEADER_ROW_H = 50;
    private static final float DAY_ROW_H = 52;

    private static final float[] BLACK = {0, 0, 0};
    private static final float[] WHITE = {1, 1, 1};
    private static final float[] LIGHT_GRAY = {0.96f, 0.96f, 0.96f};
    private static final float[] DARK_GRAY = {0.2f, 0.2f, 0.2f};
    private static final float[] MED_GRAY = {0.5f, 0.5f, 0.5f};
    private static final float[] BREAK_BG = {0.9f, 0.9f, 0.9f};

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

    // ---- Unsupported ----

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

    // ---- Column layout ----

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

        for (int i = 0; i < periods.size(); i++) {
            Period p = periods.get(i);
            int colIdx = i + 1;
            if (p.isBreak()) {
                types[colIdx] = ColType.BREAK_COL;
                String raw = p.getLabel() != null ? p.getLabel().trim() : "BREAK";
                labels[colIdx] = raw.length() <= 8 ? raw.toUpperCase() : raw.substring(0, 7).toUpperCase() + ".";
                breakFull[colIdx] = raw.toUpperCase();
                pns[colIdx] = -1;
                breaks[colIdx] = true;
            } else {
                types[colIdx] = ColType.LESSON_COL;
                labels[colIdx] = formatPeriodLabel(p.getPeriodNumber(), p.getLabel());
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

        Object[][] def = {
                {1, 0, "Assembly", "ASSEMBLY", -1},
                {2, 1, "Period 1", "", 2},
                {3, 1, "Period 2", "", 3},
                {4, 1, "Period 3", "", 4},
                {5, 0, "Tea Break", "TEA BREAK", -1},
                {6, 1, "Period 4", "", 6},
                {7, 1, "Period 5", "", 7},
                {8, 0, "Short Break", "SHORT BREAK", -1},
                {9, 1, "Period 6", "", 9},
                {10, 1, "Period 7", "", 10},
                {11, 0, "Lunch Break", "LUNCH BREAK", -1},
                {12, 1, "Period 8", "", 12},
                {13, 1, "Period 9", "", 13},
                {14, 1, "Period 10", "", 14},
                {15, 0, "Games", "GAMES", -1},
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

    private String formatPeriodLabel(int periodNumber, String dbLabel) {
        if (dbLabel != null && !dbLabel.isBlank()) {
            String u = dbLabel.trim();
            if (u.matches("P\\d+")) return "Period " + u.substring(1);
            if (u.toLowerCase().startsWith("period") || u.toLowerCase().startsWith("lesson")) return u;
            return u;
        }
        return "Period " + periodNumber;
    }

    private String shortBreakLabel(String label) {
        if (label == null) return "";
        return switch (label.toLowerCase()) {
            case "assembly" -> "Assembly";
            case "tea break", "tea" -> "Tea Break";
            case "short break", "short" -> "Short Break";
            case "lunch break", "lunch" -> "Lunch Break";
            case "games time", "games" -> "Games";
            default -> label.length() <= 10 ? label : label.substring(0, 9) + ".";
        };
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

    // ---- Helpers ----

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

    private String teacherName(Long teacherId) {
        return teacherRepository.findById(teacherId)
                .map(t -> t.getName())
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

    // ---- Times ----

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
        m.put(1, "7:10 - 8:00");
        m.put(2, "8:00 - 8:40");
        m.put(3, "8:40 - 9:20");
        m.put(4, "9:20 - 10:00");
        m.put(5, "10:20 - 11:00");
        m.put(6, "11:00 - 11:40");
        m.put(7, "11:50 - 12:30");
        m.put(8, "12:30 - 13:10");
        m.put(9, "14:00 - 14:40");
        m.put(10, "14:40 - 15:20");
        m.put(11, "15:20 - 16:00");
        m.put(12, "16:40 - 17:20");
        return m;
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

    // ---- Page rendering ----

    private void renderAscPage(PDPageContentStream cs, PDRectangle pageSize,
                                String teacherName, List<TimetableEntry> entries,
                                Map<Long, TeachingAssignment> aMap,
                                PDType1Font fb, PDType1Font f, PDType1Font fi) throws IOException {
        float pw = pageSize.getWidth();
        float ph = pageSize.getHeight();
        float tableW = tableWidth();
        float tableLeft = (pw - tableW) / 2f;
        float y = ph - MARGIN_TOP;

        float[] colStarts = computeColStarts(tableLeft);
        String school = schoolName();

        // Header
        if (!school.isBlank()) {
            setFill(cs, BLACK);
            txc(cs, tableLeft, tableLeft + tableW, y, school.toUpperCase(), fb, 16);
            y -= 22;
            setFill(cs, MED_GRAY);
            txc(cs, tableLeft, tableLeft + tableW, y, "TEACHER TIMETABLE", fb, 10);
            y -= 6;
        } else {
            setFill(cs, BLACK);
            txc(cs, tableLeft, tableLeft + tableW, y, "TEACHER TIMETABLE", fb, 18);
            y -= 22;
        }

        setFill(cs, BLACK);
        txc(cs, tableLeft, tableLeft + tableW, y, teacherName, f, 13);
        y -= 16;

        setStroke(cs, BLACK, 0.8f);
        hline(cs, tableLeft, tableLeft + tableW, y);
        y -= 12;

        // Column headers
        renderAscHeaders(cs, y, colStarts, fb, f);
        y -= HEADER_ROW_H;

        // Day rows
        renderAscDayRows(cs, y, colStarts, entries, aMap, fb, f, fi);
    }

    private void renderAscHeaders(PDPageContentStream cs, float y, float[] colStarts,
                                   PDType1Font fb, PDType1Font f) throws IOException {
        ColLayout ly = layout();

        // Empty day column header
        dr(cs, colStarts[0], y, DAY_COL_W, HEADER_ROW_H, DARK_GRAY, BLACK, 0.5f);

        for (int i = 1; i < ly.totalColumns; i++) {
            float cx = colStarts[i];
            float cw = colWidth(ly, i);
            dr(cs, cx, y, cw, HEADER_ROW_H, DARK_GRAY, BLACK, 0.5f);
            setFill(cs, WHITE);

            if (!ly.colIsBreak[i]) {
                String pn = ly.colLabels[i];
                String time = timeForPeriod(ly.colPeriodNumbers[i]);
                txc(cs, cx, cx + cw, y + HEADER_ROW_H - 14, pn, fb, 11);
                if (!time.isBlank()) {
                    txc(cs, cx, cx + cw, y + 5, time, f, 8);
                }
            } else {
                String breakLabel = shortBreakLabel(ly.colBreakFullLabels[i]);
                String bt = findBreakTime(breakLabel);
                txc(cs, cx, cx + cw, y + HEADER_ROW_H - 14, breakLabel, fb, 10);
                if (!bt.isBlank()) {
                    txc(cs, cx, cx + cw, y + 5, bt, f, 8);
                }
            }
        }

        setStroke(cs, BLACK, 0.8f);
        hline(cs, colStarts[0], colStarts[0] + tableWidth(), y);
        hline(cs, colStarts[0], colStarts[0] + tableWidth(), y - HEADER_ROW_H);
    }

    private String findBreakTime(String breakLabel) {
        if (breakLabel == null) return "";
        return switch (breakLabel.toLowerCase()) {
            case "assembly" -> "7:10 - 8:00";
            case "tea break" -> "10:00 - 10:20";
            case "short break" -> "11:40 - 11:50";
            case "lunch break" -> "13:10 - 14:00";
            case "games" -> "16:00 - 16:40";
            default -> "";
        };
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

        // Merged break columns
        for (int i = 1; i < ly.totalColumns; i++) {
            if (ly.colTypes[i] != ColType.BREAK_COL) continue;
            float cx = colStarts[i];
            float cw = colWidth(ly, i);
            dr(cs, cx, y, cw, rowsTotalH, BREAK_BG, BLACK, 0.5f);

            String fullLabel = ly.colBreakFullLabels[i];
            cs.saveGraphicsState();
            float centerX = cx + cw / 2f;
            float centerY = y - rowsTotalH / 2f;
            cs.transform(new org.apache.pdfbox.util.Matrix(0, -1, 1, 0, centerX, centerY));
            setFill(cs, DARK_GRAY);
            float tw = fb.getStringWidth(fullLabel) / 1000f * 10;
            txc(cs, -tw / 2f, tw / 2f, -cw / 2f + 3, fullLabel, fb, 10);
            cs.restoreGraphicsState();
        }

        // Day rows
        for (int ri = 0; ri < 5; ri++) {
            DayOfWeek day = DayOfWeek.workingDays().get(ri);
            boolean isMonday = ri == 0;
            float[] bg = isMonday ? DARK_GRAY : (ri % 2 == 0 ? WHITE : LIGHT_GRAY);

            dr(cs, colStarts[0], y, DAY_COL_W, DAY_ROW_H, bg, BLACK, 0.5f);
            setFill(cs, isMonday ? WHITE : BLACK);
            txc(cs, colStarts[0], colStarts[0] + DAY_COL_W, y - DAY_ROW_H / 2f + 5,
                    FULL_DAY_NAMES[ri], fb, 11);

            Map<Integer, TimetableEntry> dayEntries = dayLookup.getOrDefault(day.name(), new HashMap<>());
            float midY = y - DAY_ROW_H / 2f;

            for (int i = 1; i < ly.totalColumns; i++) {
                float cx = colStarts[i];
                float cw = colWidth(ly, i);

                if (ly.colTypes[i] == ColType.LESSON_COL) {
                    int pn = ly.colPeriodNumbers[i];
                    TimetableEntry match = dayEntries.get(pn);

                    dr(cs, cx, y, cw, DAY_ROW_H, bg, BLACK, 0.5f);

                    if (match != null) {
                        TeachingAssignment a = aMap.get(match.getTeachingAssignmentId());
                        if (a != null) {
                            String sc = subjectCode(a.getSubjectId());
                            String cn = classDisplayName(a.getClassStreamId());

                            float[] textColor = isMonday ? WHITE : BLACK;
                            float[] classColor = isMonday ? WHITE : MED_GRAY;

                            setFill(cs, textColor);
                            txc(cs, cx, cx + cw, midY + 12, sc, fb, 11);
                            setFill(cs, classColor);
                            txc(cs, cx, cx + cw, midY - 4, cn, f, 8);
                        }
                    }
                }
            }

            setStroke(cs, BLACK, 0.4f);
            hline(cs, tableLeft, tableRight, y - DAY_ROW_H);
            y -= DAY_ROW_H;
        }

        setStroke(cs, BLACK, 1f);
        strokeR(cs, tableLeft, y + rowsTotalH, tableWidth(), rowsTotalH);
    }

    // ---- Writers ----

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
}
