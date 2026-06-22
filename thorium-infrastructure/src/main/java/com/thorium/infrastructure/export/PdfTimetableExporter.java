package com.thorium.infrastructure.export;

import com.thorium.application.port.*;
import com.thorium.application.util.NameFormatter;
import com.thorium.domain.model.TeachingAssignment;
import com.thorium.domain.model.TimetableEntry;
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
import java.util.*;
import java.util.stream.Collectors;

public class PdfTimetableExporter implements TimetableExporter {

    private final TeachingAssignmentRepository assignmentRepository;
    private final SubjectRepository subjectRepository;
    private final ClassStreamRepository classStreamRepository;
    private final TeacherRepository teacherRepository;
    private final PeriodRepository periodRepository;
    private final RoomRepository roomRepository;

    public PdfTimetableExporter(TeachingAssignmentRepository assignmentRepository,
                                SubjectRepository subjectRepository,
                                ClassStreamRepository classStreamRepository,
                                TeacherRepository teacherRepository,
                                PeriodRepository periodRepository,
                                RoomRepository roomRepository) {
        this.assignmentRepository = assignmentRepository;
        this.subjectRepository = subjectRepository;
        this.classStreamRepository = classStreamRepository;
        this.teacherRepository = teacherRepository;
        this.periodRepository = periodRepository;
        this.roomRepository = roomRepository;
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

    private void writePdf(TimetableRepository.TimetableWithEntries data, OutputStream output) {
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

        try (PDDocument doc = new PDDocument()) {
            List<com.thorium.domain.model.Period> periods = periodRepository.findAll().stream()
                    .sorted(Comparator.comparingInt(com.thorium.domain.model.Period::getPeriodNumber))
                    .toList();

            Map<String, List<TimetableEntry>> byClass = groupByClass(data.entries());

            for (Map.Entry<String, List<TimetableEntry>> classEntry : byClass.entrySet()) {
                PDRectangle pageSize = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
                PDPage page = new PDPage(pageSize);
                doc.addPage(page);

                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    renderClassPage(cs, pageSize, data, classEntry.getKey(), classEntry.getValue(), periods, font, fontBold);
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
                                 List<com.thorium.domain.model.Period> periods,
                                 PDType1Font font, PDType1Font fontBold) throws IOException {

        float margin = 30;
        float yTop = pageSize.getHeight() - margin;
        float tableLeft = margin;
        float tableWidth = pageSize.getWidth() - 2 * margin;

        // Column widths
        float dayColW = 70;
        int numPeriods = periods.size();
        float periodColW = (tableWidth - dayColW) / numPeriods;

        // Row heights
        float titleH = 18;
        float headerH = 32;
        float rowH = 44;

        // Title
        cs.setFont(fontBold, 13);
        drawText(cs, tableLeft, yTop, "Thorium Timetable — " + className);

        // Subtitle: timetable name
        cs.setFont(font, 9);
        drawText(cs, tableLeft, yTop - 14, data.timetable().getName());

        float y = yTop - titleH - 10;

        // ---- Column header row ----
        drawCellBg(cs, tableLeft, y, dayColW, headerH, null);
        drawCellBorder(cs, tableLeft, y, dayColW, headerH);

        for (int p = 0; p < numPeriods; p++) {
            com.thorium.domain.model.Period period = periods.get(p);
            float cx = tableLeft + dayColW + p * periodColW;

            drawCellBg(cs, cx, y, periodColW, headerH, null);
            drawCellBorder(cs, cx, y, periodColW, headerH);

            cs.setFont(fontBold, 10);
            drawTextCentered(cs, cx, cx + periodColW, y + headerH - 12,
                    String.valueOf(period.getPeriodNumber()), fontBold, 10);

            cs.setFont(font, 7);
            drawTextCentered(cs, cx, cx + periodColW, y + headerH - 24,
                    period.getStartTime() + " - " + period.getEndTime(), font, 7);
        }

        y -= headerH;

        // Build lookup map: day_name -> period_number -> cell info
        Map<String, List<TimetableEntry>> entryLookup = new HashMap<>();
        Map<Long, TeachingAssignment> assignmentMap = assignmentRepository.findAll().stream()
                .collect(Collectors.toMap(TeachingAssignment::getId, a -> a));
        for (TimetableEntry entry : entries) {
            String key = entry.getDayOfWeek().name();
            entryLookup.computeIfAbsent(key, k -> new ArrayList<>()).add(entry);
        }

        // ---- Day rows ----
        List<DayOfWeek> days = List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
        for (int r = 0; r < days.size(); r++) {
            DayOfWeek day = days.get(r);
            boolean altRow = r % 2 == 1;
            String bgColor = altRow ? "#F9F9F9" : null;

            // Day label cell
            drawCellBg(cs, tableLeft, y, dayColW, rowH, bgColor);
            drawCellBorder(cs, tableLeft, y, dayColW, rowH);
            cs.setFont(fontBold, 10);
            drawTextCentered(cs, tableLeft, tableLeft + dayColW, y + rowH / 2f + 3,
                    day.displayName(), fontBold, 10);

            // Period cells
            List<TimetableEntry> dayEntries = entryLookup.getOrDefault(day.name(), List.of());
            for (int p = 0; p < numPeriods; p++) {
                com.thorium.domain.model.Period period = periods.get(p);
                float cx = tableLeft + dayColW + p * periodColW;
                float cellMidY = y + rowH / 2f;

                drawCellBg(cs, cx, y, periodColW, rowH, bgColor);
                drawCellBorder(cs, cx, y, periodColW, rowH);

                int pn = period.getPeriodNumber();
                TimetableEntry match = findEntry(dayEntries, pn);
                if (match != null) {
                    TeachingAssignment assignment = assignmentMap.get(match.getTeachingAssignmentId());
                    if (assignment != null) {
                        String subjectCode = subjectRepository.findById(assignment.getSubjectId())
                                .map(com.thorium.domain.model.Subject::getCode)
                                .map(c -> c.length() > 6 ? c.substring(0, 6) : c)
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
                        float innerW = periodColW - 2 * pad;
                        float innerX = cx + pad;

                        // Subject code - bold, centered at top
                        cs.setFont(fontBold, 9);
                        drawTextCentered(cs, cx, cx + periodColW, cellMidY + 6, subjectCode, fontBold, 9);

                        // Teacher initials - small, bottom-left
                        cs.setFont(font, 7);
                        drawText(cs, innerX, y + 9, teacherInit);

                        // Room code - small, bottom-right
                        if (roomCode != null) {
                            float roomW = font.getStringWidth(roomCode) / 1000f * 7;
                            drawText(cs, cx + periodColW - pad - roomW, y + 9, roomCode);
                        }
                    }
                }
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
}
