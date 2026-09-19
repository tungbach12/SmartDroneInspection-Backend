package com.smartdroneinspection.maintenance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class MaintenanceTicketFindingId implements Serializable {

  @Column(name = "maintenance_ticket_id", nullable = false)
  private UUID maintenanceTicketId;

  @Column(name = "verified_finding_id", nullable = false)
  private UUID verifiedFindingId;

  protected MaintenanceTicketFindingId() {}

  public MaintenanceTicketFindingId(UUID maintenanceTicketId, UUID verifiedFindingId) {
    this.maintenanceTicketId = maintenanceTicketId;
    this.verifiedFindingId = verifiedFindingId;
  }

  public UUID getMaintenanceTicketId() {
    return maintenanceTicketId;
  }

  public UUID getVerifiedFindingId() {
    return verifiedFindingId;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof MaintenanceTicketFindingId that)) {
      return false;
    }
    return Objects.equals(maintenanceTicketId, that.maintenanceTicketId)
        && Objects.equals(verifiedFindingId, that.verifiedFindingId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(maintenanceTicketId, verifiedFindingId);
  }
}
