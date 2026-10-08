package com.smartdroneinspection.maintenance.domain;

import com.smartdroneinspection.maintenance.domain.enums.WorkLogStatus;
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
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "maintenance_work_logs")
public class MaintenanceWorkLog {

  @Id @GeneratedValue private UUID id;

  @Column(name = "maintenance_ticket_id", nullable = false)
  private UUID maintenanceTicketId;

  @Column(name = "execution_assignment_id", nullable = false)
  private UUID executionAssignmentId;

  @Column(name = "engineer_user_id", nullable = false)
  private UUID engineerUserId;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "ended_at")
  private Instant endedAt;

  @Column(name = "progress_percent", nullable = false, precision = 5, scale = 2)
  private BigDecimal progressPercent;

  @Column(name = "work_summary", nullable = false, length = 4000)
  private String workSummary;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "materials_used", nullable = false, columnDefinition = "jsonb")
  private String materialsUsed;

  @Column(name = "labor_hours", nullable = false, precision = 10, scale = 2)
  private BigDecimal laborHours;

  @Column(name = "actual_cost", precision = 14, scale = 2)
  private BigDecimal actualCost;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(length = 3)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private WorkLogStatus status;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @Column(name = "verified_by_user_id")
  private UUID verifiedByUserId;

  @Column(name = "verified_at")
  private Instant verifiedAt;

  @Column(name = "before_evidence_id")
  private UUID beforeEvidenceId;

  @Column(name = "after_evidence_id")
  private UUID afterEvidenceId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected MaintenanceWorkLog() {}

  public MaintenanceWorkLog(
      UUID maintenanceTicketId,
      UUID executionAssignmentId,
      UUID engineerUserId,
      Instant startedAt,
      BigDecimal progressPercent,
      String workSummary,
      String materialsUsed,
      BigDecimal laborHours,
      WorkLogStatus status) {
    this.maintenanceTicketId = maintenanceTicketId;
    this.executionAssignmentId = executionAssignmentId;
    this.engineerUserId = engineerUserId;
    this.startedAt = startedAt;
    this.progressPercent = progressPercent;
    this.workSummary = workSummary;
    this.materialsUsed = materialsUsed;
    this.laborHours = laborHours;
    this.status = status;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getMaintenanceTicketId() {
    return maintenanceTicketId;
  }

  public UUID getExecutionAssignmentId() {
    return executionAssignmentId;
  }

  public UUID getEngineerUserId() {
    return engineerUserId;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getEndedAt() {
    return endedAt;
  }

  public BigDecimal getProgressPercent() {
    return progressPercent;
  }

  public String getWorkSummary() {
    return workSummary;
  }

  public String getMaterialsUsed() {
    return materialsUsed;
  }

  public BigDecimal getLaborHours() {
    return laborHours;
  }

  public BigDecimal getActualCost() {
    return actualCost;
  }

  public String getCurrency() {
    return currency;
  }

  public WorkLogStatus getStatus() {
    return status;
  }

  public Instant getSubmittedAt() {
    return submittedAt;
  }

  public UUID getVerifiedByUserId() {
    return verifiedByUserId;
  }

  public Instant getVerifiedAt() {
    return verifiedAt;
  }

  public UUID getBeforeEvidenceId() {
    return beforeEvidenceId;
  }

  public UUID getAfterEvidenceId() {
    return afterEvidenceId;
  }

  public void setEvidencePair(UUID beforeEvidenceId, UUID afterEvidenceId) {
    if (beforeEvidenceId != null && beforeEvidenceId.equals(afterEvidenceId)) {
      throw new IllegalArgumentException("Before and after evidence must be distinct");
    }
    this.beforeEvidenceId = beforeEvidenceId;
    this.afterEvidenceId = afterEvidenceId;
    this.updatedAt = Instant.now();
  }

  public void submit(Instant submitInstant) {
    this.status = WorkLogStatus.SUBMITTED;
    this.submittedAt = submitInstant;
    this.updatedAt = submitInstant;
  }

  public void verify(UUID verifierUserId, Instant verifyInstant) {
    this.status = WorkLogStatus.VERIFIED;
    this.verifiedByUserId = verifierUserId;
    this.verifiedAt = verifyInstant;
    this.updatedAt = verifyInstant;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
