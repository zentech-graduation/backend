package com.app.modules.report.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.app.modules.report.entity.Report;
import com.app.modules.report.enums.ReportStatus;
import com.app.modules.report.enums.ReportType;

@Repository
public interface ReportRepository extends JpaRepository<Report, UUID>, ReportTargetRepository {

    boolean existsByReporterIdAndReportTypeAndEntityId(
            UUID reporterId, ReportType reportType, UUID entityId);

    List<Report> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    List<Report> findAllByStatusOrderByCreatedAtDescIdDesc(ReportStatus status, Pageable pageable);

    List<Report> findAllByReportTypeOrderByCreatedAtDescIdDesc(
            ReportType reportType, Pageable pageable);

    List<Report> findAllByStatusAndReportTypeOrderByCreatedAtDescIdDesc(
            ReportStatus status, ReportType reportType, Pageable pageable);

    List<Report> findAllByStatusOrderByCreatedAtAscIdAsc(ReportStatus status, Pageable pageable);

    @Query(
            """
			SELECT r
			FROM Report r
			WHERE r.createdAt < :cursorCreatedAt
			OR (r.createdAt = :cursorCreatedAt AND r.id < :cursorId)
			ORDER BY r.createdAt DESC, r.id DESC
			""")
    List<Report> findAllBeforeCursor(
            OffsetDateTime cursorCreatedAt, UUID cursorId, Pageable pageable);

    @Query(
            """
			SELECT r
			FROM Report r
			WHERE r.status = :status
			AND (
				r.createdAt < :cursorCreatedAt
				OR (r.createdAt = :cursorCreatedAt AND r.id < :cursorId)
			)
			ORDER BY r.createdAt DESC, r.id DESC
			""")
    List<Report> findAllByStatusBeforeCursor(
            ReportStatus status, OffsetDateTime cursorCreatedAt, UUID cursorId, Pageable pageable);

    @Query(
            """
			SELECT r
			FROM Report r
			WHERE r.reportType = :reportType
			AND (
				r.createdAt < :cursorCreatedAt
				OR (r.createdAt = :cursorCreatedAt AND r.id < :cursorId)
			)
			ORDER BY r.createdAt DESC, r.id DESC
			""")
    List<Report> findAllByReportTypeBeforeCursor(
            ReportType reportType,
            OffsetDateTime cursorCreatedAt,
            UUID cursorId,
            Pageable pageable);

    @Query(
            """
			SELECT r
			FROM Report r
			WHERE r.status = :status
			AND r.reportType = :reportType
			AND (
				r.createdAt < :cursorCreatedAt
				OR (r.createdAt = :cursorCreatedAt AND r.id < :cursorId)
			)
			ORDER BY r.createdAt DESC, r.id DESC
			""")
    List<Report> findAllByStatusAndReportTypeBeforeCursor(
            ReportStatus status,
            ReportType reportType,
            OffsetDateTime cursorCreatedAt,
            UUID cursorId,
            Pageable pageable);

    @Query(
            """
			SELECT r
			FROM Report r
			WHERE r.status = :status
			AND (
				r.createdAt > :cursorCreatedAt
				OR (r.createdAt = :cursorCreatedAt AND r.id > :cursorId)
			)
			ORDER BY r.createdAt ASC, r.id ASC
			""")
    List<Report> findAllByStatusAfterCursor(
            ReportStatus status, OffsetDateTime cursorCreatedAt, UUID cursorId, Pageable pageable);
}
