package com.app.modules.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * How many reports are waiting on an administrator.
 *
 * <p>Escalation pushes no notification by design, so this count is the only signal that an
 * escalated report exists. A dashboard that does not surface it makes escalation a black hole: the
 * moderator has handed the decision up and nobody is told it arrived.
 *
 * @param count reports currently in the escalated state
 */
@Schema(description = "Number of reports waiting on an administrator")
public record EscalatedReportCountResponse(long count) {}
