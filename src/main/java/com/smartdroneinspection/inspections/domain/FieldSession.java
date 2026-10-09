package com.smartdroneinspection.inspections.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A field session under one inspection. MF3 evidence may be attributed to the session it came from.
 */
@Entity
@Table(name = "field_sessions")
public class FieldSession {

  @Id @GeneratedValue private UUID id;

  @Column(name = "inspection_id", nullable = false)
  private UUID inspectionId;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "inspector_user_id", nullable = false)
  private UUID inspectorUserId;

  @Column(name = "drone_id")
  private UUID droneId;

  @Column(name = "readiness_decision_id")
  private UUID readinessDecisionId;

  @Column(nullable = false, length = 24)
  private String status;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "ended_at")
  private Instant endedAt;

  @Column(name = "postponement_reason", length = 2000)
  private String postponementReason;

  @Column(name = "abort_reason", length = 2000)
  private String abortReason;

  @Column(name = "checklist_template_id")
  private UUID checklistTemplateId;

  @Column(name = "checklist_version")
  private Integer checklistVersion;

  @Column(name = "readiness_version")
  private Integer readinessVersion;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String limitations;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected FieldSession() {}

  public FieldSession(
      UUID inspectionId,
      UUID organizationId,
      UUID inspectorUserId,
      UUID droneId,
      UUID readinessDecisionId,
      String status,
      Instant startedAt,
      Integer checklistVersion,
      Integer readinessVersion) {
    this.inspectionId = inspectionId;
    this.organizationId = organizationId;
    this.inspectorUserId = inspectorUserId;
    this.droneId = droneId;
    this.readinessDecisionId = readinessDecisionId;
    this.status = status;
    this.startedAt = startedAt;
    this.checklistVersion = checklistVersion;
    this.readinessVersion = readinessVersion;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getInspectionId() {
    return inspectionId;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getInspectorUserId() {
    return inspectorUserId;
  }

  public String getStatus() {
    return status;
  }
}
