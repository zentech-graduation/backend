package com.app.modules.report.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.app.modules.report.enums.ReportStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Payload for transitioning a report through the review lifecycle")
public record UpdateReportStatusRequest(
        @Schema(
                        description = "Target report status",
                        example = "reviewing",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                ReportStatus status,
        @Schema(
                        description =
                                "Retained for wire compatibility and ignored. The only transition"
                                        + " this endpoint performs is pending to reviewing, which"
                                        + " carries no resolution. The note for a resolved or dismissed"
                                        + " report is the reason sent to the admin close endpoints.",
                        example = "Content removed after confirming the violation")
                @Size(max = 2000)
                String resolutionNote) {}
