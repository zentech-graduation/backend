package com.app.modules.report.service;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

import com.app.modules.report.enums.ReportType;

/**
 * Batch lookup of the targets a viewer has already reported, for viewer-state assembly on other
 * modules' response surfaces.
 *
 * <p>This is the read-side counterpart to report submission. It exists so a list page can resolve
 * the flag once for every row it renders instead of probing per row.
 */
public interface ReportedTargetService {

    /**
     * Returns the subset of {@code entityIds} that this viewer has already reported.
     *
     * <p>Membership of the returned set is exactly the condition under which a new report from this
     * viewer against that target would be rejected as a duplicate. Report status is deliberately
     * not considered, because the unique index enforcing duplicate suppression is not partial.
     *
     * @param viewerId the requesting viewer, or null for an anonymous caller
     * @param reportType target family shared by every id in {@code entityIds}
     * @param entityIds candidate target ids on the current page; may be empty or null
     * @return the reported subset, empty for an anonymous viewer or an empty batch
     */
    Set<UUID> loadReportedEntityIds(
            UUID viewerId, ReportType reportType, Collection<UUID> entityIds);
}
