package tz.ppra.ppraAMS.report;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.ppra.ppraAMS.importer.EnumMatch;
import tz.ppra.ppraAMS.model.Asset;
import tz.ppra.ppraAMS.model.AssetAssignment;
import tz.ppra.ppraAMS.model.AssetCondition;
import tz.ppra.ppraAMS.model.AssetStatus;
import tz.ppra.ppraAMS.report.ReportModels.ReportData;
import tz.ppra.ppraAMS.report.ReportModels.ReportSpec;
import tz.ppra.ppraAMS.report.ReportModels.Table;
import tz.ppra.ppraAMS.service.AssetAssignmentService;
import tz.ppra.ppraAMS.service.AssetService;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportDataService {

    public static final List<String> ALL_COLUMNS = List.of(
            "id", "assetType", "model", "serialNumber", "barcode", "status", "condition", "heldBy", "lastHolder");
    public static final List<String> DEFAULT_COLUMNS = List.of(
            "model", "serialNumber", "barcode", "status", "condition", "heldBy");

    private static final int MAX_ROWS = 5000;
    private static final Map<String, String> TABLE_NAMES = Map.of(
            "status", "By status", "condition", "By condition", "assetType", "By asset type", "heldBy", "By holder");

    private final AssetService assetService;
    private final AssetAssignmentService assignmentService;

    @Transactional(readOnly = true)
    public ReportData build(ReportSpec spec) {
        AssetStatus st = EnumMatch.resolve(spec.status(), AssetStatus.values());
        AssetCondition cd = EnumMatch.resolve(spec.condition(), AssetCondition.values());

        List<Map<String, String>> rows = new ArrayList<>();
        for (Asset a : assetService.list()) {
            if (st != null && a.getStatus() != st) continue;
            if (cd != null && a.getCondition() != cd) continue;
            if (has(spec.assetType()) && !nz(a.getAssetType()).toLowerCase().contains(spec.assetType().toLowerCase())) continue;
            if (has(spec.text()) && !(nz(a.getModel()) + " " + nz(a.getSerialNumber()) + " " + nz(a.getBarcode()))
                    .toLowerCase().contains(spec.text().toLowerCase())) continue;

            Map<String, String> row = row(a);
            if (has(spec.heldBy())) {
                String q = spec.heldBy().toLowerCase();
                if (!row.get("heldBy").toLowerCase().contains(q) && !row.get("lastHolder").toLowerCase().contains(q)) continue;
            }
            rows.add(row);
            if (rows.size() >= MAX_ROWS) break;
        }

        String key = spec.sortBy() != null ? spec.sortBy() : spec.groupBy();
        if (key != null) {
            final String k = key;
            if ("id".equals(k)) {
                rows.sort(Comparator.comparingLong((Map<String, String> m) -> parse(m.get("id"))));
            } else {
                rows.sort(Comparator.comparing((Map<String, String> m) -> m.getOrDefault(k, "").toLowerCase()));
            }
        }

        List<Table> sums = new ArrayList<>();
        String g = spec.groupBy();
        if (g != null) sums.add(table(g, rows));
        if (Boolean.TRUE.equals(spec.includeSummary())) {
            for (String f : List.of("status", "condition", "assetType")) {
                if (!f.equals(g)) sums.add(table(f, rows));
            }
        }
        return new ReportData(spec, spec.columns(), rows, rows.size(), sums);
    }

    // ADJUST: change getUser().getEmail() to match your AssetAssignment / User classes
    private static String who(AssetAssignment x) {
        if (x == null || x.getUser() == null) return "unknown";
        return String.valueOf(x.getUser().getEmail());
    }

    private Map<String, String> row(Asset a) {
        String holder = assignmentService.findOpenByAsset(a.getId()).map(ReportDataService::who).orElse("nobody currently");
        List<AssetAssignment> hist = assignmentService.historyByAsset(a.getId());
        // ADJUST: make sure the last element is the most recent assignment
        String last = (hist == null || hist.isEmpty()) ? "none recorded" : who(hist.get(hist.size() - 1));

        Map<String, String> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(a.getId()));
        m.put("assetType", nz(a.getAssetType()));
        m.put("model", nz(a.getModel()));
        m.put("serialNumber", nz(a.getSerialNumber()));
        m.put("barcode", nz(a.getBarcode()));
        m.put("status", String.valueOf(a.getStatus()));
        m.put("condition", String.valueOf(a.getCondition()));
        m.put("heldBy", holder);
        m.put("lastHolder", last);
        return m;
    }

    private static Table table(String field, List<Map<String, String>> rows) {
        Map<String, Long> counts = rows.stream().collect(Collectors.groupingBy(
                m -> m.get(field) == null || m.get(field).isBlank() ? "(none)" : m.get(field), Collectors.counting()));
        Map<String, Long> sorted = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return new Table(TABLE_NAMES.getOrDefault(field, field), sorted);
    }

    private static long parse(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return 0; }
    }
    private static boolean has(String s) { return s != null && !s.isBlank(); }
    private static String nz(Object o) { return o == null ? "" : String.valueOf(o); }
}
