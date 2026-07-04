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
import org.apache.pdfbox.util.Matrix;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class PdfTimetableExporter implements TimetableExporter {

    private final TeachingAssignmentRepository assignmentRepository;
    private final SubjectRepository subjectRepository;
    private final ClassStreamRepository classStreamRepository;
    private final TeacherRepository teacherRepository;
    private final PeriodRepository periodRepository;
    private final RoomRepository roomRepository;
    private final BreakRepository breakRepository;

    public PdfTimetableExporter(TeachingAssignmentRepository assignmentRepository,
                                SubjectRepository subjectRepository,
                                ClassStreamRepository classStreamRepository,
                                TeacherRepository teacherRepository,
                                PeriodRepository periodRepository,
                                RoomRepository roomRepository,
                                BreakRepository breakRepository) {
        this.assignmentRepository = assignmentRepository;
        this.subjectRepository = subjectRepository;
        this.classStreamRepository = classStreamRepository;
        this.teacherRepository = teacherRepository;
        this.periodRepository = periodRepository;
        this.roomRepository = roomRepository;
        this.breakRepository = breakRepository;
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
        writePdf(data, bos);
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
        writeCombinedPdf(data, "Stream", stream, entries -> {
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
        writeCombinedPdf(data, "Form", String.valueOf(form), entries -> {
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

    private void writePdf(TimetableRepository.TimetableWithEntries data, OutputStream output) {
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

        try (PDDocument doc = new PDDocument()) {
            List<Period> periods = periodRepository.findAll().stream()
                    .sorted(Comparator.comparingInt(Period::getPeriodNumber))
                    .toList();

            List<BreakPeriod> breaks = breakRepository.findAll().stream()
                    .sorted(Comparator.comparingInt(BreakPeriod::getSortOrder))
                    .toList();

            List<TimeBlock> timeline = DailyTimelineGenerator.generate(periods, breaks);

            Map<Long, TeachingAssignment> assignmentMap = assignmentRepository.findAll().stream()
                    .collect(Collectors.toMap(TeachingAssignment::getId, a -> a));

            Map<String, List<TimetableEntry>> byClass = groupByClass(data.entries());

            for (Map.Entry<String, List<TimetableEntry>> classEntry : byClass.entrySet()) {
                PDRectangle pageSize = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
                PDPage page = new PDPage(pageSize);
                doc.addPage(page);

                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    renderClassPage(cs, pageSize, data, classEntry.getKey(), classEntry.getValue(),
                            timeline, assignmentMap, font, fontBold);
                }
            }

            doc.save(output);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render PDF", e);
        }
    }

    private void renderClassPage(PDPageContentStream cs, PDRectangle pageSize,
                                 TimetableRepository.TimetableWithEntries data,
                                 String className, List<TimetableEntry> entries,
                                 List<TimeBlock> timeline,
                                 Map<Long, TeachingAssignment> assignmentMap,
                                 PDType1Font font, PDType1Font fontBold) throws IOException {

        float margin = 30;
        float yTop = pageSize.getHeight() - margin;
        float tableLeft = margin;
        float tableWidth = pageSize.getWidth() - 2 * margin;

        float dayColW = 70;
        float nonLessonColW = 50;
        int numLessons = (int) timeline.stream().filter(t -> t.blockType() == BlockType.LESSON).count();
        int numNonLessons = timeline.size() - numLessons;
        float lessonColW = numLessons > 0
                ? (tableWidth - dayColW - numNonLessons * nonLessonColW) / numLessons
                : 0;

        // Row heights
        float titleH = 18;
        float headerH = 32;
        float rowH = 44;

        // Title
        cs.setFont(fontBold, 13);
        drawText(cs, tableLeft, yTop, "Thorium Timetable — " + className);

        cs.setFont(font, 9);
        drawText(cs, tableLeft, yTop - 14, data.timetable().getName());

        float y = yTop - titleH - 10;

        // ---- Column header row ----
        drawCellBg(cs, tableLeft, y, dayColW, headerH, null);
        drawCellBorder(cs, tableLeft, y, dayColW, headerH);

        float cx = tableLeft + dayColW;
        for (TimeBlock block : timeline) {
            float colW = block.blockType() == BlockType.LESSON ? lessonColW : nonLessonColW;

            drawCellBg(cs, cx, y, colW, headerH, null);
            drawCellBorder(cs, cx, y, colW, headerH);

            if (block instanceof LessonBlock lb) {
                cs.setFont(fontBold, 10);
                drawTextCentered(cs, cx, cx + colW, y + headerH - 12,
                        String.valueOf(lb.periodNumber()), fontBold, 10);
                cs.setFont(font, 7);
                drawTextCentered(cs, cx, cx + colW, y + headerH - 24,
                        lb.startTime() + " - " + lb.endTime(), font, 7);
            } else {
                cs.setFont(font, 6);
                drawTextCentered(cs, cx, cx + colW, y + headerH - 12,
                        block.startTime() + " - " + block.endTime(), font, 6);
                cs.setFont(fontBold, 7);
                drawTextCentered(cs, cx, cx + colW, y + headerH - 22,
                        block.label().toUpperCase(), fontBold, 7);
            }

            cx += colW;
        }

        y -= headerH;

        // Build entry lookup: day_name -> period_number -> entry
        Map<String, List<TimetableEntry>> entryLookup = new HashMap<>();
        for (TimetableEntry entry : entries) {
            String key = entry.getDayOfWeek().name();
            entryLookup.computeIfAbsent(key, k -> new ArrayList<>()).add(entry);
        }

        List<DayOfWeek> days = List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

        // Draw non-lesson column backgrounds and vertical text (spanning all rows)
        cx = tableLeft + dayColW;
        for (TimeBlock block : timeline) {
            if (block.blockType() != BlockType.LESSON) {
                float colW = nonLessonColW;
                float bch = days.size() * rowH;
                String bgColor = block.blockType() == BlockType.EVENT ? "#E8F0FE" : "#EFEFEF";
                drawCellBg(cs, cx, y, colW, bch, bgColor);
                drawCellBorder(cs, cx, y, colW, bch);

                String label = block.label().toUpperCase();
                cs.saveGraphicsState();
                cs.transform(Matrix.getTranslateInstance(cx + colW / 2, y - bch / 2));
                cs.transform(Matrix.getRotateInstance(Math.toRadians(90), 0f, 0f));
                cs.beginText();
                cs.setFont(fontBold, 11);
                float tw = fontBold.getStringWidth(label) / 1000f * 11;
                cs.newLineAtOffset(-tw / 2, -3);
                cs.showText(label);
                cs.endText();
                cs.restoreGraphicsState();
            }
            cx += block.blockType() == BlockType.LESSON ? lessonColW : nonLessonColW;
        }

        // ---- Day rows ----
        cx = tableLeft + dayColW;
        for (int r = 0; r < days.size(); r++) {
            DayOfWeek day = days.get(r);
            boolean altRow = r % 2 == 1;
            String bgColor = altRow ? "#F9F9F9" : null;

            drawCellBg(cs, tableLeft, y, dayColW, rowH, bgColor);
            drawCellBorder(cs, tableLeft, y, dayColW, rowH);
            cs.setFont(fontBold, 10);
            drawTextCentered(cs, tableLeft, tableLeft + dayColW, y + rowH / 2f + 3,
                    day.displayName(), fontBold, 10);

            List<TimetableEntry> dayEntries = entryLookup.getOrDefault(day.name(), List.of());
            float colX = tableLeft + dayColW;
            for (TimeBlock block : timeline) {
                float colW = block.blockType() == BlockType.LESSON ? lessonColW : nonLessonColW;
                float cellMidY = y + rowH / 2f;

                if (block instanceof LessonBlock lb) {
                    drawCellBg(cs, colX, y, colW, rowH, bgColor);
                    drawCellBorder(cs, colX, y, colW, rowH);

                    int pn = lb.periodNumber();
                    TimetableEntry match = findEntry(dayEntries, pn);
                    if (match != null) {
                        TeachingAssignment assignment = assignmentMap.get(match.getTeachingAssignmentId());
                        if (assignment != null) {
                            String subjectCode = subjectRepository.findById(assignment.getSubjectId())
                                    .map(s -> {
                                        String n = s.getName();
                                        return n != null ? n.trim().substring(0, Math.min(4, n.trim().length())).toUpperCase() : "?";
                                    })
                                    .orElse("?");
                            String teacherInit = teacherRepository.findById(assignment.getTeacherId())
                                    .map(t -> NameFormatter.initials(t.getName()))
                                    .orElse("?");
                            String roomCode = match.getRoomId() != null
                                    ? roomRepository.findById(match.getRoomId())
                                        .map(com.thorium.domain.model.Room::getCode)
                                        .orElse(null)
                                    : null;

                            float pad = 4;
                            float innerX = colX + pad;

                            cs.setFont(fontBold, 9);
                            drawTextCentered(cs, colX, colX + colW, cellMidY + 6, subjectCode, fontBold, 9);

                            cs.setFont(font, 7);
                            drawText(cs, innerX, y + 9, teacherInit);

                            if (roomCode != null) {
                                float roomW = font.getStringWidth(roomCode) / 1000f * 7;
                                drawText(cs, colX + colW - pad - roomW, y + 9, roomCode);
                            }
                        }
                    }
                } else {
                    drawCellBorder(cs, colX, y, colW, rowH);
                }

                colX += colW;
            }

            y -= rowH;
        }
    }

    private TimetableEntry findEntry(List<TimetableEntry> entries, int periodNumber) {
        for (TimetableEntry e : entries) {
            if (e.getPeriodNumber() == periodNumber) return e;
        }
        return null;
    }

    private void drawText(PDPageContentStream cs, float x, float y, String text) throws IOException {
        cs.beginText();
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    private void drawTextCentered(PDPageContentStream cs, float x1, float x2, float y, String text,
                                   PDType1Font font, float fontSize) throws IOException {
        float textW = font.getStringWidth(text) / 1000f * fontSize;
        float centerX = (x1 + x2) / 2f - textW / 2f;
        drawText(cs, centerX, y, text);
    }

    private void drawCellBg(PDPageContentStream cs, float x, float y, float w, float h, String colorHex) throws IOException {
        if (colorHex == null) return;
        float[] rgb = hexToRgb(colorHex);
        cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);
        cs.addRect(x, y - h, w, h);
        cs.fill();
        cs.setNonStrokingColor(0, 0, 0);
    }

    private void drawCellBorder(PDPageContentStream cs, float x, float y, float w, float h) throws IOException {
        cs.setStrokingColor(0.29f, 0.29f, 0.29f);
        cs.setLineWidth(0.5f);
        cs.addRect(x, y - h, w, h);
        cs.stroke();
        cs.setStrokingColor(0, 0, 0);
    }

    private float[] hexToRgb(String hex) {
        int c = Integer.parseInt(hex.substring(1), 16);
        return new float[]{((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f};
    }

    private Map<String, List<TimetableEntry>> groupByClass(List<TimetableEntry> entries) {
        Map<Long, TeachingAssignment> assignments = assignmentRepository.findAll().stream()
                .collect(Collectors.toMap(TeachingAssignment::getId, a -> a));

        Map<String, List<TimetableEntry>> grouped = new LinkedHashMap<>();
        for (TimetableEntry entry : entries) {
            TeachingAssignment assignment = assignments.get(entry.getTeachingAssignmentId());
            String classKey = "Unknown";
            if (assignment != null) {
                classKey = classStreamRepository.findById(assignment.getClassStreamId())
                        .map(c -> c.getDisplayName())
                        .orElse("Class #" + assignment.getClassStreamId());
            }
            grouped.computeIfAbsent(classKey, k -> new ArrayList<>()).add(entry);
        }
        return grouped;
    }

    private Map<Long, TeachingAssignment> assignmentMap() {
        return assignmentRepository.findAll().stream()
                .collect(Collectors.toMap(TeachingAssignment::getId, a -> a));
    }

    private void writeTeacherPdf(TimetableRepository.TimetableWithEntries data, Long teacherId, OutputStream output) {
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

        try (PDDocument doc = new PDDocument()) {
            List<Period> periods = periodRepository.findAll().stream()
                    .sorted(Comparator.comparingInt(Period::getPeriodNumber))
                    .toList();
            List<BreakPeriod> breaks = breakRepository.findAll().stream()
                    .sorted(Comparator.comparingInt(BreakPeriod::getSortOrder))
                    .toList();
            List<TimeBlock> timeline = DailyTimelineGenerator.generate(periods, breaks);
            Map<Long, TeachingAssignment> assignmentMap = assignmentMap();

            Teacher teacher = teacherRepository.findById(teacherId)
                    .orElseThrow(() -> new IllegalArgumentException("Teacher not found: " + teacherId));

            List<TimetableEntry> teacherEntries = data.entries().stream()
                    .filter(e -> {
                        TeachingAssignment a = assignmentMap.get(e.getTeachingAssignmentId());
                        return a != null && a.getTeacherId().equals(teacherId);
                    })
                    .toList();

            PDRectangle pageSize = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
            PDPage page = new PDPage(pageSize);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                renderTeacherPage(cs, pageSize, data, teacher, teacherEntries, timeline, assignmentMap, font, fontBold);
            }

            doc.save(output);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render teacher PDF", e);
        }
    }

    private void renderTeacherPage(PDPageContentStream cs, PDRectangle pageSize,
                                    TimetableRepository.TimetableWithEntries data,
                                    Teacher teacher, List<TimetableEntry> entries,
                                    List<TimeBlock> timeline,
                                    Map<Long, TeachingAssignment> assignmentMap,
                                    PDType1Font font, PDType1Font fontBold) throws IOException {
        float margin = 30;
        float yTop = pageSize.getHeight() - margin;
        float tableLeft = margin;
        float tableWidth = pageSize.getWidth() - 2 * margin;

        float dayColW = 70;
        float nonLessonColW = 50;
        int numLessons = (int) timeline.stream().filter(t -> t.blockType() == BlockType.LESSON).count();
        int numNonLessons = timeline.size() - numLessons;
        float lessonColW = numLessons > 0
                ? (tableWidth - dayColW - numNonLessons * nonLessonColW) / numLessons
                : 0;

        float titleH = 18;
        float headerH = 32;
        float rowH = 44;

        cs.setFont(fontBold, 13);
        drawText(cs, tableLeft, yTop, "Teacher Timetable — " + teacher.getName());

        cs.setFont(font, 9);
        drawText(cs, tableLeft, yTop - 14, data.timetable().getName());

        float y = yTop - titleH - 10;

        drawCellBg(cs, tableLeft, y, dayColW, headerH, null);
        drawCellBorder(cs, tableLeft, y, dayColW, headerH);

        float cx = tableLeft + dayColW;
        for (TimeBlock block : timeline) {
            float colW = block.blockType() == BlockType.LESSON ? lessonColW : nonLessonColW;

            drawCellBg(cs, cx, y, colW, headerH, null);
            drawCellBorder(cs, cx, y, colW, headerH);

            if (block instanceof LessonBlock lb) {
                cs.setFont(fontBold, 10);
                drawTextCentered(cs, cx, cx + colW, y + headerH - 12,
                        String.valueOf(lb.periodNumber()), fontBold, 10);
                cs.setFont(font, 7);
                drawTextCentered(cs, cx, cx + colW, y + headerH - 24,
                        lb.startTime() + " - " + lb.endTime(), font, 7);
            } else {
                cs.setFont(font, 6);
                drawTextCentered(cs, cx, cx + colW, y + headerH - 12,
                        block.startTime() + " - " + block.endTime(), font, 6);
                cs.setFont(fontBold, 7);
                drawTextCentered(cs, cx, cx + colW, y + headerH - 22,
                        block.label().toUpperCase(), fontBold, 7);
            }
            cx += colW;
        }

        y -= headerH;

        Map<String, List<TimetableEntry>> entryLookup = new HashMap<>();
        for (TimetableEntry entry : entries) {
            entryLookup.computeIfAbsent(entry.getDayOfWeek().name(), k -> new ArrayList<>()).add(entry);
        }

        List<DayOfWeek> days = List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

        cx = tableLeft + dayColW;
        for (TimeBlock block : timeline) {
            if (block.blockType() != BlockType.LESSON) {
                float colW = nonLessonColW;
                float bch = days.size() * rowH;
                drawCellBg(cs, cx, y, colW, bch, "#EFEFEF");
                drawCellBorder(cs, cx, y, colW, bch);

                String label = block.label().toUpperCase();
                cs.saveGraphicsState();
                cs.transform(Matrix.getTranslateInstance(cx + colW / 2, y - bch / 2));
                cs.transform(Matrix.getRotateInstance(Math.toRadians(90), 0f, 0f));
                cs.beginText();
                cs.setFont(fontBold, 11);
                float tw = fontBold.getStringWidth(label) / 1000f * 11;
                cs.newLineAtOffset(-tw / 2, -3);
                cs.showText(label);
                cs.endText();
                cs.restoreGraphicsState();
            }
            cx += block.blockType() == BlockType.LESSON ? lessonColW : nonLessonColW;
        }

        cx = tableLeft + dayColW;
        for (int r = 0; r < days.size(); r++) {
            DayOfWeek day = days.get(r);
            boolean altRow = r % 2 == 1;
            String bgColor = altRow ? "#F9F9F9" : null;

            drawCellBg(cs, tableLeft, y, dayColW, rowH, bgColor);
            drawCellBorder(cs, tableLeft, y, dayColW, rowH);
            cs.setFont(fontBold, 10);
            drawTextCentered(cs, tableLeft, tableLeft + dayColW, y + rowH / 2f + 3,
                    day.displayName(), fontBold, 10);

            List<TimetableEntry> dayEntries = entryLookup.getOrDefault(day.name(), List.of());
            float colX = tableLeft + dayColW;
            for (TimeBlock block : timeline) {
                float colW = block.blockType() == BlockType.LESSON ? lessonColW : nonLessonColW;
                float cellMidY = y + rowH / 2f;

                if (block instanceof LessonBlock lb) {
                    drawCellBg(cs, colX, y, colW, rowH, bgColor);
                    drawCellBorder(cs, colX, y, colW, rowH);

                    int pn = lb.periodNumber();
                    TimetableEntry match = findEntry(dayEntries, pn);
                    if (match != null) {
                        TeachingAssignment assignment = assignmentMap.get(match.getTeachingAssignmentId());
                        if (assignment != null) {
                            String subjectCode = subjectRepository.findById(assignment.getSubjectId())
                                    .map(s -> {
                                        String n = s.getName();
                                        return n != null ? n.trim().substring(0, Math.min(4, n.trim().length())).toUpperCase() : "?";
                                    })
                                    .orElse("?");
                            String className = classStreamRepository.findById(assignment.getClassStreamId())
                                    .map(ClassStream::getDisplayName)
                                    .orElse("?");
                            String roomCode = match.getRoomId() != null
                                    ? roomRepository.findById(match.getRoomId())
                                        .map(Room::getCode)
                                        .orElse(null)
                                    : null;

                            float pad = 4;
                            float innerX = colX + pad;

                            cs.setFont(fontBold, 9);
                            drawTextCentered(cs, colX, colX + colW, cellMidY + 6, subjectCode, fontBold, 9);

                            cs.setFont(font, 7);
                            drawText(cs, innerX, y + 9, className);

                            if (roomCode != null) {
                                float roomW = font.getStringWidth(roomCode) / 1000f * 7;
                                drawText(cs, colX + colW - pad - roomW, y + 9, roomCode);
                            }
                        }
                    }
                } else {
                    drawCellBorder(cs, colX, y, colW, rowH);
                }
                colX += colW;
            }
            y -= rowH;
        }
    }

    @FunctionalInterface
    private interface EntryFilter {
        List<TimetableEntry> filter(List<TimetableEntry> entries);
    }

    private void writeCombinedPdf(TimetableRepository.TimetableWithEntries data, String groupLabel, String groupValue,
                                   EntryFilter filter, OutputStream output) {
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

        try (PDDocument doc = new PDDocument()) {
            List<Period> periods = periodRepository.findAll().stream()
                    .sorted(Comparator.comparingInt(Period::getPeriodNumber))
                    .toList();
            List<BreakPeriod> breaks = breakRepository.findAll().stream()
                    .sorted(Comparator.comparingInt(BreakPeriod::getSortOrder))
                    .toList();
            List<TimeBlock> timeline = DailyTimelineGenerator.generate(periods, breaks);

            Map<Long, TeachingAssignment> assignmentMap = assignmentMap();

            List<TimetableEntry> filtered = filter.filter(data.entries());

            Map<String, List<TimetableEntry>> byClass = new LinkedHashMap<>();
            for (TimetableEntry entry : filtered) {
                TeachingAssignment a = assignmentMap.get(entry.getTeachingAssignmentId());
                String classKey = "Unknown";
                if (a != null) {
                    classKey = classStreamRepository.findById(a.getClassStreamId())
                            .map(ClassStream::getDisplayName)
                            .orElse("Class #" + a.getClassStreamId());
                }
                byClass.computeIfAbsent(classKey, k -> new ArrayList<>()).add(entry);
            }

            if (byClass.isEmpty()) return;

            for (Map.Entry<String, List<TimetableEntry>> classEntry : byClass.entrySet()) {
                PDRectangle pageSize = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
                PDPage page = new PDPage(pageSize);
                doc.addPage(page);

                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    renderClassPage(cs, pageSize, data, classEntry.getKey(), classEntry.getValue(),
                            timeline, assignmentMap, font, fontBold);
                }
            }

            doc.save(output);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render combined PDF", e);
        }
    }
}
