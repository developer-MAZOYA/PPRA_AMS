package tz.ppra.ppraAMS.importer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import tz.ppra.ppraAMS.importer.ImportModels.*;
import tz.ppra.ppraAMS.model.Asset;
import tz.ppra.ppraAMS.model.AssetCondition;
import tz.ppra.ppraAMS.model.AssetStatus;
import tz.ppra.ppraAMS.service.AssetService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetImportService {

    private static final int CHUNK_CHARS = 2500;
    private static final int MAX_CHUNKS = 20;
    private static final Set<String> FIELDS = Set.of("assetType", "model", "serialNumber", "barcode",
            "status", "condition", "allocatedTo", "allocatedEmail");

    public record ExtractedAsset(String assetType, String model, String serialNumber, String barcode,
                                 String status, String condition, String allocatedTo, String allocatedEmail) {}
    public record ExtractedBatch(List<ExtractedAsset> assets) {}
    public record ColumnPlan(Map<String, String> mapping, Map<String, String> defaults) {}

    private final AssetService assetService;
    private final DocumentTextExtractor extractor;
    private final ChatClient.Builder chatBuilder;

    // ===================== STEP 1: PREVIEW =====================

    public ImportPreview preview(String fileName, byte[] data, String instruction) throws IOException {
        String ext = ext(fileName);
        List<String> notes = new ArrayList<>();
        List<Map<String, String>> raw;
        String method;

        if (ext.equals("xlsx") || ext.equals("xls")) {
            method = "Excel (column mapping)";
            raw = fromExcel(data, instruction, notes);
        } else {
            method = "AI text extraction";
            String text = extractor.extract(ext, data);
            if (text == null || text.isBlank()) {
                notes.add("No text found. If this is a scanned PDF, it needs OCR first.");
                raw = List.of();
            } else {
                raw = fromText(text, instruction, notes);
            }
        }

        List<AssetDraft> drafts = raw.stream().map(AssetImportService::toDraft).toList();
        List<AssetDraft> rows = validate(drafts);
        if (rows.isEmpty()) notes.add("No assets were found in the document.");
        return new ImportPreview(fileName, method, rows.size(), rows, notes);
    }

    // ---------- Excel: Java reads rows, AI only maps the columns ----------

    private List<Map<String, String>> fromExcel(byte[] data, String instruction, List<String> notes) throws IOException {
        List<Map<String, String>> out = new ArrayList<>();
        DataFormatter fmt = new DataFormatter();

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(data))) {
            FormulaEvaluator ev = wb.getCreationHelper().createFormulaEvaluator();

            for (Sheet sheet : wb) {
                int headerIdx = findHeader(sheet, fmt, ev);
                if (headerIdx < 0) continue;

                Row hr = sheet.getRow(headerIdx);
                int cols = Math.max(hr.getLastCellNum(), 0);
                List<String> headers = new ArrayList<>();
                for (int c = 0; c < cols; c++) headers.add(cell(hr, c, fmt, ev));

                List<List<String>> body = new ArrayList<>();
                for (int r = headerIdx + 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;
                    List<String> vals = new ArrayList<>();
                    for (int c = 0; c < cols; c++) vals.add(cell(row, c, fmt, ev));
                    // skip blank rows and section rows such as a lone "LAPTOPS"
                    if (vals.stream().filter(v -> !v.isBlank()).count() < 2) continue;
                    body.add(vals);
                }
                if (body.isEmpty()) continue;

                ColumnPlan plan = planColumns(headers, body.subList(0, Math.min(3, body.size())), instruction, notes);
                notes.add("Sheet '" + sheet.getSheetName() + "': column mapping " + plan.mapping()
                        + (plan.defaults().isEmpty() ? "" : ", defaults " + plan.defaults()));

                for (List<String> vals : body) {
                    Map<String, String> m = new LinkedHashMap<>();
                    for (int c = 0; c < headers.size(); c++) {
                        String field = plan.mapping().get(headers.get(c));
                        if (field != null && !vals.get(c).isBlank()) m.putIfAbsent(field, vals.get(c));
                    }
                    plan.defaults().forEach((f, v) -> {
                        if (m.getOrDefault(f, "").isBlank()) m.put(f, v);
                    });
                    out.add(m);
                }
            }
        }
        return out;
    }

    private int findHeader(Sheet sheet, DataFormatter fmt, FormulaEvaluator ev) {
        int last = Math.min(sheet.getLastRowNum(), 15);
        for (int r = 0; r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            long filled = 0;
            for (int c = 0; c < Math.max(row.getLastCellNum(), 0); c++) {
                if (!cell(row, c, fmt, ev).isBlank()) filled++;
            }
            if (filled >= 3) return r;
        }
        return -1;
    }

    private static String cell(Row row, int c, DataFormatter fmt, FormulaEvaluator ev) {
        Cell cell = row.getCell(c);
        return cell == null ? "" : fmt.formatCellValue(cell, ev).trim();
    }

    private ColumnPlan planColumns(List<String> headers, List<List<String>> samples, String instruction, List<String> notes) {
        Map<String, String> mapping = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        for (String h : headers) {                       // 1) obvious headers matched in Java
            String f = guess(h);
            if (f != null && !h.isBlank() && used.add(f)) mapping.put(h, f);
        }

        Map<String, String> defaults = new LinkedHashMap<>();
        try {                                            // 2) AI handles odd headers and reads the instruction
            String prompt = """
                    You map spreadsheet columns to asset register fields.
                    Allowed fields: assetType, model, serialNumber, barcode, status, condition, allocatedTo, allocatedEmail.
                    Headers: %s
                    Sample rows: %s
                    Already mapped: %s
                    User instruction: %s
                    Reply with JSON: {"mapping": {"<header>": "<field>"}, "defaults": {"<field>": "<value>"}}
                    Map only headers that clearly match a field. Put in "defaults" only values the user instruction
                    says apply to every row (for example assetType=Laptop). Do not invent anything.
                    """.formatted(headers, samples, mapping, instruction == null ? "none" : instruction);
            ColumnPlan p = chatBuilder.build().prompt().user(prompt).call().entity(ColumnPlan.class);
            if (p != null) {
                if (p.mapping() != null) p.mapping().forEach((h, f) -> {
                    if (headers.contains(h) && FIELDS.contains(f) && !mapping.containsKey(h) && used.add(f)) mapping.put(h, f);
                });
                if (p.defaults() != null) p.defaults().forEach((f, v) -> {
                    if (FIELDS.contains(f) && v != null && !v.isBlank()) defaults.put(f, v);
                });
            }
        } catch (Exception e) {
            log.warn("AI column planning failed: {}", e.getMessage());
            notes.add("AI could not read the instruction; used header names only.");
        }
        return new ColumnPlan(mapping, defaults);
    }

    private static String guess(String header) {
        String k = header == null ? "" : header.toLowerCase().replaceAll("[^a-z0-9 ]", " ");
        if (k.contains("email")) return "allocatedEmail";
        if (k.contains("serial")) return "serialNumber";
        if (k.contains("barcode") || k.contains("bar code") || k.contains("asset tag")) return "barcode";
        if (k.contains("model")) return "model";
        if (k.contains("status")) return "status";
        if (k.contains("condition")) return "condition";
        if (k.contains("allocat") || k.contains("assigned") || k.contains("holder") || k.contains("user")) return "allocatedTo";
        if (k.contains("type") || k.contains("category")) return "assetType";
        return null;
    }

    // ---------- PDF / Word / text: AI extracts the rows ----------

    private List<Map<String, String>> fromText(String text, String instruction, List<String> notes) {
        List<Map<String, String>> out = new ArrayList<>();
        List<String> chunks = chunks(text);
        if (chunks.size() > MAX_CHUNKS) {
            notes.add("Document is long: only the first " + MAX_CHUNKS + " parts were read.");
            chunks = chunks.subList(0, MAX_CHUNKS);
        }
        int i = 0;
        for (String chunk : chunks) {
            i++;
            try {
                String prompt = """
                        Extract every asset (laptop or other device) from the text below.
                        Reply with JSON: {"assets":[{"assetType":..,"model":..,"serialNumber":..,"barcode":..,
                        "status":..,"condition":..,"allocatedTo":..,"allocatedEmail":..}]}
                        Copy values exactly as written. Use null when a value is not in the text. Never invent values.
                        User instruction: %s

                        TEXT:
                        %s
                        """.formatted(instruction == null ? "none" : instruction, chunk);
                ExtractedBatch b = chatBuilder.build().prompt().user(prompt).call().entity(ExtractedBatch.class);
                if (b != null && b.assets() != null) {
                    for (ExtractedAsset a : b.assets()) {
                        Map<String, String> m = new LinkedHashMap<>();
                        put(m, "assetType", a.assetType());
                        put(m, "model", a.model());
                        put(m, "serialNumber", a.serialNumber());
                        put(m, "barcode", a.barcode());
                        put(m, "status", a.status());
                        put(m, "condition", a.condition());
                        put(m, "allocatedTo", a.allocatedTo());
                        put(m, "allocatedEmail", a.allocatedEmail());
                        if (!m.isEmpty()) out.add(m);
                    }
                }
            } catch (Exception e) {
                log.warn("Extraction failed on part {}: {}", i, e.getMessage());
                notes.add("Part " + i + " of the document could not be read by the AI.");
            }
        }
        return out;
    }

    private static void put(Map<String, String> m, String k, String v) {
        if (v != null && !v.isBlank() && !"null".equalsIgnoreCase(v.trim())) m.put(k, v.trim());
    }

    private static List<String> chunks(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\\R")) {
            if (line.isBlank()) continue;
            if (sb.length() + line.length() > CHUNK_CHARS && sb.length() > 0) {
                out.add(sb.toString());
                sb.setLength(0);
            }
            sb.append(line).append('\n');
        }
        if (sb.length() > 0) out.add(sb.toString());
        return out;
    }

    private static AssetDraft toDraft(Map<String, String> m) {
        return new AssetDraft(0, m.get("assetType"), m.get("model"), m.get("serialNumber"), m.get("barcode"),
                m.get("status"), m.get("condition"), m.get("allocatedTo"), m.get("allocatedEmail"),
                new ArrayList<>(), "OK");
    }

    // ===================== VALIDATION (used by preview AND confirm) =====================

    public List<AssetDraft> validate(List<AssetDraft> in) {
        List<Asset> existing = assetService.list();
        Set<String> dbSerial = existing.stream().map(a -> low(a.getSerialNumber())).collect(Collectors.toSet());
        Set<String> dbBarcode = existing.stream().map(a -> low(a.getBarcode())).collect(Collectors.toSet());
        Set<String> seenSerial = new HashSet<>();
        Set<String> seenBarcode = new HashSet<>();

        List<AssetDraft> out = new ArrayList<>();
        int n = 0;
        for (AssetDraft d : in) {
            n++;
            List<String> issues = new ArrayList<>();
            boolean error = false;

            String serial = clean(d.serialNumber()), barcode = clean(d.barcode());
            String model = clean(d.model()), type = clean(d.assetType());

            if (serial.isEmpty() && barcode.isEmpty()) {
                issues.add("No serial number or barcode");
                error = true;
            }
            if (!serial.isEmpty()) {
                if (dbSerial.contains(low(serial))) { issues.add("Serial already exists in the system"); error = true; }
                else if (!seenSerial.add(low(serial))) { issues.add("Duplicate serial in this file"); error = true; }
            }
            if (!barcode.isEmpty()) {
                if (dbBarcode.contains(low(barcode))) { issues.add("Barcode already exists in the system"); error = true; }
                else if (!seenBarcode.add(low(barcode))) { issues.add("Duplicate barcode in this file"); error = true; }
            }
            if (model.isEmpty()) issues.add("Model missing");
            if (type.isEmpty()) { type = "Laptop"; issues.add("Asset type missing, set to Laptop"); }

            String statusIn = clean(d.status()), condIn = clean(d.condition());
            AssetStatus st = EnumMatch.resolve(statusIn, AssetStatus.values());
            AssetCondition cd = EnumMatch.resolve(condIn, AssetCondition.values());

            if (st == null && !statusIn.isEmpty()) {
                // registers often put the condition (GOOD, FAIR...) in a column called STATUS
                AssetCondition asCond = EnumMatch.resolve(statusIn, AssetCondition.values());
                if (asCond != null && cd == null) {
                    cd = asCond;
                    issues.add("'" + statusIn + "' looks like a condition; moved to condition");
                } else {
                    issues.add("Status '" + statusIn + "' not recognised; set to AVAILABLE");
                }
            } else if (st != null && !EnumMatch.norm(statusIn).equals(EnumMatch.norm(st.name()))) {
                issues.add("Status '" + statusIn + "' read as " + st);
            }
            if (st == null) st = AssetStatus.AVAILABLE;

            if (cd == null && !condIn.isEmpty()) issues.add("Condition '" + condIn + "' not recognised; set to NEW");
            else if (cd != null && !condIn.isEmpty() && !EnumMatch.norm(condIn).equals(EnumMatch.norm(cd.name()))) {
                issues.add("Condition '" + condIn + "' read as " + cd);
            }
            if (cd == null) cd = AssetCondition.NEW;

            String level = error ? "ERROR" : (issues.isEmpty() ? "OK" : "WARNING");
            out.add(new AssetDraft(n, type, model, serial, barcode, st.name(), cd.name(),
                    clean(d.allocatedTo()), clean(d.allocatedEmail()), issues, level));
        }
        return out;
    }

    // ===================== STEP 2: CONFIRM =====================

    public ImportResult confirm(List<AssetDraft> rows) {
        List<AssetDraft> checked = validate(rows == null ? List.of() : rows);
        int created = 0, skipped = 0;
        List<String> messages = new ArrayList<>();

        for (AssetDraft d : checked) {
            if ("ERROR".equals(d.level())) {
                skipped++;
                messages.add("Row " + d.row() + " skipped: " + String.join("; ", d.issues()));
                continue;
            }
            try {
                Asset asset = Asset.builder()
                        .assetType(d.assetType())
                        .model(d.model())
                        .serialNumber(d.serialNumber().isEmpty() ? null : d.serialNumber())
                        .barcode(d.barcode().isEmpty() ? null : d.barcode())
                        .status(AssetStatus.valueOf(d.status()))
                        .condition(AssetCondition.valueOf(d.condition()))
                        .build();
                assetService.create(asset);
                created++;
            } catch (Exception e) {
                skipped++;
                messages.add("Row " + d.row() + " failed: " + e.getMessage());
            }
        }
        return new ImportResult(created, skipped, messages);
    }

    // ===================== helpers =====================

    private static String ext(String name) {
        int i = name == null ? -1 : name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1).toLowerCase();
    }
    private static String clean(String s) { return s == null ? "" : s.trim(); }
    private static String low(String s) { return s == null ? "" : s.trim().toLowerCase(); }
}
