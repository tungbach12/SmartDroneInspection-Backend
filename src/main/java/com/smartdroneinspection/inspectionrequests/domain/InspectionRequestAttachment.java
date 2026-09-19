package com.smartdroneinspection.inspectionrequests.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "inspection_request_attachments",
    uniqueConstraints = {
      @UniqueConstraint(columnNames = "object_key"),
      @UniqueConstraint(columnNames = {"inspection_request_id", "checksum_sha256"})
    })
public class InspectionRequestAttachment {

  private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

  @Id @GeneratedValue private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "inspection_request_id", nullable = false)
  private InspectionRequest inspectionRequest;

  @Column(name = "uploaded_by_user_id", nullable = false)
  private UUID uploadedByUserId;

  @Column(name = "file_name", nullable = false, length = 500)
  private String fileName;

  @Column(name = "content_type", nullable = false, length = 160)
  private String contentType;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "checksum_sha256", nullable = false, length = 64)
  private String checksumSha256;

  @Column(name = "object_key", nullable = false, unique = true, length = 1000)
  private String objectKey;

  @Column(length = 2000)
  private String description;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected InspectionRequestAttachment() {}

  InspectionRequestAttachment(
      InspectionRequest inspectionRequest,
      UUID uploadedByUserId,
      String fileName,
      String contentType,
      long sizeBytes,
      String checksumSha256,
      String objectKey,
      String description) {
    if (uploadedByUserId == null) {
      throw new IllegalArgumentException("Uploader is required");
    }
    if (fileName == null || fileName.isBlank()) {
      throw new IllegalArgumentException("File name must not be blank");
    }
    if (contentType == null || contentType.isBlank()) {
      throw new IllegalArgumentException("Content type must not be blank");
    }
    if (sizeBytes <= 0) {
      throw new IllegalArgumentException("Attachment size must be positive");
    }
    if (checksumSha256 == null || !SHA_256.matcher(checksumSha256).matches()) {
      throw new IllegalArgumentException(
          "Checksum must be a 64-character lowercase hexadecimal SHA-256 value");
    }
    if (objectKey == null || objectKey.isBlank()) {
      throw new IllegalArgumentException("Object key must not be blank");
    }
    this.inspectionRequest = inspectionRequest;
    this.uploadedByUserId = uploadedByUserId;
    this.fileName = fileName;
    this.contentType = contentType;
    this.sizeBytes = sizeBytes;
    this.checksumSha256 = checksumSha256;
    this.objectKey = objectKey;
    this.description = description;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getChecksumSha256() {
    return checksumSha256;
  }
}
