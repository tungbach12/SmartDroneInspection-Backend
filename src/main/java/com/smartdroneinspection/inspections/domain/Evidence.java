package com.smartdroneinspection.inspections.domain;

import com.smartdroneinspection.inspections.domain.enums.EvidenceKind;
import com.smartdroneinspection.inspections.domain.enums.UploadStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Evidence metadata only; the file bytes live in object storage behind the shared object-store
 * port.
 */
@Entity
@Table(name = "evidence")
public class Evidence {

  @Id @GeneratedValue private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "inspection_id")
  private UUID inspectionId;

  @Column(name = "field_session_id")
  private UUID fieldSessionId;

  @Column(name = "maintenance_work_order_id")
  private UUID maintenanceWorkOrderId;

  @Column(name = "maintenance_task_id")
  private UUID maintenanceTaskId;

  /** Legacy MF4 work-log link retained by the shared evidence schema. */
  @Column(name = "maintenance_work_log_id", insertable = false, updatable = false)
  private UUID maintenanceWorkLogId;

  @Enumerated(EnumType.STRING)
  @Column(name = "evidence_kind", nullable = false, length = 32)
  private EvidenceKind evidenceKind;

  @Column(name = "file_name", nullable = false, length = 500)
  private String fileName;

  @Column(name = "content_type", nullable = false, length = 160)
  private String contentType;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  @Column(name = "object_key", nullable = false, length = 1000)
  private String objectKey;

  @Column(name = "capture_time")
  private Instant captureTime;

  @Column(precision = 9, scale = 6)
  private BigDecimal latitude;

  @Column(precision = 9, scale = 6)
  private BigDecimal longitude;

  @Enumerated(EnumType.STRING)
  @Column(name = "upload_status", nullable = false, length = 24)
  private UploadStatus uploadStatus;

  @Column(name = "retired_at")
  private Instant retiredAt;

  @Column(name = "location_description", length = 1000)
  private String locationDescription;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Evidence() {}

  public Evidence(
      UUID organizationId,
      UUID inspectionId,
      UUID fieldSessionId,
      EvidenceKind evidenceKind,
      String fileName,
      String contentType,
      long sizeBytes,
      String objectKey,
      Instant captureTime,
      BigDecimal latitude,
      BigDecimal longitude) {
    this.organizationId = organizationId;
    this.inspectionId = inspectionId;
    this.fieldSessionId = fieldSessionId;
    this.evidenceKind = evidenceKind;
    this.fileName = fileName;
    this.contentType = contentType;
    this.sizeBytes = sizeBytes;
    this.objectKey = objectKey;
    this.captureTime = captureTime;
    this.latitude = latitude;
    this.longitude = longitude;
    this.uploadStatus = UploadStatus.AVAILABLE;
    this.createdAt = Instant.now();
  }

  public boolean isImage() {
    return contentType != null && contentType.startsWith("image/");
  }

  /** Retiring keeps the original bytes while removing it from the active evidence set. */
  public void retire() {
    if (retiredAt != null) {
      throw new IllegalStateException("This capture is already retired");
    }
    this.retiredAt = Instant.now();
  }

  public boolean isRetired() {
    return retiredAt != null;
  }

  public Instant getRetiredAt() {
    return retiredAt;
  }

  public String getLocationDescription() {
    return locationDescription;
  }

  public void setLocationDescription(String locationDescription) {
    this.locationDescription =
        locationDescription == null || locationDescription.isBlank()
            ? null
            : locationDescription.trim();
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getInspectionId() {
    return inspectionId;
  }

  public UUID getFieldSessionId() {
    return fieldSessionId;
  }

  public EvidenceKind getEvidenceKind() {
    return evidenceKind;
  }

  public String getFileName() {
    return fileName;
  }

  public String getContentType() {
    return contentType;
  }

  public long getSizeBytes() {
    return sizeBytes;
  }

  public String getObjectKey() {
    return objectKey;
  }

  public Instant getCaptureTime() {
    return captureTime;
  }

  public BigDecimal getLatitude() {
    return latitude;
  }

  public BigDecimal getLongitude() {
    return longitude;
  }

  public UploadStatus getUploadStatus() {
    return uploadStatus;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
