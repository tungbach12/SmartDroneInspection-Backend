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
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** An organization-scoped or Inspector-partner-owned airframe registered in the Drone inventory. */
@Entity
@Table(
    name = "drones",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_drones_organization_serial",
            columnNames = {"organization_id", "serial_number"}))
public class Drone {

  @Id @GeneratedValue private UUID id;

  @Column(name = "organization_id")
  private UUID organizationId;

  @Column(name = "inspector_owner_id")
  private UUID inspectorOwnerId;

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

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected Drone() {}

  public Drone(UUID organizationId, String serialNumber) {
    this(organizationId, serialNumber, null, null, null, DroneServiceability.ACTIVE);
  }

  public Drone(
      UUID organizationId,
      String serialNumber,
      String model,
      String manufacturer,
      String payloadMetadata,
      DroneServiceability serviceability) {
    this(organizationId, serialNumber, model, manufacturer, payloadMetadata, serviceability, null);
  }

  public Drone(
      UUID organizationId,
      String serialNumber,
      String model,
      String manufacturer,
      String payloadMetadata,
      DroneServiceability serviceability,
      String notes,
      String registrationNumber,
      String registrationFileKey,
      Instant registrationExpiresAt) {
    this(organizationId, serialNumber, model, manufacturer, payloadMetadata, serviceability, notes);
    this.registrationNumber = registrationNumber;
    this.registrationFileKey = registrationFileKey;
    this.registrationExpiresAt = registrationExpiresAt;
  }

  public Drone(
      UUID organizationId,
      String serialNumber,
      String model,
      String manufacturer,
      String payloadMetadata,
      DroneServiceability serviceability,
      String notes) {
    this.organizationId = Objects.requireNonNull(organizationId, "Drone organization is required");
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
      UUID organizationId,
      UUID inspectorOwnerId,
      String serialNumber,
      String model,
      String manufacturer,
      String registrationNumber,
      String registrationFileKey,
      Instant registrationExpiresAt) {
    if ((organizationId == null) == (inspectorOwnerId == null)) {
      throw new IllegalArgumentException("Exactly one Drone owner scope is required.");
    }
    if (serialNumber == null || serialNumber.isBlank()) {
      throw new IllegalArgumentException("Drone serial number must not be blank.");
    }
    this.organizationId = organizationId;
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

  public static Drone createOrganizationOwned(
      UUID organizationId,
      String serialNumber,
      String model,
      String manufacturer,
      String registrationNumber,
      String registrationFileKey,
      Instant registrationExpiresAt) {
    return new Drone(
        organizationId,
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

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getInspectorOwnerId() {
    return inspectorOwnerId;
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

  public void updateRegistration(
      String registrationNumber, String registrationFileKey, Instant registrationExpiresAt) {
    this.registrationNumber = registrationNumber;
    this.registrationFileKey = registrationFileKey;
    this.registrationExpiresAt = registrationExpiresAt;
    this.updatedAt = Instant.now();
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

  public void changeServiceability(DroneServiceability target) {
    Objects.requireNonNull(target, "Drone serviceability is required");
    if (serviceability == DroneServiceability.RETIRED) {
      throw new IllegalStateException("Retired drones cannot change serviceability");
    }
    this.serviceability = target;
    this.updatedAt = Instant.now();
  }
}
