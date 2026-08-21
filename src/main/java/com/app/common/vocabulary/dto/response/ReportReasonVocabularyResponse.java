package com.app.common.vocabulary.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One row of {@code report_reason_configs}.
 *
 * <p>The enum constrains what may be stored; this table decides what is currently offered and how
 * it is presented. Both a reporter choosing why they are flagging something and a moderator citing
 * a reason on a warning draw from the same rows.
 *
 * @param key the value to send as {@code reportReason} or {@code reasonKey}
 * @param displayName the label to render
 * @param description longer explanatory text, or null when the row carries none
 * @param appliesTo report types this reason is valid for; empty means every type
 * @param isEnabled whether the reason is currently accepted
 * @param sortOrder ascending display position
 */
@Schema(description = "One selectable report reason with its display metadata")
public record ReportReasonVocabularyResponse(
        @Schema(description = "Value to send on the wire", example = "hate_speech") String key,
        @Schema(description = "Label to render", example = "Hate Speech") String displayName,
        @Schema(description = "Longer explanatory text", nullable = true) String description,
        @Schema(
                        description =
                                "Report types this reason is valid for; empty means every type",
                        example = "[\"post\",\"comment\"]")
                List<String> appliesTo,
        @Schema(
                        description =
                                "Whether the reason is currently accepted. A disabled reason is"
                                        + " returned rather than omitted so a client can render it"
                                        + " as unavailable instead of having it silently vanish"
                                        + " between page loads.")
                boolean isEnabled,
        @Schema(description = "Ascending display position", example = "4") short sortOrder) {}
