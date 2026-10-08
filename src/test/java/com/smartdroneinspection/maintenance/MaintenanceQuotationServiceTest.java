package com.smartdroneinspection.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.maintenance.api.dto.request.CreateMaintenanceAssessmentQuotationRequest;
import com.smartdroneinspection.maintenance.domain.MaintenanceAssessment;
import com.smartdroneinspection.maintenance.domain.MaintenanceQuotation;
import com.smartdroneinspection.maintenance.domain.MaintenanceTicket;
import com.smartdroneinspection.maintenance.domain.enums.AssessmentMode;
import com.smartdroneinspection.maintenance.domain.enums.MaintenancePriority;
import com.smartdroneinspection.maintenance.domain.enums.MaintenanceQuotationStatus;
import com.smartdroneinspection.maintenance.domain.enums.MaintenanceTicketStatus;
import com.smartdroneinspection.maintenance.repository.MaintenanceAssessmentRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceQuotationRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceTicketRepository;
import com.smartdroneinspection.maintenance.service.MaintenanceQuotationService;
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
class MaintenanceQuotationServiceTest {

  @Mock MaintenanceAssessmentRepository assessments;
  @Mock MaintenanceQuotationRepository quotations;
  @Mock MaintenanceTicketRepository tickets;
  @Mock UserAccess users;

  MaintenanceQuotationService service;

  final UUID managerId = UUID.randomUUID();
  final UUID clientId = UUID.randomUUID();
  final UUID providerId = UUID.randomUUID();
  final UUID clientOrgId = UUID.randomUUID();
  final UUID ticketId = UUID.randomUUID();
  final UUID quotationId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new MaintenanceQuotationService(assessments, quotations, tickets, users);
  }

  @Test
  void createQuotationFailsIfNotProviderManager() {
    when(users.findActiveUser(clientId))
        .thenReturn(
            Optional.of(new UserAccess.ActiveUser(clientId, Set.of("CLIENT"), clientOrgId)));

    var req =
        new CreateMaintenanceAssessmentQuotationRequest(
            AssessmentMode.ON_SITE,
            "Inject epoxy resin",
            "{\"epoxy\": \"2kg\"}",
            BigDecimal.valueOf(4),
            BigDecimal.valueOf(8),
            "High humidity",
            "Pier dry",
            BigDecimal.valueOf(5000000),
            BigDecimal.valueOf(500000),
            BigDecimal.valueOf(5500000),
            "{\"items\": []}",
            "{\"scope\": \"Pier 2\"}",
            "Direct transfer",
            180);

    assertThatThrownBy(() -> service.createAssessmentAndQuotation(clientId, ticketId, req))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            e -> assertThat(((BusinessException) e).code()).isEqualTo("MAINTENANCE_SCOPE_DENIED"));
  }

  @Test
  void createQuotationSucceedsForProviderManager() {
    when(users.findActiveUser(managerId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(managerId, Set.of("PROVIDER_MANAGER"), providerId)));

    MaintenanceTicket ticket =
        new MaintenanceTicket(
            clientOrgId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            clientId,
            MaintenancePriority.HIGH,
            Instant.now().plusSeconds(86400),
            "Repair instructions");
    when(tickets.findById(ticketId)).thenReturn(Optional.of(ticket));

    when(assessments.save(any(MaintenanceAssessment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(quotations.save(any(MaintenanceQuotation.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var req =
        new CreateMaintenanceAssessmentQuotationRequest(
            AssessmentMode.ON_SITE,
            "Inject epoxy resin",
            "{\"epoxy\": \"2kg\"}",
            BigDecimal.valueOf(4),
            BigDecimal.valueOf(8),
            "High humidity",
            "Pier dry",
            BigDecimal.valueOf(5000000),
            BigDecimal.valueOf(500000),
            BigDecimal.valueOf(5500000),
            "{\"items\": []}",
            "{\"scope\": \"Pier 2\"}",
            "Direct transfer",
            180);

    service.createAssessmentAndQuotation(managerId, ticketId, req);

    verify(assessments).save(any(MaintenanceAssessment.class));
    verify(quotations).save(any(MaintenanceQuotation.class));
    assertThat(ticket.getStatus()).isEqualTo(MaintenanceTicketStatus.AWAITING_CLIENT_APPROVAL);
  }

  @Test
  void approveQuotationTransitionsStatusAndTicket() {
    when(users.findActiveUser(clientId))
        .thenReturn(
            Optional.of(new UserAccess.ActiveUser(clientId, Set.of("CLIENT"), clientOrgId)));

    MaintenanceTicket ticket =
        new MaintenanceTicket(
            clientOrgId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            clientId,
            MaintenancePriority.HIGH,
            Instant.now().plusSeconds(86400),
            "Repair instructions");
    ticket.transitionTo(MaintenanceTicketStatus.AWAITING_CLIENT_APPROVAL);
    when(tickets.findById(ticketId)).thenReturn(Optional.of(ticket));

    MaintenanceQuotation quotation =
        new MaintenanceQuotation(
            UUID.randomUUID(),
            ticketId,
            UUID.randomUUID(),
            1,
            null,
            managerId,
            "VND",
            BigDecimal.valueOf(5000000),
            BigDecimal.valueOf(500000),
            BigDecimal.valueOf(5500000),
            "{}",
            "{}",
            BigDecimal.valueOf(8),
            "Direct transfer");
    quotation.send(Instant.now());
    when(quotations.findById(quotationId)).thenReturn(Optional.of(quotation));

    service.approveQuotation(clientId, quotationId);

    assertThat(quotation.getStatus()).isEqualTo(MaintenanceQuotationStatus.APPROVED);
    assertThat(ticket.getStatus()).isEqualTo(MaintenanceTicketStatus.ORDER_CONFIRMED);
  }
}
