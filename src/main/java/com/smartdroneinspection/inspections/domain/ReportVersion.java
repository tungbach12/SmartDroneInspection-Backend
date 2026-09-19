package com.smartdroneinspection.inspections.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "report_versions")
public class ReportVersion {

  @Id @GeneratedValue private UUID id;

  @Column(name = "report_id", nullable = false)
  private UUID reportId;

  @Column(name = "version_number", nullable = false)
  private int versionNumber;

  @Column(name = "source_version_id")
  private UUID sourceVersionId;

  @Column(name = "created_by_user_id", nullable = false)
  private UUID createdByUserId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "content_snapshot", nullable = false, columnDefinition = "jsonb")
  private String contentSnapshot;

  @Column(name = "pdf_object_key", length = 1000)
  private String pdfObjectKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private ReportStatus status;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @Column(name = "technically_approved_at")
  private Instant technicallyApprovedAt;

  @Column(name = "released_at")
  private Instant releasedAt;

  @Column(name = "accepted_at")
  private Instant acceptedAt;

  @Column(nullable = false)
  private boolean immutable;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected ReportVersion() {}

  public ReportVersion(
      UUID reportId,
      int versionNumber,
      UUID sourceVersionId,
      UUID createdByUserId,
      String contentSnapshot) {
    this.reportId = reportId;
    this.versionNumber = versionNumber;
    this.sourceVersionId = sourceVersionId;
    this.createdByUserId = createdByUserId;
    this.contentSnapshot = contentSnapshot;
    this.status = ReportStatus.DRAFT;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getReportId() {
    return reportId;
  }

  public int getVersionNumber() {
    return versionNumber;
  }

  public ReportStatus getStatus() {
    return status;
  }
}
