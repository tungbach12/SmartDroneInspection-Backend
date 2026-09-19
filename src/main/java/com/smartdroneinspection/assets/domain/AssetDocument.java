package com.smartdroneinspection.assets.domain;

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
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Pattern;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "asset_documents",
    uniqueConstraints = {
      @UniqueConstraint(columnNames = "object_key"),
      @UniqueConstraint(columnNames = {"asset_id", "checksum_sha256"})
    })
public class AssetDocument {

  private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

  @Id @GeneratedValue private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "asset_id", nullable = false)
  private Asset asset;

  @Column(name = "uploaded_by_user_id", nullable = false)
  private UUID uploadedByUserId;

  @Column(name = "document_type", nullable = false, length = 32)
  private String documentType;

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

  @Column(name = "document_date")
  private LocalDate documentDate;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected AssetDocument() {}

  AssetDocument(
      Asset asset,
      UUID uploadedByUserId,
      String documentType,
      String fileName,
      String contentType,
      long sizeBytes,
      String checksumSha256,
      String objectKey,
      LocalDate documentDate) {
    if (sizeBytes <= 0) {
      throw new IllegalArgumentException("Document size must be positive");
    }
    if (checksumSha256 == null || !SHA_256.matcher(checksumSha256).matches()) {
      throw new IllegalArgumentException(
          "Checksum must be a 64-character lowercase hexadecimal SHA-256 value");
    }
    if (objectKey == null || objectKey.isBlank()) {
      throw new IllegalArgumentException("Object key must not be blank");
    }
    this.asset = asset;
    this.uploadedByUserId = uploadedByUserId;
    this.documentType = documentType;
    this.fileName = fileName;
    this.contentType = contentType;
    this.sizeBytes = sizeBytes;
    this.checksumSha256 = checksumSha256;
    this.objectKey = objectKey;
    this.documentDate = documentDate;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getChecksumSha256() {
    return checksumSha256;
  }
}
