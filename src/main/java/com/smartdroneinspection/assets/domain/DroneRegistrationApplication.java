package com.smartdroneinspection.assets.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "drone_registration_applications")
public class DroneRegistrationApplication {
  @Id @GeneratedValue private UUID id;

  @Column(name = "inspector_user_id", nullable = false)
  private UUID inspectorUserId;

  @Column(name = "serial_number", nullable = false, length = 128)
  private String serialNumber;

  @Column(length = 1000)
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

  @Column(name = "inspection_file_key", length = 1000)
  private String inspectionFileKey;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "document_files", columnDefinition = "jsonb")
  private Map<String, String> documentFiles = new HashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "image_file_keys", columnDefinition = "jsonb")
  private List<String> imageFileKeys = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "features", columnDefinition = "jsonb")
  private List<String> features = new ArrayList<>();

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
    this(
        inspectorId,
        serial,
        model,
        manufacturer,
        registrationNumber,
        registrationFileKey,
        registrationExpiresAt,
        ownershipFileKey,
        inspectionFileKey,
        null,
        null,
        null,
        false,
        false,
        null);
  }

  public DroneRegistrationApplication(
      UUID inspectorId,
      String serial,
      String model,
      String manufacturer,
      String registrationNumber,
      String registrationFileKey,
      Instant registrationExpiresAt,
      String ownershipFileKey,
      String inspectionFileKey,
      Map<String, String> documentFiles,
      BigDecimal maxWindResistanceMps,
      String opticalZoomLevel,
      boolean hasThermalCamera,
      boolean hasRtk,
      List<String> imageFileKeys) {
    this(
        inspectorId,
        serial,
        model,
        manufacturer,
        registrationNumber,
        registrationFileKey,
        registrationExpiresAt,
        ownershipFileKey,
        inspectionFileKey,
        documentFiles,
        maxWindResistanceMps,
        null,
        opticalZoomLevel,
        hasThermalCamera,
        hasRtk,
        imageFileKeys,
        null);
  }

  public DroneRegistrationApplication(
      UUID inspectorId,
      String serial,
      String model,
      String manufacturer,
      String registrationNumber,
      String registrationFileKey,
      Instant registrationExpiresAt,
      String ownershipFileKey,
      String inspectionFileKey,
      Map<String, String> documentFiles,
      BigDecimal maxWindResistanceMps,
      String opticalZoomLevel,
      boolean hasThermalCamera,
      boolean hasRtk,
      List<String> imageFileKeys,
      List<String> features) {
    this(
        inspectorId,
        serial,
        model,
        manufacturer,
        registrationNumber,
        registrationFileKey,
        registrationExpiresAt,
        ownershipFileKey,
        inspectionFileKey,
        documentFiles,
        maxWindResistanceMps,
        null,
        opticalZoomLevel,
        hasThermalCamera,
        hasRtk,
        imageFileKeys,
        features);
  }

  public DroneRegistrationApplication(
      UUID inspectorId,
      String serial,
      String model,
      String manufacturer,
      String registrationNumber,
      String registrationFileKey,
      Instant registrationExpiresAt,
      String ownershipFileKey,
      String inspectionFileKey,
      Map<String, String> documentFiles,
      BigDecimal maxWindResistanceMps,
      BigDecimal maxAltitudeMeters,
      String opticalZoomLevel,
      boolean hasThermalCamera,
      boolean hasRtk,
      List<String> imageFileKeys,
      List<String> features) {
    this.inspectorUserId = inspectorId;
    this.maxAltitudeMeters = maxAltitudeMeters;
    this.serialNumber = serial.trim();
    this.model = model;
    this.manufacturer = manufacturer;
    this.registrationNumber = registrationNumber.trim();
    this.registrationFileKey = registrationFileKey.trim();
    this.registrationExpiresAt = registrationExpiresAt;
    this.ownershipFileKey = ownershipFileKey.trim();
    this.inspectionFileKey = inspectionFileKey != null ? inspectionFileKey.trim() : null;
    this.documentFiles = documentFiles != null ? new HashMap<>(documentFiles) : new HashMap<>();
    if (this.documentFiles.isEmpty()) {
      if (this.registrationFileKey != null)
        this.documentFiles.put("REGISTRATION", this.registrationFileKey);
      if (this.ownershipFileKey != null) this.documentFiles.put("OWNERSHIP", this.ownershipFileKey);
      if (this.inspectionFileKey != null)
        this.documentFiles.put("INSPECTION", this.inspectionFileKey);
    }
    this.maxWindResistanceMps = maxWindResistanceMps;
    this.maxAltitudeMeters = maxAltitudeMeters;
    this.opticalZoomLevel = opticalZoomLevel;
    this.features = features != null ? new ArrayList<>(features) : new ArrayList<>();
    this.hasThermalCamera = hasThermalCamera || this.features.contains("THERMAL_CAMERA");
    this.hasRtk = hasRtk || this.features.contains("RTK_POSITIONING");
    this.imageFileKeys = imageFileKeys != null ? new ArrayList<>(imageFileKeys) : new ArrayList<>();
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

  public void resubmit(
      String serial,
      String model,
      String manufacturer,
      String registrationNumber,
      String registrationFileKey,
      Instant registrationExpiresAt,
      String ownershipFileKey,
      String inspectionFileKey,
      Map<String, String> documentFiles,
      BigDecimal maxWindResistanceMps,
      String opticalZoomLevel,
      boolean hasThermalCamera,
      boolean hasRtk,
      List<String> imageFileKeys,
      List<String> features) {
    resubmit(
        serial,
        model,
        manufacturer,
        registrationNumber,
        registrationFileKey,
        registrationExpiresAt,
        ownershipFileKey,
        inspectionFileKey,
        documentFiles,
        maxWindResistanceMps,
        null,
        opticalZoomLevel,
        hasThermalCamera,
        hasRtk,
        imageFileKeys,
        features);
  }

  public void resubmit(
      String model,
      String manufacturer,
      String registrationNumber,
      String registrationFileKey,
      Instant registrationExpiresAt,
      String ownershipFileKey,
      String inspectionFileKey,
      Map<String, String> documentFiles,
      BigDecimal maxWindResistanceMps,
      BigDecimal maxAltitudeMeters,
      String opticalZoomLevel,
      boolean hasThermalCamera,
      boolean hasRtk,
      List<String> imageFileKeys,
      List<String> features) {
    resubmit(
        null,
        model,
        manufacturer,
        registrationNumber,
        registrationFileKey,
        registrationExpiresAt,
        ownershipFileKey,
        inspectionFileKey,
        documentFiles,
        maxWindResistanceMps,
        maxAltitudeMeters,
        opticalZoomLevel,
        hasThermalCamera,
        hasRtk,
        imageFileKeys,
        features);
  }

  public void resubmit(
      String serial,
      String model,
      String manufacturer,
      String registrationNumber,
      String registrationFileKey,
      Instant registrationExpiresAt,
      String ownershipFileKey,
      String inspectionFileKey,
      Map<String, String> documentFiles,
      BigDecimal maxWindResistanceMps,
      BigDecimal maxAltitudeMeters,
      String opticalZoomLevel,
      boolean hasThermalCamera,
      boolean hasRtk,
      List<String> imageFileKeys,
      List<String> features) {
    if (!"REJECTED".equals(status) && !"APPROVED".equals(status)) {
      throw new IllegalStateException(
          "Only rejected or approved applications may be resubmitted or renewed.");
    }
    if (serial != null && !serial.isBlank()) {
      this.serialNumber = serial.trim();
    }
    this.model = model;
    this.manufacturer = manufacturer;
    this.registrationNumber = registrationNumber.trim();
    this.registrationFileKey = registrationFileKey.trim();
    this.registrationExpiresAt = registrationExpiresAt;
    this.ownershipFileKey = ownershipFileKey.trim();
    this.inspectionFileKey = inspectionFileKey != null ? inspectionFileKey.trim() : null;
    this.documentFiles = documentFiles != null ? new HashMap<>(documentFiles) : new HashMap<>();
    if (this.documentFiles.isEmpty()) {
      if (this.registrationFileKey != null)
        this.documentFiles.put("REGISTRATION", this.registrationFileKey);
      if (this.ownershipFileKey != null) this.documentFiles.put("OWNERSHIP", this.ownershipFileKey);
      if (this.inspectionFileKey != null)
        this.documentFiles.put("INSPECTION", this.inspectionFileKey);
    }
    this.maxWindResistanceMps = maxWindResistanceMps;
    this.maxAltitudeMeters = maxAltitudeMeters;
    this.opticalZoomLevel = opticalZoomLevel;
    this.features = features != null ? new ArrayList<>(features) : new ArrayList<>();
    this.hasThermalCamera = hasThermalCamera || this.features.contains("THERMAL_CAMERA");
    this.hasRtk = hasRtk || this.features.contains("RTK_POSITIONING");
    this.imageFileKeys = imageFileKeys != null ? new ArrayList<>(imageFileKeys) : new ArrayList<>();
    this.status = "PENDING";
    this.reviewNote = null;
    this.reviewedByUserId = null;
    this.reviewedAt = null;
  }

  public void linkExistingDrone(UUID existingDroneId) {
    this.droneId = existingDroneId;
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

  public Map<String, String> getDocumentFiles() {
    return documentFiles;
  }

  public List<String> getImageFileKeys() {
    return imageFileKeys != null ? imageFileKeys : List.of();
  }

  public List<String> getFeatures() {
    return features != null ? features : List.of();
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

  public boolean isHasThermalCamera() {
    return hasThermalCamera;
  }

  public boolean isHasRtk() {
    return hasRtk;
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
