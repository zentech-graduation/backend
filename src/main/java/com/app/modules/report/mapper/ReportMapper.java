package com.app.modules.report.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import com.app.modules.report.dto.response.ReportResponse;
import com.app.modules.report.entity.Report;

/** Maps {@link Report} entities to report-layer DTOs. */
@Mapper(componentModel = "spring")
public interface ReportMapper {

    ReportResponse toResponse(Report report);

    List<ReportResponse> toResponseList(List<Report> reports);
}
