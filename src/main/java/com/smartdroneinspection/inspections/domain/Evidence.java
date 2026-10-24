package com.smartdroneinspection.inspections.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

  @Column(name = "object_key", nullable = false, length = 1000)
  private String objectKey;

  @Column(name = "capture_time")
  private Instant captureTime;

  @Column(precision = 9, scale = 6)
  private BigDecimal latitude;

  @Column(precision = 9, scale = 6)
  private BigDecimal longitude;

  @Column(name = "location_description", length = 1000)
  private String locationDescription;

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
    this.objectKey = objectKey;
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

  public Instant getCreatedAt() {
    return createdAt;
  }
}
