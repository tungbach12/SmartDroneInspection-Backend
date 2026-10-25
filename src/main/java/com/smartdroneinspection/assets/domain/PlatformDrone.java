package com.smartdroneinspection.assets.domain;

import com.smartdroneinspection.assets.domain.enums.PlatformDroneStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "platform_drones")
public class PlatformDrone {

  @Id @GeneratedValue private UUID id;

  @Column(name = "serial_number", nullable = false, unique = true, length = 128)
  private String serialNumber;

  @Column(nullable = false, length = 200)
  private String model;

  @Column(length = 200)
  private String manufacturer;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload_metadata", columnDefinition = "jsonb")
  private String payloadMetadata;

  @Column(name = "monthly_rate_usd", nullable = false, precision = 12, scale = 2)
  private BigDecimal monthlyRateUsd;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private PlatformDroneStatus status;

  @Column(length = 2000)
  private String notes;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected PlatformDrone() {}

  public PlatformDrone(
      String serialNumber,
      String model,
      String manufacturer,
      String payloadMetadata,
      BigDecimal monthlyRateUsd,
      String notes) {
    if (serialNumber == null || serialNumber.isBlank()) {
      throw new IllegalArgumentException("Platform drone serial number must not be blank");
    }
    if (model == null || model.isBlank()) {
      throw new IllegalArgumentException("Platform drone model must not be blank");
    }
    Objects.requireNonNull(monthlyRateUsd, "Monthly rate is required");
    if (monthlyRateUsd.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Monthly rate must be greater than zero");
    }
    this.serialNumber = serialNumber.trim();
    this.model = model.trim();
    this.manufacturer = manufacturer != null ? manufacturer.trim() : null;
    this.payloadMetadata = payloadMetadata;
    this.monthlyRateUsd = monthlyRateUsd;
    this.status = PlatformDroneStatus.AVAILABLE;
    this.notes = notes;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  public UUID getId() {
    return id;
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

  public BigDecimal getMonthlyRateUsd() {
    return monthlyRateUsd;
  }

  public PlatformDroneStatus getStatus() {
    return status;
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

  public void markLeased() {
    this.status = PlatformDroneStatus.LEASED;
    this.updatedAt = Instant.now();
  }

  public void markAvailable() {
    this.status = PlatformDroneStatus.AVAILABLE;
    this.updatedAt = Instant.now();
  }

  public void changeStatus(PlatformDroneStatus target, String notes) {
    Objects.requireNonNull(target, "Target status is required");
    this.status = target;
    if (notes != null) {
      this.notes = notes;
    }
    this.updatedAt = Instant.now();
  }
}
