package com.smartdroneinspection.maintenance.service;

import com.smartdroneinspection.maintenance.api.dto.request.CreateChangeRequestPayload;
import com.smartdroneinspection.maintenance.api.dto.response.MaintenanceOrderResponse;
import com.smartdroneinspection.maintenance.domain.Invoice;
import com.smartdroneinspection.maintenance.domain.MaintenanceChangeRequest;
import com.smartdroneinspection.maintenance.domain.MaintenanceOrder;
import com.smartdroneinspection.maintenance.domain.MaintenanceQuotation;
import com.smartdroneinspection.maintenance.domain.MaintenanceTicket;
import com.smartdroneinspection.maintenance.domain.enums.ChangeRequestStatus;
import com.smartdroneinspection.maintenance.domain.enums.MaintenanceOrderStatus;
import com.smartdroneinspection.maintenance.domain.enums.MaintenanceQuotationStatus;
import com.smartdroneinspection.maintenance.domain.enums.MaintenanceTicketStatus;
import com.smartdroneinspection.maintenance.repository.InvoiceRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceChangeRequestRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceOrderRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceQuotationRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceTicketRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaintenanceOrderService {

  private final MaintenanceOrderRepository orders;
  private final MaintenanceQuotationRepository quotations;
  private final MaintenanceTicketRepository tickets;
  private final MaintenanceChangeRequestRepository changeRequests;
  private final InvoiceRepository invoices;
  private final UserAccess users;

  public MaintenanceOrderService(
      MaintenanceOrderRepository orders,
      MaintenanceQuotationRepository quotations,
      MaintenanceTicketRepository tickets,
      MaintenanceChangeRequestRepository changeRequests,
      InvoiceRepository invoices,
      UserAccess users) {
    this.orders = orders;
    this.quotations = quotations;
    this.tickets = tickets;
    this.changeRequests = changeRequests;
    this.invoices = invoices;
    this.users = users;
  }

  @Transactional
  public UUID createOrderFromApprovedQuotation(UUID userId, UUID quotationId) {
    UserAccess.ActiveUser user = requireActiveUser(userId);
    if (!user.hasRole(Roles.CLIENT)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "REQUEST_SCOPE_DENIED",
          "Only clients can create maintenance orders.");
    }

    MaintenanceQuotation quotation =
        quotations
            .findById(quotationId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "QUOTATION_NOT_FOUND",
                        "Maintenance quotation was not found."));

    if (quotation.getStatus() != MaintenanceQuotationStatus.APPROVED) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "QUOTATION_NOT_APPROVED",
          "Only approved quotations can be used to establish a maintenance order.");
    }

    MaintenanceTicket ticket =
        tickets
            .findById(quotation.getMaintenanceTicketId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "MAINTENANCE_TICKET_NOT_FOUND",
                        "Maintenance ticket was not found."));

    if (!ticket.getOrganizationId().equals(user.organizationId())) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "REQUEST_SCOPE_DENIED",
          "Maintenance ticket does not belong to your organization.");
    }

    UUID seriesId = UUID.randomUUID();
    String orderNumber = "MO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    Instant now = Instant.now();

    MaintenanceOrder order =
        new MaintenanceOrder(
            seriesId,
            orderNumber,
            ticket.getId(),
            quotation.getId(),
            null,
            1,
            null,
            quotation.getScopeSnapshot(),
            quotation.getTotalAmount(),
            quotation.getCurrency(),
            quotation.getPaymentTerms(),
            userId,
            now);

    order.setProviderId(quotation.getProviderId());
    order.setLockedWarrantyDays(quotation.getLockedWarrantyDays());

    MaintenanceOrder savedOrder = orders.save(order);

    ticket.transitionTo(MaintenanceTicketStatus.ORDER_CONFIRMED);

    return savedOrder.getId();
  }

  @Transactional
  public UUID createChangeRequest(UUID userId, UUID orderId, CreateChangeRequestPayload payload) {
    UserAccess.ActiveUser user = requireActiveUser(userId);
    if (!user.hasRole(Roles.PROVIDER_MANAGER)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "MAINTENANCE_SCOPE_DENIED",
          "Only provider managers can submit change requests.");
    }

    MaintenanceOrder order =
        orders
            .findById(orderId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "MAINTENANCE_ORDER_NOT_FOUND",
                        "Maintenance order was not found."));

    MaintenanceTicket ticket =
        tickets
            .findById(order.getMaintenanceTicketId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "MAINTENANCE_TICKET_NOT_FOUND",
                        "Maintenance ticket was not found."));

    MaintenanceChangeRequest changeRequest =
        new MaintenanceChangeRequest(
            ticket.getId(),
            payload.workLogId(),
            order.getId(),
            userId,
            payload.reason(),
            payload.additionalScope());

    changeRequest.setEstimatedCostDelta(payload.estimatedCostDelta(), order.getCurrency());

    MaintenanceChangeRequest saved = changeRequests.save(changeRequest);

    ticket.transitionTo(MaintenanceTicketStatus.CHANGE_PENDING);

    return saved.getId();
  }

  @Transactional
  public void approveChangeRequest(UUID userId, UUID changeRequestId) {
    UserAccess.ActiveUser user = requireActiveUser(userId);
    MaintenanceChangeRequest changeRequest =
        changeRequests
            .findById(changeRequestId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "CHANGE_REQUEST_NOT_FOUND",
                        "Change request was not found."));

    MaintenanceTicket ticket =
        tickets
            .findById(changeRequest.getMaintenanceTicketId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "MAINTENANCE_TICKET_NOT_FOUND",
                        "Maintenance ticket was not found."));

    if (user.hasRole(Roles.CLIENT) && !ticket.getOrganizationId().equals(user.organizationId())) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "REQUEST_SCOPE_DENIED",
          "Maintenance ticket does not belong to your organization.");
    }

    if (changeRequest.getStatus() != ChangeRequestStatus.SUBMITTED) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "CHANGE_REQUEST_STATE_CONFLICT",
          "Change request is not in SUBMITTED status.");
    }

    changeRequest.approve(userId, Instant.now());

    MaintenanceOrder currentOrder =
        orders
            .findById(changeRequest.getCurrentOrderId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "MAINTENANCE_ORDER_NOT_FOUND",
                        "Current maintenance order was not found."));

    currentOrder.markSuperseded();

    BigDecimal newTotal =
        currentOrder.getApprovedAmount().add(changeRequest.getEstimatedCostDelta());
    Instant now = Instant.now();
    MaintenanceOrder newOrder =
        new MaintenanceOrder(
            currentOrder.getOrderSeriesId(),
            currentOrder.getOrderNumber(),
            currentOrder.getMaintenanceTicketId(),
            null,
            changeRequest.getId(),
            currentOrder.getVersionNumber() + 1,
            currentOrder.getId(),
            currentOrder.getScopeSnapshot(),
            newTotal,
            currentOrder.getCurrency(),
            currentOrder.getPaymentTerms(),
            userId,
            now);

    newOrder.setProviderId(currentOrder.getProviderId());
    newOrder.setLockedWarrantyDays(currentOrder.getLockedWarrantyDays());

    orders.save(newOrder);

    ticket.transitionTo(MaintenanceTicketStatus.IN_PROGRESS);
  }

  @Transactional
  public void acceptCompletion(UUID userId, UUID orderId) {
    UserAccess.ActiveUser user = requireActiveUser(userId);
    MaintenanceOrder order =
        orders
            .findById(orderId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "MAINTENANCE_ORDER_NOT_FOUND",
                        "Maintenance order was not found."));

    MaintenanceTicket ticket =
        tickets
            .findById(order.getMaintenanceTicketId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "MAINTENANCE_TICKET_NOT_FOUND",
                        "Maintenance ticket was not found."));

    if (user.hasRole(Roles.CLIENT) && !ticket.getOrganizationId().equals(user.organizationId())) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "REQUEST_SCOPE_DENIED",
          "Maintenance ticket does not belong to your organization.");
    }

    Instant acceptedAt = Instant.now();
    int warrantyDays = order.getLockedWarrantyDays() != null ? order.getLockedWarrantyDays() : 0;
    Instant warrantyEndDate =
        warrantyDays > 0 ? acceptedAt.plusSeconds(warrantyDays * 86400L) : null;

    order.markAwaitingPayment(acceptedAt, warrantyEndDate);
    ticket.recordAcceptance(acceptedAt, warrantyDays);

    String invoiceNumber = "INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    BigDecimal tax = order.getApprovedAmount().multiply(BigDecimal.valueOf(0.1));
    BigDecimal subtotal = order.getApprovedAmount();
    BigDecimal total = subtotal.add(tax);

    Invoice invoice =
        new Invoice(
            invoiceNumber,
            ticket.getOrganizationId(),
            order.getId(),
            ticket.getId(),
            order.getCurrency(),
            subtotal,
            tax,
            total);
    invoice.issue();
    invoices.save(invoice);
  }

  @Transactional
  public void confirmPaymentReceipt(
      UUID userId, UUID orderId, String bankAccountNumber, String bankName) {
    UserAccess.ActiveUser user = requireActiveUser(userId);
    if (!user.hasRole(Roles.PROVIDER_MANAGER)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "MAINTENANCE_SCOPE_DENIED",
          "Only provider managers can confirm payment receipt.");
    }

    MaintenanceOrder order =
        orders
            .findById(orderId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "MAINTENANCE_ORDER_NOT_FOUND",
                        "Maintenance order was not found."));

    if (order.getStatus() != MaintenanceOrderStatus.AWAITING_PAYMENT) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "ORDER_STATE_CONFLICT", "Order is not awaiting payment.");
    }

    Instant paidAt = Instant.now();
    order.setProviderBankAccountNumber(bankAccountNumber);
    order.setProviderBankName(bankName);
    order.markPaid(paidAt);

    invoices.findByMaintenanceOrderId(orderId).ifPresent(inv -> inv.markPaid(paidAt));
  }

  @Transactional(readOnly = true)
  public MaintenanceOrderResponse getOrderDetail(UUID userId, UUID orderId) {
    requireActiveUser(userId);
    MaintenanceOrder order =
        orders
            .findById(orderId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "MAINTENANCE_ORDER_NOT_FOUND",
                        "Maintenance order was not found."));
    return toResponse(order);
  }

  @Transactional(readOnly = true)
  public List<MaintenanceOrderResponse> listOrdersForTicket(UUID userId, UUID ticketId) {
    requireActiveUser(userId);
    return orders.findByMaintenanceTicketIdOrderByVersionNumberDesc(ticketId).stream()
        .map(this::toResponse)
        .toList();
  }

  private MaintenanceOrderResponse toResponse(MaintenanceOrder o) {
    return new MaintenanceOrderResponse(
        o.getId(),
        o.getOrderSeriesId(),
        o.getOrderNumber(),
        o.getMaintenanceTicketId(),
        o.getApprovedQuotationId(),
        o.getChangeRequestId(),
        o.getVersionNumber(),
        o.getPreviousVersionId(),
        o.getScopeSnapshot(),
        o.getApprovedAmount(),
        o.getCurrency(),
        o.getPaymentTerms(),
        o.getStatus(),
        o.getApprovedByUserId(),
        o.getApprovedAt(),
        o.getProviderId(),
        o.getLockedWarrantyDays(),
        o.getWarrantyEndDate(),
        o.getPaymentInvoiceIssuedAt(),
        o.getPaidAt(),
        o.getProviderBankAccountNumber(),
        o.getProviderBankName(),
        o.getStartedAt(),
        o.getCompletedAt(),
        o.getCreatedAt());
  }

  private UserAccess.ActiveUser requireActiveUser(UUID userId) {
    return users
        .findActiveUser(userId)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED",
                    "User is not authenticated or not active."));
  }
}
