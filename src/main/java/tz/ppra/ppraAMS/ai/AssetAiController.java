package tz.ppra.ppraAMS.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

@Controller
public class AssetAiController {

    private final ChatClient chat;
    private final AssetTools tools;

    public AssetAiController(ChatClient.Builder builder, AssetTools tools) {
        this.tools = tools;
        this.chat = builder.defaultSystem("""
                You answer questions about the organisation's ICT asset register.
                Use the tools for every fact (serials, status, counts, who holds an asset).
                If a tool returns nothing, say no records match.
                Never guess or invent data. Keep answers short.""").build();
    }

    @QueryMapping
    @PreAuthorize("hasRole('ADMIN')")
    public String askAssets(@Argument String question) {
        if (question == null || question.isBlank() || question.length() > 500) {
            return "Please ask a shorter question.";
        }
        return chat.prompt().user(question).tools(tools).call().content();
    }

    /**
     * Parse a free-text description of an asset and return the fields
     * pre-filled. Used by the "AI fill" helper on the asset form.
     */
    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public AssetSuggestion suggestAssetFields(@Argument String description) {
        if (description == null || description.isBlank()) {
            return new AssetSuggestion(null, null, null, null, null, null,
                    "Please describe the asset.");
        }
        if (description.length() > 500) {
            return new AssetSuggestion(null, null, null, null, null, null,
                    "Description is too long (max 500 chars).");
        }

        String prompt = """
                Extract asset fields from the following description.
                Return a JSON object with exactly these keys:
                  assetType   (short uppercase word, e.g. LAPTOP, PRINTER, DESK, MONITOR, PHONE)
                  model       (the product name as written)
                  serialNumber (manufacturer serial, or null if not stated)
                  barcode      (asset tag / barcode, or null if not stated)
                  condition    (one of NEW, VERYGOOD, GOOD, FAIR, or null if not stated)
                  description  (software, antivirus, OS notes — keep as written, or null)
                Do not invent values. Use null for anything missing.
                Reply with JSON only.

                Description: %s
                """.formatted(description);

        try {
            AssetSuggestion s = chat.prompt().user(prompt)
                    .call()
                    .entity(AssetSuggestion.class);

            if (s == null) {
                return new AssetSuggestion(null, null, null, null, null, null,
                        "The assistant could not parse that.");
            }
            // Normalise condition to the enum's uppercase form
            String cond = s.condition() == null ? null : s.condition().toUpperCase().replace(" ", "");
            if (cond != null && !isValidCondition(cond)) cond = null;

            return new AssetSuggestion(
                    s.assetType() == null ? null : s.assetType().toUpperCase(),
                    s.model(),
                    s.serialNumber(),
                    s.barcode(),
                    cond,
                    s.description(),
                    null
            );
        } catch (Exception e) {
            return new AssetSuggestion(null, null, null, null, null, null,
                    "AI fill failed: " + e.getMessage());
        }
    }

    private static boolean isValidCondition(String c) {
        return switch (c) {
            case "NEW", "VERYGOOD", "GOOD", "FAIR" -> true;
            default -> false;
        };
    }

    public record AssetSuggestion(
            String assetType,
            String model,
            String serialNumber,
            String barcode,
            String condition,
            String description,
            String message
    ) {}
}
