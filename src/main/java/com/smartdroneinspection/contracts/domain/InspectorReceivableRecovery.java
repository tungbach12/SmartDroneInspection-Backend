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
import java.util.UUID;

@Entity
@Table(name = "inspector_receivable_recoveries")
public class InspectorReceivableRecovery {

  @Id @GeneratedValue private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "receivable_id", nullable = false)
  private InspectorReceivable receivable;

  @Column(name = "amount", nullable = false, precision = 15, scale = 2)
  private BigDecimal amount;

  @Column(name = "external_reference", nullable = false, length = 255)
  private String externalReference;

  @Column(name = "note", nullable = false, length = 2000)
  private String note;

  @Column(name = "recorded_by_user_id", nullable = false)
  private UUID recordedByUserId;

  @Column(name = "recorded_at", nullable = false, updatable = false)
  private Instant recordedAt = Instant.now();

  protected InspectorReceivableRecovery() {}

  InspectorReceivableRecovery(
      InspectorReceivable receivable,
      BigDecimal amount,
      String reference,
      String note,
      UUID actorId) {
    this.receivable = receivable;
    this.amount = amount;
    this.externalReference = reference;
    this.note = note;
    this.recordedByUserId = actorId;
  }

  public UUID getId() {
    return id;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getExternalReference() {
    return externalReference;
  }

  public String getNote() {
    return note;
  }

  public UUID getRecordedByUserId() {
    return recordedByUserId;
  }

  public Instant getRecordedAt() {
    return recordedAt;
  }
}
