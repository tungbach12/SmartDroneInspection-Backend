package com.smartdroneinspection.assets.domain;

import com.smartdroneinspection.assets.domain.enums.AssetStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(
    name = "assets",
    uniqueConstraints = @UniqueConstraint(columnNames = {"organization_id", "code"}))
public class Asset {

  private static final BigDecimal MINIMUM_LATITUDE = BigDecimal.valueOf(-90);
  private static final BigDecimal MAXIMUM_LATITUDE = BigDecimal.valueOf(90);
  private static final BigDecimal MINIMUM_LONGITUDE = BigDecimal.valueOf(-180);
  private static final BigDecimal MAXIMUM_LONGITUDE = BigDecimal.valueOf(180);

  @Id @GeneratedValue private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "category_id", nullable = false)
  private UUID categoryId;

  @Column(nullable = false, length = 64)
  private String code;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(length = 2000)
  private String description;

  @Column(name = "location_text", nullable = false, length = 500)
  private String locationText;

  @Column(precision = 9, scale = 6)
  private BigDecimal latitude;

  @Column(precision = 9, scale = 6)
  private BigDecimal longitude;

  @Column(name = "ownership_information", length = 1000)
  private String ownershipInformation;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private AssetStatus status;

  @Column(name = "created_by_user_id", nullable = false)
  private UUID createdByUserId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  @OneToMany(
      mappedBy = "asset",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @OrderBy("createdAt DESC")
  private List<AssetDocument> documents = new ArrayList<>();

  protected Asset() {}

  public Asset(
      UUID organizationId,
      UUID categoryId,
      String code,
      String name,
      String description,
      String locationText,
      BigDecimal latitude,
      BigDecimal longitude,
      String ownershipInformation,
      UUID createdByUserId) {
    validateCoordinates(latitude, longitude);
    this.organizationId = organizationId;
    this.categoryId = categoryId;
    this.code = normalizeCode(code);
    this.name = name;
    this.description = description;
    this.locationText = locationText;
    this.latitude = latitude;
    this.longitude = longitude;
    this.ownershipInformation = ownershipInformation;
    this.status = AssetStatus.ACTIVE;
    this.createdByUserId = createdByUserId;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public AssetDocument addDocument(
      UUID uploadedByUserId,
      String documentType,
      String fileName,
      String contentType,
      long sizeBytes,
      String checksumSha256,
      String objectKey,
      LocalDate documentDate) {
    AssetDocument document =
        new AssetDocument(
            this,
            uploadedByUserId,
            documentType,
            fileName,
            contentType,
            sizeBytes,
            checksumSha256,
            objectKey,
            documentDate);
    documents.add(document);
    updatedAt = Instant.now();
    return document;
  }

  public void activate() {
    ensureNotRetired();
    status = AssetStatus.ACTIVE;
    updatedAt = Instant.now();
  }

  public void deactivate() {
    ensureNotRetired();
    status = AssetStatus.INACTIVE;
    updatedAt = Instant.now();
  }

  public void retire() {
    ensureNotRetired();
    status = AssetStatus.RETIRED;
    updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public String getCode() {
    return code;
  }

  public AssetStatus getStatus() {
    return status;
  }

  public List<AssetDocument> getDocuments() {
    return List.copyOf(documents);
  }

  private void ensureNotRetired() {
    if (status == AssetStatus.RETIRED) {
      throw new IllegalStateException("Retired assets cannot change status");
    }
  }

  private static String normalizeCode(String value) {
    return value.trim().toUpperCase(Locale.ROOT);
  }

  private static void validateCoordinates(BigDecimal latitude, BigDecimal longitude) {
    if (latitude != null
        && (latitude.compareTo(MINIMUM_LATITUDE) < 0 || latitude.compareTo(MAXIMUM_LATITUDE) > 0)) {
      throw new IllegalArgumentException("Latitude must be between -90 and 90");
    }
    if (longitude != null
        && (longitude.compareTo(MINIMUM_LONGITUDE) < 0
            || longitude.compareTo(MAXIMUM_LONGITUDE) > 0)) {
      throw new IllegalArgumentException("Longitude must be between -180 and 180");
    }
  }
}
