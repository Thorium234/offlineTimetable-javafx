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

public class TeacherTimetablePdfExporter implements TimetableExporter {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font FONT_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private static final float MM = 72f / 25.4f;
    private static final float MARGIN = 10 * MM;
    private static final float PAGE_W = 842;
    private static final float PAGE_H = 595;

    private record ColDef(String label, String time, boolean isBreak) {}
    private static final List<ColDef> COLS = List.of(
            new ColDef("ASSEMBLY/PREPS", "7:10-8:00", true),
            new ColDef("P1", "8:00-8:40", false),
            new ColDef("P2", "8:40-9:20", false),
            new ColDef("P3", "9:20-10:00", false),
            new ColDef("TEA BREAK", "10:00-10:20", true),
            new ColDef("P4", "10:20-11:00", false),
            new ColDef("P5", "11:00-11:40", false),
            new ColDef("SHORT BREAK", "11:40-11:50", true),
            new ColDef("P6", "11:50-12:30", false),
            new ColDef("P7", "12:30-13:10", false),
            new ColDef("LUNCH BREAK", "13:10-14:00", true),
            new ColDef("P8", "14:00-14:40", false),
            new ColDef("P9", "14:40-15:20", false),
            new ColDef("P10", "15:20-16:00", false),
            new ColDef("GAMES", "16:00-16:40", true),
            new ColDef("P11", "16:40-17:20", false)
    );

    private static final List<DayOfWeek> DAYS = DayOfWeek.workingDays();
    private static final List<String> DAY_ABBR = List.of("Mo", "Tu", "We", "Th", "Fr");

    private final TeachingAssignmentRepository assignmentRepo;
    private final SubjectRepository subjectRepo;
    private final ClassStreamRepository classRepo;
    private final TeacherRepository teacherRepo;
    private final SchoolSettingsRepository settingsRepo;

    public TeacherTimetablePdfExporter(
            TeachingAssignmentRepository assignmentRepo,
            SubjectRepository subjectRepo,
            ClassStreamRepository classRepo,
            TeacherRepository teacherRepo,
            SchoolSettingsRepository settingsRepo) {
        this.assignmentRepo = assignmentRepo;
        this.subjectRepo = subjectRepo;
        this.classRepo = classRepo;
        this.teacherRepo = teacherRepo;
        this.settingsRepo = settingsRepo;
    }

    @Override
    public byte[] renderClassPdfToBytes(TimetableWithEntries data, Long classStreamId) {
        throw new UnsupportedOperationException("Use ClassTimetablePdfExporter for class PDFs");
    }

    @Override
    public byte[] renderAllClassesPdfToBytes(TimetableWithEntries data) {
        throw new UnsupportedOperationException("Use ClassTimetablePdfExporter for all-classes PDF");
    }

    @Override
    public byte[] renderTeacherPdfToBytes(TimetableWithEntries data, Long teacherId) {
        Teacher teacher = teacherRepo.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found: " + teacherId));
        SchoolSettings settings = settingsRepo.get();
        String schoolName = settings != null && settings.getSchoolName() != null
                ? settings.getSchoolName().toUpperCase() : "";

        List<TeachingAssignment> assignments = assignmentRepo.findByTeacherId(teacherId);
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
        Map<Long, ClassStream> clsCache = new HashMap<>();

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(PAGE_W, PAGE_H));
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float usableW = PAGE_W - 2 * MARGIN;
                float usableH = PAGE_H - 2 * MARGIN;

                float dayColW = 40;
                float periodColW = (usableW - dayColW) / COLS.size();
                float totalW = dayColW + periodColW * COLS.size();
                float tableLeft = MARGIN;

                float[] colX = new float[1 + COLS.size()];
                colX[0] = tableLeft;
                for (int i = 0; i < COLS.size(); i++)
                    colX[i + 1] = colX[i] + (i == 0 ? dayColW : periodColW);

                float headerH = 36;
                float rowH = (usableH - headerH - 24) / DAYS.size();

                float startY = PAGE_H - MARGIN - 8;

                // School name
                cs.setFont(FONT_BOLD, 8);
                cs.beginText();
                cs.newLineAtOffset(tableLeft, startY);
                cs.showText(schoolName);
                cs.endText();

                // Teacher name
                float titleY = startY - 16;
                cs.setFont(FONT_BOLD, 13);
                String teacherLabel = "Teacher " + teacher.getName().toUpperCase();
                float tw = FONT_BOLD.getStringWidth(teacherLabel) / 1000f * 13;
                cs.beginText();
                cs.newLineAtOffset(MARGIN + (usableW - tw) / 2f, titleY);
                cs.showText(teacherLabel);
                cs.endText();

                float tableTopY = titleY - 20;

                // Horizontal lines
                cs.setLineWidth(0.5f);
                cs.setStrokingColor(0, 0, 0);

                // Vertical grid lines
                for (int c = 0; c <= COLS.size(); c++) {
                    cs.moveTo(colX[c], tableTopY);
                    cs.lineTo(colX[c], tableTopY - headerH - DAYS.size() * rowH);
                    cs.stroke();
                }

                // Header bottom line
                float headBot = tableTopY - headerH;
                cs.moveTo(colX[0], headBot);
                cs.lineTo(colX[COLS.size()], headBot);
                cs.stroke();

                // Day row lines
                for (int r = 0; r <= DAYS.size(); r++) {
                    float ry = tableTopY - headerH - r * rowH;
                    cs.moveTo(colX[0], ry);
                    cs.lineTo(colX[COLS.size()], ry);
                    cs.stroke();
                }

                // Outer border (thicker)
                cs.setLineWidth(1.2f);
                float tableBot = tableTopY - headerH - DAYS.size() * rowH;
                cs.moveTo(colX[0], tableTopY);
                cs.lineTo(colX[COLS.size()], tableTopY);
                cs.lineTo(colX[COLS.size()], tableBot);
                cs.lineTo(colX[0], tableBot);
                cs.closePath();
                cs.stroke();
                cs.setLineWidth(0.5f);

                // Header cells — period numbers + times
                for (int c = 0; c < COLS.size(); c++) {
                    float cx = colX[c + 1];
                    float cw = periodColW;
                    float cy = tableTopY;
                    float ch = headerH;

                    cs.setFont(FONT_BOLD, 7);
                    String num = (c + 1) + "";
                    float nw = FONT_BOLD.getStringWidth(num) / 1000f * 7;
                    cs.beginText();
                    cs.newLineAtOffset(cx + (cw - nw) / 2f, cy + ch / 2f + 4);
                    cs.showText(num);
                    cs.endText();

                    cs.setFont(FONT, 4.5f);
                    String t = COLS.get(c).time();
                    float tw2 = FONT.getStringWidth(t) / 1000f * 4.5f;
                    cs.beginText();
                    cs.newLineAtOffset(cx + (cw - tw2) / 2f, cy + ch / 2f - 7);
                    cs.showText(t);
                    cs.endText();
                }

                // Day rows
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

                    for (int c = 0; c < COLS.size(); c++) {
                        if (COLS.get(c).isBreak()) continue;
                        float cellCenterX = colX[c + 1] + periodColW / 2f;

                        int pn = c + 1;
                        TimetableEntry entry = dayMap.get(pn);
                        if (entry == null) continue;

                        TeachingAssignment ta = taMap.get(entry.getTeachingAssignmentId());
                        if (ta == null) continue;

                        Subject subj = subjCache.computeIfAbsent(ta.getSubjectId(),
                                id -> subjectRepo.findById(id).orElse(null));
                        ClassStream cls = clsCache.computeIfAbsent(ta.getClassStreamId(),
                                id -> classRepo.findById(id).orElse(null));

                        if (subj != null) {
                            cs.setFont(FONT_BOLD, 7);
                            String s = subj.getCode();
                            float sw = FONT_BOLD.getStringWidth(s) / 1000f * 7;
                            cs.beginText();
                            cs.newLineAtOffset(cellCenterX - sw / 2f, rowCenterY + 8);
                            cs.showText(s);
                            cs.endText();
                        }
                        if (cls != null) {
                            cs.setFont(FONT, 6.5f);
                            String f = "F" + cls.getForm();
                            float fw = FONT.getStringWidth(f) / 1000f * 6.5f;
                            cs.beginText();
                            cs.newLineAtOffset(cellCenterX - fw / 2f, rowCenterY - 2);
                            cs.showText(f);
                            cs.endText();

                            String st = cls.getStream();
                            float stw = FONT.getStringWidth(st) / 1000f * 6.5f;
                            cs.beginText();
                            cs.newLineAtOffset(cellCenterX - stw / 2f, rowCenterY - 11);
                            cs.showText(st);
                            cs.endText();
                        }
                    }
                }

                // Break column labels drawn character by character vertically
                for (int c = 0; c < COLS.size(); c++) {
                    if (!COLS.get(c).isBreak()) continue;
                    float cx = colX[c + 1];
                    float cw = periodColW;
                    float mergeTop = tableTopY - headerH;
                    float mergeBot = tableBot;
                    float mergeCenterY = (mergeTop + mergeBot) / 2f;
                    float labelX = cx + cw / 2f;

                    cs.setFont(FONT_BOLD, 5.5f);
                    String label = COLS.get(c).label();
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

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to render teacher PDF", e);
        }
    }
}
