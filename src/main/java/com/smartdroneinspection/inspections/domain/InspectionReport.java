package com.smartdroneinspection.inspections.domain;

import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

public class InspectionReport {

  @Id @GeneratedValue private UUID id;

  @Column(name = "inspection_id", nullable = false, unique = true)
  private UUID inspectionId;

  @Column(name = "author_user_id", nullable = false)
  private UUID authorUserId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private ReportStatus status;

  @Column(name = "current_version_number", nullable = false)
  private int currentVersionNumber;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected InspectionReport() {}

  public InspectionReport(UUID inspectionId, UUID authorUserId) {
    this.inspectionId = inspectionId;
    this.authorUserId = authorUserId;
    this.status = ReportStatus.DRAFT;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getInspectionId() {
    return inspectionId;
  }

  public UUID getAuthorUserId() {
    return authorUserId;
  }

  public ReportStatus getStatus() {
    return status;
  }

  public int getCurrentVersionNumber() {
    return currentVersionNumber;
  }

  public void startVersion(int versionNumber) {
    if (status == ReportStatus.ACCEPTED || immutableVersionExists()) {
      throw new IllegalStateException("An accepted report cannot be revised");
    }
    if (versionNumber != currentVersionNumber + 1) {
      throw new IllegalArgumentException("Report versions must be sequential");
    }
    currentVersionNumber = versionNumber;
    status = ReportStatus.DRAFT;
    updatedAt = Instant.now();
  }

  public void changeStatus(ReportStatus nextStatus) {
    if (status == ReportStatus.ACCEPTED && nextStatus != ReportStatus.ACCEPTED) {
      throw new IllegalStateException("An accepted report is immutable");
    }
    status = nextStatus;
    updatedAt = Instant.now();
  }

  private boolean immutableVersionExists() {
    return status == ReportStatus.ACCEPTED;
  }
}
