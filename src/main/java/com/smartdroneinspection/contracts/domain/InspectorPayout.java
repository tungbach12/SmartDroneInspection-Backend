package com.smartdroneinspection.contracts.domain;

import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence.OccurrencePayoutStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inspector_payouts")
public class InspectorPayout {

  @Id
  @Column(name = "occurrence_id")
  private UUID occurrenceId;

  @MapsId
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "occurrence_id")
  private ServiceRequestOccurrence occurrence;

  @Column(name = "inspector_id")
  private UUID inspectorId;

  @Enumerated(EnumType.STRING)
  @Column(name = "payout_status", nullable = false, length = 24)
  private OccurrencePayoutStatus payoutStatus = OccurrencePayoutStatus.PENDING;

  @Column(name = "payout_reference", length = 255)
  private String payoutReference;

  @Column(name = "payout_recorded_at")
  private Instant payoutRecordedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected InspectorPayout() {}

  InspectorPayout(ServiceRequestOccurrence occurrence) {
    this.occurrence = occurrence;
  }

  void assignInspector(UUID inspectorId) {
    this.inspectorId = inspectorId;
    updatedAt = Instant.now();
  }

  void markEligible() {
    if (payoutStatus == OccurrencePayoutStatus.PENDING) {
      payoutStatus = OccurrencePayoutStatus.ELIGIBLE;
      updatedAt = Instant.now();
    }
  }

  void markAutoDisbursed(String paymentReference, Instant paidAt) {
    payoutStatus = OccurrencePayoutStatus.PAID;
    payoutReference = "AUTO-DISBURSE-" + paymentReference;
    payoutRecordedAt = paidAt;
    updatedAt = paidAt;
  }

  void recordPayout(String reference) {
    if (payoutStatus == OccurrencePayoutStatus.PAID) {
      if (reference.trim().equals(payoutReference)) {
        return;
      }
      if (payoutReference != null && payoutReference.startsWith("AUTO-DISBURSE-")) {
        payoutReference = reference.trim();
        payoutRecordedAt = Instant.now();
        updatedAt = payoutRecordedAt;
        return;
      }
      throw new IllegalStateException(
          "Payout was already recorded with reference " + payoutReference);
    }
    payoutStatus = OccurrencePayoutStatus.PAID;
    payoutReference = reference.trim();
    payoutRecordedAt = Instant.now();
    updatedAt = payoutRecordedAt;
  }

  public UUID getOccurrenceId() {
    return occurrenceId;
  }

  public UUID getInspectorId() {
    return inspectorId;
  }

  public OccurrencePayoutStatus getPayoutStatus() {
    return payoutStatus;
  }

  public String getPayoutReference() {
    return payoutReference;
  }

  public Instant getPayoutRecordedAt() {
    return payoutRecordedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
