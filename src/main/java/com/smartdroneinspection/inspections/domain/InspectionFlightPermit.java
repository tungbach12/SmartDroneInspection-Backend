package com.smartdroneinspection.inspections.domain;

import com.smartdroneinspection.inspections.domain.enums.FlightPermitVerificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Flight permit artifact metadata for one {@link Inspection}. The uploaded permit is self-attested
 * by the assigned inspector, who is responsible for its validity.
 */
@Entity
@Table(name = "inspection_flight_permits")
public class InspectionFlightPermit {

  @Id
  @Column(name = "inspection_id")
  private UUID inspectionId;

  @MapsId
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "inspection_id")
  private Inspection inspection;

  @Column(name = "file_key", length = 1000)
  private String fileKey;

  @Column(name = "file_name", length = 500)
  private String fileName;

  @Column(name = "permit_number", length = 120)
  private String permitNumber;

  @Column(name = "authority", length = 200)
  private String authority;

  @Column(name = "valid_from")
  private Instant validFrom;

  @Column(name = "valid_to")
  private Instant validTo;

  @Column(name = "drone_serial", length = 120)
  private String droneSerial;

  @Column(name = "max_altitude_m")
  private Integer maxAltitudeM;

  @Column(name = "file_sha256", length = 64)
  private String fileSha256;

  @Column(name = "file_size_bytes")
  private Long fileSizeBytes;

  @Column(name = "content_type", length = 120)
  private String contentType;

  @Column(name = "uploaded_at")
  private Instant uploadedAt;

  @Column(name = "uploaded_by")
  private UUID uploadedBy;

  @Enumerated(EnumType.STRING)
  @Column(name = "verification_status", nullable = false, length = 32)
  private FlightPermitVerificationStatus verificationStatus =
      FlightPermitVerificationStatus.NOT_UPLOADED;

  @Column(name = "verified_by")
  private UUID verifiedBy;

  @Column(name = "verified_at")
  private Instant verifiedAt;

  @Column(name = "rejection_reason", length = 1000)
  private String rejectionReason;

  @Column(name = "review_notes", length = 1000)
  private String reviewNotes;

  protected InspectionFlightPermit() {}

  InspectionFlightPermit(Inspection inspection) {
    this.inspection = Objects.requireNonNull(inspection, "Inspection is required");
  }

  void record(
      String fileKey,
      String fileName,
      String permitNumber,
      String issuingAuthority,
      Instant validFrom,
      Instant validTo,
      String droneSerial,
      Integer maxAltitudeM,
      String fileSha256,
      Long fileSizeBytes,
      String contentType,
      Instant uploadedAt,
      UUID uploadedBy) {
    this.fileKey = fileKey.trim();
    this.fileName = fileName.trim();
    this.permitNumber = permitNumber == null || permitNumber.isBlank() ? null : permitNumber.trim();
    this.authority =
        issuingAuthority == null || issuingAuthority.isBlank() ? null : issuingAuthority.trim();
    this.validFrom = validFrom;
    this.validTo = validTo;
    this.droneSerial = droneSerial == null || droneSerial.isBlank() ? null : droneSerial.trim();
    this.maxAltitudeM = maxAltitudeM;
    this.fileSha256 = fileSha256 == null || fileSha256.isBlank() ? null : fileSha256.trim();
    this.fileSizeBytes = fileSizeBytes;
    this.contentType = contentType == null || contentType.isBlank() ? null : contentType.trim();
    this.uploadedAt = uploadedAt;
    this.uploadedBy = uploadedBy;
    this.verificationStatus = FlightPermitVerificationStatus.APPROVED;
    this.verifiedBy = uploadedBy;
    this.verifiedAt = uploadedAt;
    this.rejectionReason = null;
    this.reviewNotes = null;
  }

  void requireReupload() {
    this.verificationStatus = FlightPermitVerificationStatus.PENDING_REVIEW;
    this.verifiedBy = null;
    this.verifiedAt = null;
    this.rejectionReason = null;
    this.reviewNotes = null;
  }

  void shiftValidFrom(Instant shiftedStart) {
    this.validFrom = shiftedStart;
  }

  boolean hasFile() {
    return fileKey != null && !fileKey.isBlank();
  }

  boolean isApproved() {
    return hasFile() && verificationStatus == FlightPermitVerificationStatus.APPROVED;
  }

  public UUID getInspectionId() {
    return inspectionId;
  }

  public String getFileKey() {
    return fileKey;
  }

  public String getFileName() {
    return fileName;
  }

  public String getPermitNumber() {
    return permitNumber;
  }

  public String getAuthority() {
    return authority;
  }

  public Instant getValidFrom() {
    return validFrom;
  }

  public Instant getValidTo() {
    return validTo;
  }

  public String getDroneSerial() {
    return droneSerial;
  }

  public Integer getMaxAltitudeM() {
    return maxAltitudeM;
  }

  public String getFileSha256() {
    return fileSha256;
  }

  public Long getFileSizeBytes() {
    return fileSizeBytes;
  }

  public String getContentType() {
    return contentType;
  }

  public Instant getUploadedAt() {
    return uploadedAt;
  }

  public UUID getUploadedBy() {
    return uploadedBy;
  }

  public FlightPermitVerificationStatus getVerificationStatus() {
    return verificationStatus == null
        ? FlightPermitVerificationStatus.NOT_UPLOADED
        : verificationStatus;
  }

  public UUID getVerifiedBy() {
    return verifiedBy;
  }

  public Instant getVerifiedAt() {
    return verifiedAt;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  public String getReviewNotes() {
    return reviewNotes;
  }
}
