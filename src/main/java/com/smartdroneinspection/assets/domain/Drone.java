package com.smartdroneinspection.assets.domain;

import com.smartdroneinspection.assets.domain.enums.DroneServiceability;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** An organization-scoped or Inspector-partner-owned airframe registered in the Drone inventory. */
@Entity
@Table(
    name = "drones",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_drones_owner_customer_serial",
            columnNames = {"owner_customer_id", "serial_number"}))
public class Drone {

  @Id @GeneratedValue private UUID id;

  @Column(name = "inspector_owner_id")
  private UUID inspectorOwnerId;

  @Column(name = "owner_customer_id")
  private UUID ownerCustomerId;

  @Column(name = "serial_number", nullable = false, length = 128)
  private String serialNumber;

  @Column(length = 1000)
  private String model;

  @Column(length = 200)
  private String manufacturer;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload_metadata", columnDefinition = "jsonb")
  private String payloadMetadata;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private DroneServiceability serviceability;

  @Column(length = 2000)
  private String notes;

  @Column(name = "registration_number", length = 100)
  private String registrationNumber;

  @Column(name = "registration_file_key", length = 1000)
  private String registrationFileKey;

  @Column(name = "registration_expires_at")
  private Instant registrationExpiresAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "document_files", columnDefinition = "jsonb")
  private Map<String, String> documentFiles;

  @Column(name = "max_wind_resistance_mps", precision = 4, scale = 1)
  private BigDecimal maxWindResistanceMps;

  @Column(name = "max_altitude_meters", precision = 8, scale = 1)
  private BigDecimal maxAltitudeMeters;

  @Column(name = "optical_zoom_level", length = 32)
  private String opticalZoomLevel;

  @Column(name = "has_thermal_camera", nullable = false)
  private boolean hasThermalCamera;

  @Column(name = "has_rtk", nullable = false)
  private boolean hasRtk;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "image_file_keys", columnDefinition = "jsonb")
  private List<String> imageFileKeys = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "features", columnDefinition = "jsonb")
  private List<String> features = new ArrayList<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected Drone() {}

  public Drone(UUID ownerCustomerId, String serialNumber) {
    this(ownerCustomerId, serialNumber, null, null, null, DroneServiceability.ACTIVE);
  }

  public Drone(
      UUID ownerCustomerId,
      String serialNumber,
      String model,
      String manufacturer,
      String payloadMetadata,
      DroneServiceability serviceability) {
    this(ownerCustomerId, serialNumber, model, manufacturer, payloadMetadata, serviceability, null);
  }

  public Drone(
      UUID ownerCustomerId,
      String serialNumber,
      String model,
      String manufacturer,
      String payloadMetadata,
      DroneServiceability serviceability,
      String notes,
      String registrationNumber,
      String registrationFileKey,
      Instant registrationExpiresAt) {
    this(
        ownerCustomerId, serialNumber, model, manufacturer, payloadMetadata, serviceability, notes);
    this.registrationNumber = registrationNumber;
    this.registrationFileKey = registrationFileKey;
    this.registrationExpiresAt = registrationExpiresAt;
  }

  public Drone(
      UUID ownerCustomerId,
      String serialNumber,
      String model,
      String manufacturer,
      String payloadMetadata,
      DroneServiceability serviceability,
      String notes) {
    this.ownerCustomerId =
        Objects.requireNonNull(ownerCustomerId, "Drone owner customer is required");
    if (serialNumber == null || serialNumber.isBlank()) {
      throw new IllegalArgumentException("Drone serial number must not be blank");
    }
    this.serialNumber = serialNumber.trim();
    this.model = model;
    this.manufacturer = manufacturer;
    this.payloadMetadata = payloadMetadata;
    this.serviceability =
        Objects.requireNonNull(serviceability, "Drone serviceability is required");
    this.notes = notes;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  private Drone(
      UUID ownerCustomerId,
      UUID inspectorOwnerId,
      String serialNumber,
      String model,
      String manufacturer,
      String registrationNumber,
      String registrationFileKey,
      Instant registrationExpiresAt) {
    if ((ownerCustomerId == null) == (inspectorOwnerId == null)) {
      throw new IllegalArgumentException("Exactly one Drone owner scope is required.");
    }
    if (serialNumber == null || serialNumber.isBlank()) {
      throw new IllegalArgumentException("Drone serial number must not be blank.");
    }
    this.ownerCustomerId = ownerCustomerId;
    this.inspectorOwnerId = inspectorOwnerId;
    this.serialNumber = serialNumber.trim();
    this.model = model;
    this.manufacturer = manufacturer;
    this.registrationNumber = registrationNumber;
    this.registrationFileKey = registrationFileKey;
    this.registrationExpiresAt = registrationExpiresAt;
    this.serviceability = DroneServiceability.ACTIVE;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public static Drone createInspectorOwned(
      UUID inspectorId,
      String serialNumber,
      String model,
      String manufacturer,
      String registrationNumber,
      String registrationFileKey,
      Instant registrationExpiresAt) {
    return new Drone(
        null,
        inspectorId,
        serialNumber,
        model,
        manufacturer,
        registrationNumber,
        registrationFileKey,
        registrationExpiresAt);
  }

  public static Drone createCustomerOwned(
      UUID ownerCustomerId,
      String serialNumber,
      String model,
      String manufacturer,
      String registrationNumber,
      String registrationFileKey,
      Instant registrationExpiresAt) {
    return new Drone(
        ownerCustomerId,
        null,
        serialNumber,
        model,
        manufacturer,
        registrationNumber,
        registrationFileKey,
        registrationExpiresAt);
  }

  public UUID getId() {
    return id;
  }

  public UUID getInspectorOwnerId() {
    return inspectorOwnerId;
  }

  public UUID getOwnerCustomerId() {
    return ownerCustomerId;
  }

  /** Phase 1 bridge: records the owning customer user for organization-owned drones. */
  public void markCustomerOwned(UUID ownerCustomerId) {
    this.ownerCustomerId = ownerCustomerId;
    this.updatedAt = Instant.now();
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

  public String getPayloadMetadata() {
    return payloadMetadata;
  }

  public DroneServiceability getServiceability() {
    return serviceability;
  }

  public String getNotes() {
    return notes;
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

  public void updateDetails(String model, String manufacturer) {
    if (model != null) this.model = model;
    if (manufacturer != null) this.manufacturer = manufacturer;
    this.updatedAt = Instant.now();
  }

  public void updateRegistration(
      String registrationNumber, String registrationFileKey, Instant registrationExpiresAt) {
    this.registrationNumber = registrationNumber;
    this.registrationFileKey = registrationFileKey;
    this.registrationExpiresAt = registrationExpiresAt;
    this.updatedAt = Instant.now();
  }

  public void applyCapabilitiesAndDocuments(
      BigDecimal maxWindResistanceMps,
      BigDecimal maxAltitudeMeters,
      String opticalZoomLevel,
      boolean hasThermalCamera,
      boolean hasRtk,
      Map<String, String> documentFiles,
      List<String> imageFileKeys,
      List<String> features) {
    this.maxWindResistanceMps = maxWindResistanceMps;
    this.maxAltitudeMeters = maxAltitudeMeters;
    this.opticalZoomLevel = opticalZoomLevel;
    this.features = features != null ? new ArrayList<>(features) : new ArrayList<>();
    this.hasThermalCamera = hasThermalCamera || this.features.contains("THERMAL_CAMERA");
    this.hasRtk = hasRtk || this.features.contains("RTK_POSITIONING");
    this.documentFiles = documentFiles != null ? new HashMap<>(documentFiles) : null;
    this.imageFileKeys = imageFileKeys != null ? new ArrayList<>(imageFileKeys) : new ArrayList<>();
    this.updatedAt = Instant.now();
  }

  public List<String> getFeatures() {
    return features != null ? features : List.of();
  }

  public boolean hasKnownCapabilityDeclaration() {
    return features != null;
  }

  public List<String> getImageFileKeys() {
    return imageFileKeys != null ? imageFileKeys : List.of();
  }

  public String getPrimaryImageKey() {
    return (imageFileKeys != null && !imageFileKeys.isEmpty()) ? imageFileKeys.get(0) : null;
  }

  public Map<String, String> getDocumentFiles() {
    return documentFiles;
  }

  public BigDecimal getMaxWindResistanceMps() {
    return maxWindResistanceMps;
  }

  public BigDecimal getMaxAltitudeMeters() {
    return maxAltitudeMeters;
  }

  public String getOpticalZoomLevel() {
    return opticalZoomLevel;
  }

  public boolean hasThermalCamera() {
    return hasThermalCamera;
  }

  public boolean hasRtk() {
    return hasRtk;
  }

  public boolean isHasThermalCamera() {
    return hasThermalCamera;
  }

  public boolean isHasRtk() {
    return hasRtk;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  /** MF2 assignment eligibility; it is not a permit or flight-readiness decision. */
  public boolean isEligibleForAssignment() {
    return serviceability == DroneServiceability.ACTIVE;
  }

  private static final Pattern MTOW_PATTERN =
      Pattern.compile(
          "(?:mtow\\s*[:=]?\\s*|\"mtowKg\"\\s*:\\s*)(\\d+(?:[.,]\\d+)?)\\s*(?:kg)?",
          Pattern.CASE_INSENSITIVE);

  /**
   * Resolves Maximum Take-Off Weight (MTOW in kg) from payloadMetadata or structured model text, or
   * known industrial airframe families per Decree 288/2025/ND-CP Article 4.
   */
  public Double resolveMtowKg() {
    for (String source : new String[] {payloadMetadata, model, notes}) {
      if (source == null || source.isBlank()) {
        continue;
      }
      Matcher matcher = MTOW_PATTERN.matcher(source);
      if (matcher.find()) {
        try {
          return Double.parseDouble(matcher.group(1).replace(',', '.'));
        } catch (NumberFormatException ignored) {
          // continue
        }
      }
    }
    if (model != null) {
      String lower = model.toLowerCase(Locale.ROOT);
      if (lower.contains("matrice 350")
          || lower.contains("m350")
          || lower.contains("matrice 300")) {
        return 9.20;
      }
      if (lower.contains("matrice 30") || lower.contains("m30t") || lower.contains("m30")) {
        return 4.07;
      }
      if (lower.contains("wingtra")) {
        return 4.50;
      }
      if (lower.contains("elios 3") && lower.contains("lidar")) {
        return 2.50;
      }
      if (lower.contains("mavic 3") || lower.contains("m3e") || lower.contains("m3t")) {
        return 1.05;
      }
      if (lower.contains("mini 4")) {
        return 0.249;
      }
    }
    return null;
  }

  /**
   * Returns true when this airframe is Category 3, 4, or 5 (MTOW >= 2.0 kg) under Decree
   * 288/2025/ND-CP Article 4 & Article 27, which requires a Class B pilot license.
   */
  public boolean requiresClassBLicense() {
    Double mtow = resolveMtowKg();
    return mtow != null && mtow >= 2.0;
  }

  public boolean hasKnownMtow() {
    Double mtow = resolveMtowKg();
    return mtow != null && mtow > 0;
  }

  /**
   * Classifies this airframe into one of the 5 statutory MTOW categories under Article 4 of Decree
   * 288/2025/ND-CP (`CATEGORY_1` through `CATEGORY_5`).
   */
  public String resolveLegalCategory() {
    Double mtow = resolveMtowKg();
    if (mtow == null || mtow <= 0) {
      return "UNKNOWN";
    }
    double effectiveMtow = mtow;
    if (effectiveMtow < 0.25) {
      return "CATEGORY_1";
    }
    if (effectiveMtow < 2.0) {
      return "CATEGORY_2";
    }
    if (effectiveMtow < 25.0) {
      return "CATEGORY_3";
    }
    if (effectiveMtow < 150.0) {
      return "CATEGORY_4";
    }
    return "CATEGORY_5";
  }

  public void changeServiceability(DroneServiceability target) {
    Objects.requireNonNull(target, "Drone serviceability is required");
    if (serviceability == DroneServiceability.RETIRED) {
      throw new IllegalStateException("Retired drones cannot change serviceability");
    }
    this.serviceability = target;
    this.updatedAt = Instant.now();
  }
}
