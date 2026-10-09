package com.smartdroneinspection.inspections.domain;

import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An inspection on one asset (MF2's subject record).
 *
 * <p>This entity exists because {@code inspection_preparations} and the MF2 readiness and session
 * tables all hang off an inspection, and nothing else in the codebase mapped the table. It carries
 * the columns MF2 reads to scope and gate work: the organization, the assigned pair and the planned
 * window that a readiness decision is checked against.
 *
 * <p>The JSON columns stay raw strings, matching the rest of the schema. The shape of {@code scope}
 * and {@code component_scope} is agreed with the clients rather than enforced here, and inventing a
 * mapped type now would freeze a contract MF2-03 has not settled yet.
 */
@Entity
@Table(name = "inspections")
public class Inspection {

  @Id @GeneratedValue private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "asset_id", nullable = false)
  private UUID assetId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private InspectionStatus status;

  @Column(length = 1000)
  private String objective;

  @Column(name = "schedule_id")
  private UUID scheduleId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "scope", columnDefinition = "jsonb")
  private String scope;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "component_scope", columnDefinition = "jsonb")
  private String componentScope;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "acceptance_criteria", columnDefinition = "jsonb")
  private String acceptanceCriteria;

  @Column(name = "planned_start_at")
  private Instant plannedStartAt;

  @Column(name = "planned_end_at")
  private Instant plannedEndAt;

  @Column(name = "asset_pair_assignment_id")
  private UUID assetPairAssignmentId;

  @Column(name = "inspector_id")
  private UUID inspectorId;

  @Column(name = "drone_id")
  private UUID droneId;

  @Column(name = "due_cycle_key", length = 64)
  private String dueCycleKey;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected Inspection() {}

  public Inspection(UUID organizationId, UUID assetId, String objective, Instant plannedStartAt) {
    this.organizationId =
        Objects.requireNonNull(organizationId, "Inspection organization is required");
    this.assetId = Objects.requireNonNull(assetId, "Inspection asset is required");
    this.status = InspectionStatus.DRAFT;
    this.objective = objective;
    this.plannedStartAt = plannedStartAt;
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

  public InspectionStatus getStatus() {
    return status;
  }

  public String getObjective() {
    return objective;
  }

  public UUID getScheduleId() {
    return scheduleId;
  }

  public String getScope() {
    return scope;
  }

  public String getComponentScope() {
    return componentScope;
  }

  public String getAcceptanceCriteria() {
    return acceptanceCriteria;
  }

  public Instant getPlannedStartAt() {
    return plannedStartAt;
  }

  public Instant getPlannedEndAt() {
    return plannedEndAt;
  }

  public UUID getAssetPairAssignmentId() {
    return assetPairAssignmentId;
  }

  public UUID getInspectorId() {
    return inspectorId;
  }

  public UUID getDroneId() {
    return droneId;
  }

  public String getDueCycleKey() {
    return dueCycleKey;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  /** Whether this inspection is assigned to the given inspector, if it is assigned at all. */
  public boolean isInspectedBy(UUID candidateInspectorId) {
    return inspectorId != null && inspectorId.equals(candidateInspectorId);
  }

  /**
   * Moves to a new workflow state.
   *
   * <p>Only the vocabulary the CHECK constraint allows can be reached, and an unchanged transition
   * is refused so a caller cannot quietly restate a status it did not earn.
   */
  public void startPreparation() {
    if (status != InspectionStatus.ASSIGNED) {
      throw new IllegalStateException("Only an assigned inspection can start preparation");
    }
    moveTo(InspectionStatus.PREPARING);
  }

  public void moveTo(InspectionStatus nextStatus) {
    Objects.requireNonNull(nextStatus, "Inspection status is required");
    if (nextStatus == this.status) {
      throw new IllegalStateException("The inspection is already " + nextStatus);
    }
    this.status = nextStatus;
    this.updatedAt = Instant.now();
  }
}
