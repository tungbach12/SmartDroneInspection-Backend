package com.smartdroneinspection.inspections.domain;

import com.smartdroneinspection.inspections.domain.enums.EvidenceUploadSessionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Persistent state for a chunked, resumable inspection evidence upload. */
@Entity
@Table(name = "evidence_upload_sessions")
public class EvidenceUploadSession {

  @Id @GeneratedValue private UUID id;

  @Column(name = "inspection_id", nullable = false)
  private UUID inspectionId;

  @Column(name = "field_session_id")
  private UUID fieldSessionId;

  @Column(name = "created_by_user_id", nullable = false)
  private UUID createdByUserId;

  @Column(name = "file_name", nullable = false, length = 500)
  private String fileName;

  @Column(name = "content_type", nullable = false, length = 160)
  private String contentType;

  @Column(name = "declared_size_bytes", nullable = false)
  private long declaredSizeBytes;

  @Column(name = "chunk_size_bytes", nullable = false)
  private int chunkSizeBytes;

  @Column(name = "total_parts", nullable = false)
  private int totalParts;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private EvidenceUploadSessionStatus status;

  @Column(name = "storage_upload_id", length = 500)
  private String storageUploadId;

  @Column(name = "raw_object_key", nullable = false, length = 1000)
  private String rawObjectKey;

  @Column(name = "evidence_id")
  private UUID evidenceId;

  @Column(name = "capture_time")
  private Instant captureTime;

  @Column(precision = 9, scale = 6)
  private BigDecimal latitude;

  @Column(precision = 9, scale = 6)
  private BigDecimal longitude;

  @Column(name = "location_description", length = 1000)
  private String locationDescription;

  @Column(name = "uploaded_parts_json", nullable = false)
  private String uploadedPartsJson = "[]";

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected EvidenceUploadSession() {}

  public EvidenceUploadSession(
      UUID inspectionId,
      UUID fieldSessionId,
      UUID createdByUserId,
      String fileName,
      String contentType,
      long declaredSizeBytes,
      int chunkSizeBytes,
      int totalParts,
      String storageUploadId,
      String rawObjectKey,
      Instant captureTime,
      BigDecimal latitude,
      BigDecimal longitude,
      String locationDescription,
      Instant expiresAt,
      Instant now) {
    this.inspectionId = inspectionId;
    this.fieldSessionId = fieldSessionId;
    this.createdByUserId = createdByUserId;
    this.fileName = fileName;
    this.contentType = contentType;
    this.declaredSizeBytes = declaredSizeBytes;
    this.chunkSizeBytes = chunkSizeBytes;
    this.totalParts = totalParts;
    this.status = EvidenceUploadSessionStatus.OPEN;
    this.storageUploadId = storageUploadId;
    this.rawObjectKey = rawObjectKey;
    this.captureTime = captureTime;
    this.latitude = latitude;
    this.longitude = longitude;
    this.locationDescription = locationDescription;
    this.uploadedPartsJson = "[]";
    this.expiresAt = expiresAt;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public long expectedPartSize(int partNumber) {
    if (partNumber < 1 || partNumber > totalParts) {
      throw new IllegalArgumentException("Invalid part number: " + partNumber);
    }
    if (partNumber < totalParts) {
      return chunkSizeBytes;
    }
    return declaredSizeBytes - ((long) (totalParts - 1) * chunkSizeBytes);
  }

  public boolean isExpired(Instant now) {
    return status == EvidenceUploadSessionStatus.OPEN && now.isAfter(expiresAt);
  }

  public void updateUploadedPartsJson(String uploadedPartsJson, Instant now) {
    this.uploadedPartsJson = uploadedPartsJson == null ? "[]" : uploadedPartsJson;
    this.updatedAt = now;
  }

  public void markCompleting(Instant now) {
    this.status = EvidenceUploadSessionStatus.COMPLETING;
    this.updatedAt = now;
  }

  public void markProcessing(UUID evidenceId, Instant now) {
    this.status = EvidenceUploadSessionStatus.PROCESSING;
    this.evidenceId = evidenceId;
    this.errorMessage = null;
    this.completedAt = now;
    this.updatedAt = now;
  }

  public void markReady(Instant now) {
    this.status = EvidenceUploadSessionStatus.READY;
    this.errorMessage = null;
    this.updatedAt = now;
  }

  public void markFailed(String errorMessage, Instant now) {
    this.status = EvidenceUploadSessionStatus.FAILED;
    this.errorMessage = errorMessage;
    this.updatedAt = now;
  }

  public void markCancelled(Instant now) {
    this.status = EvidenceUploadSessionStatus.CANCELLED;
    this.updatedAt = now;
  }

  public void markExpired(Instant now) {
    this.status = EvidenceUploadSessionStatus.EXPIRED;
    this.updatedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public UUID getInspectionId() {
    return inspectionId;
  }

  public UUID getFieldSessionId() {
    return fieldSessionId;
  }

  public UUID getCreatedByUserId() {
    return createdByUserId;
  }

  public String getFileName() {
    return fileName;
  }

  public String getContentType() {
    return contentType;
  }

  public long getDeclaredSizeBytes() {
    return declaredSizeBytes;
  }

  public int getChunkSizeBytes() {
    return chunkSizeBytes;
  }

  public int getTotalParts() {
    return totalParts;
  }

  public EvidenceUploadSessionStatus getStatus() {
    return status;
  }

  public String getStorageUploadId() {
    return storageUploadId;
  }

  public String getRawObjectKey() {
    return rawObjectKey;
  }

  public UUID getEvidenceId() {
    return evidenceId;
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

  public String getLocationDescription() {
    return locationDescription;
  }

  public String getUploadedPartsJson() {
    return uploadedPartsJson;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }
}
