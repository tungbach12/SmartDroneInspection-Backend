package com.smartdroneinspection.assets.domain;

import com.smartdroneinspection.assets.domain.enums.DroneDocumentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Persistence mapping for a document attached to an organization-owned Drone. */
@Entity
@Table(name = "drone_documents")
public class DroneDocument {

  @Id @GeneratedValue private UUID id;

  @Column(name = "drone_id", nullable = false)
  private UUID droneId;

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

  @Column(name = "object_key", nullable = false, length = 1000)
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

  public UUID getId() {
    return id;
  }

  public UUID getDroneId() {
    return droneId;
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
}
