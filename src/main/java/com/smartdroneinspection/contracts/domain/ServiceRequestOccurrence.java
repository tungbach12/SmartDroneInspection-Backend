package com.smartdroneinspection.contracts.domain;

import com.smartdroneinspection.contracts.repository.InspectorReceivableRepository;
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
    payout.markEligible();
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
    payout.markEligible();
    updatedAt = payment.getPaymentConfirmedAt();
  }

  public ServiceRefund initiateRefund(
      BigDecimal amount,
      String requestReference,
      String reason,
      BigDecimal inspectorReceivableAmount,
      BigDecimal platformAbsorbedAmount,
      UUID managerId) {
    if (payment.getRefunds().stream()
        .anyMatch(r -> r.getStatus() == ServiceRefund.RefundStatus.INITIATED)) {
      throw new IllegalStateException("An existing refund must be completed or cancelled first");
    }
    boolean payoutAlreadyDisbursed = payout.getPayoutStatus() == OccurrencePayoutStatus.PAID;
    if (payoutAlreadyDisbursed && inspectorId == null) {
      throw new IllegalStateException("A completed Inspector payout must retain its recipient");
    }
    if (amount == null || amount.signum() <= 0) {
      throw new IllegalArgumentException("Refund amount must be positive");
    }
    BigDecimal receivable = requireNonNegative(inspectorReceivableAmount, "Inspector receivable");
    BigDecimal absorbed = requireNonNegative(platformAbsorbedAmount, "Platform absorbed amount");
    BigDecimal existingReceivablePlans =
        payment.getRefunds().stream()
            .filter(refund -> refund.getStatus() == ServiceRefund.RefundStatus.COMPLETED)
            .map(ServiceRefund::getInspectorReceivableAmount)
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (payoutAlreadyDisbursed
        && (receivable.add(absorbed).compareTo(amount.setScale(2, java.math.RoundingMode.HALF_UP))
                != 0
            || payout.getFinalPayoutAmount() == null
            || existingReceivablePlans.add(receivable).compareTo(payout.getFinalPayoutAmount())
                > 0)) {
      throw new IllegalArgumentException(
          "Post-payout recovery must equal the planned amount and cannot exceed the paid payout");
    }
    if (!payoutAlreadyDisbursed && (receivable.signum() != 0 || absorbed.signum() != 0)) {
      throw new IllegalArgumentException(
          "Inspector receivable and absorption plans apply only after payout was disbursed");
    }
    ServiceRefund refund =
        payment.initiateRefund(
            getTotalPrice(),
            amount,
            requestReference,
            reason,
            receivable,
            absorbed,
            managerId,
            payoutAlreadyDisbursed);
    if (!payoutAlreadyDisbursed) {
      if (!payout.isHeldForRefund()) {
        payout.holdForRefund();
      }
    } else if (inspectorId == null) {
      throw new IllegalStateException(
          "A paid Inspector payout must retain its recipient before a refund");
    }
    updatedAt = Instant.now();
    return refund;
  }

  public void completeRefund(
      UUID refundId,
      BigDecimal inspectorPayoutReduction,
      BigDecimal platformCommissionAdjustment,
      BigDecimal vatAdjustment,
      BigDecimal tncnAdjustment,
      BigDecimal platformAbsorbedAmount,
      String externalReference,
      UUID managerId,
      InspectorReceivableRepository receivables) {
    ServiceRefund refund = findRefund(refundId);
    if (payment.getRefunds().stream()
        .anyMatch(
            existing ->
                !existing.getId().equals(refundId)
                    && existing.getStatus() == ServiceRefund.RefundStatus.COMPLETED
                    && existing.isAllocationMissing())) {
      throw new IllegalStateException(
          "Reconcile legacy refunds before completing this refund allocation");
    }
    validateRefundAllocationWithinFinancialBreakdown(
        refundId,
        refund.getAmount(),
        inspectorPayoutReduction,
        platformCommissionAdjustment,
        vatAdjustment,
        tncnAdjustment);
    BigDecimal normalizedInspectorReduction =
        requireNonNegative(inspectorPayoutReduction, "Inspector payout reduction");
    BigDecimal normalizedAllocationCommission =
        requireNonNegative(platformCommissionAdjustment, "Platform commission adjustment");
    BigDecimal normalizedAllocationVat = requireNonNegative(vatAdjustment, "VAT adjustment");
    BigDecimal normalizedAllocationTncn = requireNonNegative(tncnAdjustment, "TNCN adjustment");
    BigDecimal normalizedAllocationAbsorbed =
        requireNonNegative(platformAbsorbedAmount, "Platform absorbed amount");
    BigDecimal allocationTotal =
        normalizedInspectorReduction
            .add(normalizedAllocationCommission)
            .add(normalizedAllocationVat)
            .add(normalizedAllocationTncn)
            .add(normalizedAllocationAbsorbed);
    if (allocationTotal.compareTo(refund.getAmount()) != 0) {
      throw new IllegalArgumentException("Refund allocation must exactly equal the refund amount");
    }
    BigDecimal intendedReceivable =
        requireNonNegative(refund.getInspectorReceivableAmount(), "Inspector receivable");
    if (payout.getPayoutStatus() == OccurrencePayoutStatus.PAID
        && (normalizedInspectorReduction.add(normalizedAllocationTncn).compareTo(intendedReceivable)
                != 0
            || normalizedAllocationAbsorbed.compareTo(refund.getPlatformAbsorbedPlanAmount())
                != 0)) {
      throw new IllegalArgumentException(
          "Completed allocation differs from the initiated receivable/platform plan");
    }
    if (payout.getPayoutStatus() != OccurrencePayoutStatus.PAID
        && getRemainingInspectorNetPayout() != null
        && normalizedInspectorReduction.compareTo(getRemainingInspectorNetPayout()) > 0) {
      throw new IllegalArgumentException("Refund cannot exceed the unpaid Inspector payout");
    }
    payment.completeRefund(
        refund,
        inspectorPayoutReduction,
        platformCommissionAdjustment,
        vatAdjustment,
        tncnAdjustment,
        platformAbsorbedAmount,
        externalReference,
        managerId,
        getTotalPrice());
    if (payout.getPayoutStatus() == OccurrencePayoutStatus.PAID
        && refund.getInspectorReceivableAmount().signum() > 0
        && receivables.findByRefundId(refund.getId()).isEmpty()) {
      receivables.save(
          new InspectorReceivable(
              refund.getId(),
              this,
              inspectorId,
              refund.getInspectorReceivableAmount(),
              externalReference,
              "Inspector payout recovery for customer refund " + refund.getRequestReference(),
              managerId));
    }
    releaseRefundHoldIfResolved();
    updatedAt = Instant.now();
  }

  public void cancelRefund(UUID refundId, String reason, UUID managerId) {
    payment.cancelRefund(findRefund(refundId), reason, managerId);
    releaseRefundHoldIfResolved();
    updatedAt = Instant.now();
  }

  private ServiceRefund findRefund(UUID refundId) {
    return payment.getRefunds().stream()
        .filter(refund -> refundId.equals(refund.getId()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Refund not found for occurrence"));
  }

  public java.util.List<InspectorReceivable> getInspectorReceivables(
      InspectorReceivableRepository receivables) {
    return receivables.findAllByOccurrenceId(id);
  }

  private BigDecimal requireNonNegative(BigDecimal value, String label) {
    if (value == null || value.signum() < 0)
      throw new IllegalArgumentException(label + " must be non-negative");
    return value.setScale(2, java.math.RoundingMode.HALF_UP);
  }

  private void releaseRefundHoldIfResolved() {
    boolean hasPending =
        payment.getRefunds().stream()
            .anyMatch(refund -> refund.getStatus() == ServiceRefund.RefundStatus.INITIATED);
    boolean hasUnallocatedLegacy =
        payment.getRefunds().stream()
            .anyMatch(
                refund ->
                    refund.getStatus() == ServiceRefund.RefundStatus.COMPLETED
                        && refund.isAllocationMissing());
    if (!hasPending
        && !hasUnallocatedLegacy
        && payout.getPayoutStatus() != OccurrencePayoutStatus.PAID) {
      payout.releaseRefundHold();
    }
  }

  public void reconcileRefundAllocation(
      UUID refundId,
      BigDecimal inspectorReceivablePlan,
      BigDecimal platformAbsorbedPlan,
      BigDecimal inspectorPayoutReduction,
      BigDecimal platformCommissionAdjustment,
      BigDecimal vatAdjustment,
      BigDecimal tncnAdjustment,
      BigDecimal platformAbsorbedAmount,
      UUID managerId,
      String reference,
      String note,
      InspectorReceivableRepository receivables) {
    ServiceRefund refund = findRefund(refundId);
    if (refund.getStatus() != ServiceRefund.RefundStatus.COMPLETED
        || !refund.isAllocationMissing()) {
      throw new IllegalStateException(
          "Only a completed legacy refund with missing allocation can be reconciled");
    }
    validateRefundAllocationWithinFinancialBreakdown(
        refundId,
        refund.getAmount(),
        inspectorPayoutReduction,
        platformCommissionAdjustment,
        vatAdjustment,
        tncnAdjustment);
    BigDecimal platformAmount =
        requireNonNegative(platformAbsorbedAmount, "Platform absorbed amount");
    BigDecimal total =
        inspectorPayoutReduction
            .add(platformCommissionAdjustment)
            .add(vatAdjustment)
            .add(tncnAdjustment)
            .add(platformAmount);
    if (total.compareTo(refund.getAmount()) != 0)
      throw new IllegalArgumentException("Allocation must equal legacy refund amount");
    BigDecimal normalizedInspectorReduction =
        requireNonNegative(inspectorPayoutReduction, "Inspector payout reduction");
    BigDecimal normalizedTncnAdjustment = requireNonNegative(tncnAdjustment, "TNCN adjustment");
    BigDecimal receivableAmount =
        payout.getPayoutStatus() == OccurrencePayoutStatus.PAID
            ? normalizedInspectorReduction.add(normalizedTncnAdjustment)
            : BigDecimal.ZERO;
    BigDecimal normalizedReceivablePlan =
        requireNonNegative(inspectorReceivablePlan, "Inspector receivable plan");
    BigDecimal normalizedPlatformPlan =
        requireNonNegative(platformAbsorbedPlan, "Platform absorbed plan");
    if (payout.getPayoutStatus() == OccurrencePayoutStatus.PAID
        && (receivableAmount.compareTo(normalizedReceivablePlan) != 0
            || platformAmount.compareTo(normalizedPlatformPlan) != 0
            || payout.getFinalPayoutAmount() == null
            || normalizedReceivablePlan.compareTo(payout.getFinalPayoutAmount()) > 0)) {
      throw new IllegalArgumentException(
          "Legacy recovery allocation must match the reviewed receivable plan");
    }
    if (payout.getPayoutStatus() != OccurrencePayoutStatus.PAID
        && normalizedReceivablePlan.signum() != 0) {
      throw new IllegalArgumentException(
          "Inspector receivable plan applies only after payout was disbursed");
    }
    refund.setLegacyRecoveryPlan(normalizedReceivablePlan, normalizedPlatformPlan);
    payment.reconcileLegacyRefundAllocation(
        refund,
        inspectorPayoutReduction,
        platformCommissionAdjustment,
        vatAdjustment,
        tncnAdjustment,
        platformAmount,
        managerId,
        reference,
        note);
    if (receivableAmount.signum() > 0 && receivables.findByRefundId(refund.getId()).isEmpty()) {
      receivables.save(
          new InspectorReceivable(
              refund.getId(), this, inspectorId, receivableAmount, reference, note, managerId));
    }
    releaseRefundHoldIfResolved();
    updatedAt = Instant.now();
  }

  public void recordReceivableRecovery(
      UUID receivableId,
      BigDecimal amount,
      String reference,
      String note,
      UUID managerId,
      InspectorReceivableRepository receivables) {
    InspectorReceivable receivable =
        receivables
            .findByIdForUpdate(receivableId)
            .orElseThrow(() -> new IllegalArgumentException("Inspector receivable not found"));
    if (!id.equals(receivable.getOccurrenceId()))
      throw new IllegalArgumentException("Receivable belongs to another occurrence");
    receivable.recordRecovery(amount, reference, note, managerId);
    receivables.save(receivable);
  }

  private void validateRefundAllocationWithinFinancialBreakdown(
      UUID refundId,
      BigDecimal refundAmount,
      BigDecimal inspectorReduction,
      BigDecimal commissionAdjustment,
      BigDecimal vatAdjustment,
      BigDecimal tncnAdjustment) {
    if (refundAmount == null || refundAmount.signum() <= 0) {
      throw new IllegalArgumentException("Refund amount must be positive");
    }
    validateAllocationComponent(
        refundId, inspectorReduction, getInspectorNetPayout(), "Inspector payout");
    validateAllocationComponent(
        refundId, commissionAdjustment, getCommissionAmount(), "Platform commission");
    validateAllocationComponent(refundId, vatAdjustment, getVatAmount(), "VAT");
    validateAllocationComponent(refundId, tncnAdjustment, getTncnAmount(), "TNCN");
  }

  private void validateAllocationComponent(
      UUID refundId, BigDecimal requested, BigDecimal maximum, String label) {
    if (requested == null || requested.signum() < 0) {
      throw new IllegalArgumentException(label + " refund allocation must be non-negative");
    }
    if (maximum == null) {
      if (requested.signum() != 0) {
        throw new IllegalArgumentException(
            label + " allocation is unavailable for this occurrence");
      }
      return;
    }
    BigDecimal prior = BigDecimal.ZERO;
    for (ServiceRefund refund : payment.getRefunds()) {
      if (!refund.getId().equals(refundId)
          && refund.getStatus() != ServiceRefund.RefundStatus.COMPLETED) {
        continue;
      }
      if (refund.getId().equals(refundId)) {
        continue;
      }
      BigDecimal priorValue =
          switch (label) {
            case "Inspector payout" -> refund.getInspectorPayoutReduction();
            case "Platform commission" -> refund.getPlatformCommissionAdjustment();
            case "VAT" -> refund.getVatAdjustment();
            case "TNCN" -> refund.getTncnAdjustment();
            default -> BigDecimal.ZERO;
          };
      if (priorValue == null) {
        if (refund.getStatus() == ServiceRefund.RefundStatus.COMPLETED
            && refund.isAllocationMissing()) {
          continue;
        }
        throw new IllegalStateException(
            "Existing refund allocation must be reconciled before recording another refund");
      }
      prior = prior.add(priorValue);
    }
    if (prior.add(requested).compareTo(maximum) > 0) {
      throw new IllegalArgumentException(label + " refund allocation exceeds the remaining amount");
    }
  }

  private void validateRecordedRefundAllocations(
      BigDecimal inspectorReduction,
      BigDecimal commissionAdjustment,
      BigDecimal vatAdjustment,
      BigDecimal tncnAdjustment,
      BigDecimal platformAbsorbedAmount) {
    BigDecimal[] requested = {
      inspectorReduction,
      commissionAdjustment,
      vatAdjustment,
      tncnAdjustment,
      platformAbsorbedAmount
    };
    BigDecimal[] recorded = {
      sumRefundAllocation(ServiceRefund::getInspectorPayoutReduction),
      sumRefundAllocation(ServiceRefund::getPlatformCommissionAdjustment),
      sumRefundAllocation(ServiceRefund::getVatAdjustment),
      sumRefundAllocation(ServiceRefund::getTncnAdjustment),
      sumRefundAllocation(ServiceRefund::getPlatformAbsorbedAmount)
    };
    for (int i = 0; i < requested.length; i++) {
      if (requested[i] == null || requested[i].compareTo(recorded[i]) != 0) {
        throw new IllegalArgumentException(
            "Reconciliation allocation must match the recorded refund allocation");
      }
    }
  }

  private BigDecimal sumRefundAllocation(
      java.util.function.Function<ServiceRefund, BigDecimal> field) {
    BigDecimal sum = BigDecimal.ZERO;
    for (ServiceRefund refund : payment.getRefunds()) {
      BigDecimal amount = field.apply(refund);
      if (amount == null) {
        throw new IllegalStateException(
            "Legacy refunds must be reconciled individually from external evidence");
      }
      sum = sum.add(amount);
    }
    return sum;
  }

  public boolean isPayoutHeldForRefund() {
    return payout.isHeldForRefund();
  }

  public void clearUnpaidPaymentOrder(long orderCode, String method) {
    payment.clearUnpaidGatewayOrder(orderCode, method);
    updatedAt = Instant.now();
  }

  public boolean hasActivePaymentOrder(String method) {
    return payment.hasActiveGatewayOrder(method);
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
    payout.recordPayout(reference, managerUserId, getRemainingInspectorNetPayout());
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

  public BigDecimal getRemainingInspectorNetPayout() {
    BigDecimal original = getInspectorNetPayout();
    if (original == null) {
      return null;
    }
    BigDecimal reductions = BigDecimal.ZERO;
    for (ServiceRefund refund : payment.getRefunds()) {
      if (refund.getStatus() != ServiceRefund.RefundStatus.COMPLETED) {
        continue;
      }
      BigDecimal reduction = refund.getInspectorPayoutReduction();
      if (reduction == null) {
        if (refund.getStatus() == ServiceRefund.RefundStatus.INITIATED) {
          continue;
        }
        return null;
      }
      reductions = reductions.add(reduction);
    }
    return original.subtract(reductions).setScale(2, java.math.RoundingMode.HALF_UP);
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

  public BigDecimal getRefundedAmount() {
    return payment.getRefundedAmount();
  }

  public String getRefundStatus() {
    return payment.getRefundStatus();
  }

  public java.util.List<ServiceRefund> getRefunds() {
    return payment.getRefunds();
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

  public BigDecimal getFinalPayoutAmount() {
    return payout.getFinalPayoutAmount();
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
