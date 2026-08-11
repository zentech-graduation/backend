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
}
