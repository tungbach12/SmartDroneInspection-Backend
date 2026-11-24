package com.smartdroneinspection.contracts.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "service_request_partner_message_attachments")
public class ServiceRequestPartnerMessageAttachment {

  @Id @GeneratedValue private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "message_id", nullable = false)
  private ServiceRequestPartnerMessage message;

  @Column(name = "file_name", nullable = false, length = 255)
  private String fileName;

  @Column(name = "content_type", nullable = false, length = 120)
  private String contentType;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  @Column(name = "checksum_sha256", nullable = false, length = 64)
  private String checksumSha256;

  @Column(name = "object_key", nullable = false, length = 512)
  private String objectKey;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  protected ServiceRequestPartnerMessageAttachment() {}

  public ServiceRequestPartnerMessageAttachment(
      ServiceRequestPartnerMessage message,
      String fileName,
      String contentType,
      long sizeBytes,
      String checksumSha256,
      String objectKey) {
    if (message == null
        || fileName == null
        || fileName.isBlank()
        || contentType == null
        || contentType.isBlank()
        || sizeBytes <= 0
        || checksumSha256 == null
        || !checksumSha256.matches("[0-9a-f]{64}")
        || objectKey == null
        || objectKey.isBlank()) {
      throw new IllegalArgumentException("Attachment metadata is invalid");
    }
    this.message = message;
    this.fileName = fileName;
    this.contentType = contentType;
    this.sizeBytes = sizeBytes;
    this.checksumSha256 = checksumSha256;
    this.objectKey = objectKey;
  }

  public UUID getId() {
    return id;
  }

  public ServiceRequestPartnerMessage getMessage() {
    return message;
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

  public Instant getCreatedAt() {
    return createdAt;
  }
}
