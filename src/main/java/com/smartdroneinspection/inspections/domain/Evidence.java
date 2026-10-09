package com.smartdroneinspection.inspections.domain;

import com.smartdroneinspection.inspections.domain.enums.EvidenceKind;
import com.smartdroneinspection.inspections.domain.enums.EvidenceSource;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Evidence metadata only; the file bytes live in object storage behind the shared object-store
 * port. The checksum is computed server-side and a repeated inspection plus checksum pair is
 * idempotent.
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

  @Column(name = "uploaded_by_user_id", nullable = false)
  private UUID uploadedByUserId;

  @Enumerated(EnumType.STRING)
  @Column(name = "evidence_kind", nullable = false, length = 32)
  private EvidenceKind evidenceKind;

  @Column(name = "file_name", nullable = false, length = 500)
  private String fileName;

  @Column(name = "content_type", nullable = false, length = 160)
  private String contentType;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "checksum_sha256", nullable = false, length = 64)
  private String checksumSha256;

  @Column(name = "object_key", nullable = false, length = 1000)
  private String objectKey;

  @Column(name = "capture_time")
  private Instant captureTime;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private EvidenceSource source;

  @Column(precision = 9, scale = 6)
  private BigDecimal latitude;

  @Column(precision = 9, scale = 6)
  private BigDecimal longitude;

  @Column(name = "external_reference", length = 200)
  private String externalReference;

  @Enumerated(EnumType.STRING)
  @Column(name = "upload_status", nullable = false, length = 24)
  private UploadStatus uploadStatus;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "capture_metadata", columnDefinition = "jsonb")
  private String captureMetadata;

  @Column(name = "immutable_original", nullable = false)
  private boolean immutableOriginal;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Evidence() {}

  public Evidence(
      UUID organizationId,
      UUID inspectionId,
      UUID fieldSessionId,
      UUID uploadedByUserId,
      EvidenceKind evidenceKind,
      String fileName,
      String contentType,
      long sizeBytes,
      String checksumSha256,
      String objectKey,
      Instant captureTime,
      EvidenceSource source,
      BigDecimal latitude,
      BigDecimal longitude,
      String externalReference,
      String captureMetadata) {
    this.organizationId = organizationId;
    this.inspectionId = inspectionId;
    this.fieldSessionId = fieldSessionId;
    this.uploadedByUserId = uploadedByUserId;
    this.evidenceKind = evidenceKind;
    this.fileName = fileName;
    this.contentType = contentType;
    this.sizeBytes = sizeBytes;
    this.checksumSha256 = checksumSha256;
    this.objectKey = objectKey;
    this.captureTime = captureTime;
    this.source = source;
    this.latitude = latitude;
    this.longitude = longitude;
    this.externalReference = externalReference;
    this.captureMetadata = captureMetadata;
    this.immutableOriginal = true;
    this.uploadStatus = UploadStatus.AVAILABLE;
    this.createdAt = Instant.now();
  }

  public boolean isImage() {
    return contentType != null && contentType.startsWith("image/");
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

  public UUID getUploadedByUserId() {
    return uploadedByUserId;
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

  public String getChecksumSha256() {
    return checksumSha256;
  }

  public String getObjectKey() {
    return objectKey;
  }

  public Instant getCaptureTime() {
    return captureTime;
  }

  public EvidenceSource getSource() {
    return source;
  }

  public BigDecimal getLatitude() {
    return latitude;
  }

  public BigDecimal getLongitude() {
    return longitude;
  }

  public String getExternalReference() {
    return externalReference;
  }

  public String getCaptureMetadata() {
    return captureMetadata;
  }

  public UploadStatus getUploadStatus() {
    return uploadStatus;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
