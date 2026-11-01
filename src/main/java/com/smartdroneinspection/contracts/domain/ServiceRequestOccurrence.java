package com.smartdroneinspection.contracts.domain;

import com.smartdroneinspection.contracts.service.InspectionFinanceCalculator;
import com.smartdroneinspection.contracts.service.InspectionFinanceCalculator.FinanceCalculation;
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

  // Financial rate snapshot (amounts are derived dynamically via InspectionFinanceCalculator)
  @Column(name = "total_price", precision = 15, scale = 2)
  private BigDecimal totalPrice;

  @Column(name = "vat_rate", precision = 5, scale = 2)
  private BigDecimal vatRate;

  @Column(name = "commission_rate", precision = 5, scale = 2)
  private BigDecimal commissionRate;

  @Column(name = "tncn_rate", precision = 5, scale = 2)
  private BigDecimal tncnRate;

  // Report Acceptance
  @Column(name = "report_accepted_at")
  private Instant reportAcceptedAt;

  // Customer Payment
  @Enumerated(EnumType.STRING)
  @Column(name = "payment_status", nullable = false, length = 24)
  private OccurrencePaymentStatus paymentStatus = OccurrencePaymentStatus.PENDING;

  @Column(name = "payment_reference", length = 255)
  private String paymentReference;

  @Column(name = "payment_confirmed_at")
  private Instant paymentConfirmedAt;

  @Column(name = "payos_order_code", unique = true)
  private Long payosOrderCode;

  @Column(name = "payment_method", length = 30)
  private String paymentMethod;

  // Inspector Payout
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
  }

  public void snapshotFinance(
      BigDecimal totalPrice, BigDecimal vatRate, BigDecimal commissionRate, BigDecimal tncnRate) {
    if (this.totalPrice != null) {
      return; // Immutable once snapshotted
    }
    this.totalPrice = totalPrice;
    this.vatRate = vatRate;
    this.commissionRate = commissionRate;
    this.tncnRate = tncnRate;
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

  private FinanceCalculation financeCalculation() {
    if (totalPrice == null
        || totalPrice.signum() <= 0
        || vatRate == null
        || commissionRate == null
        || tncnRate == null) {
      return null;
    }
    return InspectionFinanceCalculator.calculate(totalPrice, vatRate, commissionRate, tncnRate);
  }

  public void acceptReport() {
    if (this.reportAcceptedAt != null) {
      return; // Idempotent
    }
    this.reportAcceptedAt = Instant.now();
    if (this.paymentStatus == OccurrencePaymentStatus.PENDING) {
      this.paymentStatus = OccurrencePaymentStatus.PAYMENT_DUE;
    }
    this.updatedAt = reportAcceptedAt;
  }

  public void acceptReport(UUID versionId, UUID customerUserId) {
    if (versionId == null || customerUserId == null) {
      throw new IllegalArgumentException("Report version and customer user are required");
    }
    acceptReport();
  }

  public void confirmPayment(String reference, BigDecimal amount, UUID managerUserId) {
    if (reference == null || reference.isBlank()) {
      throw new IllegalArgumentException("Payment reference is required");
    }
    if (amount == null) {
      throw new IllegalArgumentException("Payment amount is required");
    }
    if (this.reportAcceptedAt == null) {
      throw new IllegalStateException(
          "Customer must accept the report before payment can be confirmed");
    }
    if (this.totalPrice != null && amount.compareTo(this.totalPrice) != 0) {
      throw new IllegalArgumentException(
          "Payment amount must match agreed total: " + this.totalPrice);
    }
    if (this.paymentStatus == OccurrencePaymentStatus.PAID) {
      if (reference.trim().equals(this.paymentReference)) {
        return; // Idempotent
      }
      throw new IllegalStateException("Payment was already confirmed with different details");
    }
    this.paymentStatus = OccurrencePaymentStatus.PAID;
    this.paymentReference = reference.trim();
    this.paymentMethod = (this.paymentMethod != null) ? this.paymentMethod : "MANUAL_WIRE";
    this.paymentConfirmedAt = Instant.now();
    if (this.payoutStatus == OccurrencePayoutStatus.PENDING) {
      this.payoutStatus = OccurrencePayoutStatus.ELIGIBLE;
    }
    this.updatedAt = paymentConfirmedAt;
  }

  public void assignPayOSOrder(long orderCode) {
    this.payosOrderCode = orderCode;
    this.paymentMethod = "PAYOS";
    this.updatedAt = Instant.now();
  }

  public void assignPayOSOrder(long orderCode, String paymentLinkId) {
    assignPayOSOrder(orderCode);
  }

  public void confirmOnlinePayment(String reference, BigDecimal amount, String method) {
    if (reference == null || reference.isBlank()) {
      throw new IllegalArgumentException("Payment reference is required");
    }
    if (amount == null) {
      throw new IllegalArgumentException("Payment amount is required");
    }
    if (this.reportAcceptedAt == null) {
      throw new IllegalStateException(
          "Customer must accept the report before payment can be confirmed");
    }
    if (this.totalPrice != null && amount.compareTo(this.totalPrice) != 0) {
      throw new IllegalArgumentException(
          "Payment amount must match agreed total: " + this.totalPrice);
    }
    if (this.paymentStatus == OccurrencePaymentStatus.PAID) {
      if (reference.trim().equals(this.paymentReference)) {
        return; // Idempotent
      }
      throw new IllegalStateException("Payment was already confirmed with different details");
    }
    this.paymentStatus = OccurrencePaymentStatus.PAID;
    this.paymentReference = reference.trim();
    this.paymentMethod = (method != null && !method.isBlank()) ? method : "PAYOS";
    this.paymentConfirmedAt = Instant.now();
    if (this.payoutStatus == OccurrencePayoutStatus.PENDING) {
      this.payoutStatus = OccurrencePayoutStatus.ELIGIBLE;
    }
    this.updatedAt = paymentConfirmedAt;
  }

  public void recordPayout(String reference, UUID managerUserId) {
    if (reference == null || reference.isBlank()) {
      throw new IllegalArgumentException("Payout reference is required");
    }
    if (this.reportAcceptedAt == null || this.paymentStatus != OccurrencePaymentStatus.PAID) {
      throw new IllegalStateException("Payout requires both accepted report and confirmed payment");
    }
    if (this.inspectorId == null) {
      throw new IllegalStateException("No inspector assigned to receive payout");
    }
    if (this.payoutStatus == OccurrencePayoutStatus.PAID) {
      if (reference.trim().equals(this.payoutReference)) {
        return; // Idempotent
      }
      throw new IllegalStateException(
          "Payout was already recorded with reference " + this.payoutReference);
    }
    this.payoutStatus = OccurrencePayoutStatus.PAID;
    this.payoutReference = reference.trim();
    this.payoutRecordedAt = Instant.now();
    this.updatedAt = payoutRecordedAt;
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
    return totalPrice;
  }

  public BigDecimal getServiceBaseAmount() {
    FinanceCalculation calc = financeCalculation();
    return calc != null ? calc.serviceBaseAmount() : null;
  }

  public BigDecimal getVatRate() {
    return vatRate;
  }

  public BigDecimal getVatAmount() {
    FinanceCalculation calc = financeCalculation();
    return calc != null ? calc.vatAmount() : null;
  }

  public BigDecimal getCommissionRate() {
    return commissionRate;
  }

  public BigDecimal getCommissionAmount() {
    FinanceCalculation calc = financeCalculation();
    return calc != null ? calc.commissionAmount() : null;
  }

  public BigDecimal getInspectorGrossPayout() {
    FinanceCalculation calc = financeCalculation();
    return calc != null ? calc.inspectorGrossPayout() : null;
  }

  public BigDecimal getTncnRate() {
    return tncnRate;
  }

  public BigDecimal getTncnAmount() {
    FinanceCalculation calc = financeCalculation();
    return calc != null ? calc.tncnAmount() : null;
  }

  public BigDecimal getInspectorNetPayout() {
    FinanceCalculation calc = financeCalculation();
    return calc != null ? calc.inspectorNetPayout() : null;
  }

  public Instant getReportAcceptedAt() {
    return reportAcceptedAt;
  }

  public UUID getReportAcceptedByUserId() {
    return null;
  }

  public UUID getAcceptedReportVersionId() {
    return null;
  }

  public OccurrencePaymentStatus getPaymentStatus() {
    return paymentStatus;
  }

  public String getPaymentReference() {
    return paymentReference;
  }

  public BigDecimal getPaymentAmount() {
    return paymentStatus == OccurrencePaymentStatus.PAID ? totalPrice : null;
  }

  public UUID getPaymentConfirmedByUserId() {
    return null;
  }

  public Instant getPaymentConfirmedAt() {
    return paymentConfirmedAt;
  }

  public Long getPayosOrderCode() {
    return payosOrderCode;
  }

  public String getPaymentMethod() {
    return paymentMethod;
  }

  public OccurrencePayoutStatus getPayoutStatus() {
    return payoutStatus;
  }

  public String getPayoutReference() {
    return payoutReference;
  }

  public UUID getPayoutRecordedByUserId() {
    return null;
  }

  public Instant getPayoutRecordedAt() {
    return payoutRecordedAt;
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
