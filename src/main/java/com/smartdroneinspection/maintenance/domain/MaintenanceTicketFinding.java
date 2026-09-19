package com.smartdroneinspection.maintenance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "maintenance_ticket_findings")
public class MaintenanceTicketFinding {

  @EmbeddedId private MaintenanceTicketFindingId id;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected MaintenanceTicketFinding() {}

  public MaintenanceTicketFinding(MaintenanceTicketFindingId id) {
    this.id = id;
    this.createdAt = Instant.now();
  }

  public MaintenanceTicketFindingId getId() {
    return id;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
