package com.smartdroneinspection.assets.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "drone_registration_applications")
public class DroneRegistrationApplication {
  @Id @GeneratedValue private UUID id;

  @Column(name = "inspector_user_id", nullable = false)
  private UUID inspectorUserId;

  @Column(name = "serial_number", nullable = false, length = 128)
  private String serialNumber;

  @Column(length = 200)
  private String model;

  @Column(length = 200)
  private String manufacturer;

  @Column(name = "registration_number", nullable = false, length = 100)
  private String registrationNumber;

  @Column(name = "registration_file_key", nullable = false, length = 1000)
  private String registrationFileKey;

  @Column(name = "registration_expires_at", nullable = false)
  private Instant registrationExpiresAt;

  @Column(name = "ownership_file_key", nullable = false, length = 1000)
  private String ownershipFileKey;

  @Column(name = "inspection_file_key", nullable = false, length = 1000)
  private String inspectionFileKey;

  @Column(nullable = false, length = 24)
  private String status;

  @Column(name = "review_note", length = 2000)
  private String reviewNote;

  @Column(name = "reviewed_by_user_id")
  private UUID reviewedByUserId;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @Column(name = "drone_id")
  private UUID droneId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected DroneRegistrationApplication() {}

  public DroneRegistrationApplication(
      UUID inspectorId,
      String serial,
      String model,
      String manufacturer,
      String registrationNumber,
      String registrationFileKey,
      Instant registrationExpiresAt,
      String ownershipFileKey,
      String inspectionFileKey) {
    this.inspectorUserId = inspectorId;
    this.serialNumber = serial.trim();
    this.model = model;
    this.manufacturer = manufacturer;
    this.registrationNumber = registrationNumber.trim();
    this.registrationFileKey = registrationFileKey.trim();
    this.registrationExpiresAt = registrationExpiresAt;
    this.ownershipFileKey = ownershipFileKey.trim();
    this.inspectionFileKey = inspectionFileKey.trim();
    this.status = "PENDING";
    this.createdAt = Instant.now();
  }

  public void approve(UUID reviewerId, UUID approvedDroneId) {
    review("APPROVED", reviewerId, null);
    droneId = approvedDroneId;
  }

  public void reject(UUID reviewerId, String note) {
    review("REJECTED", reviewerId, note);
  }

  private void review(String next, UUID reviewerId, String note) {
    if (!"PENDING".equals(status))
      throw new IllegalStateException("Only pending applications may be reviewed.");
    status = next;
    reviewedByUserId = reviewerId;
    reviewedAt = Instant.now();
    reviewNote = note;
  }

  public UUID getId() {
    return id;
  }

  public UUID getInspectorUserId() {
    return inspectorUserId;
  }

  public String getSerialNumber() {
    return serialNumber;
  }

  public String getModel() {
    return model;
  }

  public String getManufacturer() {
    return manufacturer;
  }

  public String getRegistrationNumber() {
    return registrationNumber;
  }

  public String getRegistrationFileKey() {
    return registrationFileKey;
  }

  public Instant getRegistrationExpiresAt() {
    return registrationExpiresAt;
  }

  public String getOwnershipFileKey() {
    return ownershipFileKey;
  }

  public String getInspectionFileKey() {
    return inspectionFileKey;
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

  public UUID getDroneId() {
    return droneId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
