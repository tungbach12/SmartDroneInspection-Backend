package com.smartdroneinspection.users.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "inspector_applications")
public class InspectorApplication {

  @Id @GeneratedValue private UUID id;

  @Column(name = "normalized_email", nullable = false, length = 320)
  private String normalizedEmail;

  @Column(name = "email", nullable = false, length = 320)
  private String email;

  @Column(name = "full_name", nullable = false, length = 200)
  private String fullName;

  @Column(name = "phone_number", length = 32)
  private String phoneNumber;

  @Column(name = "citizen_id", length = 32)
  private String citizenId;

  @Column(length = 500)
  private String address;

  @Column(name = "pilot_license_number", length = 128)
  private String pilotLicenseNumber;

  @Column(name = "pilot_license_class", nullable = false, length = 64)
  private String pilotLicenseClass;

  @Column(name = "pilot_license_expires_at")
  private Instant pilotLicenseExpiresAt;

  @Column(name = "license_scan_file_key", length = 1000)
  private String licenseScanFileKey;

  @Column(name = "password_hash", nullable = false, length = 1024)
  private String passwordHash;

  @Column(nullable = false, length = 32)
  private String status = "PENDING_REVIEW";

  @Column(name = "reviewed_by_user_id")
  private UUID reviewedByUserId;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @Column(name = "rejection_reason", length = 1000)
  private String rejectionReason;

  @Column(name = "notification_state", nullable = false, length = 32)
  private String notificationState = "PENDING";

  @Column(name = "notification_attempts", nullable = false)
  private int notificationAttempts;

  @Column(name = "cleanup_attempts", nullable = false)
  private int cleanupAttempts;

  @Column(name = "last_notification_error", length = 500)
  private String lastNotificationError;

  @Column(name = "correlation_id", length = 128)
  private String correlationId;

  @Column(name = "approved_user_id")
  private UUID approvedUserId;

  @Column(name = "setup_email_sent_at")
  private Instant setupEmailSentAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected InspectorApplication() {}

  public InspectorApplication(
      String email,
      String normalizedEmail,
      String fullName,
      String phoneNumber,
      String citizenId,
      String address,
      String pilotLicenseNumber,
      String pilotLicenseClass,
      Instant pilotLicenseExpiresAt,
      String licenseScanFileKey,
      String correlationId,
      String passwordHash) {
    this.email = email;
    this.normalizedEmail = normalizedEmail;
    this.fullName = fullName;
    this.phoneNumber = phoneNumber;
    this.citizenId = citizenId;
    this.address = address;
    this.pilotLicenseNumber = pilotLicenseNumber;
    this.pilotLicenseClass = pilotLicenseClass;
    this.pilotLicenseExpiresAt = pilotLicenseExpiresAt;
    this.licenseScanFileKey = licenseScanFileKey;
    this.correlationId = correlationId;
    this.passwordHash = passwordHash;
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

  public String getPhoneNumber() {
    return phoneNumber;
  }

  public String getCitizenId() {
    return citizenId;
  }

  public String getAddress() {
    return address;
  }

  public String getPilotLicenseNumber() {
    return pilotLicenseNumber;
  }

  public String getPilotLicenseClass() {
    return pilotLicenseClass;
  }

  public Instant getPilotLicenseExpiresAt() {
    return pilotLicenseExpiresAt;
  }

  public String getLicenseScanFileKey() {
    return licenseScanFileKey;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getStatus() {
    return status;
  }

  public UUID getReviewedByUserId() {
    return reviewedByUserId;
  }

  public Instant getReviewedAt() {
    return reviewedAt;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  public String getNotificationState() {
    return notificationState;
  }

  public int getNotificationAttempts() {
    return notificationAttempts;
  }

  public int getCleanupAttempts() {
    return cleanupAttempts;
  }

  public String getLastNotificationError() {
    return lastNotificationError;
  }

  public String getCorrelationId() {
    return correlationId;
  }

  public UUID getApprovedUserId() {
    return approvedUserId;
  }

  public Instant getSetupEmailSentAt() {
    return setupEmailSentAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void approve(UUID reviewerId, UUID userId) {
    this.status = "APPROVED";
    this.approvedUserId = userId;
    this.reviewedByUserId = reviewerId;
    this.reviewedAt = Instant.now();
    this.rejectionReason = null;
    this.notificationState = "PENDING";
  }

  public void reject(UUID reviewerId, String reason) {
    this.status = "REJECTED";
    this.reviewedByUserId = reviewerId;
    this.reviewedAt = Instant.now();
    this.rejectionReason = reason;
    this.notificationState = "PENDING";
  }

  public void recordCleanupAttempt(String failure) {
    this.cleanupAttempts++;
    this.lastNotificationError =
        failure == null ? null : failure.substring(0, Math.min(500, failure.length()));
  }

  public void recordNotificationAttempt(String failure) {
    this.notificationAttempts++;
    this.lastNotificationError =
        failure == null ? null : failure.substring(0, Math.min(500, failure.length()));
  }

  public void markNotificationSent() {
    this.notificationState = "SENT";
    this.lastNotificationError = null;
  }

  public void clearLicenseScanFileKey() {
    this.licenseScanFileKey = null;
  }

  public void markSetupEmailSent() {
    this.setupEmailSentAt = Instant.now();
    this.notificationState = "SENT";
    this.lastNotificationError = null;
  }

  public boolean isSetupEmailSent() {
    return setupEmailSentAt != null;
  }

  public void clearRejectedPersonalData() {
    this.email = "deleted+" + id + "@invalid.local";
    this.normalizedEmail = "deleted+" + id + "@invalid.local";
    this.fullName = "Deleted applicant";
    this.phoneNumber = null;
    this.citizenId = null;
    this.address = null;
    this.pilotLicenseNumber = null;
    this.pilotLicenseClass = "WITHDRAWN";
    this.pilotLicenseExpiresAt = null;
    this.licenseScanFileKey = null;
    this.passwordHash = "WITHDRAWN";
    this.rejectionReason = null;
    this.lastNotificationError = null;
  }
}
