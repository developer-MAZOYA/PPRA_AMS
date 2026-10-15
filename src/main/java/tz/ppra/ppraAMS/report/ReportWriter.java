package tz.ppra.ppraAMS.report;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import tz.ppra.ppraAMS.report.ReportModels.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class ReportWriter {

    private static final Map<String, String> LABELS = Map.of(
            "id", "ID", "assetType", "Type", "model", "Model", "serialNumber", "Serial No.",
            "barcode", "Barcode", "status", "Status", "condition", "Condition",
            "heldBy", "Held by", "lastHolder", "Last holder");

    public ReportFile write(ReportData d, String narrative) throws IOException {
        String fmt = d.spec().format();
        String base = "asset-report-" + LocalDate.now();
        return switch (fmt) {
            case "pdf" -> new ReportFile(pdf(d, narrative), base + ".pdf", "application/pdf");
            case "csv" -> new ReportFile(csv(d), base + ".csv", "text/csv");
            default -> new ReportFile(xlsx(d, narrative), base + ".xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        };
    }

    private static List<String> headers(ReportData d) {
        return d.columns().stream().map(c -> LABELS.getOrDefault(c, c)).toList();
    }

    private static boolean showRows(ReportData d) {
        return !Boolean.FALSE.equals(d.spec().showRows());
    }

    // ---------------- CSV ----------------

    private byte[] csv(ReportData d) {
        StringBuilder sb = new StringBuilder("\uFEFF");
        if (showRows(d)) {
            sb.append(String.join(",", headers(d).stream().map(ReportWriter::esc).toList())).append("\r\n");
            for (Map<String, String> r : d.rows()) {
                sb.append(String.join(",", d.columns().stream().map(c -> esc(r.getOrDefault(c, ""))).toList())).append("\r\n");
            }
        } else {
            for (ReportModels.Table t : d.summaries()) {
                sb.append(esc(t.name())).append(",Count\r\n");
                t.counts().forEach((k, v) -> sb.append(esc(k)).append(',').append(v).append("\r\n"));
            }
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String esc(String s) {
        String v = s == null ? "" : s;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) v = "'" + v;   // stop spreadsheet formula injection
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    // ---------------- XLSX ----------------

    private byte[] xlsx(ReportData d, String narrative) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Font bf = wb.createFont();
            bf.setBold(true);
            Font tf = wb.createFont();
            tf.setBold(true);
            tf.setFontHeightInPoints((short) 14);
            CellStyle title = wb.createCellStyle();
            title.setFont(tf);
            CellStyle head = wb.createCellStyle();
            head.setFont(bf);
            head.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Sheet sh = wb.createSheet("Report");
            int r = 0;
            r = line(sh, r, d.spec().title(), title);
            r = line(sh, r, "Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")), null);
            r = line(sh, r, "Total assets in this report: " + d.total(), null);
            if (narrative != null && !narrative.isBlank()) r = line(sh, r, narrative.trim(), null);
            r++;

            for (ReportModels.Table t : d.summaries()) {
                Row hr = sh.createRow(r++);
                cell(hr, 0, t.name(), head);
                cell(hr, 1, "Count", head);
                for (Map.Entry<String, Long> e : t.counts().entrySet()) {
                    Row row = sh.createRow(r++);
                    row.createCell(0).setCellValue(e.getKey());
                    row.createCell(1).setCellValue(e.getValue());
                }
                r++;
            }

            if (showRows(d)) {
                Row hr = sh.createRow(r++);
                List<String> hs = headers(d);
                for (int c = 0; c < hs.size(); c++) cell(hr, c, hs.get(c), head);
                for (Map<String, String> m : d.rows()) {
                    Row row = sh.createRow(r++);
                    for (int c = 0; c < d.columns().size(); c++) {
                        row.createCell(c).setCellValue(m.getOrDefault(d.columns().get(c), ""));
                    }
                }
            }

            int cols = Math.max(d.columns().size(), 2);
            for (int c = 0; c < cols; c++) {
                sh.autoSizeColumn(c);
                if (sh.getColumnWidth(c) > 50 * 256) sh.setColumnWidth(c, 50 * 256);
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static int line(Sheet sh, int r, String text, CellStyle style) {
        Row row = sh.createRow(r);
        cell(row, 0, text, style);
        return r + 1;
    }

    private static void cell(Row row, int c, String v, CellStyle style) {
        Cell cell = row.createCell(c);
        cell.setCellValue(v == null ? "" : v);
        if (style != null) cell.setCellStyle(style);
    }

    // ---------------- PDF ----------------

    private byte[] pdf(ReportData d, String narrative) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Pdf pdf = new Pdf(doc,
                    new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                    new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));

            pdf.text(d.spec().title(), pdf.bold, 16);
            pdf.text("Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    + "   |   Total assets: " + d.total(), pdf.font, 9);
            if (narrative != null && !narrative.isBlank()) {
                pdf.y -= 4;
                pdf.paragraph(narrative.trim(), 10);
            }
            pdf.y -= 8;

            for (ReportModels.Table t : d.summaries()) {
                List<List<String>> rows = new ArrayList<>();
                t.counts().forEach((k, v) -> rows.add(List.of(k, String.valueOf(v))));
                pdf.table(List.of(t.name(), "Count"), rows, new float[]{300, 80});
            }

            if (showRows(d)) {
                float w = (Pdf.W - 2 * Pdf.M) / d.columns().size();
                float[] widths = new float[d.columns().size()];
                Arrays.fill(widths, w);
                List<List<String>> rows = new ArrayList<>();
                for (Map<String, String> m : d.rows()) {
                    rows.add(d.columns().stream().map(c -> m.getOrDefault(c, "")).toList());
                }
                pdf.table(headers(d), rows, widths);
            }
            pdf.finish();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static final class Pdf {
        static final float W = 842, H = 595, M = 30, LH = 13;
        final PDDocument doc;
        final PDFont font, bold;
        PDPageContentStream cs;
        float y;
        List<String> repeatHeader;
        float[] repeatWidths;

        Pdf(PDDocument doc, PDFont font, PDFont bold) throws IOException {
            this.doc = doc;
            this.font = font;
            this.bold = bold;
            newPage();
        }

        void newPage() throws IOException {
            if (cs != null) cs.close();
            PDPage p = new PDPage(new PDRectangle(W, H));
            doc.addPage(p);
            cs = new PDPageContentStream(doc, p);
            y = H - M;
        }

        void ensure(float need) throws IOException {
            if (y - need < M) {
                newPage();
                if (repeatHeader != null) row(repeatHeader, repeatWidths, true);
            }
        }

        void text(String s, PDFont f, float size) throws IOException {
            ensure(size + 6);
            cs.beginText();
            cs.setFont(f, size);
            cs.newLineAtOffset(M, y - size);
            cs.showText(safe(s));
            cs.endText();
            y -= size + 6;
        }

        void paragraph(String s, float size) throws IOException {
            for (String line : wrap(s, 120)) text(line, font, size);
        }

        void row(List<String> cells, float[] widths, boolean header) throws IOException {
            ensure(LH);
            PDFont f = header ? bold : font;
            float x = M;
            for (int i = 0; i < cells.size(); i++) {
                cs.beginText();
                cs.setFont(f, 8);
                cs.newLineAtOffset(x, y - 9);
                cs.showText(fit(cells.get(i), widths[i]));
                cs.endText();
                x += widths[i];
            }
            y -= LH;
        }

        void table(List<String> headers, List<List<String>> rows, float[] widths) throws IOException {
            repeatHeader = headers;
            repeatWidths = widths;
            row(headers, widths, true);
            for (List<String> r : rows) row(r, widths, false);
            repeatHeader = null;
            y -= 10;
        }

        void finish() throws IOException {
            cs.close();
        }

        static String fit(String s, float width) {
            String v = safe(s);
            int max = (int) (width / 4f) - 1;
            return v.length() > max && max > 3 ? v.substring(0, max - 2) + ".." : v;
        }

        static String safe(String s) {
            if (s == null) return "";
            StringBuilder b = new StringBuilder();
            for (char ch : s.toCharArray()) {
                if ((ch >= 32 && ch <= 126) || (ch >= 160 && ch <= 255)) b.append(ch);
                else b.append(ch == '\t' || ch == '\n' || ch == '\r' ? ' ' : '?');
            }
            return b.toString();
        }

        static List<String> wrap(String s, int max) {
            List<String> out = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            for (String w : s.split("\\s+")) {
                if (cur.length() > 0 && cur.length() + w.length() + 1 > max) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
                if (cur.length() > 0) cur.append(' ');
                cur.append(w);
            }
            if (cur.length() > 0) out.add(cur.toString());
            return out;
        }
    }
}
