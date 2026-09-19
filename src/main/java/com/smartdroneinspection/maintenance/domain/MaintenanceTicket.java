package com.smartdroneinspection.maintenance.domain;

import com.smartdroneinspection.maintenance.domain.enums.MaintenancePriority;
import com.smartdroneinspection.maintenance.domain.enums.MaintenanceTicketStatus;
import com.smartdroneinspection.maintenance.domain.enums.ResolutionDecision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "maintenance_tickets")
public class MaintenanceTicket {

  @Id @GeneratedValue private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "asset_id", nullable = false)
  private UUID assetId;

  @Column(name = "accepted_report_version_id", nullable = false)
  private UUID acceptedReportVersionId;

  @Column(name = "created_by_user_id", nullable = false)
  private UUID createdByUserId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private MaintenancePriority priority;

  @Column(name = "preferred_deadline")
  private Instant preferredDeadline;

  @Column(length = 4000)
  private String instructions;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private MaintenanceTicketStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "resolution_decision", length = 32)
  private ResolutionDecision resolutionDecision;

  @Column(name = "released_at")
  private Instant releasedAt;

  @Column(name = "closed_at")
  private Instant closedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected MaintenanceTicket() {}

  public MaintenanceTicket(
      UUID organizationId,
      UUID assetId,
      UUID acceptedReportVersionId,
      UUID createdByUserId,
      MaintenancePriority priority,
      Instant preferredDeadline,
      String instructions) {
    this.organizationId = organizationId;
    this.assetId = assetId;
    this.acceptedReportVersionId = acceptedReportVersionId;
    this.createdByUserId = createdByUserId;
    this.priority = priority;
    this.preferredDeadline = preferredDeadline;
    this.instructions = instructions;
    this.status = MaintenanceTicketStatus.SUBMITTED;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getAssetId() {
    return assetId;
  }

  public UUID getAcceptedReportVersionId() {
    return acceptedReportVersionId;
  }

  public MaintenanceTicketStatus getStatus() {
    return status;
  }
}
