package com.smartdroneinspection.inspections.domain;

import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** The report aggregate for one inspection. Versions carry the content; this carries ownership. */
@Entity
@Table(name = "inspection_reports")
public class InspectionReport {

  @Id @GeneratedValue private UUID id;

  @Column(name = "inspection_id", nullable = false, unique = true)
  private UUID inspectionId;

  @Column(name = "author_user_id", nullable = false)
  private UUID authorUserId;

  @Column(nullable = false, length = 32)
  private String status;

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
    this.status = ReportStatus.DRAFT.name();
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public int nextVersionNumber() {
    return currentVersionNumber + 1;
  }

  public void recordNewVersion(int versionNumber) {
    if (versionNumber != nextVersionNumber()) {
      throw new IllegalArgumentException("Report versions must be sequential");
    }
    currentVersionNumber = versionNumber;
    updatedAt = Instant.now();
  }

  public void setStatus(ReportStatus next) {
    this.status = next.name();
    updatedAt = Instant.now();
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

  public String getStatus() {
    return status;
  }

  public int getCurrentVersionNumber() {
    return currentVersionNumber;
  }
}
