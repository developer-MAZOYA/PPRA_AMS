package tz.ppra.ppraAMS.report;

import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import tz.ppra.ppraAMS.report.ReportModels.ReportFile;
import tz.ppra.ppraAMS.report.ReportModels.ReportPlan;
import tz.ppra.ppraAMS.report.ReportModels.ReportSpec;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class ReportController {

    private final ReportService service;

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ReportPlan planReport(@Argument("instruction") String instruction) {
        if (instruction == null || instruction.isBlank() || instruction.length() > 1000) {
            throw new RuntimeException("Give an instruction of up to 1000 characters");
        }
        return service.plan(instruction);
    }

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ReportFilePayload generateReport(@Argument("spec") ReportSpecInput spec,
                                            @Argument("narrative") String narrative) throws IOException {
        if (spec == null) {
            throw new RuntimeException("spec is required");
        }
        // Convert GraphQL input → ReportSpec record
        ReportSpec s = new ReportSpec(
                spec.title(),
                spec.status(),
                spec.condition(),
                spec.assetType(),
                spec.heldBy(),
                spec.text(),
                spec.groupBy(),
                spec.columns(),
                spec.sortBy(),
                spec.format(),
                spec.includeSummary(),
                spec.showRows()
        );
        ReportFile f = service.generate(s, narrative);   // 👈 now passes ReportSpec
        return new ReportFilePayload(
                f.filename(),
                f.contentType(),
                Base64.getEncoder().encodeToString(f.bytes())
        );
    }

    // ---------- GraphQL output/input types ----------

    public record ReportFilePayload(String filename, String contentType, String contentBase64) {}

    public record ReportSpecInput(
            String title,
            String status,
            String condition,
            String assetType,
            String heldBy,
            String text,
            String groupBy,
            List<String> columns,
            String sortBy,
            String format,
            Boolean includeSummary,
            Boolean showRows
    ) {}
}
