package com.thorium.infrastructure.export;

import com.thorium.application.port.*;
import com.thorium.application.port.TimetableRepository.TimetableWithEntries;
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
import java.util.*;
import java.util.stream.Collectors;

public class ClassTimetablePdfExporter implements TimetableExporter {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font FONT_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private static final float MM = 72f / 25.4f;
    private static final float MARGIN = 10 * MM;
    private static final float PAGE_W = 842;
    private static final float PAGE_H = 595;

    private record PeriodCol(int periodNumber, String label, String startTime, String endTime, boolean isBreak) {}

    private static final List<DayOfWeek> DAYS = DayOfWeek.workingDays();
    private static final List<String> DAY_ABBR = List.of("Mo", "Tu", "We", "Th", "Fr");

    private final TeachingAssignmentRepository assignmentRepo;
    private final SubjectRepository subjectRepo;
    private final TeacherRepository teacherRepo;
    private final ClassStreamRepository classRepo;
    private final SchoolSettingsRepository settingsRepo;
    private final PeriodRepository periodRepo;

    public ClassTimetablePdfExporter(
            TeachingAssignmentRepository assignmentRepo,
            SubjectRepository subjectRepo,
            TeacherRepository teacherRepo,
            ClassStreamRepository classRepo,
            SchoolSettingsRepository settingsRepo,
            PeriodRepository periodRepo) {
        this.assignmentRepo = assignmentRepo;
        this.subjectRepo = subjectRepo;
        this.teacherRepo = teacherRepo;
        this.classRepo = classRepo;
        this.settingsRepo = settingsRepo;
        this.periodRepo = periodRepo;
    }

    private List<PeriodCol> loadPeriods() {
        return periodRepo.findAll().stream()
                .sorted(Comparator.comparingInt(Period::getPeriodNumber))
                .map(p -> new PeriodCol(
                        p.getPeriodNumber(),
                        p.getLabel(),
                        p.getStartTime().toString(),
                        p.getEndTime().toString(),
                        "BREAK".equals(p.getType())
                ))
                .collect(Collectors.toList());
    }

    @Override
    public byte[] renderTeacherPdfToBytes(TimetableWithEntries data, Long teacherId) {
        throw new UnsupportedOperationException("Use TeacherTimetablePdfExporter for teacher PDFs");
    }

    @Override
    public byte[] renderClassPdfToBytes(TimetableWithEntries data, Long classStreamId) {
        ClassStream classStream = classRepo.findById(classStreamId)
                .orElseThrow(() -> new IllegalArgumentException("Class/stream not found: " + classStreamId));
        SchoolSettings settings = settingsRepo.get();
        String schoolName = settings != null && settings.getSchoolName() != null
                ? settings.getSchoolName().toUpperCase() : "";

        try (PDDocument doc = new PDDocument()) {
            renderSingleClassPage(doc, data, classStream, schoolName);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to render class PDF", e);
        }
    }

    @Override
    public byte[] renderAllClassesPdfToBytes(TimetableWithEntries data) {
        SchoolSettings settings = settingsRepo.get();
        String schoolName = settings != null && settings.getSchoolName() != null
                ? settings.getSchoolName().toUpperCase() : "";
        List<ClassStream> allClasses = classRepo.findAll();

        try (PDDocument doc = new PDDocument()) {
            for (ClassStream cs : allClasses) {
                renderSingleClassPage(doc, data, cs, schoolName);
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to render all-classes PDF", e);
        }
    }

    private void renderSingleClassPage(PDDocument doc, TimetableWithEntries data,
                                        ClassStream classStream, String schoolName) throws IOException {
        List<PeriodCol> periods = loadPeriods();
        int numPeriods = periods.size();

        List<TeachingAssignment> assignments = assignmentRepo.findByClassStreamId(classStream.getId());
        Set<Long> taIds = assignments.stream().map(TeachingAssignment::getId).collect(Collectors.toSet());
        Map<Long, TeachingAssignment> taMap = assignments.stream()
                .collect(Collectors.toMap(TeachingAssignment::getId, a -> a));

        Map<Integer, Map<Integer, TimetableEntry>> grid = new HashMap<>();
        for (int r = 0; r < 5; r++) grid.put(r, new HashMap<>());

        for (TimetableEntry e : data.entries()) {
            if (!taIds.contains(e.getTeachingAssignmentId())) continue;
            int dayIdx = DAYS.indexOf(e.getDayOfWeek());
            if (dayIdx < 0) continue;
            grid.get(dayIdx).put(e.getPeriodNumber(), e);
        }

        Map<Long, Subject> subjCache = new HashMap<>();
        Map<Long, Teacher> teacherCache = new HashMap<>();

        PDPage page = new PDPage(new PDRectangle(PAGE_W, PAGE_H));
        doc.addPage(page);

        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            float usableW = PAGE_W - 2 * MARGIN;
            float usableH = PAGE_H - 2 * MARGIN;

            float dayColW = 40;
            float periodColW = Math.min((usableW - dayColW) / numPeriods, 55);
            float tableLeft = MARGIN;

            float[] colX = new float[1 + numPeriods];
            colX[0] = tableLeft;
            for (int i = 0; i < numPeriods; i++)
                colX[i + 1] = colX[i] + (i == 0 ? dayColW : periodColW);

            float headerH = 36;
            float rowH = (usableH - headerH - 24) / DAYS.size();

            float schoolNameY = PAGE_H - MARGIN - 8;
            float titleY = schoolNameY - 16;
            float tableTopY = titleY - 28;
            float headBot = tableTopY - headerH;
            float tableBot = tableTopY - headerH - DAYS.size() * rowH;

            // ---- TITLES (independent of grid) ----
            cs.setFont(FONT_BOLD, 8);
            cs.beginText();
            cs.newLineAtOffset(tableLeft, schoolNameY);
            cs.showText(schoolName);
            cs.endText();

            cs.setFont(FONT_BOLD, 13);
            String classLabel = "F" + classStream.getForm() + " " + classStream.getStream();
            float tw = FONT_BOLD.getStringWidth(classLabel) / 1000f * 13;
            cs.beginText();
            cs.newLineAtOffset(MARGIN + (usableW - tw) / 2f, titleY);
            cs.showText(classLabel);
            cs.endText();

            // ---- CELL FILLS (before grid lines, so lines stay on top) ----
            // Header fill
            cs.setNonStrokingColor(0.2f, 0.2f, 0.2f);
            cs.addRect(colX[0], headBot, colX[numPeriods] - colX[0], tableTopY - headBot);
            cs.fill();

            // Day row fills (alternating)
            for (int r = 0; r < DAYS.size(); r++) {
                float ry = tableTopY - headerH - r * rowH;
                float tint = (r % 2 == 0) ? 0.97f : 1.0f;
                cs.setNonStrokingColor(tint, tint, tint);
                cs.addRect(colX[0], ry - rowH, colX[numPeriods] - colX[0], rowH);
                cs.fill();
            }

            // Break column fills (amber tint)
            cs.setNonStrokingColor(1.0f, 0.97f, 0.85f);
            for (int c = 0; c < numPeriods; c++) {
                if (!periods.get(c).isBreak()) continue;
                cs.addRect(colX[c + 1], headBot, periodColW, tableBot - headBot);
                cs.fill();
            }

            // ---- GRID LINES ----
            cs.setStrokingColor(0.8f, 0.8f, 0.8f);
            cs.setLineWidth(0.4f);

            // Vertical grid lines (period column edges)
            for (int c = 1; c < numPeriods; c++) {
                cs.moveTo(colX[c], tableTopY);
                cs.lineTo(colX[c], tableBot);
                cs.stroke();
            }

            // Header bottom line
            cs.setLineWidth(0.6f);
            cs.moveTo(colX[0], headBot);
            cs.lineTo(colX[numPeriods], headBot);
            cs.stroke();

            // Day row lines
            cs.setLineWidth(0.4f);
            for (int r = 0; r <= DAYS.size(); r++) {
                float ry = tableTopY - headerH - r * rowH;
                cs.moveTo(colX[0], ry);
                cs.lineTo(colX[numPeriods], ry);
                cs.stroke();
            }

            // Outer border (thicker)
            cs.setLineWidth(1.0f);
            cs.setStrokingColor(0, 0, 0);
            cs.addRect(colX[0], tableBot, colX[numPeriods] - colX[0], tableTopY - tableBot);
            cs.stroke();

            // ---- HEADER TEXT (period numbers + times, using exact colX) ----
            cs.setNonStrokingColor(1, 1, 1);
            for (int c = 0; c < numPeriods; c++) {
                PeriodCol p = periods.get(c);
                float cx = colX[c + 1];
                float cw = periodColW;
                float cy = tableTopY;
                float ch = headerH;

                String label = p.isBreak() ? "" : p.label();
                String time = p.startTime() + "-" + p.endTime();

                cs.setFont(FONT_BOLD, 7);
                float nw = FONT_BOLD.getStringWidth(label) / 1000f * 7;
                cs.beginText();
                cs.newLineAtOffset(cx + (cw - nw) / 2f, cy + ch / 2f + 4);
                cs.showText(label);
                cs.endText();

                cs.setFont(FONT, 4.5f);
                float tw2 = FONT.getStringWidth(time) / 1000f * 4.5f;
                cs.beginText();
                cs.newLineAtOffset(cx + (cw - tw2) / 2f, cy + ch / 2f - 7);
                cs.showText(time);
                cs.endText();
            }

            // ---- BODY CELL TEXT (centered within each column with padding) ----
            cs.setNonStrokingColor(0, 0, 0);
            for (int r = 0; r < DAYS.size(); r++) {
                float ry = tableTopY - headerH - r * rowH;
                float rowCenterY = ry - rowH / 2f;

                // Day abbreviation
                cs.setFont(FONT_BOLD, 9);
                String abbr = DAY_ABBR.get(r);
                float aw = FONT_BOLD.getStringWidth(abbr) / 1000f * 9;
                cs.beginText();
                cs.newLineAtOffset(colX[0] + (dayColW - aw) / 2f, rowCenterY - 3);
                cs.showText(abbr);
                cs.endText();

                Map<Integer, TimetableEntry> dayMap = grid.get(r);

                for (int c = 0; c < numPeriods; c++) {
                    PeriodCol p = periods.get(c);
                    if (p.isBreak()) continue;
                    float cellCenterX = colX[c + 1] + periodColW / 2f;

                    TimetableEntry entry = dayMap.get(p.periodNumber());
                    if (entry == null) continue;

                    TeachingAssignment ta = taMap.get(entry.getTeachingAssignmentId());
                    if (ta == null) continue;

                    Subject subj = subjCache.computeIfAbsent(ta.getSubjectId(),
                            id -> subjectRepo.findById(id).orElse(null));
                    Teacher teacher = teacherCache.computeIfAbsent(ta.getTeacherId(),
                            id -> teacherRepo.findById(id).orElse(null));

                    if (subj != null) {
                        cs.setFont(FONT_BOLD, 6.5f);
                        String s = subj.getCode();
                        float sw = FONT_BOLD.getStringWidth(s) / 1000f * 6.5f;
                        float maxW = periodColW - 4;
                        if (sw > maxW) {
                            cs.setFont(FONT_BOLD, 5.5f);
                            sw = FONT_BOLD.getStringWidth(s) / 1000f * 5.5f;
                        }
                        cs.beginText();
                        cs.newLineAtOffset(cellCenterX - sw / 2f, rowCenterY + 7);
                        cs.showText(s);
                        cs.endText();
                    }
                    if (teacher != null) {
                        cs.setFont(FONT, 5.5f);
                        String tName = teacher.getName();
                        float tnw = FONT.getStringWidth(tName) / 1000f * 5.5f;
                        float maxW = periodColW - 4;
                        if (tnw > maxW) {
                            cs.setFont(FONT, 4.5f);
                            tnw = FONT.getStringWidth(tName) / 1000f * 4.5f;
                        }
                        cs.beginText();
                        cs.newLineAtOffset(cellCenterX - tnw / 2f, rowCenterY - 6);
                        cs.showText(tName);
                        cs.endText();
                    }
                }
            }

            // ---- BREAK COLUMN VERTICAL LABELS (spans exactly day rows) ----
            cs.setNonStrokingColor(0, 0, 0);
            for (int c = 0; c < numPeriods; c++) {
                PeriodCol p = periods.get(c);
                if (!p.isBreak()) continue;
                float cx = colX[c + 1];
                float cw = periodColW;
                float mergeCenterY = (headBot + tableBot) / 2f;
                float labelX = cx + cw / 2f;

                cs.setFont(FONT_BOLD, 5.5f);
                String label = p.label().toUpperCase();
                float charH = 7;
                float textTopY = mergeCenterY + (label.length() * charH) / 2f;
                for (int i = 0; i < label.length(); i++) {
                    String ch = String.valueOf(label.charAt(i));
                    float cw2 = FONT_BOLD.getStringWidth(ch) / 1000f * 5.5f;
                    cs.beginText();
                    cs.newLineAtOffset(labelX - cw2 / 2f, textTopY - i * charH);
                    cs.showText(ch);
                    cs.endText();
                }
            }
        }
    }
}
