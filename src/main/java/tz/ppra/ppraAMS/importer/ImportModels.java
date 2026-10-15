package tz.ppra.ppraAMS.importer;

import java.util.List;

public final class ImportModels {
    private ImportModels() {}

    /** level: OK, WARNING or ERROR (ERROR rows are not saved) */
    public record AssetDraft(int row, String assetType, String model, String serialNumber, String barcode,
                             String status, String condition, String allocatedTo, String allocatedEmail,
                             List<String> issues, String level) {}

    public record ImportPreview(String fileName, String method, int rowCount,
                                List<AssetDraft> rows, List<String> notes) {}

    public record ConfirmRequest(List<AssetDraft> rows) {}

    public record ImportResult(int created, int skipped, List<String> messages) {}

    // 👇 NEW: input type used by the GraphQL confirmImport mutation
    public record AssetDraftInput(Integer row, String assetType, String model,
                                  String serialNumber, String barcode,
                                  String status, String condition,
                                  String allocatedTo, String allocatedEmail,
                                  List<String> issues, String level) {}


}
