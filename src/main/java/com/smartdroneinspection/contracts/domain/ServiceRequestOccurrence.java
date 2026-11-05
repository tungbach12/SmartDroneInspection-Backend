package com.smartdroneinspection.contracts.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "service_request_occurrences")
public class ServiceRequestOccurrence {

  @Id @GeneratedValue private UUID id;

  @Column(name = "request_id", nullable = false)
  private UUID requestId;

  @Column(name = "occurrence_index", nullable = false)
  private int occurrenceIndex;

  @Column(name = "planned_start_at", nullable = false)
  private Instant plannedStartAt;

  @Column(name = "planned_end_at", nullable = false)
  private Instant plannedEndAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private OccurrenceStatus status = OccurrenceStatus.PENDING_ASSIGNMENT;

  @Column(name = "inspector_id")
  private UUID inspectorId;

  @Column(name = "drone_id")
  private UUID droneId;

  @Column(name = "inspection_id", unique = true)
  private UUID inspectionId;

  @OneToOne(
      mappedBy = "occurrence",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.EAGER)
  private ServiceInvoice invoice;

  @OneToOne(
      mappedBy = "occurrence",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.EAGER)
  private ServicePayment payment;

  @OneToOne(
      mappedBy = "occurrence",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.EAGER)
  private InspectorPayout payout;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected ServiceRequestOccurrence() {}

  public ServiceRequestOccurrence(
      UUID requestId, int occurrenceIndex, Instant plannedStartAt, Instant plannedEndAt) {
    if (requestId == null
        || occurrenceIndex < 1
        || plannedStartAt == null
        || plannedEndAt == null
        || !plannedEndAt.isAfter(plannedStartAt)) {
      throw new IllegalArgumentException("Occurrence details are invalid");
    }
    this.requestId = requestId;
    this.occurrenceIndex = occurrenceIndex;
    this.plannedStartAt = plannedStartAt;
    this.plannedEndAt = plannedEndAt;
    this.invoice = new ServiceInvoice(this);
    this.payment = new ServicePayment(this);
    this.payout = new InspectorPayout(this);
  }

  public void snapshotFinance(
      BigDecimal totalPrice, BigDecimal vatRate, BigDecimal commissionRate, BigDecimal tncnRate) {
    invoice.snapshotFinance(totalPrice, vatRate, commissionRate, tncnRate);
    this.updatedAt = Instant.now();
  }

  public void snapshotFinance(
      String currency,
      BigDecimal totalPrice,
      BigDecimal serviceBaseAmount,
      BigDecimal vatRate,
      BigDecimal vatAmount,
      BigDecimal commissionRate,
      BigDecimal commissionAmount,
      BigDecimal inspectorGrossPayout,
      BigDecimal tncnRate,
      BigDecimal tncnAmount,
      BigDecimal inspectorNetPayout) {
    snapshotFinance(totalPrice, vatRate, commissionRate, tncnRate);
  }

  public void acceptReport() {
    invoice.acceptReport();
    payment.paymentDue();
    updatedAt = Instant.now();
  }

  public void acceptReport(UUID versionId, UUID customerUserId) {
    if (versionId == null || customerUserId == null) {
      throw new IllegalArgumentException("Report version and customer user are required");
    }
    acceptReport();
  }

  public void confirmPayment(String reference, BigDecimal amount, UUID managerUserId) {
    validatePayment(reference, amount);
    payment.confirm(
        reference, payment.getPaymentMethod() != null ? payment.getPaymentMethod() : "MANUAL_WIRE");
    if (inspectorId != null) {
      payout.markAutoDisbursed(reference.trim(), payment.getPaymentConfirmedAt());
    } else {
      payout.markEligible();
    }
    updatedAt = payment.getPaymentConfirmedAt();
  }

  public void assignPayOSOrder(long orderCode) {
    payment.assignPayOSOrder(orderCode);
    updatedAt = Instant.now();
  }

  public void assignPayOSOrder(long orderCode, String paymentLinkId) {
    assignPayOSOrder(orderCode);
  }

  public void assignVnPayOrder(long orderCode) {
    payment.assignVnPayOrder(orderCode);
    updatedAt = Instant.now();
  }

  public void confirmOnlinePayment(String reference, BigDecimal amount, String method) {
    validatePayment(reference, amount);
    payment.confirm(reference, method);
    if (inspectorId != null) {
      payout.markAutoDisbursed(reference.trim(), payment.getPaymentConfirmedAt());
    } else {
      payout.markEligible();
    }
    updatedAt = payment.getPaymentConfirmedAt();
  }

  private void validatePayment(String reference, BigDecimal amount) {
    if (reference == null || reference.isBlank()) {
      throw new IllegalArgumentException("Payment reference is required");
    }
    if (amount == null) {
      throw new IllegalArgumentException("Payment amount is required");
    }
    if (getReportAcceptedAt() == null) {
      throw new IllegalStateException(
          "Customer must accept the report before payment can be confirmed");
    }
    if (getTotalPrice() != null && amount.compareTo(getTotalPrice()) != 0) {
      throw new IllegalArgumentException(
          "Payment amount must match agreed total: " + getTotalPrice());
    }
  }

  public void recordPayout(String reference, UUID managerUserId) {
    if (reference == null || reference.isBlank()) {
      throw new IllegalArgumentException("Payout reference is required");
    }
    if (getReportAcceptedAt() == null || getPaymentStatus() != OccurrencePaymentStatus.PAID) {
      throw new IllegalStateException("Payout requires both accepted report and confirmed payment");
    }
    if (inspectorId == null) {
      throw new IllegalStateException("No inspector assigned to receive payout");
    }
    payout.recordPayout(reference);
    updatedAt = payout.getPayoutRecordedAt();
  }

  public void assign(UUID inspectorId, UUID droneId, UUID inspectionId, UUID assignedByUserId) {
    assign(
        inspectorId,
        droneId,
        inspectionId,
        this.plannedStartAt,
        this.plannedEndAt,
        assignedByUserId);
  }

  public void assign(
      UUID inspectorId,
      UUID droneId,
      UUID inspectionId,
      Instant plannedStartAt,
      Instant plannedEndAt,
      UUID assignedByUserId) {
    if (status != OccurrenceStatus.PENDING_ASSIGNMENT) {
      throw new IllegalStateException("Occurrence is not awaiting assignment");
    }
    if (inspectorId == null
        || droneId == null
        || inspectionId == null
        || assignedByUserId == null) {
      throw new IllegalArgumentException("Occurrence assignment is incomplete");
    }
    reschedule(plannedStartAt, plannedEndAt);
    this.inspectorId = inspectorId;
    this.droneId = droneId;
    this.inspectionId = inspectionId;
    this.payout.assignInspector(inspectorId);
    this.status = OccurrenceStatus.ASSIGNED;
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getRequestId() {
    return requestId;
  }

  public int getOccurrenceIndex() {
    return occurrenceIndex;
  }

  public void reschedule(Instant start, Instant end) {
    if (status != OccurrenceStatus.PENDING_ASSIGNMENT) {
      throw new IllegalStateException("Only pending occurrences may be rescheduled");
    }
    if (start == null || end == null || !end.isAfter(start)) {
      throw new IllegalArgumentException("Occurrence end must be after start");
    }
    plannedStartAt = start;
    plannedEndAt = end;
    updatedAt = Instant.now();
  }

  public Instant getPlannedStartAt() {
    return plannedStartAt;
  }

  public Instant getPlannedEndAt() {
    return plannedEndAt;
  }

  public OccurrenceStatus getStatus() {
    return status;
  }

  public UUID getInspectorId() {
    return inspectorId;
  }

  public UUID getDroneId() {
    return droneId;
  }

  public UUID getInspectionId() {
    return inspectionId;
  }

  public Instant getAssignedAt() {
    return status == OccurrenceStatus.ASSIGNED ? updatedAt : null;
  }

  public String getCurrency() {
    return "VND";
  }

  public BigDecimal getTotalPrice() {
    return invoice.getTotalPrice();
  }

  public BigDecimal getServiceBaseAmount() {
    return invoice.getServiceBaseAmount();
  }

  public BigDecimal getVatRate() {
    return invoice.getVatRate();
  }

  public BigDecimal getVatAmount() {
    return invoice.getVatAmount();
  }

  public BigDecimal getCommissionRate() {
    return invoice.getCommissionRate();
  }

  public BigDecimal getCommissionAmount() {
    return invoice.getCommissionAmount();
  }

  public BigDecimal getInspectorGrossPayout() {
    return invoice.getInspectorGrossPayout();
  }

  public BigDecimal getTncnRate() {
    return invoice.getTncnRate();
  }

  public BigDecimal getTncnAmount() {
    return invoice.getTncnAmount();
  }

  public BigDecimal getInspectorNetPayout() {
    return invoice.getInspectorNetPayout();
  }

  public Instant getReportAcceptedAt() {
    return invoice.getReportAcceptedAt();
  }

  public UUID getReportAcceptedByUserId() {
    return null;
  }

  public UUID getAcceptedReportVersionId() {
    return null;
  }

  public OccurrencePaymentStatus getPaymentStatus() {
    return payment.getPaymentStatus();
  }

  public String getPaymentReference() {
    return payment.getPaymentReference();
  }

  public BigDecimal getPaymentAmount() {
    return getPaymentStatus() == OccurrencePaymentStatus.PAID ? getTotalPrice() : null;
  }

  public UUID getPaymentConfirmedByUserId() {
    return null;
  }

  public Instant getPaymentConfirmedAt() {
    return payment.getPaymentConfirmedAt();
  }

  public Long getPayosOrderCode() {
    return payment.getPayosOrderCode();
  }

  public String getPaymentMethod() {
    return payment.getPaymentMethod();
  }

  public OccurrencePayoutStatus getPayoutStatus() {
    return payout.getPayoutStatus();
  }

  public String getPayoutReference() {
    return payout.getPayoutReference();
  }

  public UUID getPayoutRecordedByUserId() {
    return null;
  }

  public Instant getPayoutRecordedAt() {
    return payout.getPayoutRecordedAt();
  }

  public ServiceInvoice getInvoice() {
    return invoice;
  }

  public ServicePayment getPayment() {
    return payment;
  }

  public InspectorPayout getPayout() {
    return payout;
  }

  public enum OccurrenceStatus {
    PENDING_ASSIGNMENT,
    ASSIGNED,
    CANCELLED
  }

  public enum OccurrencePaymentStatus {
    PENDING,
    PAYMENT_DUE,
    PAID
  }

  public enum OccurrencePayoutStatus {
    PENDING,
    ELIGIBLE,
    PAID
  }
}
