package com.smartdroneinspection.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.maintenance.domain.Invoice;
import com.smartdroneinspection.maintenance.domain.MaintenanceOrder;
import com.smartdroneinspection.maintenance.domain.MaintenanceQuotation;
import com.smartdroneinspection.maintenance.domain.MaintenanceTicket;
import com.smartdroneinspection.maintenance.domain.enums.MaintenanceOrderStatus;
import com.smartdroneinspection.maintenance.domain.enums.MaintenancePriority;
import com.smartdroneinspection.maintenance.domain.enums.MaintenanceTicketStatus;
import com.smartdroneinspection.maintenance.repository.InvoiceRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceChangeRequestRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceOrderRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceQuotationRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceTicketRepository;
import com.smartdroneinspection.maintenance.service.MaintenanceOrderService;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MaintenanceOrderServiceTest {

  @Mock MaintenanceOrderRepository orders;
  @Mock MaintenanceQuotationRepository quotations;
  @Mock MaintenanceTicketRepository tickets;
  @Mock MaintenanceChangeRequestRepository changeRequests;
  @Mock InvoiceRepository invoices;
  @Mock UserAccess users;

  MaintenanceOrderService service;

  final UUID clientId = UUID.randomUUID();
  final UUID managerId = UUID.randomUUID();
  final UUID orgId = UUID.randomUUID();
  final UUID quotationId = UUID.randomUUID();
  final UUID ticketId = UUID.randomUUID();
  final UUID orderId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service =
        new MaintenanceOrderService(orders, quotations, tickets, changeRequests, invoices, users);
  }

  @Test
  void createOrderFailsIfQuotationNotApproved() {
    when(users.findActiveUser(clientId))
        .thenReturn(Optional.of(new UserAccess.ActiveUser(clientId, Set.of("CLIENT"), orgId)));

    MaintenanceQuotation quotation =
        new MaintenanceQuotation(
            UUID.randomUUID(),
            ticketId,
            UUID.randomUUID(),
            1,
            null,
            managerId,
            "VND",
            BigDecimal.valueOf(1000000),
            BigDecimal.valueOf(100000),
            BigDecimal.valueOf(1100000),
            "{}",
            "{}",
            BigDecimal.valueOf(4),
            "Terms");
    when(quotations.findById(quotationId)).thenReturn(Optional.of(quotation));

    assertThatThrownBy(() -> service.createOrderFromApprovedQuotation(clientId, quotationId))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            e -> assertThat(((BusinessException) e).code()).isEqualTo("QUOTATION_NOT_APPROVED"));
  }

  @Test
  void createOrderSucceedsAndSnapshotsWarrantyDays() {
    when(users.findActiveUser(clientId))
        .thenReturn(Optional.of(new UserAccess.ActiveUser(clientId, Set.of("CLIENT"), orgId)));

    MaintenanceQuotation quotation =
        new MaintenanceQuotation(
            UUID.randomUUID(),
            ticketId,
            UUID.randomUUID(),
            1,
            null,
            managerId,
            "VND",
            BigDecimal.valueOf(1000000),
            BigDecimal.valueOf(100000),
            BigDecimal.valueOf(1100000),
            "{}",
            "{}",
            BigDecimal.valueOf(4),
            "Terms");
    quotation.approve(clientId, Instant.now());
    quotation.setLockedWarrantyDays(180);
    when(quotations.findById(quotationId)).thenReturn(Optional.of(quotation));

    MaintenanceTicket ticket =
        new MaintenanceTicket(
            orgId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            clientId,
            MaintenancePriority.HIGH,
            Instant.now().plusSeconds(86400),
            "Instructions");
    when(tickets.findById(ticketId)).thenReturn(Optional.of(ticket));

    when(orders.save(any(MaintenanceOrder.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.createOrderFromApprovedQuotation(clientId, quotationId);

    verify(orders).save(any(MaintenanceOrder.class));
    assertThat(ticket.getStatus()).isEqualTo(MaintenanceTicketStatus.ORDER_CONFIRMED);
  }

  @Test
  void acceptCompletionActivatesWarrantyClockAndIssuesInvoice() {
    when(users.findActiveUser(clientId))
        .thenReturn(Optional.of(new UserAccess.ActiveUser(clientId, Set.of("CLIENT"), orgId)));

    MaintenanceOrder order =
        new MaintenanceOrder(
            UUID.randomUUID(),
            "MO-001",
            ticketId,
            quotationId,
            null,
            1,
            null,
            "{}",
            BigDecimal.valueOf(1000000),
            "VND",
            "Terms",
            clientId,
            Instant.now());
    order.setLockedWarrantyDays(180);
    when(orders.findById(orderId)).thenReturn(Optional.of(order));

    MaintenanceTicket ticket =
        new MaintenanceTicket(
            orgId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            clientId,
            MaintenancePriority.HIGH,
            Instant.now().plusSeconds(86400),
            "Instructions");
    when(tickets.findById(ticketId)).thenReturn(Optional.of(ticket));

    service.acceptCompletion(clientId, orderId);

    assertThat(order.getStatus()).isEqualTo(MaintenanceOrderStatus.AWAITING_PAYMENT);
    assertThat(order.getWarrantyEndDate()).isNotNull();
    assertThat(ticket.getAcceptedAt()).isNotNull();
    assertThat(ticket.getWarrantyStartedAt()).isNotNull();
    assertThat(ticket.getWarrantyEndsAt()).isNotNull();
    verify(invoices).save(any(Invoice.class));
  }
}
