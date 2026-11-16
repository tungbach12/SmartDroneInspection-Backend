package com.smartdroneinspection.inspections.domain;

import com.smartdroneinspection.inspections.domain.enums.EvidenceProcessingStatus;
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

  @Column(name = "inspection_id", nullable = false)
  private UUID inspectionId;

  @Column(name = "field_session_id")
  private UUID fieldSessionId;

  @Column(name = "file_name", nullable = false, length = 500)
  private String fileName;

  @Column(name = "content_type", nullable = false, length = 160)
  private String contentType;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  @Column(name = "original_size_bytes")
  private Long originalSizeBytes;

  @Column(name = "object_key", nullable = false, length = 1000)
  private String objectKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "processing_status", nullable = false, length = 32)
  private EvidenceProcessingStatus processingStatus = EvidenceProcessingStatus.READY;

  @Column(name = "processing_error")
  private String processingError;

  @Column(name = "raw_object_key", length = 1000)
  private String rawObjectKey;

  @Column(name = "thumbnail_object_key", length = 1000)
  private String thumbnailObjectKey;

  @Column(name = "thumbnail_size_bytes")
  private Long thumbnailSizeBytes;

  @Column(name = "media_width")
  private Integer mediaWidth;

  @Column(name = "media_height")
  private Integer mediaHeight;

  @Column(name = "duration_seconds")
  private Double durationSeconds;

  @Column(name = "processed_at")
  private Instant processedAt;

  @Column(name = "processing_attempts", nullable = false)
  private int processingAttempts;

  @Column(name = "processing_locked_at")
  private Instant processingLockedAt;

  @Column(name = "capture_time")
  private Instant captureTime;

  @Column(precision = 9, scale = 6)
  private BigDecimal latitude;

  @Column(precision = 9, scale = 6)
  private BigDecimal longitude;

  @Column(name = "location_description", length = 1000)
  private String locationDescription;

  @Column(name = "source_video_evidence_id")
  private UUID sourceVideoEvidenceId;

  @Column(name = "source_timestamp_seconds")
  private Double sourceTimestampSeconds;

  @Column(name = "is_deleted", nullable = false)
  private boolean isDeleted;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Evidence() {}

  public Evidence(
      UUID inspectionId,
      UUID fieldSessionId,
      String fileName,
      String contentType,
      long sizeBytes,
      String objectKey,
      Instant captureTime,
      BigDecimal latitude,
      BigDecimal longitude,
      String locationDescription,
      Instant createdAt) {
    this.inspectionId = inspectionId;
    this.fieldSessionId = fieldSessionId;
    this.fileName = fileName;
    this.contentType = contentType;
    this.sizeBytes = sizeBytes;
    this.originalSizeBytes = sizeBytes;
    this.objectKey = objectKey;
    this.processingStatus = EvidenceProcessingStatus.READY;
    this.processingAttempts = 0;
    this.captureTime = captureTime;
    this.latitude = latitude;
    this.longitude = longitude;
    this.locationDescription = locationDescription;
    this.isDeleted = false;
    this.createdAt = createdAt != null ? createdAt : Instant.now();
  }

  public boolean isImage() {
    return contentType != null && contentType.startsWith("image/");
  }

  public boolean isVideo() {
    return contentType != null && contentType.startsWith("video/");
  }

  public boolean isReady() {
    return processingStatus == null || processingStatus == EvidenceProcessingStatus.READY;
  }

  public void applyImageOptimization(
      String normalizedObjectKey,
      String normalizedContentType,
      long normalizedSizeBytes,
      long originalSizeBytes,
      String thumbnailObjectKey,
      Long thumbnailSizeBytes,
      Integer mediaWidth,
      Integer mediaHeight,
      Instant processedAt) {
    this.objectKey = normalizedObjectKey;
    this.contentType = normalizedContentType;
    this.sizeBytes = normalizedSizeBytes;
    this.originalSizeBytes = originalSizeBytes;
    this.thumbnailObjectKey = thumbnailObjectKey;
    this.thumbnailSizeBytes = thumbnailSizeBytes;
    this.mediaWidth = mediaWidth;
    this.mediaHeight = mediaHeight;
    this.processingStatus = EvidenceProcessingStatus.READY;
    this.processingError = null;
    this.processedAt = processedAt != null ? processedAt : Instant.now();
  }

  public void markVideoPending(String rawObjectKey, long originalSizeBytes) {
    this.rawObjectKey = rawObjectKey;
    this.originalSizeBytes = originalSizeBytes;
    this.processingStatus = EvidenceProcessingStatus.PENDING;
    this.processingError = null;
    this.processingLockedAt = null;
  }

  public void markVideoReady(
      String processedObjectKey,
      long processedSizeBytes,
      String thumbnailObjectKey,
      long thumbnailSizeBytes,
      int mediaWidth,
      int mediaHeight,
      double durationSeconds,
      Instant processedAt) {
    this.objectKey = processedObjectKey;
    this.contentType = "video/mp4";
    this.sizeBytes = processedSizeBytes;
    this.thumbnailObjectKey = thumbnailObjectKey;
    this.thumbnailSizeBytes = thumbnailSizeBytes;
    this.mediaWidth = mediaWidth;
    this.mediaHeight = mediaHeight;
    this.durationSeconds = durationSeconds;
    this.processingStatus = EvidenceProcessingStatus.READY;
    this.processingError = null;
    this.processingLockedAt = null;
    this.processedAt = processedAt != null ? processedAt : Instant.now();
  }

  public void clearRawObjectKey() {
    this.rawObjectKey = null;
  }

  public void markProcessingFailed(String error) {
    this.processingStatus = EvidenceProcessingStatus.FAILED;
    this.processingError =
        error == null || error.isBlank() ? "Media processing failed" : error.trim();
    this.processingLockedAt = null;
  }

  public void requestRetry() {
    if (this.rawObjectKey == null || this.rawObjectKey.isBlank()) {
      throw new IllegalStateException("Raw media source is no longer available for retry");
    }
    this.processingStatus = EvidenceProcessingStatus.PENDING;
    this.processingError = null;
    this.processingLockedAt = null;
  }

  /** Retiring keeps the original bytes while marking it deleted from the active set. */
  public void retire() {
    if (this.isDeleted) {
      throw new IllegalStateException("This capture is already retired");
    }
    this.isDeleted = true;
  }

  public boolean isDeleted() {
    return isDeleted;
  }

  public boolean isRetired() {
    return isDeleted;
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

  public UUID getInspectionId() {
    return inspectionId;
  }

  public UUID getFieldSessionId() {
    return fieldSessionId;
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

  public Long getOriginalSizeBytes() {
    return originalSizeBytes;
  }

  public String getObjectKey() {
    return objectKey;
  }

  public EvidenceProcessingStatus getProcessingStatus() {
    return processingStatus;
  }

  public String getProcessingError() {
    return processingError;
  }

  public String getRawObjectKey() {
    return rawObjectKey;
  }

  public String getThumbnailObjectKey() {
    return thumbnailObjectKey;
  }

  public Long getThumbnailSizeBytes() {
    return thumbnailSizeBytes;
  }

  public Integer getMediaWidth() {
    return mediaWidth;
  }

  public Integer getMediaHeight() {
    return mediaHeight;
  }

  public Double getDurationSeconds() {
    return durationSeconds;
  }

  public Instant getProcessedAt() {
    return processedAt;
  }

  public int getProcessingAttempts() {
    return processingAttempts;
  }

  public Instant getProcessingLockedAt() {
    return processingLockedAt;
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

  public void linkVideoSource(UUID sourceVideoEvidenceId, Double sourceTimestampSeconds) {
    this.sourceVideoEvidenceId = sourceVideoEvidenceId;
    this.sourceTimestampSeconds = sourceTimestampSeconds;
  }

  public UUID getSourceVideoEvidenceId() {
    return sourceVideoEvidenceId;
  }

  public Double getSourceTimestampSeconds() {
    return sourceTimestampSeconds;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
