package com.smartdroneinspection.assets.domain;

import com.smartdroneinspection.assets.domain.enums.DroneDocumentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Tenant-owned Drone document metadata; the object key points to immutable stored bytes. */
@Entity
@Table(name = "drone_documents")
public class DroneDocument {

  private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

  @Id @GeneratedValue private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "drone_id", nullable = false)
  private Drone drone;

  @Column(name = "document_type", nullable = false, length = 48)
  private String documentType;

  @Column(length = 200)
  private String issuer;

  @Column(name = "document_reference", length = 200)
  private String documentReference;

  @Column(name = "valid_from")
  private Instant validFrom;

  @Column(name = "valid_until")
  private Instant validUntil;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private DroneDocumentStatus status;

  @Column(name = "object_key", nullable = false, unique = true, length = 1000)
  private String objectKey;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "checksum_sha256", nullable = false, length = 64)
  private String checksumSha256;

  @Column(name = "uploaded_by_user_id", nullable = false)
  private UUID uploadedByUserId;

  @Column(name = "reviewed_by_user_id")
  private UUID reviewedByUserId;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected DroneDocument() {}

  public DroneDocument(
      Drone drone,
      String documentType,
      String issuer,
      String documentReference,
      Instant validFrom,
      Instant validUntil,
      DroneDocumentStatus status,
      String objectKey,
      String checksumSha256,
      UUID uploadedByUserId) {
    this(
        drone,
        documentType,
        issuer,
        documentReference,
        validFrom,
        validUntil,
        status,
        objectKey,
        checksumSha256,
        uploadedByUserId,
        null,
        null);
  }

  public DroneDocument(
      Drone drone,
      String documentType,
      String issuer,
      String documentReference,
      Instant validFrom,
      Instant validUntil,
      DroneDocumentStatus status,
      String objectKey,
      String checksumSha256,
      UUID uploadedByUserId,
      UUID reviewedByUserId,
      Instant reviewedAt) {
    if (validFrom != null && validUntil != null && !validUntil.isAfter(validFrom)) {
      throw new IllegalArgumentException("Document validity must end after it starts");
    }
    if (checksumSha256 == null || !SHA_256.matcher(checksumSha256).matches()) {
      throw new IllegalArgumentException(
          "Checksum must be a 64-character lowercase hexadecimal SHA-256 value");
    }
    if (objectKey == null || objectKey.isBlank()) {
      throw new IllegalArgumentException("Object key must not be blank");
    }
    this.drone = drone;
    this.documentType = documentType;
    this.issuer = issuer;
    this.documentReference = documentReference;
    this.validFrom = validFrom;
    this.validUntil = validUntil;
    this.status = status;
    this.objectKey = objectKey;
    this.checksumSha256 = checksumSha256;
    this.uploadedByUserId = uploadedByUserId;
    this.reviewedByUserId = reviewedByUserId;
    this.reviewedAt = reviewedAt;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public Drone getDrone() {
    return drone;
  }

  /** ID projection retained for existing readiness API consumers. */
  public UUID getDroneId() {
    return drone.getId();
  }

  public String getDocumentType() {
    return documentType;
  }

  public String getIssuer() {
    return issuer;
  }

  public String getDocumentReference() {
    return documentReference;
  }

  public Instant getValidFrom() {
    return validFrom;
  }

  public Instant getValidUntil() {
    return validUntil;
  }

  public DroneDocumentStatus getStatus() {
    return status;
  }

  public String getObjectKey() {
    return objectKey;
  }

  public String getChecksumSha256() {
    return checksumSha256;
  }

  public UUID getUploadedByUserId() {
    return uploadedByUserId;
  }

  public UUID getReviewedByUserId() {
    return reviewedByUserId;
  }

  public Instant getReviewedAt() {
    return reviewedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public boolean isExpiredAt(Instant at) {
    return status == DroneDocumentStatus.ACTIVE && validUntil != null && validUntil.isBefore(at);
  }

  public DroneDocumentStatus effectiveStatusAt(Instant at) {
    return isExpiredAt(at) ? DroneDocumentStatus.EXPIRED : status;
  }

  public void changeStatus(DroneDocumentStatus target, UUID reviewerId, Instant reviewedAt) {
    if (status == DroneDocumentStatus.REVOKED) {
      throw new IllegalStateException("Revoked documents cannot change status");
    }
    this.status = target;
    this.reviewedByUserId = reviewerId;
    this.reviewedAt = reviewedAt;
  }
}
