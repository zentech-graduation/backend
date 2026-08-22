package com.app.modules.admin.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One entry in an account's violation history, discriminated by {@code kind}.
 *
 * <p>Warnings and strikes are interleaved in one chronological listing rather than returned as two
 * lists, because a reviewer reads them as a sequence: which warnings preceded which strike is the
 * whole question. Two lists could not share one cursor either, so a single ordered page is the only
 * shape the pagination contract admits.
 *
 * <p>Modelled as a union of two shapes rather than one flat record carrying both. The flat form
 * declared {@code reasonKey}, {@code note} and {@code strikeNumber} on every row and left each null
 * on the half of the rows it does not describe, which told a generated client nothing about which
 * row was which. {@code kind} is the discriminator: a reader switches on it and reaches only fields
 * that exist.
 *
 * <p>A moderator receives warnings only, so a moderator's page contains no {@code strike} entry.
 *
 * <p>A revoked entry is returned only when the caller asks for it, and is told apart by {@code
 * revokedAt} being non-null. Revoking used to remove the row from this listing entirely, so an
 * account that had been disciplined and then cleared looked identical to one that never was, and
 * the decision survived only as a separate row in the moderation action log.
 */
@Schema(
        description = "One entry in an account's violation history, discriminated by kind",
        discriminatorProperty = "kind",
        discriminatorMapping = {
            @DiscriminatorMapping(value = "warning", schema = AdminWarningViolationResponse.class),
            @DiscriminatorMapping(value = "strike", schema = AdminStrikeViolationResponse.class)
        },
        oneOf = {AdminWarningViolationResponse.class, AdminStrikeViolationResponse.class})
public sealed interface AdminViolationResponse
        permits AdminWarningViolationResponse, AdminStrikeViolationResponse {

    String KIND_WARNING = "warning";
    String KIND_STRIKE = "strike";

    /** Which of the two shapes this entry is; {@code warning} or {@code strike}. */
    String kind();

    /** Identifier of the underlying warning or strike row. */
    UUID id();

    /** Account the entry is against. */
    UUID userId();

    /**
     * Moderator who issued the warning, or whose warning triggered the strike; null once that
     * account is deleted.
     */
    UUID actorId();

    /** Moment the entry was recorded. */
    OffsetDateTime createdAt();

    /** When the entry was revoked, or null while it still stands. */
    OffsetDateTime revokedAt();

    /** Who revoked it; null while it stands, and null once that account is deleted. */
    UUID revokedBy();
}
