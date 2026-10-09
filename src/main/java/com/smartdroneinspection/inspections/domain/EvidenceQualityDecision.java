package com.smartdroneinspection.inspections.domain;

import com.smartdroneinspection.inspections.domain.enums.EvidenceQualityDecisionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The assigned Inspector's substantive adequacy decision (MF3-03). Only an accepted decision makes
 * the evidence set eligible for advisory detection and report drafting.
 */
@Entity
@Table(name = "evidence_quality_decisions")
public class EvidenceQualityDecision {

  @Id @GeneratedValue private UUID id;

  @Column(name = "inspection_id", nullable = false)
  private UUID inspectionId;

  @Column(name = "field_session_id")
  private UUID fieldSessionId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private EvidenceQualityDecisionType decision;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "shot_list_comparison", columnDefinition = "jsonb")
  private Map<String, String> shotListComparison;

  @Column(name = "limitation_reason", length = 2000)
  private String limitationReason;

  @Column(name = "decided_by_user_id", nullable = false)
  private UUID decidedByUserId;

  @Column(name = "decided_at", nullable = false)
  private Instant decidedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected EvidenceQualityDecision() {}

  public EvidenceQualityDecision(
      UUID inspectionId,
      UUID fieldSessionId,
      EvidenceQualityDecisionType decision,
      String shotListComparison,
      String limitationReason,
      UUID decidedByUserId) {
    this.inspectionId = inspectionId;
    this.fieldSessionId = fieldSessionId;
    this.decision = decision;
    // shot_list_comparison is a jsonb column while the Inspector writes prose, so the text is
    // stored as a single summary entry and handed back unchanged through the API.
    this.shotListComparison =
        shotListComparison == null || shotListComparison.isBlank()
            ? null
            : Map.of("summary", shotListComparison);
    this.limitationReason = limitationReason;
    this.decidedByUserId = decidedByUserId;
    this.decidedAt = Instant.now();
    this.createdAt = decidedAt;
  }

  public boolean isAccepted() {
    return decision == EvidenceQualityDecisionType.ACCEPTED;
  }

  public UUID getId() {
    return id;
  }

  public UUID getInspectionId() {
    return inspectionId;
  }

  public UUID getFieldSessionId() {
    return fieldSessionId;
  }

  public EvidenceQualityDecisionType getDecision() {
    return decision;
  }

  public String getShotListComparison() {
    return shotListComparison == null ? null : shotListComparison.get("summary");
  }

  public String getLimitationReason() {
    return limitationReason;
  }

  public UUID getDecidedByUserId() {
    return decidedByUserId;
  }

  public Instant getDecidedAt() {
    return decidedAt;
  }
}
