package com.app.modules.report.repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.app.modules.report.entity.Report;
import com.app.modules.report.enums.ReportStatus;
import com.app.modules.report.enums.ReportType;

@Repository
public interface ReportRepository extends JpaRepository<Report, UUID>, ReportTargetRepository {

    boolean existsByReporterIdAndReportTypeAndEntityId(
            UUID reporterId, ReportType reportType, UUID entityId);

    /**
     * Target ids among {@code entityIds} that this reporter has already reported under the given
     * type, for batched viewer-state flags.
     *
     * <p>Carries no status predicate on purpose. The uniqueness key that rejects a duplicate
     * submission is the unique index {@code uq_reports_reporter_type_entity}, which is not partial,
     * so a report in any status - including a resolved or dismissed one - still blocks a new report
     * on the same target. Adding a status filter here would report a target as un-reported while a
     * fresh submission would still be rejected.
     *
     * @param reporterId the requesting viewer
     * @param reportType target family shared by every id on the current page
     * @param entityIds candidate target ids on the current page
     * @return the subset the reporter has already reported, in no particular order
     */
    @Query(
            "SELECT r.entityId FROM Report r WHERE r.reporterId = :reporterId"
                    + " AND r.reportType = :reportType AND r.entityId IN :entityIds")
    List<UUID> findReportedEntityIds(
            @Param("reporterId") UUID reporterId,
            @Param("reportType") ReportType reportType,
            @Param("entityIds") Collection<UUID> entityIds);

    /**
     * Finds the newest reports up to the requested limit.
     *
     * @param limit maximum number of reports to return
     * @return reports ordered newest first with UUID as the stable tie-breaker
     */
    @Query("SELECT r FROM Report r ORDER BY r.createdAt DESC, r.id DESC LIMIT :limit")
    List<Report> findFirstReports(@Param("limit") int limit);

    /**
     * Finds the newest reports filed against one target, up to the requested limit.
     *
     * <p>Served by {@code idx_reports_entity} (V15). Bounded rather than paginated on purpose: this
     * answers "has this account been reported, and for what" on an administrative detail view, not
     * a full report browse, which the reports surface already provides.
     *
     * @param reportType target family, for example {@code USER}
     * @param entityId identifier of the reported target
     * @param limit maximum number of reports to return
     * @return matching reports ordered newest first with UUID as the stable tie-breaker
     */
    @Query(
            "SELECT r FROM Report r WHERE r.reportType = :reportType AND r.entityId = :entityId "
                    + "ORDER BY r.createdAt DESC, r.id DESC LIMIT :limit")
    List<Report> findFirstReportsAgainstEntity(
            @Param("reportType") ReportType reportType,
            @Param("entityId") UUID entityId,
            @Param("limit") int limit);

    /**
     * Finds the newest reports in any of the requested statuses.
     *
     * <p>Used for the moderator listing with no status filter, which is narrowed to the open
     * statuses rather than returning everything.
     *
     * @param statuses statuses to include
     * @param limit maximum number of reports to return
     * @return matching reports ordered newest first with UUID as the stable tie-breaker
     */
    @Query(
            "SELECT r FROM Report r WHERE r.status IN :statuses "
                    + "ORDER BY r.createdAt DESC, r.id DESC LIMIT :limit")
    List<Report> findFirstReportsByStatusIn(
            @Param("statuses") List<ReportStatus> statuses, @Param("limit") int limit);

    /**
     * Keyset page of reports in any of the requested statuses, after a cursor position.
     *
     * <p>Served by {@code idx_reports_open_queue}, whose predicate names exactly {@link
     * ReportStatus#OPEN_QUEUE}.
     *
     * <p>The extra {@code createdAt <= :cursorCreatedAt} is what makes that index usable, and it is
     * not redundant to the planner even though it implies nothing the disjunction below does not
     * already imply. An OR cannot become an index condition, so the tie-breaking disjunction alone
     * is applied as a filter after the scan has already walked every index entry newer than the
     * cursor. The extra conjunct bounds the scan at the cursor and leaves the disjunction to decide
     * only within the one timestamp tie. Measured at 5,000,000 reports on a page roughly a million
     * rows deep: without it, 1,085,242 buffers in 673 ms; with it, 24 buffers in 0.05 ms. The row
     * set is identical either way.
     *
     * @param statuses statuses to include
     * @param cursorCreatedAt creation time of the last row on the previous page
     * @param cursorId identifier of the last row on the previous page
     * @param limit maximum number of reports to return
     * @return matching reports ordered newest first with UUID as the stable tie-breaker
     */
    @Query(
            "SELECT r FROM Report r WHERE r.status IN :statuses "
                    + "AND r.createdAt <= :cursorCreatedAt "
                    + "AND (r.createdAt < :cursorCreatedAt "
                    + "OR (r.createdAt = :cursorCreatedAt AND r.id < :cursorId)) "
                    + "ORDER BY r.createdAt DESC, r.id DESC LIMIT :limit")
    List<Report> findAllByStatusInBeforeCursor(
            @Param("statuses") List<ReportStatus> statuses,
            @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            @Param("limit") int limit);

    /**
     * Finds the newest reports in any of the requested statuses and of the requested type.
     *
     * @param statuses statuses to include
     * @param reportType type used to filter reports
     * @param limit maximum number of reports to return
     * @return matching reports ordered newest first with UUID as the stable tie-breaker
     */
    @Query(
            "SELECT r FROM Report r WHERE r.status IN :statuses "
                    + "AND r.reportType = :reportType "
                    + "ORDER BY r.createdAt DESC, r.id DESC LIMIT :limit")
    List<Report> findFirstReportsByStatusInAndReportType(
            @Param("statuses") List<ReportStatus> statuses,
            @Param("reportType") ReportType reportType,
            @Param("limit") int limit);

    /**
     * Keyset page of reports in any of the requested statuses and of the requested type.
     *
     * <p>Bounded by {@code createdAt <= :cursorCreatedAt} for the same reason as {@link
     * #findAllByStatusInBeforeCursor}. The type predicate is applied as a filter over that bounded
     * range rather than as part of the index condition, which is correct: by the time it is
     * evaluated the range is already one page's worth of entries.
     *
     * @param statuses statuses to include
     * @param reportType type used to filter reports
     * @param cursorCreatedAt creation time of the last row on the previous page
     * @param cursorId identifier of the last row on the previous page
     * @param limit maximum number of reports to return
     * @return matching reports ordered newest first with UUID as the stable tie-breaker
     */
    @Query(
            "SELECT r FROM Report r WHERE r.status IN :statuses "
                    + "AND r.reportType = :reportType "
                    + "AND r.createdAt <= :cursorCreatedAt "
                    + "AND (r.createdAt < :cursorCreatedAt "
                    + "OR (r.createdAt = :cursorCreatedAt AND r.id < :cursorId)) "
                    + "ORDER BY r.createdAt DESC, r.id DESC LIMIT :limit")
    List<Report> findAllByStatusInAndReportTypeBeforeCursor(
            @Param("statuses") List<ReportStatus> statuses,
            @Param("reportType") ReportType reportType,
            @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            @Param("limit") int limit);

    /**
     * Counts the reports currently in the requested status.
     *
     * <p>Served by {@code idx_reports_escalated} for the escalated status, which is the only one
     * this is called with.
     *
     * @param status status to count
     * @return number of reports in that status
     */
    @Query("SELECT count(r) FROM Report r WHERE r.status = :status")
    long countByStatus(@Param("status") ReportStatus status);

    /**
     * Finds the newest reports with the requested status up to the requested limit.
     *
     * @param status status used to filter reports
     * @param limit maximum number of reports to return
     * @return matching reports ordered newest first with UUID as the stable tie-breaker
     */
    @Query(
            "SELECT r FROM Report r WHERE r.status = :status "
                    + "ORDER BY r.createdAt DESC, r.id DESC LIMIT :limit")
    List<Report> findFirstReportsByStatus(
            @Param("status") ReportStatus status, @Param("limit") int limit);

    /**
     * Finds the newest reports with the requested target type up to the requested limit.
     *
     * @param reportType target type used to filter reports
     * @param limit maximum number of reports to return
     * @return matching reports ordered newest first with UUID as the stable tie-breaker
     */
    @Query(
            "SELECT r FROM Report r WHERE r.reportType = :reportType "
                    + "ORDER BY r.createdAt DESC, r.id DESC LIMIT :limit")
    List<Report> findFirstReportsByReportType(
            @Param("reportType") ReportType reportType, @Param("limit") int limit);

    /**
     * Finds the newest reports with the requested status and target type up to the requested limit.
     *
     * @param status status used to filter reports
     * @param reportType target type used to filter reports
     * @param limit maximum number of reports to return
     * @return matching reports ordered newest first with UUID as the stable tie-breaker
     */
    @Query(
            "SELECT r FROM Report r WHERE r.status = :status AND r.reportType = :reportType "
                    + "ORDER BY r.createdAt DESC, r.id DESC LIMIT :limit")
    List<Report> findFirstReportsByStatusAndReportType(
            @Param("status") ReportStatus status,
            @Param("reportType") ReportType reportType,
            @Param("limit") int limit);

    /**
     * Finds the oldest reports with the requested status up to the requested limit.
     *
     * @param status status used to filter reports
     * @param limit maximum number of reports to return
     * @return matching reports ordered oldest first with UUID as the stable tie-breaker
     */
    @Query(
            "SELECT r FROM Report r WHERE r.status = :status "
                    + "ORDER BY r.createdAt ASC, r.id ASC LIMIT :limit")
    List<Report> findFirstReportsByStatusOldestFirst(
            @Param("status") ReportStatus status, @Param("limit") int limit);

    /**
     * Finds reports strictly before a descending cursor up to the requested limit.
     *
     * @param cursorCreatedAt cursor creation timestamp
     * @param cursorId cursor UUID used as the stable tie-breaker
     * @param limit maximum number of reports to return
     * @return reports following the cursor in newest-first order
     */
    @Query(
            """
			SELECT r
			FROM Report r
			WHERE r.createdAt < :cursorCreatedAt
			OR (r.createdAt = :cursorCreatedAt AND r.id < :cursorId)
			ORDER BY r.createdAt DESC, r.id DESC
			LIMIT :limit
			""")
    List<Report> findAllBeforeCursor(
            @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            @Param("limit") int limit);

    /**
     * Finds reports with a status strictly before a descending cursor.
     *
     * @param status status used to filter reports
     * @param cursorCreatedAt cursor creation timestamp
     * @param cursorId cursor UUID used as the stable tie-breaker
     * @param limit maximum number of reports to return
     * @return matching reports following the cursor in newest-first order
     */
    @Query(
            """
			SELECT r
			FROM Report r
			WHERE r.status = :status
			AND (r.createdAt < :cursorCreatedAt
				OR (r.createdAt = :cursorCreatedAt AND r.id < :cursorId))
			ORDER BY r.createdAt DESC, r.id DESC
			LIMIT :limit
			""")
    List<Report> findAllByStatusBeforeCursor(
            @Param("status") ReportStatus status,
            @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            @Param("limit") int limit);

    /**
     * Finds reports with a target type strictly before a descending cursor.
     *
     * @param reportType target type used to filter reports
     * @param cursorCreatedAt cursor creation timestamp
     * @param cursorId cursor UUID used as the stable tie-breaker
     * @param limit maximum number of reports to return
     * @return matching reports following the cursor in newest-first order
     */
    @Query(
            """
			SELECT r
			FROM Report r
			WHERE r.reportType = :reportType
			AND (r.createdAt < :cursorCreatedAt
				OR (r.createdAt = :cursorCreatedAt AND r.id < :cursorId))
			ORDER BY r.createdAt DESC, r.id DESC
			LIMIT :limit
			""")
    List<Report> findAllByReportTypeBeforeCursor(
            @Param("reportType") ReportType reportType,
            @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            @Param("limit") int limit);

    /**
     * Finds reports with a status and target type strictly before a descending cursor.
     *
     * @param status status used to filter reports
     * @param reportType target type used to filter reports
     * @param cursorCreatedAt cursor creation timestamp
     * @param cursorId cursor UUID used as the stable tie-breaker
     * @param limit maximum number of reports to return
     * @return matching reports following the cursor in newest-first order
     */
    @Query(
            """
			SELECT r
			FROM Report r
			WHERE r.status = :status
			AND r.reportType = :reportType
			AND (r.createdAt < :cursorCreatedAt
				OR (r.createdAt = :cursorCreatedAt AND r.id < :cursorId))
			ORDER BY r.createdAt DESC, r.id DESC
			LIMIT :limit
			""")
    List<Report> findAllByStatusAndReportTypeBeforeCursor(
            @Param("status") ReportStatus status,
            @Param("reportType") ReportType reportType,
            @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            @Param("limit") int limit);

    /**
     * Finds reports with a status strictly after an ascending cursor.
     *
     * @param status status used to filter reports
     * @param cursorCreatedAt cursor creation timestamp
     * @param cursorId cursor UUID used as the stable tie-breaker
     * @param limit maximum number of reports to return
     * @return matching reports following the cursor in oldest-first order
     */
    @Query(
            """
			SELECT r
			FROM Report r
			WHERE r.status = :status
			AND (r.createdAt > :cursorCreatedAt
				OR (r.createdAt = :cursorCreatedAt AND r.id > :cursorId))
			ORDER BY r.createdAt ASC, r.id ASC
			LIMIT :limit
			""")
    List<Report> findAllByStatusAfterCursor(
            @Param("status") ReportStatus status,
            @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            @Param("limit") int limit);

    /**
     * First page of the reports one moderator escalated, newest escalation first.
     *
     * <p>Deliberately carries no status predicate. An escalated report leaves every queue the
     * moderator can read, and an administrator may since have resolved it; excluding those would
     * hide exactly the outcomes the moderator escalated in order to follow. Served by {@code
     * idx_reports_escalated_by} (V81).
     *
     * @param escalatedBy the moderator whose escalations to list
     * @param limit maximum number of reports to return
     * @return matching reports ordered by escalation time descending, id as tie-breaker
     */
    @Query(
            "SELECT r FROM Report r WHERE r.escalatedBy = :escalatedBy "
                    + "ORDER BY r.escalatedAt DESC, r.id DESC LIMIT :limit")
    List<Report> findFirstEscalatedBy(
            @Param("escalatedBy") UUID escalatedBy, @Param("limit") int limit);

    /**
     * Page of the reports one moderator escalated, after a cursor position.
     *
     * @param escalatedBy the moderator whose escalations to list
     * @param cursorEscalatedAt escalation time of the last row on the previous page
     * @param cursorId identifier of the last row on the previous page
     * @param limit maximum number of reports to return
     * @return matching reports following the cursor, newest escalation first
     */
    @Query(
            """
			SELECT r
			FROM Report r
			WHERE r.escalatedBy = :escalatedBy
			AND (r.escalatedAt < :cursorEscalatedAt
				OR (r.escalatedAt = :cursorEscalatedAt AND r.id < :cursorId))
			ORDER BY r.escalatedAt DESC, r.id DESC
			LIMIT :limit
			""")
    List<Report> findEscalatedByAfterCursor(
            @Param("escalatedBy") UUID escalatedBy,
            @Param("cursorEscalatedAt") OffsetDateTime cursorEscalatedAt,
            @Param("cursorId") UUID cursorId,
            @Param("limit") int limit);
}
