package com.smartdroneinspection.inspectionrequests.domain;

import com.smartdroneinspection.inspectionrequests.domain.enums.InspectionOrderStatus;
import com.smartdroneinspection.inspectionrequests.domain.enums.InspectionQuotationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "inspection_service_orders")
public class InspectionServiceOrder {

  @Id @GeneratedValue private UUID id;

  @Column(name = "order_number", nullable = false, length = 64)
  private String orderNumber;

  @Column(name = "approved_quotation_id", nullable = false)
  private UUID approvedQuotationId;

  @Column(name = "inspection_request_id", nullable = false)
  private UUID inspectionRequestId;

  @Column(name = "provider_id")
  private UUID providerId;

  @Column(name = "flight_permit_no", length = 128)
  private String flightPermitNo;

  @Column(name = "confirmed_by_user_id", nullable = false)
  private UUID confirmedByUserId;

  @Column(name = "confirmed_at", nullable = false)
  private Instant confirmedAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "scope_snapshot", nullable = false, columnDefinition = "jsonb")
  private String scopeSnapshot;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "deliverables", nullable = false, columnDefinition = "jsonb")
  private String deliverables;

  @Column(name = "payment_terms", nullable = false, length = 2000)
  private String paymentTerms;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private InspectionOrderStatus status;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected InspectionServiceOrder() {}

  public static InspectionServiceOrder fromApprovedQuotation(
      InspectionQuotation quotation,
      String orderNumber,
      UUID confirmedByUserId,
      String deliverables) {
    Objects.requireNonNull(quotation, "Quotation is required");
    if (quotation.getStatus() != InspectionQuotationStatus.APPROVED) {
      throw new IllegalStateException("Only approved quotations can create service orders");
    }
    if (orderNumber == null || orderNumber.isBlank()) {
      throw new IllegalArgumentException("Order number must not be blank");
    }
    if (deliverables == null || deliverables.isBlank()) {
      throw new IllegalArgumentException("Deliverables must not be blank");
    }
    InspectionServiceOrder order = new InspectionServiceOrder();
    order.orderNumber = orderNumber;
    order.approvedQuotationId = quotation.getId();
    order.inspectionRequestId = quotation.getInspectionRequestId();
    order.providerId = quotation.getProviderId();
    order.confirmedByUserId = Objects.requireNonNull(confirmedByUserId, "Confirmer is required");
    order.confirmedAt = Instant.now();
    order.scopeSnapshot = quotation.getScopeSnapshot();
    order.deliverables = deliverables;
    order.paymentTerms = quotation.getPaymentTerms();
    order.status = InspectionOrderStatus.CONFIRMED;
    order.createdAt = Instant.now();
    order.updatedAt = order.createdAt;
    return order;
  }

  public void markAssignmentPending() {
    if (status != InspectionOrderStatus.CONFIRMED) {
      throw new IllegalStateException("Only confirmed orders can await assignment");
    }
    status = InspectionOrderStatus.ASSIGNMENT_PENDING;
    updatedAt = Instant.now();
  }

  /** MF2-06: PROVIDER_MANAGER mission plan approval moves the order to READY_FOR_FLIGHT. */
  public void markReadyForFlight() {
    if (status != InspectionOrderStatus.CONFIRMED
        && status != InspectionOrderStatus.ASSIGNMENT_PENDING
        && status != InspectionOrderStatus.READY_FOR_INSPECTION) {
      throw new IllegalStateException(
          "Only a confirmed or awaiting-assignment order can go READY_FOR_FLIGHT");
    }
    status = InspectionOrderStatus.READY_FOR_FLIGHT;
    updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getProviderId() {
    return providerId;
  }

  public void setProviderId(UUID providerId) {
    this.providerId = providerId;
    this.updatedAt = Instant.now();
  }

  public String getFlightPermitNo() {
    return flightPermitNo;
  }

  public void setFlightPermitNo(String flightPermitNo) {
    this.flightPermitNo = flightPermitNo;
    this.updatedAt = Instant.now();
  }

  public String getOrderNumber() {
    return orderNumber;
  }

  public UUID getApprovedQuotationId() {
    return approvedQuotationId;
  }

  public UUID getInspectionRequestId() {
    return inspectionRequestId;
  }

  public UUID getConfirmedByUserId() {
    return confirmedByUserId;
  }

  public InspectionOrderStatus getStatus() {
    return status;
  }

  public String getScopeSnapshot() {
    return scopeSnapshot;
  }

  public String getDeliverables() {
    return deliverables;
  }
}
