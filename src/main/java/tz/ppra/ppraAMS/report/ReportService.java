package tz.ppra.ppraAMS.report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import tz.ppra.ppraAMS.importer.EnumMatch;
import tz.ppra.ppraAMS.model.AssetCondition;
import tz.ppra.ppraAMS.model.AssetStatus;
import tz.ppra.ppraAMS.report.ReportModels.*;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportDataService data;
    private final ReportWriter writer;
    private final ChatClient.Builder chatBuilder;

    private static final Map<String, String> COLUMN_ALIAS = new HashMap<>();
    static {
        for (String c : ReportDataService.ALL_COLUMNS) COLUMN_ALIAS.put(c.toLowerCase(), c);
        COLUMN_ALIAS.put("serial", "serialNumber");
        COLUMN_ALIAS.put("type", "assetType");
        COLUMN_ALIAS.put("holder", "heldBy");
        COLUMN_ALIAS.put("person", "heldBy");
        COLUMN_ALIAS.put("user", "heldBy");
        COLUMN_ALIAS.put("allocatedto", "heldBy");
        COLUMN_ALIAS.put("responsible", "lastHolder");
    }

    // ---------- step 1: plan (AI interprets, Java computes) ----------

    public ReportPlan plan(String instruction) {
        List<String> notes = new ArrayList<>();
        ReportSpec spec = sanitize(interpret(instruction, notes), instruction, notes);
        ReportData d = data.build(spec);
        String narrative = narrate(d);
        if (d.total() == 0) notes.add("No assets matched these filters.");
        return new ReportPlan(spec, d.total(), d.summaries(), narrative,
                d.rows().stream().limit(10).toList(), notes);
    }

    // ---------- step 2: generate the file ----------

    // 👈 CHANGED: now takes ReportSpec directly (was String)
    public ReportFile generate(ReportSpec spec, String narrative) throws IOException {
        ReportSpec clean = sanitize(spec, null, new ArrayList<>());
        return writer.write(data.build(clean), narrative);
    }

    // ---------- AI: instruction -> spec ----------

    private ReportSpec interpret(String instruction, List<String> notes) {
        String prompt = """
                You turn a request for an ICT asset register report into a JSON report specification.
                Reply with JSON only, with these keys (use null for anything not requested):
                title, status, condition, assetType, heldBy, text, groupBy, columns, sortBy, format, includeSummary, showRows.
                - status: one of %s, in the user's words
                - condition: one of %s, in the user's words
                - heldBy: part of a person's name or email, when the report is about one person
                - text: text to find in model, serial or barcode
                - groupBy: status, condition, assetType or heldBy, when the user wants counts per category
                - columns: any of %s (omit for the default columns)
                - format: xlsx, pdf or csv (xlsx if not stated)
                - includeSummary: true to add count breakdowns
                - showRows: false if the user wants only totals or counts and not the list of assets
                Request: %s
                """.formatted(Arrays.toString(AssetStatus.values()), Arrays.toString(AssetCondition.values()),
                ReportDataService.ALL_COLUMNS, instruction);
        try {
            return chatBuilder.build().prompt().user(prompt).call().entity(ReportSpec.class);
        } catch (Exception e) {
            log.warn("Report interpretation failed: {}", e.getMessage());
            notes.add("The AI could not interpret the request, so a report of all assets was prepared.");
            return null;
        }
    }

    // ---------- Java: correct whatever the model returned ----------

    private ReportSpec sanitize(ReportSpec in, String instruction, List<String> notes) {
        ReportSpec s = in != null ? in
                : new ReportSpec(null, null, null, null, null, null, null, null, null, null, null, null);

        String status = null;
        if (has(s.status())) {
            AssetStatus e = EnumMatch.resolve(s.status(), AssetStatus.values());
            if (e == null) notes.add("Status '" + s.status() + "' not recognised and was ignored. Valid: " + Arrays.toString(AssetStatus.values()));
            else status = e.name();
        }
        String condition = null;
        if (has(s.condition())) {
            AssetCondition e = EnumMatch.resolve(s.condition(), AssetCondition.values());
            if (e == null) notes.add("Condition '" + s.condition() + "' not recognised and was ignored. Valid: " + Arrays.toString(AssetCondition.values()));
            else condition = e.name();
        }

        List<String> cols = s.columns() == null ? List.of() : s.columns().stream()
                .map(c -> COLUMN_ALIAS.get(c == null ? "" : c.toLowerCase().replaceAll("[^a-z]", "")))
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (cols.isEmpty()) cols = ReportDataService.DEFAULT_COLUMNS;

        String sortBy = s.sortBy() == null ? null : COLUMN_ALIAS.get(s.sortBy().toLowerCase().replaceAll("[^a-z]", ""));

        String groupBy = switch (s.groupBy() == null ? "" : s.groupBy().toLowerCase().replaceAll("[^a-z]", "")) {
            case "status" -> "status";
            case "condition" -> "condition";
            case "assettype", "type" -> "assetType";
            case "heldby", "holder", "person", "user" -> "heldBy";
            default -> null;
        };

        String format = s.format() == null ? "xlsx" : s.format().toLowerCase().trim();
        if (!List.of("xlsx", "pdf", "csv").contains(format)) format = "xlsx";
        if (instruction != null) {
            String low = instruction.toLowerCase();
            if (low.contains("pdf")) format = "pdf";
            else if (low.contains("csv")) format = "csv";
            else if (low.contains("excel") || low.contains("xlsx") || low.contains("spreadsheet")) format = "xlsx";
        }

        return new ReportSpec(
                has(s.title()) ? s.title().trim() : "Asset Register Report",
                status, condition, blankNull(s.assetType()), blankNull(s.heldBy()), blankNull(s.text()),
                groupBy, cols, sortBy, format,
                s.includeSummary() == null || s.includeSummary(),
                s.showRows() == null || s.showRows());
    }

    // ---------- AI: short summary of numbers Java already computed ----------

    private String narrate(ReportData d) {
        if (d.total() == 0) return null;
        try {
            String filters = Stream(d.spec());
            String breakdown = d.summaries().stream()
                    .map(t -> t.name() + ": " + t.counts())
                    .collect(Collectors.joining("; "));
            String prompt = """
                    Write 2 or 3 plain sentences summarising this asset register report for a manager.
                    Use ONLY the numbers below. Do not add advice, guesses or any other facts.
                    Title: %s
                    Total assets: %d
                    Filters: %s
                    Breakdowns: %s
                    """.formatted(d.spec().title(), d.total(), filters, breakdown.isBlank() ? "none" : breakdown);
            return chatBuilder.build().prompt().user(prompt).call().content();
        } catch (Exception e) {
            log.warn("Narrative failed: {}", e.getMessage());
            return null;
        }
    }

    private static String Stream(ReportSpec s) {
        List<String> f = new ArrayList<>();
        if (s.status() != null) f.add("status=" + s.status());
        if (s.condition() != null) f.add("condition=" + s.condition());
        if (s.assetType() != null) f.add("type=" + s.assetType());
        if (s.heldBy() != null) f.add("person=" + s.heldBy());
        if (s.text() != null) f.add("text=" + s.text());
        return f.isEmpty() ? "none (all assets)" : String.join(", ", f);
    }

    private static boolean has(String s) { return s != null && !s.isBlank(); }
    private static String blankNull(String s) { return has(s) && !"null".equalsIgnoreCase(s.trim()) ? s.trim() : null; }
}
