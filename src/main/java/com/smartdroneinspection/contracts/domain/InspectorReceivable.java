package com.smartdroneinspection.contracts.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Attributable amount owed by an Inspector after a refund follows an already-paid payout. */
@Entity
@Table(name = "inspector_receivables")
public class InspectorReceivable {

  @Id @GeneratedValue private UUID id;

  @Column(name = "refund_id", nullable = false, unique = true)
  private UUID refundId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "occurrence_id", nullable = false)
  private ServiceRequestOccurrence occurrence;

  @Column(name = "occurrence_id", insertable = false, updatable = false)
  private UUID occurrenceId;

  @Column(name = "inspector_id", nullable = false)
  private UUID inspectorId;

  @Column(name = "assessed_amount", nullable = false, precision = 15, scale = 2)
  private BigDecimal assessedAmount;

  @Column(name = "source_reference", nullable = false, length = 255)
  private String sourceReference;

  @Column(name = "reason", nullable = false, length = 2000)
  private String reason;

  @Column(name = "recorded_by_user_id", nullable = false)
  private UUID recordedByUserId;

  @Column(name = "recorded_at", nullable = false, updatable = false)
  private Instant recordedAt = Instant.now();

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @jakarta.persistence.OneToMany(
      mappedBy = "receivable",
      cascade = jakarta.persistence.CascadeType.ALL,
      orphanRemoval = true)
  private List<InspectorReceivableRecovery> recoveries = new ArrayList<>();

  @jakarta.persistence.Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected InspectorReceivable() {}

  public InspectorReceivable(
      UUID refundId,
      ServiceRequestOccurrence occurrence,
      UUID inspectorId,
      BigDecimal assessedAmount,
      String sourceReference,
      String reason,
      UUID recordedByUserId) {
    UUID occurrenceId = occurrence == null ? null : occurrence.getId();
    if (refundId == null
        || occurrenceId == null
        || inspectorId == null
        || recordedByUserId == null
        || assessedAmount == null
        || assessedAmount.signum() <= 0
        || sourceReference == null
        || sourceReference.isBlank()
        || reason == null
        || reason.isBlank()) {
      throw new IllegalArgumentException("Inspector receivable details are required");
    }
    this.refundId = refundId;
    this.occurrence = occurrence;
    this.occurrenceId = occurrenceId;
    this.inspectorId = inspectorId;
    this.assessedAmount = assessedAmount.setScale(2, java.math.RoundingMode.HALF_UP);
    this.sourceReference = sourceReference.trim();
    this.reason = reason.trim();
    this.recordedByUserId = recordedByUserId;
  }

  public BigDecimal getRecoveredAmount() {
    return recoveries.stream()
        .map(InspectorReceivableRecovery::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  public BigDecimal getOutstandingAmount() {
    return assessedAmount
        .subtract(getRecoveredAmount())
        .setScale(2, java.math.RoundingMode.HALF_UP);
  }

  public void recordRecovery(BigDecimal amount, String reference, String note, UUID actorId) {
    if (amount == null
        || amount.signum() <= 0
        || reference == null
        || reference.isBlank()
        || note == null
        || note.isBlank()
        || actorId == null) {
      throw new IllegalArgumentException("Receivable recovery details are required");
    }
    BigDecimal normalized = amount.setScale(2, java.math.RoundingMode.HALF_UP);
    if (normalized.signum() <= 0) {
      throw new IllegalArgumentException(
          "Recovery amount must be positive after currency rounding");
    }
    if (normalized.compareTo(getOutstandingAmount()) > 0) {
      throw new IllegalArgumentException("Recovery cannot exceed the outstanding receivable");
    }
    recoveries.add(
        new InspectorReceivableRecovery(this, normalized, reference.trim(), note.trim(), actorId));
  }

  public UUID getId() {
    return id;
  }

  public UUID getRefundId() {
    return refundId;
  }

  public UUID getOccurrenceId() {
    return occurrenceId;
  }

  public UUID getInspectorId() {
    return inspectorId;
  }

  public BigDecimal getAssessedAmount() {
    return assessedAmount;
  }

  public String getSourceReference() {
    return sourceReference;
  }

  public String getReason() {
    return reason;
  }

  public UUID getRecordedByUserId() {
    return recordedByUserId;
  }

  public Instant getRecordedAt() {
    return recordedAt;
  }

  public List<InspectorReceivableRecovery> getRecoveries() {
    return List.copyOf(recoveries);
  }
}
