package com.app.modules.report.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.app.modules.report.entity.Report;
import com.app.modules.report.enums.ReportStatus;
import com.app.modules.report.enums.ReportType;

@Repository
public interface ReportRepository extends JpaRepository<Report, UUID>, ReportTargetRepository {

    boolean existsByReporterIdAndReportTypeAndEntityId(
            UUID reporterId, ReportType reportType, UUID entityId);

    Page<Report> findAllByStatus(ReportStatus status, Pageable pageable);

    Page<Report> findAllByReportType(ReportType reportType, Pageable pageable);

    Page<Report> findAllByStatusAndReportType(
            ReportStatus status, ReportType reportType, Pageable pageable);
}
