package com.smartdroneinspection.users.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "prospective_customer_registrations")
public class ProspectiveCustomerRegistration {

  @Id @GeneratedValue private UUID id;

  @Column(name = "user_id", nullable = false, unique = true)
  private UUID userId;

  @Column(name = "organization_name", nullable = false, length = 200)
  private String organizationName;

  @Column(name = "organization_code", nullable = false, length = 64)
  private String organizationCode;

  @Column(name = "asset_category_id", nullable = false)
  private UUID assetCategoryId;

  @Column(name = "asset_code", nullable = false, length = 64)
  private String assetCode;

  @Column(name = "asset_name", nullable = false, length = 200)
  private String assetName;

  @Column(name = "asset_description", length = 2000)
  private String assetDescription;

  @Column(name = "asset_location", nullable = false, length = 500)
  private String assetLocation;

  @Column(nullable = false, precision = 9, scale = 6)
  private BigDecimal latitude;

  @Column(nullable = false, precision = 9, scale = 6)
  private BigDecimal longitude;

  @Column(name = "ownership_information", length = 1000)
  private String ownershipInformation;

  @Column(nullable = false, length = 32)
  private String status;

  @Column(name = "airspace_classification", nullable = false, length = 32)
  private String airspaceClassification;

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

  protected ProspectiveCustomerRegistration() {}

  public ProspectiveCustomerRegistration(
      UUID userId,
      String organizationName,
      String organizationCode,
      UUID assetCategoryId,
      String assetCode,
      String assetName,
      String assetDescription,
      String assetLocation,
      BigDecimal latitude,
      BigDecimal longitude,
      String ownershipInformation,
      String airspaceClassification) {
    this.userId = userId;
    this.organizationName = organizationName;
    this.organizationCode = organizationCode;
    this.assetCategoryId = assetCategoryId;
    this.assetCode = assetCode;
    this.assetName = assetName;
    this.assetDescription = assetDescription;
    this.assetLocation = assetLocation;
    this.latitude = latitude;
    this.longitude = longitude;
    this.ownershipInformation = ownershipInformation;
    this.airspaceClassification = airspaceClassification;
    this.status = "PENDING";
    this.createdAt = Instant.now();
  }

  public void approve(UUID reviewerId, String note) {
    review("APPROVED", reviewerId, note);
  }

  public void reject(UUID reviewerId, String note) {
    review("REJECTED", reviewerId, note);
  }

  private void review(String nextStatus, UUID reviewerId, String note) {
    if (!"PENDING".equals(status)) {
      throw new IllegalStateException("Only pending registrations may be reviewed.");
    }
    status = nextStatus;
    reviewedByUserId = reviewerId;
    reviewedAt = Instant.now();
    reviewNote = note;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getOrganizationName() {
    return organizationName;
  }

  public String getOrganizationCode() {
    return organizationCode;
  }

  public UUID getAssetCategoryId() {
    return assetCategoryId;
  }

  public String getAssetCode() {
    return assetCode;
  }

  public String getAssetName() {
    return assetName;
  }

  public String getAssetDescription() {
    return assetDescription;
  }

  public String getAssetLocation() {
    return assetLocation;
  }

  public BigDecimal getLatitude() {
    return latitude;
  }

  public BigDecimal getLongitude() {
    return longitude;
  }

  public String getOwnershipInformation() {
    return ownershipInformation;
  }

  public String getStatus() {
    return status;
  }

  public String getAirspaceClassification() {
    return airspaceClassification;
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
