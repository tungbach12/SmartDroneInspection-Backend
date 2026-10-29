package com.smartdroneinspection.users.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "inspector_partner_applications")
public class InspectorPartnerApplication {
  @Id @GeneratedValue private UUID id;

  @Column(nullable = false, length = 320)
  private String email;

  @Column(name = "normalized_email", nullable = false, length = 320)
  private String normalizedEmail;

  @Column(name = "full_name", nullable = false, length = 200)
  private String fullName;

  @Column(length = 64)
  private String phone;

  @Column(name = "pilot_license_number", nullable = false, length = 128)
  private String pilotLicenseNumber;

  @Column(name = "pilot_license_class", nullable = false, length = 64)
  private String pilotLicenseClass;

  @Column(name = "pilot_license_expires_at", nullable = false)
  private LocalDate pilotLicenseExpiresAt;

  @Column(name = "pilot_license_file_key", nullable = false, length = 1000)
  private String pilotLicenseFileKey;

  @Column(name = "partner_contract_file_key", length = 1000)
  private String partnerContractFileKey;

  @Column(nullable = false, length = 24)
  private String status;

  @Column(name = "review_note", length = 2000)
  private String reviewNote;

  @Column(name = "reviewed_by_user_id")
  private UUID reviewedByUserId;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected InspectorPartnerApplication() {}

  public InspectorPartnerApplication(
      String email,
      String fullName,
      String phone,
      String licenseNumber,
      String licenseClass,
      LocalDate licenseExpiry,
      String licenseFileKey) {
    this.email = email.trim();
    this.normalizedEmail = User.normalizeEmail(email);
    this.fullName = fullName.trim();
    this.phone = phone == null ? null : phone.trim();
    this.pilotLicenseNumber = licenseNumber.trim();
    this.pilotLicenseClass = licenseClass.trim();
    this.pilotLicenseExpiresAt = licenseExpiry;
    this.pilotLicenseFileKey = licenseFileKey.trim();
    this.status = "PENDING";
    this.createdAt = Instant.now();
  }

  public void approve(UUID reviewer, String contractFileKey, String note) {
    review("APPROVED", reviewer, note);
    partnerContractFileKey = contractFileKey.trim();
  }

  public void reject(UUID reviewer, String note) {
    review("REJECTED", reviewer, note);
  }

  private void review(String next, UUID reviewer, String note) {
    if (!"PENDING".equals(status))
      throw new IllegalStateException("Only pending applications may be reviewed.");
    status = next;
    reviewedByUserId = reviewer;
    reviewedAt = Instant.now();
    reviewNote = note;
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getNormalizedEmail() {
    return normalizedEmail;
  }

  public String getFullName() {
    return fullName;
  }

  public String getPhone() {
    return phone;
  }

  public String getPilotLicenseNumber() {
    return pilotLicenseNumber;
  }

  public String getPilotLicenseClass() {
    return pilotLicenseClass;
  }

  public LocalDate getPilotLicenseExpiresAt() {
    return pilotLicenseExpiresAt;
  }

  public String getPilotLicenseFileKey() {
    return pilotLicenseFileKey;
  }

  public String getPartnerContractFileKey() {
    return partnerContractFileKey;
  }

  public String getStatus() {
    return status;
  }

  public String getReviewNote() {
    return reviewNote;
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
