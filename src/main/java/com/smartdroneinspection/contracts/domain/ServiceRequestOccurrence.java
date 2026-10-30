package com.smartdroneinspection.contracts.domain;

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

  @Column(name = "assigned_by_user_id")
  private UUID assignedByUserId;

  @Column(name = "assigned_at")
  private Instant assignedAt;

  // Occurrence signed contract attachment metadata
  @Column(name = "contract_object_key", length = 1000)
  private String contractObjectKey;

  @Column(name = "contract_file_name", length = 500)
  private String contractFileName;

  @Column(name = "contract_content_type", length = 160)
  private String contractContentType;

  @Column(name = "contract_size_bytes")
  private Long contractSizeBytes;

  @Column(name = "contract_checksum_sha256", length = 64)
  private String contractChecksumSha256;

  @Column(name = "contract_uploaded_by_user_id")
  private UUID contractUploadedByUserId;

  @Column(name = "contract_uploaded_at")
  private Instant contractUploadedAt;

  // Financial snapshot (VAT split, platform commission, and Inspector payout)
  @Column(name = "currency", nullable = false, length = 3)
  private String currency = "VND";

  @Column(name = "total_price", precision = 15, scale = 2)
  private BigDecimal totalPrice;

  @Column(name = "service_base_amount", precision = 15, scale = 2)
  private BigDecimal serviceBaseAmount;

  @Column(name = "vat_rate", precision = 5, scale = 2)
  private BigDecimal vatRate;

  @Column(name = "vat_amount", precision = 15, scale = 2)
  private BigDecimal vatAmount;

  @Column(name = "commission_rate", precision = 5, scale = 2)
  private BigDecimal commissionRate;

  @Column(name = "commission_amount", precision = 15, scale = 2)
  private BigDecimal commissionAmount;

  /**
   * MVP assumption for accountant review: Tax base is defined as Inspector gross payout after
   * platform commission and before TNCN. Configured TNCN withholding rate is applied to this base.
   * This is a simplified product assumption for MVP workflow and accounting validation, NOT
   * statutory tax advice.
   */
  @Column(name = "inspector_gross_payout", precision = 15, scale = 2)
  private BigDecimal inspectorGrossPayout;

  @Column(name = "tncn_rate", precision = 5, scale = 2)
  private BigDecimal tncnRate;

  @Column(name = "tncn_amount", precision = 15, scale = 2)
  private BigDecimal tncnAmount;

  @Column(name = "inspector_net_payout", precision = 15, scale = 2)
  private BigDecimal inspectorNetPayout;

  // Report Acceptance
  @Column(name = "report_accepted_at")
  private Instant reportAcceptedAt;

  @Column(name = "report_accepted_by_user_id")
  private UUID reportAcceptedByUserId;

  @Column(name = "accepted_report_version_id")
  private UUID acceptedReportVersionId;

  // Customer Payment
  @Enumerated(EnumType.STRING)
  @Column(name = "payment_status", nullable = false, length = 24)
  private OccurrencePaymentStatus paymentStatus = OccurrencePaymentStatus.PENDING;

  @Column(name = "payment_reference", length = 255)
  private String paymentReference;

  @Column(name = "payment_amount", precision = 15, scale = 2)
  private BigDecimal paymentAmount;

  @Column(name = "payment_confirmed_by_user_id")
  private UUID paymentConfirmedByUserId;

  @Column(name = "payment_confirmed_at")
  private Instant paymentConfirmedAt;

  @Column(name = "payos_order_code", unique = true)
  private Long payosOrderCode;

  @Column(name = "payos_payment_link_id", length = 100)
  private String payosPaymentLinkId;

  @Column(name = "payment_method", length = 30)
  private String paymentMethod;

  // Inspector Payout
  @Enumerated(EnumType.STRING)
  @Column(name = "payout_status", nullable = false, length = 24)
  private OccurrencePayoutStatus payoutStatus = OccurrencePayoutStatus.PENDING;

  @Column(name = "payout_reference", length = 255)
  private String payoutReference;

  @Column(name = "payout_recorded_by_user_id")
  private UUID payoutRecordedByUserId;

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

  public boolean hasSignedContract() {
    return contractObjectKey != null && !contractObjectKey.isBlank();
  }

  public void recordSignedContract(
      String objectKey,
      String fileName,
      String contentType,
      long sizeBytes,
      String checksumSha256,
      UUID uploadedByUserId) {
    if (objectKey == null || objectKey.isBlank() || fileName == null || fileName.isBlank()) {
      throw new IllegalArgumentException("Contract file metadata is required");
    }
    this.contractObjectKey = objectKey;
    this.contractFileName = fileName;
    this.contractContentType = contentType;
    this.contractSizeBytes = sizeBytes;
    this.contractChecksumSha256 = checksumSha256;
    this.contractUploadedByUserId = uploadedByUserId;
    this.contractUploadedAt = Instant.now();
    this.updatedAt = contractUploadedAt;
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
    if (this.totalPrice != null) {
      // Immutable once snapshotted
      return;
    }
    this.currency = (currency != null && !currency.isBlank()) ? currency : "VND";
    this.totalPrice = totalPrice;
    this.serviceBaseAmount = serviceBaseAmount;
    this.vatRate = vatRate;
    this.vatAmount = vatAmount;
    this.commissionRate = commissionRate;
    this.commissionAmount = commissionAmount;
    this.inspectorGrossPayout = inspectorGrossPayout;
    this.tncnRate = tncnRate;
    this.tncnAmount = tncnAmount;
    this.inspectorNetPayout = inspectorNetPayout;
    this.updatedAt = Instant.now();
  }

  public void acceptReport(UUID versionId, UUID customerUserId) {
    if (versionId == null || customerUserId == null) {
      throw new IllegalArgumentException("Report version and customer user are required");
    }
    if (this.reportAcceptedAt != null) {
      if (versionId.equals(this.acceptedReportVersionId)) {
        return; // Idempotent
      }
      throw new IllegalStateException("A different report version was already accepted");
    }
    this.acceptedReportVersionId = versionId;
    this.reportAcceptedByUserId = customerUserId;
    this.reportAcceptedAt = Instant.now();
    if (this.paymentStatus == OccurrencePaymentStatus.PENDING) {
      this.paymentStatus = OccurrencePaymentStatus.PAYMENT_DUE;
    }
    this.updatedAt = reportAcceptedAt;
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
      if (reference.equals(this.paymentReference) && amount.compareTo(this.paymentAmount) == 0) {
        return; // Idempotent
      }
      throw new IllegalStateException("Payment was already confirmed with different details");
    }
    this.paymentStatus = OccurrencePaymentStatus.PAID;
    this.paymentReference = reference.trim();
    this.paymentAmount = amount;
    this.paymentMethod = (this.paymentMethod != null) ? this.paymentMethod : "MANUAL_WIRE";
    this.paymentConfirmedByUserId = managerUserId;
    this.paymentConfirmedAt = Instant.now();
    if (this.payoutStatus == OccurrencePayoutStatus.PENDING) {
      this.payoutStatus = OccurrencePayoutStatus.ELIGIBLE;
    }
    this.updatedAt = paymentConfirmedAt;
  }

  public void assignPayOSOrder(long orderCode, String paymentLinkId) {
    this.payosOrderCode = orderCode;
    this.payosPaymentLinkId = paymentLinkId;
    this.paymentMethod = "PAYOS";
    this.updatedAt = Instant.now();
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
      if (reference.equals(this.paymentReference) && amount.compareTo(this.paymentAmount) == 0) {
        return; // Idempotent
      }
      throw new IllegalStateException("Payment was already confirmed with different details");
    }
    this.paymentStatus = OccurrencePaymentStatus.PAID;
    this.paymentReference = reference.trim();
    this.paymentAmount = amount;
    this.paymentMethod = (method != null && !method.isBlank()) ? method : "PAYOS";
    this.paymentConfirmedByUserId = null;
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
      if (reference.equals(this.payoutReference)) {
        return; // Idempotent
      }
      throw new IllegalStateException(
          "Payout was already recorded with reference " + this.payoutReference);
    }
    this.payoutStatus = OccurrencePayoutStatus.PAID;
    this.payoutReference = reference.trim();
    this.payoutRecordedByUserId = managerUserId;
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
    this.assignedByUserId = assignedByUserId;
    this.assignedAt = Instant.now();
    this.status = OccurrenceStatus.ASSIGNED;
    this.updatedAt = assignedAt;
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

  public UUID getAssignedByUserId() {
    return assignedByUserId;
  }

  public Instant getAssignedAt() {
    return assignedAt;
  }

  public String getContractObjectKey() {
    return contractObjectKey;
  }

  public String getContractFileName() {
    return contractFileName;
  }

  public String getContractContentType() {
    return contractContentType;
  }

  public Long getContractSizeBytes() {
    return contractSizeBytes;
  }

  public String getContractChecksumSha256() {
    return contractChecksumSha256;
  }

  public UUID getContractUploadedByUserId() {
    return contractUploadedByUserId;
  }

  public Instant getContractUploadedAt() {
    return contractUploadedAt;
  }

  public String getCurrency() {
    return currency;
  }

  public BigDecimal getTotalPrice() {
    return totalPrice;
  }

  public BigDecimal getServiceBaseAmount() {
    return serviceBaseAmount;
  }

  public BigDecimal getVatRate() {
    return vatRate;
  }

  public BigDecimal getVatAmount() {
    return vatAmount;
  }

  public BigDecimal getCommissionRate() {
    return commissionRate;
  }

  public BigDecimal getCommissionAmount() {
    return commissionAmount;
  }

  public BigDecimal getInspectorGrossPayout() {
    return inspectorGrossPayout;
  }

  public BigDecimal getTncnRate() {
    return tncnRate;
  }

  public BigDecimal getTncnAmount() {
    return tncnAmount;
  }

  public BigDecimal getInspectorNetPayout() {
    return inspectorNetPayout;
  }

  public Instant getReportAcceptedAt() {
    return reportAcceptedAt;
  }

  public UUID getReportAcceptedByUserId() {
    return reportAcceptedByUserId;
  }

  public UUID getAcceptedReportVersionId() {
    return acceptedReportVersionId;
  }

  public OccurrencePaymentStatus getPaymentStatus() {
    return paymentStatus;
  }

  public String getPaymentReference() {
    return paymentReference;
  }

  public BigDecimal getPaymentAmount() {
    return paymentAmount;
  }

  public UUID getPaymentConfirmedByUserId() {
    return paymentConfirmedByUserId;
  }

  public Instant getPaymentConfirmedAt() {
    return paymentConfirmedAt;
  }

  public Long getPayosOrderCode() {
    return payosOrderCode;
  }

  public String getPayosPaymentLinkId() {
    return payosPaymentLinkId;
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
    return payoutRecordedByUserId;
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
