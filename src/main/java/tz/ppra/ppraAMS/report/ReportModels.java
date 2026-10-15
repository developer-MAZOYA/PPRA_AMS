package tz.ppra.ppraAMS.report;

import java.util.List;
import java.util.Map;

public final class ReportModels {
    private ReportModels() {}

    /** What the AI produces from the instruction. Every field may be null. */
    public record ReportSpec(String title, String status, String condition, String assetType,
                             String heldBy, String text, String groupBy, List<String> columns,
                             String sortBy, String format, Boolean includeSummary, Boolean showRows) {}

    public record Table(String name, Map<String, Long> counts) {}

    public record ReportData(ReportSpec spec, List<String> columns, List<Map<String, String>> rows,
                             int total, List<Table> summaries) {}

    public record ReportPlan(ReportSpec spec, int total, List<Table> summaries, String narrative,
                             List<Map<String, String>> sample, List<String> notes) {}

    public record DownloadRequest(ReportSpec spec, String narrative) {}

    public record ReportFile(byte[] bytes, String filename, String contentType) {}
}
