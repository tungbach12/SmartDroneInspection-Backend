package com.smartdroneinspection.maintenance.domain;

import com.smartdroneinspection.maintenance.domain.enums.TeamMemberRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Assignment history entry for one engineer on one work order.
 *
 * <p>This is a history table, not a current-state table: replacing a lead or report author closes
 * the previous row with {@code effective_until} rather than overwriting it, so old work logs keep
 * their authors. The database enforces one active LEAD and one active REPORT_AUTHOR per work order
 * with partial unique indexes.
 */
@Entity
@Table(name = "maintenance_team_members")
public class MaintenanceTeamMember {

  @Id @GeneratedValue private UUID id;

  @Column(name = "work_order_id", nullable = false)
  private UUID workOrderId;

  @Column(name = "engineer_user_id", nullable = false)
  private UUID engineerUserId;

  @Enumerated(EnumType.STRING)
  @Column(name = "member_role", nullable = false, length = 24)
  private TeamMemberRole memberRole;

  @Column(name = "effective_from", nullable = false)
  private Instant effectiveFrom;

  @Column(name = "effective_until")
  private Instant effectiveUntil;

  @Column(name = "assigned_by_user_id", nullable = false)
  private UUID assignedByUserId;

  @Column(length = 2000)
  private String reason;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected MaintenanceTeamMember() {}

  public MaintenanceTeamMember(
      UUID workOrderId,
      UUID engineerUserId,
      TeamMemberRole memberRole,
      Instant effectiveFrom,
      UUID assignedByUserId,
      String reason) {
    this.workOrderId = require(workOrderId, "workOrderId");
    this.engineerUserId = require(engineerUserId, "engineerUserId");
    this.memberRole = require(memberRole, "memberRole");
    this.assignedByUserId = require(assignedByUserId, "assignedByUserId");
    this.effectiveFrom = effectiveFrom == null ? Instant.now() : effectiveFrom;
    this.reason = reason;
    this.active = true;
    this.createdAt = Instant.now();
  }

  /** Report 3 §3.8.1: replacement happens by ORG_ADMIN with a recorded handover reason. */
  public void closeWithHandover(Instant until, String handoverReason) {
    if (!active) {
      throw new IllegalStateException("This assignment is already closed");
    }
    if (until == null || !until.isAfter(effectiveFrom)) {
      throw new IllegalArgumentException("effectiveUntil must be after effectiveFrom");
    }
    if (handoverReason == null || handoverReason.isBlank()) {
      throw new IllegalArgumentException("A handover requires a reason");
    }
    this.effectiveUntil = until;
    this.active = false;
  }

  /** True when this assignment covers the given moment, used for the active-team check. */
  public boolean covers(Instant moment) {
    if (!active) {
      return false;
    }
    if (moment.isBefore(effectiveFrom)) {
      return false;
    }
    return effectiveUntil == null || moment.isBefore(effectiveUntil);
  }

  private static UUID require(UUID value, String name) {
    if (value == null) {
      throw new IllegalArgumentException(name + " is required");
    }
    return value;
  }

  private static TeamMemberRole require(TeamMemberRole value, String name) {
    if (value == null) {
      throw new IllegalArgumentException(name + " is required");
    }
    return value;
  }

  public UUID getId() {
    return id;
  }

  public UUID getWorkOrderId() {
    return workOrderId;
  }

  public UUID getEngineerUserId() {
    return engineerUserId;
  }

  public TeamMemberRole getMemberRole() {
    return memberRole;
  }

  public Instant getEffectiveFrom() {
    return effectiveFrom;
  }

  public Instant getEffectiveUntil() {
    return effectiveUntil;
  }

  public UUID getAssignedByUserId() {
    return assignedByUserId;
  }

  public String getReason() {
    return reason;
  }

  public boolean isActive() {
    return active;
  }
}
