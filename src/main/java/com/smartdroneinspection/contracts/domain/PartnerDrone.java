package com.smartdroneinspection.contracts.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contract_partner_drones")
public class PartnerDrone {

  @Id
  @Column(name = "drone_id", nullable = false)
  private UUID droneId;

  @Column(name = "inspector_owner_id", nullable = false)
  private UUID inspectorOwnerId;

  @Column(nullable = false, length = 16)
  private String status = "ACTIVE";

  @Column(name = "registered_by_user_id", nullable = false)
  private UUID registeredByUserId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected PartnerDrone() {}

  public PartnerDrone(UUID droneId, UUID inspectorOwnerId, UUID registeredByUserId) {
    this.droneId = droneId;
    this.inspectorOwnerId = inspectorOwnerId;
    this.registeredByUserId = registeredByUserId;
  }

  public UUID getDroneId() {
    return droneId;
  }

  public UUID getInspectorOwnerId() {
    return inspectorOwnerId;
  }

  public void changeInspectorOwner(UUID inspectorOwnerId) {
    this.inspectorOwnerId = inspectorOwnerId;
    updatedAt = Instant.now();
  }

  public String getStatus() {
    return status;
  }

  public UUID getRegisteredByUserId() {
    return registeredByUserId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void changeStatus(boolean active) {
    status = active ? "ACTIVE" : "INACTIVE";
    updatedAt = Instant.now();
  }
}
