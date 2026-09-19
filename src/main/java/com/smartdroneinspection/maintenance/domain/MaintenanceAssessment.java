package com.smartdroneinspection.maintenance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "maintenance_assessments")
public class MaintenanceAssessment {

  @Id @GeneratedValue private UUID id;

  @Column(name = "assessment_assignment_id", nullable = false, unique = true)
  private UUID assessmentAssignmentId;

  @Column(name = "maintenance_ticket_id", nullable = false)
  private UUID maintenanceTicketId;

  @Column(name = "engineer_user_id", nullable = false)
  private UUID engineerUserId;

  @Enumerated(EnumType.STRING)
  @Column(name = "assessment_mode", nullable = false, length = 16)
  private AssessmentMode assessmentMode;

  @Column(name = "required_work", nullable = false, length = 4000)
  private String requiredWork;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "materials_estimate", nullable = false, columnDefinition = "jsonb")
  private String materialsEstimate;

  @Column(name = "labor_hours_estimate", nullable = false, precision = 10, scale = 2)
  private BigDecimal laborHoursEstimate;

  @Column(name = "duration_hours_estimate", nullable = false, precision = 10, scale = 2)
  private BigDecimal durationHoursEstimate;

  @Column(name = "risk_notes", length = 4000)
  private String riskNotes;

  @Column(length = 4000)
  private String assumptions;

  @Column(name = "estimated_cost_min", nullable = false, precision = 14, scale = 2)
  private BigDecimal estimatedCostMin;

  @Column(name = "estimated_cost_max", nullable = false, precision = 14, scale = 2)
  private BigDecimal estimatedCostMax;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected MaintenanceAssessment() {}

  public MaintenanceAssessment(
      UUID assessmentAssignmentId,
      UUID maintenanceTicketId,
      UUID engineerUserId,
      AssessmentMode assessmentMode,
      String requiredWork,
      String materialsEstimate,
      BigDecimal laborHoursEstimate,
      BigDecimal durationHoursEstimate,
      String riskNotes,
      String assumptions,
      BigDecimal estimatedCostMin,
      BigDecimal estimatedCostMax,
      String currency) {
    this.assessmentAssignmentId = assessmentAssignmentId;
    this.maintenanceTicketId = maintenanceTicketId;
    this.engineerUserId = engineerUserId;
    this.assessmentMode = assessmentMode;
    this.requiredWork = requiredWork;
    this.materialsEstimate = materialsEstimate;
    this.laborHoursEstimate = laborHoursEstimate;
    this.durationHoursEstimate = durationHoursEstimate;
    this.riskNotes = riskNotes;
    this.assumptions = assumptions;
    this.estimatedCostMin = estimatedCostMin;
    this.estimatedCostMax = estimatedCostMax;
    this.currency = currency;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getMaintenanceTicketId() {
    return maintenanceTicketId;
  }

  public UUID getAssessmentAssignmentId() {
    return assessmentAssignmentId;
  }
}
