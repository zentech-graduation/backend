package com.app.modules.report.service.impl;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.modules.report.enums.ReportType;
import com.app.modules.report.repository.ReportRepository;
import com.app.modules.report.service.ReportedTargetService;

@Service
public class ReportedTargetServiceImpl implements ReportedTargetService {

    private final ReportRepository reportRepository;

    public ReportedTargetServiceImpl(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> loadReportedEntityIds(
            UUID viewerId, ReportType reportType, Collection<UUID> entityIds) {
        if (viewerId == null || entityIds == null || entityIds.isEmpty()) {
            return Set.of();
        }
        Set<UUID> distinct = new LinkedHashSet<>(entityIds);
        return new HashSet<>(
                reportRepository.findReportedEntityIds(viewerId, reportType, distinct));
    }
}
