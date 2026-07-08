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

public class PdfTimetableExporter implements TimetableExporter {

    // ---- Fixed 17-column layout (matching table.txt spec) ----
    // 1 day col + 16 timetable cols: Ass.|1|2|3|Tea|4|5|SB|6|7|Lunch|8|9|10|Games|11

    private static final int TOTAL_COLUMNS = 17;

    private enum ColType { DAY_LABEL, LESSON_COL, BREAK_COL }

    private static final ColType[] COL_TYPES = {
            ColType.DAY_LABEL,      // 0: Day labels
            ColType.BREAK_COL,      // 1: Assembly
            ColType.LESSON_COL,     // 2: Period 1
            ColType.LESSON_COL,     // 3: Period 2
            ColType.LESSON_COL,     // 4: Period 3
            ColType.BREAK_COL,      // 5: Tea Break
            ColType.LESSON_COL,     // 6: Period 4
            ColType.LESSON_COL,     // 7: Period 5
            ColType.BREAK_COL,      // 8: Short Break
            ColType.LESSON_COL,     // 9: Period 6
            ColType.LESSON_COL,     // 10: Period 7
            ColType.BREAK_COL,      // 11: Lunch
            ColType.LESSON_COL,     // 12: Period 8
            ColType.LESSON_COL,     // 13: Period 9
            ColType.LESSON_COL,     // 14: Period 10
            ColType.BREAK_COL,      // 15: Games
            ColType.LESSON_COL      // 16: Period 11
    };

    private static final String[] COL_LABELS = {
            "",          // Day
            "Ass.",
            "1", "2", "3",
            "Tea",
            "4", "5",
            "SB",
            "6", "7",
            "Lunch",
            "8", "9", "10",
            "Games",
            "11"
    };

    private static final int[] COL_PERIOD_NUMBERS = {
            -1,  // Day
            -1,  // Assembly
            1, 2, 3,
            -1,  // Tea Break
            4, 5,
            -1,  // Short Break
            6, 7,
            -1,  // Lunch
            8, 9, 10,
            -1,  // Games
            11
    };

    private static final String[] DAY_ABBREVIATIONS = {"Mo", "Tu", "We", "Th", "Fr"};

    // ---- Layout constants (mm converted to pt: 1mm = 2.83465pt) ----

    private static final float MM_TO_PT = 2.83465f;
    private static final float MARGIN_TOP = 15 * MM_TO_PT;
    private static final float MARGIN_BOTTOM = 15 * MM_TO_PT;
    private static final float MARGIN_LEFT = 12 * MM_TO_PT;
    private static final float MARGIN_RIGHT = 12 * MM_TO_PT;

    private static final float DAY_COL_W = 48;
    private static final float BREAK_COL_W = 52;
    private static final float LESSON_COL_W = 46;

    private static final float SCHOOL_NAME_H = 16;
    private static final float HEADING_H = 26;
    private static final float HEADER_ROW_H = 38;
    private static final float DAY_ROW_H = 44;

    // ---- Black & White palette ----

    private static final float[] BLACK = {0, 0, 0};
    private static final float[] WHITE = {1, 1, 1};
    private static final float[] LIGHT_GRAY = {0.92f, 0.92f, 0.92f}; // subtle alternating rows
    private static final float[] DARK_GRAY = {0.4f, 0.4f, 0.4f};     // school name / secondary text
    private static final float[] GRID_LINE = {0, 0, 0};

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    // ---- Repositories ----

    private final TeachingAssignmentRepository assignmentRepository;
    private final SubjectRepository subjectRepository;
    private final ClassStreamRepository classStreamRepository;
    private final TeacherRepository teacherRepository;
    private final RoomRepository roomRepository;
    private final PeriodRepository periodRepository;
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
        this.roomRepository = roomRepository;
        this.breakRepository = breakRepository;
        this.schoolSettingsRepository = schoolSettingsRepository;
        this.periodRepository = periodRepository;
        this.periodTimeCache = null;
    }

    // ---- TimetableExporter interface ----

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
        writeAllClassesPdf(data, bos);
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
        writeFilteredPdf(data, entries -> {
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
        writeFilteredPdf(data, entries -> {
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

    @Override
    public byte[] renderAscTeacherPdfToBytes(TimetableRepository.TimetableWithEntries data, Long teacherId) {
        throw new UnsupportedOperationException("Use AscStyleTeacherPdfExporter");
    }

    @Override
    public byte[] renderAscAllTeachersPdfToBytes(TimetableRepository.TimetableWithEntries data) {
        throw new UnsupportedOperationException("Use AscStyleTeacherPdfExporter");
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

    // ---- Column layout ----

    private float[] computeColStarts(float tableLeft) {
        float[] starts = new float[TOTAL_COLUMNS];
        starts[0] = tableLeft;
        float cx = tableLeft + DAY_COL_W;
        for (int i = 1; i < TOTAL_COLUMNS; i++) {
            starts[i] = cx;
            cx += colWidth(i);
        }
        return starts;
    }

    private float colWidth(int colIdx) {
        if (colIdx == 0) return DAY_COL_W;
        return COL_TYPES[colIdx] == ColType.BREAK_COL ? BREAK_COL_W : LESSON_COL_W;
    }

    private float tableWidth() {
        float w = DAY_COL_W;
        for (int i = 1; i < TOTAL_COLUMNS; i++) {
            w += colWidth(i);
        }
        return w;
    }

    // ---- DB-backed period times ----

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

    private String breakTimeForColumn(int colIdx) {
        switch (colIdx) {
            case 1:  return "7:10 - 8:00";  // Assembly
            case 5:  return "10:00 - 10:20"; // Tea Break
            case 8:  return "11:40 - 11:50"; // Short Break
            case 11: return "13:10 - 14:00"; // Lunch
            case 15: return "16:00 - 16:40"; // Games
            default: return "";
        }
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

    // ---- Group entries ----

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

    // ========== Page Rendering (fixed 17-column, A4 landscape, B&W) ==========

    private void renderPage(PDPageContentStream cs, PDRectangle pageSize,
                            String headingText, List<TimetableEntry> entries,
                            Map<Long, TeachingAssignment> aMap,
                            PDType1Font fb, PDType1Font f, PDType1Font fi) throws IOException {
        float pw = pageSize.getWidth();
        float ph = pageSize.getHeight();
        float tableW = tableWidth();

        // Center table horizontally
        float tableLeft = (pw - tableW) / 2f;
        float yTop = ph - MARGIN_TOP;

        float[] colStarts = computeColStarts(tableLeft);
        String school = schoolName();
        float y = yTop;

        // ---- School name (top-left, uppercase small) ----
        if (!school.isBlank()) {
            setFill(cs, DARK_GRAY);
            txt(cs, tableLeft, y, school.toUpperCase(), fb, 9);
            y -= SCHOOL_NAME_H;
        }

        // ---- Heading (class/teacher name, centered large bold) ----
        setFill(cs, BLACK);
        txc(cs, tableLeft, tableLeft + tableW, y, headingText.toUpperCase(), fb, 17);
        y -= HEADING_H;

        // ---- Top border before header ----
        setStroke(cs, GRID_LINE, 0.8f);
        hline(cs, tableLeft, tableLeft + tableW, y);

        // ---- Column headers (period number + time) ----
        renderColumnHeaders(cs, y, colStarts, fb, f);
        y -= HEADER_ROW_H;

        // ---- Day rows with merged break columns ----
        renderDayRows(cs, y, colStarts, entries, aMap, fb, f, fi);
    }

    private void renderColumnHeaders(PDPageContentStream cs, float y, float[] colStarts,
                                     PDType1Font fb, PDType1Font f) throws IOException {
        // Day header cell — empty (just grid border)
        dr(cs, colStarts[0], y, DAY_COL_W, HEADER_ROW_H, WHITE, GRID_LINE, 0.5f);

        for (int i = 1; i < TOTAL_COLUMNS; i++) {
            float cx = colStarts[i];
            float cw = colWidth(i);
            dr(cs, cx, y, cw, HEADER_ROW_H, WHITE, GRID_LINE, 0.5f);
            setFill(cs, BLACK);

            boolean isBreak = COL_TYPES[i] == ColType.BREAK_COL;
            int pn = COL_PERIOD_NUMBERS[i];

            // Time label (small, top)
            String timeLabel;
            if (isBreak) {
                timeLabel = breakTimeForColumn(i);
            } else {
                timeLabel = timeForPeriod(pn);
            }
            if (!timeLabel.isBlank()) {
                txc(cs, cx, cx + cw, y + HEADER_ROW_H - 8, timeLabel, f, 7);
            }

            // Period/break label (bold, below time)
            txc(cs, cx, cx + cw, y + 6, COL_LABELS[i], fb, 10);
        }

        // Bottom border of header row
        setStroke(cs, GRID_LINE, 0.8f);
        hline(cs, colStarts[0], colStarts[0] + tableWidth(), y);
    }

    private void renderDayRows(PDPageContentStream cs, float y, float[] colStarts,
                               List<TimetableEntry> entries, Map<Long, TeachingAssignment> aMap,
                               PDType1Font fb, PDType1Font f, PDType1Font fi) throws IOException {
        // Build lookup: day.name -> (periodNumber -> entry)
        Map<String, Map<Integer, TimetableEntry>> dayLookup = new HashMap<>();
        for (TimetableEntry e : entries) {
            dayLookup.computeIfAbsent(e.getDayOfWeek().name(), _k -> new HashMap<>())
                    .put(e.getPeriodNumber(), e);
        }

        float rowsTotalH = 5 * DAY_ROW_H;
        float tableLeft = colStarts[0];
        float tableRight = tableLeft + tableWidth();

        // ---- Pre-render: merged break columns across all rows ----
        for (int i = 1; i < TOTAL_COLUMNS; i++) {
            if (COL_TYPES[i] != ColType.BREAK_COL) continue;
            float cx = colStarts[i];
            float cw = colWidth(i);
            // White background with border
            dr(cs, cx, y, cw, rowsTotalH, WHITE, GRID_LINE, 0.5f);

            String fullLabel;
            switch (i) {
                case 1:  fullLabel = "ASSEMBLY/PREPS"; break;
                case 5:  fullLabel = "TEA BREAK"; break;
                case 8:  fullLabel = "SHORT BREAK"; break;
                case 11: fullLabel = "LUNCH BREAK"; break;
                case 15: fullLabel = "GAMES"; break;
                default: fullLabel = COL_LABELS[i].toUpperCase();
            }

            // Rotated text (90° CCW)
            cs.saveGraphicsState();
            float centerX = cx + cw / 2f;
            float centerY = y - rowsTotalH / 2f;
            cs.transform(new org.apache.pdfbox.util.Matrix(0, -1, 1, 0, centerX, centerY));
            setFill(cs, BLACK);
            float tw = fb.getStringWidth(fullLabel) / 1000f * 10;
            txc(cs, -tw / 2f, tw / 2f, -cw / 2f + 3, fullLabel, fb, 10);
            cs.restoreGraphicsState();
        }

        // ---- Render day rows ----
        for (int ri = 0; ri < 5; ri++) {
            DayOfWeek day = DayOfWeek.workingDays().get(ri);
            float[] bg = ri % 2 == 1 ? LIGHT_GRAY : WHITE;

            // Day column
            dr(cs, colStarts[0], y, DAY_COL_W, DAY_ROW_H, bg, GRID_LINE, 0.5f);
            setFill(cs, BLACK);
            txc(cs, colStarts[0], colStarts[0] + DAY_COL_W, y + DAY_ROW_H / 2f - 5,
                    DAY_ABBREVIATIONS[ri], fb, 12);

            // Lesson columns
            Map<Integer, TimetableEntry> dayEntries = dayLookup.getOrDefault(day.name(), new HashMap<>());
            float midY = y + DAY_ROW_H / 2f;

            for (int i = 1; i < TOTAL_COLUMNS; i++) {
                float cx = colStarts[i];
                float cw = colWidth(i);

                if (COL_TYPES[i] == ColType.LESSON_COL) {
                    int pn = COL_PERIOD_NUMBERS[i];
                    TimetableEntry match = dayEntries.get(pn);

                    dr(cs, cx, y, cw, DAY_ROW_H, bg, GRID_LINE, 0.5f);

                    if (match != null) {
                        TeachingAssignment a = aMap.get(match.getTeachingAssignmentId());
                        if (a != null) {
                            String sc = subjectCode(a.getSubjectId());
                            String ti = teacherInitials(a.getTeacherId());

                            // Subject code (top line, bold)
                            setFill(cs, BLACK);
                            txc(cs, cx, cx + cw, midY + 10, sc, fb, 9);

                            // Teacher initials (bottom line)
                            setFill(cs, DARK_GRAY);
                            txc(cs, cx, cx + cw, midY - 6, ti, f, 7);
                        }
                    }
                } else {
                    // Break column — draw vertical border on right
                    setStroke(cs, GRID_LINE, 0.4f);
                    vline(cs, cx + cw, y, y - DAY_ROW_H);
                }
            }

            // Bottom border for this row
            setStroke(cs, GRID_LINE, 0.4f);
            hline(cs, tableLeft, tableRight, y - DAY_ROW_H);

            y -= DAY_ROW_H;
        }

        // ---- Outer border (slightly thicker) ----
        setStroke(cs, GRID_LINE, 1.2f);
        strokeR(cs, tableLeft, y + rowsTotalH, tableWidth(), rowsTotalH);
    }

    // ========== PDF writers ==========

    private void writeAllClassesPdf(TimetableRepository.TimetableWithEntries data, OutputStream output) {
        PDType1Font fb = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDType1Font f = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font fi = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

        try (PDDocument doc = new PDDocument()) {
            Map<Long, TeachingAssignment> aMap = assignmentMap();
            Map<String, List<TimetableEntry>> byClass = groupByClass(data.entries());
            // A4 landscape
            float pw = PDRectangle.A4.getHeight();
            float ph = PDRectangle.A4.getWidth();

            for (var ce : byClass.entrySet()) {
                PDPage pg = new PDPage(new PDRectangle(pw, ph));
                doc.addPage(pg);
                PDPageContentStream cs = new PDPageContentStream(doc, pg);
                renderPage(cs, pg.getMediaBox(), ce.getKey(), ce.getValue(), aMap, fb, f, fi);
                cs.close();
            }
            doc.save(output);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render PDF", e);
        }
    }

    private void writeFilteredPdf(TimetableRepository.TimetableWithEntries data,
                                   Filter filter, OutputStream output) {
        PDType1Font fb = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDType1Font f = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font fi = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

        try (PDDocument doc = new PDDocument()) {
            Map<Long, TeachingAssignment> aMap = assignmentMap();
            List<TimetableEntry> filtered = filter.filter(data.entries());
            Map<String, List<TimetableEntry>> byClass = groupByClass(filtered);
            float pw = PDRectangle.A4.getHeight();
            float ph = PDRectangle.A4.getWidth();

            if (byClass.isEmpty()) {
                PDPage pg = new PDPage(new PDRectangle(pw, ph));
                doc.addPage(pg);
                try (PDPageContentStream cs = new PDPageContentStream(doc, pg)) {
                    txt(cs, MARGIN_LEFT, ph - MARGIN_TOP, "No entries found", fi, 12);
                }
                doc.save(output);
                return;
            }

            for (var ce : byClass.entrySet()) {
                PDPage pg = new PDPage(new PDRectangle(pw, ph));
                doc.addPage(pg);
                PDPageContentStream cs = new PDPageContentStream(doc, pg);
                renderPage(cs, pg.getMediaBox(), ce.getKey(), ce.getValue(), aMap, fb, f, fi);
                cs.close();
            }
            doc.save(output);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render filtered PDF", e);
        }
    }

    private void writeTeacherPdf(TimetableRepository.TimetableWithEntries data, Long teacherId, OutputStream output) {
        PDType1Font fb = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDType1Font f = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font fi = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

        try (PDDocument doc = new PDDocument()) {
            Map<Long, TeachingAssignment> aMap = assignmentMap();
            Teacher teacher = teacherRepository.findById(teacherId)
                    .orElseThrow(() -> new IllegalArgumentException("Teacher not found: " + teacherId));
            List<TimetableEntry> teacherEntries = data.entries().stream()
                    .filter(e -> {
                        TeachingAssignment a = aMap.get(e.getTeachingAssignmentId());
                        return a != null && a.getTeacherId().equals(teacherId);
                    })
                    .toList();
            float pw = PDRectangle.A4.getHeight();
            float ph = PDRectangle.A4.getWidth();

            PDPage pg = new PDPage(new PDRectangle(pw, ph));
            doc.addPage(pg);
            PDPageContentStream cs = new PDPageContentStream(doc, pg);
            // For teacher PDF, heading is the teacher name
            String heading = "Teacher " + teacher.getName();
            renderPage(cs, pg.getMediaBox(), heading, teacherEntries, aMap, fb, f, fi);
            cs.close();
            doc.save(output);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render teacher PDF", e);
        }
    }

    private void writeAllTeachersPdf(TimetableRepository.TimetableWithEntries data, OutputStream output) {
        PDType1Font fb = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDType1Font f = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font fi = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

        try (PDDocument doc = new PDDocument()) {
            Map<Long, TeachingAssignment> aMap = assignmentMap();
            List<Teacher> allTeachers = teacherRepository.findAll();
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
                PDPageContentStream cs = new PDPageContentStream(doc, pg);
                String heading = "Teacher " + teacher.getName();
                renderPage(cs, pg.getMediaBox(), heading, teacherEntries, aMap, fb, f, fi);
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
