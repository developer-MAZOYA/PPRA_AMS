package tz.ppra.ppraAMS.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tz.ppra.ppraAMS.model.Asset;
import tz.ppra.ppraAMS.model.AssetAssignment;
import tz.ppra.ppraAMS.model.AssetCondition;
import tz.ppra.ppraAMS.model.AssetStatus;
import tz.ppra.ppraAMS.service.AssetAssignmentService;
import tz.ppra.ppraAMS.service.AssetService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AssetTools {

    private final AssetService assetService;
    private final AssetAssignmentService assignmentService;

    public record AssetView(Long id, String assetType, String model, String serialNumber,
                            String barcode, String status, String condition,
                            String heldBy, String lastHolder) {}

    // ---------- helpers ----------

    private static final Map<String, String> SYN = Map.ofEntries(
            Map.entry("theft", "stol"), Map.entry("stolen", "stol"), Map.entry("missing", "stol"),
            Map.entry("free", "avail"), Map.entry("unassigned", "avail"), Map.entry("unallocated", "avail"),
            Map.entry("spare", "avail"), Map.entry("available", "avail"),
            Map.entry("allocated", "assign"), Map.entry("issued", "assign"), Map.entry("in use", "assign"),
            Map.entry("assigned", "assign"), Map.entry("brand new", "new"),
            Map.entry("broken", "damag"), Map.entry("faulty", "damag"), Map.entry("bad", "poor"));

    private static <E extends Enum<E>> E resolve(String input, E[] values) {
        if (input == null || input.isBlank()) return null;
        String k = input.trim().toLowerCase();
        String key = SYN.getOrDefault(k, k).replaceAll("[^a-z]", "");
        if (key.isEmpty()) return null;
        for (E v : values) {
            String n = v.name().toLowerCase().replaceAll("[^a-z]", "");
            if (n.equals(key) || n.contains(key) || key.contains(n)) return v;
        }
        return null;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    // ADJUST: change getUser().getEmail() to match your AssetAssignment / User classes
    private static String who(AssetAssignment x) {
        if (x == null || x.getUser() == null) return "unknown";
        return String.valueOf(x.getUser().getEmail());
    }

    private AssetView view(Asset a) {
        String holder = assignmentService.findOpenByAsset(a.getId())
                .map(AssetTools::who)
                .orElse("nobody currently");

        List<AssetAssignment> hist = assignmentService.historyByAsset(a.getId());
        // ADJUST: make sure the last element is the most recent assignment
        // (sort by your assigned-date field first if the service does not already do it)
        String last = (hist == null || hist.isEmpty())
                ? "no assignment history recorded"
                : who(hist.get(hist.size() - 1));

        return new AssetView(a.getId(), a.getAssetType(), a.getModel(), a.getSerialNumber(),
                a.getBarcode(), String.valueOf(a.getStatus()), String.valueOf(a.getCondition()),
                holder, last);
    }

    // ---------- tools ----------

    @Tool(description = "Search the asset register. All parameters are optional; leave out any you don't need. "
            + "Works for lists, serial or barcode lookups, a person's assets, stolen, new, available "
            + "or assigned assets, and condition. "
            + "Each item has heldBy (current holder) and lastHolder (last person it was assigned to). "
            + "Returns total, up to 25 items, and a note.")
    @Transactional(readOnly = true)
    public Map<String, Object> searchAssets(
            @ToolParam(required = false, description = "status in the user's words, e.g. stolen, available, assigned") String status,
            @ToolParam(required = false, description = "condition in the user's words, e.g. new, good") String condition,
            @ToolParam(required = false, description = "asset type, e.g. laptop") String assetType,
            @ToolParam(required = false, description = "part of a person's name or email") String heldBy,
            @ToolParam(required = false, description = "text in model, serial number or barcode") String text) {

        List<String> notes = new ArrayList<>();

        AssetStatus st = resolve(status, AssetStatus.values());
        if (!blank(status) && st == null) {
            notes.add("Status '" + status + "' not recognised, ignored. Valid: " + Arrays.toString(AssetStatus.values()));
        }
        AssetCondition cd = resolve(condition, AssetCondition.values());
        if (!blank(condition) && cd == null) {
            notes.add("Condition '" + condition + "' not recognised, ignored. Valid: " + Arrays.toString(AssetCondition.values()));
        }

        List<Asset> matched = assetService.list().stream()
                .filter(a -> st == null || a.getStatus() == st)
                .filter(a -> cd == null || a.getCondition() == cd)
                .filter(a -> blank(assetType)
                        || String.valueOf(a.getAssetType()).toLowerCase().contains(assetType.trim().toLowerCase()))
                .filter(a -> blank(text)
                        || (a.getModel() + " " + a.getSerialNumber() + " " + a.getBarcode())
                        .toLowerCase().contains(text.trim().toLowerCase()))
                .toList();

        // Holder lookup costs queries per asset: only scan everything when filtering by holder.
        boolean byHolder = !blank(heldBy);
        List<AssetView> views = (byHolder ? matched.stream() : matched.stream().limit(25))
                .map(this::view)
                .filter(v -> !byHolder
                        || v.heldBy().toLowerCase().contains(heldBy.trim().toLowerCase())
                        || v.lastHolder().toLowerCase().contains(heldBy.trim().toLowerCase()))
                .toList();
        int total = byHolder ? views.size() : matched.size();

        if (total == 0) {
            notes.add("Nothing matched all filters. Retry with fewer filters. Overview: " + overview());
        }

        return Map.of(
                "total", total,
                "items", views.stream().limit(25).toList(),
                "note", String.join(" | ", notes));
    }

    @Tool(description = "Count assets grouped by one field: status, condition or assetType")
    @Transactional(readOnly = true)
    public Map<String, Long> countBy(@ToolParam(description = "status, condition or assetType") String field) {
        String f = field == null ? "status" : field.trim().toLowerCase();
        return assetService.list().stream().collect(Collectors.groupingBy(a -> switch (f) {
            case "condition" -> String.valueOf(a.getCondition());
            case "assettype", "type" -> String.valueOf(a.getAssetType());
            default -> String.valueOf(a.getStatus());
        }, Collectors.counting()));
    }

    /** Short summary used both as a hint inside results and in the system prompt. Not a tool. */
    @Transactional(readOnly = true)
    public String overview() {
        List<Asset> all = assetService.list();
        return "Total " + all.size()
                + ". By status: " + all.stream().collect(Collectors.groupingBy(a -> String.valueOf(a.getStatus()), Collectors.counting()))
                + ". By condition: " + all.stream().collect(Collectors.groupingBy(a -> String.valueOf(a.getCondition()), Collectors.counting()))
                + ". By type: " + all.stream().collect(Collectors.groupingBy(a -> String.valueOf(a.getAssetType()), Collectors.counting()));
    }
}
