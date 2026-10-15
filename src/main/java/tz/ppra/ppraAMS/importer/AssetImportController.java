package tz.ppra.ppraAMS.importer;

import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import tz.ppra.ppraAMS.importer.ImportModels.AssetDraft;
import tz.ppra.ppraAMS.importer.ImportModels.ImportPreview;
import tz.ppra.ppraAMS.importer.ImportModels.ImportResult;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Set;

@Controller
@RequiredArgsConstructor
public class AssetImportController {

    private static final Set<String> ALLOWED =
            Set.of("pdf", "xlsx", "xls", "docx", "txt", "csv");

    private final AssetImportService service;

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ImportPreview previewImport(@Argument("filename") String filename,
                                       @Argument("fileBase64") String fileBase64,
                                       @Argument("instruction") String instruction)
            throws IOException {

        if (filename == null || filename.isBlank()) {
            throw new RuntimeException("filename is required");
        }
        String ext = filename.contains(".")
                ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase()
                : "";
        if (!ALLOWED.contains(ext)) {
            throw new RuntimeException("Allowed files: " + ALLOWED);
        }
        if (fileBase64 == null || fileBase64.isBlank()) {
            throw new RuntimeException("The file is empty");
        }

        byte[] bytes = Base64.getDecoder().decode(fileBase64);
        return service.preview(filename, bytes, instruction);
    }

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ImportResult confirmImport(@Argument("rows") List<ImportModels.AssetDraftInput> rows) {
        // Map GraphQL input records → AssetDraft
        List<AssetDraft> drafts = rows.stream()
                .map(r -> new AssetDraft(
                        r.row() != null ? r.row() : 0,
                        r.assetType(),
                        r.model(),
                        r.serialNumber(),
                        r.barcode(),
                        r.status(),
                        r.condition(),
                        r.allocatedTo(),
                        r.allocatedEmail(),
                        r.issues() != null ? r.issues() : List.of(),
                        r.level() != null ? r.level() : "OK"
                ))
                .toList();

        return service.confirm(drafts);
    }
}
