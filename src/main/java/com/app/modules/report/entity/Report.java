package com.app.modules.report.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

import com.app.modules.report.converter.ReportReasonConverter;
import com.app.modules.report.converter.ReportStatusConverter;
import com.app.modules.report.converter.ReportTypeConverter;
import com.app.modules.report.enums.ReportReason;
import com.app.modules.report.enums.ReportStatus;
import com.app.modules.report.enums.ReportType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Maps one user-submitted moderation flag to the {@code reports} table. */
@Entity
@Table(name = "reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "reporter_id", nullable = false, updatable = false)
    private UUID reporterId;

    @Convert(converter = ReportTypeConverter.class)
    @Column(
            name = "report_type",
            nullable = false,
            updatable = false,
            columnDefinition = "report_type")
    private ReportType reportType;

    @Convert(converter = ReportReasonConverter.class)
    @Column(
            name = "report_reason",
            nullable = false,
            updatable = false,
            columnDefinition = "report_reason")
    private ReportReason reportReason;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    @Column(name = "description", columnDefinition = "TEXT", updatable = false)
    private String description;

    @Convert(converter = ReportStatusConverter.class)
    @Column(name = "status", nullable = false, columnDefinition = "report_status")
    private ReportStatus status;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "resolution_note", columnDefinition = "TEXT")
    private String resolutionNote;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
