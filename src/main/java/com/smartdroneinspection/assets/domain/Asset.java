package com.smartdroneinspection.assets.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "assets",
    uniqueConstraints = @UniqueConstraint(columnNames = {"owner_customer_id", "asset_code"}))
public class Asset {

  private static final BigDecimal MINIMUM_LATITUDE = BigDecimal.valueOf(-90);
  private static final BigDecimal MAXIMUM_LATITUDE = BigDecimal.valueOf(90);
  private static final BigDecimal MINIMUM_LONGITUDE = BigDecimal.valueOf(-180);
  private static final BigDecimal MAXIMUM_LONGITUDE = BigDecimal.valueOf(180);

  @Id @GeneratedValue private UUID id;

  @Column(name = "owner_customer_id", nullable = false)
  private UUID ownerCustomerId;

  @Column(name = "category_id", nullable = false)
  private UUID categoryId;

  @Column(name = "asset_code", nullable = false, length = 64)
  private String code;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(length = 2000)
  private String description;

  @Column(name = "location", nullable = false, columnDefinition = "jsonb")
  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
  private String locationText;

  @Column(precision = 9, scale = 6)
  private BigDecimal latitude;

  @Column(precision = 9, scale = 6)
  private BigDecimal longitude;

  @Column(name = "technical_profile", columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private String ownershipInformation;

  @Column(name = "structure_height_meters", precision = 8, scale = 2)
  private BigDecimal structureHeightMeters;

  @Column(name = "footprint_area_square_meters", precision = 12, scale = 2)
  private BigDecimal footprintAreaSquareMeters;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "inspection_items", columnDefinition = "jsonb", nullable = false)
  private String inspectionItems = "[]";

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private AssetStatus status;

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
      UUID ownerCustomerId,
      UUID categoryId,
      String code,
      String name,
      String description,
      String locationText,
      BigDecimal latitude,
      BigDecimal longitude,
      String ownershipInformation,
      List<String> inspectionItems,
      UUID createdByUserId) {
    this(
        ownerCustomerId,
        categoryId,
        code,
        name,
        description,
        locationText,
        latitude,
        longitude,
        ownershipInformation,
        inspectionItems,
        null,
        null,
        createdByUserId);
  }

  public Asset(
      UUID ownerCustomerId,
      UUID categoryId,
      String code,
      String name,
      String description,
      String locationText,
      BigDecimal latitude,
      BigDecimal longitude,
      String ownershipInformation,
      List<String> inspectionItems,
      BigDecimal structureHeightMeters,
      BigDecimal footprintAreaSquareMeters,
      UUID createdByUserId) {
    validateCoordinates(latitude, longitude);
    validateDimension(structureHeightMeters, "Structure height");
    validateDimension(footprintAreaSquareMeters, "Footprint area");
    this.ownerCustomerId =
        java.util.Objects.requireNonNull(ownerCustomerId, "Asset owner customer is required");
    this.categoryId = categoryId;
    this.code = normalizeCode(code);
    this.name = name;
    this.description = description;
    this.locationText = toLocationJson(locationText, latitude, longitude);
    this.latitude = latitude;
    this.longitude = longitude;
    this.ownershipInformation = ownershipInformation;
    this.structureHeightMeters = structureHeightMeters;
    this.footprintAreaSquareMeters = footprintAreaSquareMeters;
    setInspectionItems(inspectionItems);
    this.status = AssetStatus.ACTIVE;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public Asset(
      UUID ownerCustomerId,
      UUID categoryId,
      String code,
      String name,
      String description,
      String locationText,
      BigDecimal latitude,
      BigDecimal longitude,
      String ownershipInformation,
      UUID createdByUserId) {
    this(
        ownerCustomerId,
        categoryId,
        code,
        name,
        description,
        locationText,
        latitude,
        longitude,
        ownershipInformation,
        List.of(),
        createdByUserId);
  }

  private static String toLocationJson(
      String locationText, BigDecimal latitude, BigDecimal longitude) {
    StringBuilder location = new StringBuilder("{");
    boolean hasValue = false;
    if (locationText != null) {
      location.append("\"label\":\"").append(escapeJson(locationText)).append('"');
      hasValue = true;
    }
    if (latitude != null && longitude != null) {
      if (hasValue) location.append(',');
      location.append("\"latitude\":").append(latitude);
      location.append(",\"longitude\":").append(longitude);
    }
    return location.append('}').toString();
  }

  private String getLocationLabel() {
    if (locationText == null || locationText.isBlank()) return null;
    try {
      var node = JSON_MAPPER.readTree(locationText);
      var label = node.get("label");
      return label == null || label.isNull() ? null : label.asText();
    } catch (Exception ignored) {
      return locationText;
    }
  }

  private static String escapeJson(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  public void removeDocument(AssetDocument document) {
    documents.remove(document);
    updatedAt = Instant.now();
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

  public void update(
      String name,
      String description,
      String locationText,
      BigDecimal latitude,
      BigDecimal longitude,
      String ownershipInformation,
      List<String> inspectionItems,
      BigDecimal structureHeightMeters,
      BigDecimal footprintAreaSquareMeters,
      boolean clearCoordinates,
      boolean clearStructureHeight,
      boolean clearFootprintArea,
      boolean clearDescription) {
    validateCoordinates(latitude, longitude);
    validateDimension(structureHeightMeters, "Structure height");
    validateDimension(footprintAreaSquareMeters, "Footprint area");
    if (name != null) this.name = name;
    if (clearDescription) this.description = null;
    else if (description != null) this.description = description;
    if (clearCoordinates) {
      this.latitude = null;
      this.longitude = null;
    } else {
      if (latitude != null) this.latitude = latitude;
      if (longitude != null) this.longitude = longitude;
    }
    if (locationText != null || clearCoordinates) {
      this.locationText =
          toLocationJson(
              locationText != null ? locationText : getLocationLabel(),
              clearCoordinates ? null : (latitude != null ? latitude : this.latitude),
              clearCoordinates ? null : (longitude != null ? longitude : this.longitude));
    }
    if (ownershipInformation != null) this.ownershipInformation = ownershipInformation;
    if (clearStructureHeight) this.structureHeightMeters = null;
    else if (structureHeightMeters != null) this.structureHeightMeters = structureHeightMeters;
    if (clearFootprintArea) this.footprintAreaSquareMeters = null;
    else if (footprintAreaSquareMeters != null)
      this.footprintAreaSquareMeters = footprintAreaSquareMeters;
    if (inspectionItems != null) {
      setInspectionItems(inspectionItems);
    }
    updatedAt = Instant.now();
  }

  public void update(
      String name,
      String description,
      String locationText,
      BigDecimal latitude,
      BigDecimal longitude,
      String ownershipInformation,
      List<String> inspectionItems,
      BigDecimal structureHeightMeters,
      BigDecimal footprintAreaSquareMeters) {
    update(
        name,
        description,
        locationText,
        latitude,
        longitude,
        ownershipInformation,
        inspectionItems,
        structureHeightMeters,
        footprintAreaSquareMeters,
        false,
        false,
        false,
        false);
  }

  public void update(
      String name,
      String description,
      String locationText,
      BigDecimal latitude,
      BigDecimal longitude,
      String ownershipInformation,
      List<String> inspectionItems) {
    update(
        name,
        description,
        locationText,
        latitude,
        longitude,
        ownershipInformation,
        inspectionItems,
        null,
        null,
        false,
        false,
        false,
        false);
  }

  public void update(
      String name,
      String description,
      String locationText,
      BigDecimal latitude,
      BigDecimal longitude,
      String ownershipInformation) {
    update(name, description, locationText, latitude, longitude, ownershipInformation, null);
  }

  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  public List<String> getInspectionItems() {
    if (inspectionItems == null || inspectionItems.isBlank()) {
      return List.of();
    }
    try {
      return JSON_MAPPER.readValue(inspectionItems, new TypeReference<List<String>>() {});
    } catch (Exception e) {
      return List.of();
    }
  }

  public void setInspectionItems(List<String> items) {
    if (items == null || items.isEmpty()) {
      this.inspectionItems = "[]";
    } else {
      try {
        this.inspectionItems = JSON_MAPPER.writeValueAsString(items);
      } catch (Exception e) {
        this.inspectionItems = "[]";
      }
    }
    updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getOwnerCustomerId() {
    return ownerCustomerId;
  }

  public void markCustomerOwned(UUID ownerCustomerId) {
    this.ownerCustomerId = ownerCustomerId;
    this.updatedAt = Instant.now();
  }

  public String getCode() {
    return code;
  }

  public AssetStatus getStatus() {
    return status;
  }

  public UUID getCategoryId() {
    return categoryId;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getLocationText() {
    return locationText;
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

  public BigDecimal getStructureHeightMeters() {
    return structureHeightMeters;
  }

  public BigDecimal getFootprintAreaSquareMeters() {
    return footprintAreaSquareMeters;
  }

  public Instant getCreatedAt() {
    return createdAt;
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

  private static void validateDimension(BigDecimal value, String label) {
    if (value != null && value.signum() < 0) {
      throw new IllegalArgumentException(label + " must not be negative");
    }
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
