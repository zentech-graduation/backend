package com.app.modules.report.repository;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.app.modules.report.entity.Report;
import com.app.modules.report.enums.ReportStatus;
import com.app.modules.report.enums.ReportType;

public interface ReportRepository extends JpaRepository<Report, UUID> {
    boolean existsByReporterIdAndReportTypeAndEntityIdAndStatusIn(
            UUID reporterId,
            ReportType reportType,
            UUID entityId,
            Collection<ReportStatus> statuses);

    @Query(
            "select r from Report r where (:status is null or r.status = :status) "
                    + "and (:type is null or r.reportType = :type)")
    Page<Report> findAllFiltered(
            @Param("status") ReportStatus status,
            @Param("type") ReportType type,
            Pageable pageable);
}
