package com.app.modules.report.repository;

import java.util.Optional;
import java.util.UUID;

import com.app.modules.report.enums.ReportType;

/** Resolves ownership for the polymorphic entity targeted by a report. */
public interface ReportTargetRepository {

    /**
     * Finds the owner of a live target entity.
     *
     * @param reportType domain type that determines the target table
     * @param entityId target entity identifier
     * @return target owner identifier, or empty when the target does not exist or is deleted
     */
    Optional<UUID> findOwnerId(ReportType reportType, UUID entityId);
}
