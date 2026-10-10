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

/**
 * An organization-owned airframe registered in the Drone inventory.
 *
 * <p>Serviceability is a maintenance fact, not a permit or flight-readiness assertion. Only ACTIVE
 * drones are eligible for MF2 pairing; permit and readiness decisions belong to their own flows.
 */
@Entity
@Table(
    name = "drones",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_drones_organization_serial",
            columnNames = {"organization_id", "serial_number"}))
public class Drone {

  @Id @GeneratedValue private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "serial_number", nullable = false, length = 128)
  private String serialNumber;

  @Column(length = 200)
  private String model;

  @Column(length = 200)
  private String manufacturer;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload_metadata", columnDefinition = "jsonb")
  private String payloadMetadata;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private DroneServiceability serviceability;

  @Column(name = "last_maintenance_at")
  private Instant lastMaintenanceAt;

  @Column(name = "next_maintenance_at")
  private Instant nextMaintenanceAt;

  @Column(length = 2000)
  private String notes;

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
    this(
        organizationId,
        serialNumber,
        model,
        manufacturer,
        payloadMetadata,
        serviceability,
        null,
        null,
        null);
  }

  public Drone(
      UUID organizationId,
      String serialNumber,
      String model,
      String manufacturer,
      String payloadMetadata,
      DroneServiceability serviceability,
      Instant lastMaintenanceAt,
      Instant nextMaintenanceAt,
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
    this.lastMaintenanceAt = lastMaintenanceAt;
    this.nextMaintenanceAt = nextMaintenanceAt;
    this.notes = notes;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrganizationId() {
    return organizationId;
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

  public Instant getLastMaintenanceAt() {
    return lastMaintenanceAt;
  }

  public Instant getNextMaintenanceAt() {
    return nextMaintenanceAt;
  }

  public String getNotes() {
    return notes;
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

  public void recordMaintenance(Instant servicedAt, Instant nextDueAt, String maintenanceNotes) {
    this.lastMaintenanceAt = servicedAt;
    this.nextMaintenanceAt = nextDueAt;
    if (maintenanceNotes != null) this.notes = maintenanceNotes;
    this.updatedAt = Instant.now();
  }
}
