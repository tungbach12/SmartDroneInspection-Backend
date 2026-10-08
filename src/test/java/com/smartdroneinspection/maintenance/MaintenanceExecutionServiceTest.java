package com.smartdroneinspection.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.inspections.InspectionAccess;
import com.smartdroneinspection.maintenance.api.dto.request.AssignMaintenanceEngineerRequest;
import com.smartdroneinspection.maintenance.api.dto.request.SubmitWorkLogRequest;
import com.smartdroneinspection.maintenance.domain.MaintenanceAssignment;
import com.smartdroneinspection.maintenance.domain.MaintenanceOrder;
import com.smartdroneinspection.maintenance.domain.MaintenanceTicket;
import com.smartdroneinspection.maintenance.domain.MaintenanceWorkLog;
import com.smartdroneinspection.maintenance.domain.enums.MaintenanceAssignmentType;
import com.smartdroneinspection.maintenance.domain.enums.MaintenanceOrderStatus;
import com.smartdroneinspection.maintenance.domain.enums.MaintenancePriority;
import com.smartdroneinspection.maintenance.domain.enums.MaintenanceTicketStatus;
import com.smartdroneinspection.maintenance.repository.MaintenanceAssignmentRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceOrderRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceTicketRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceWorkLogRepository;
import com.smartdroneinspection.maintenance.service.MaintenanceExecutionService;
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
class MaintenanceExecutionServiceTest {

  @Mock MaintenanceAssignmentRepository assignments;
  @Mock MaintenanceWorkLogRepository workLogs;
  @Mock MaintenanceOrderRepository orders;
  @Mock MaintenanceTicketRepository tickets;
  @Mock InspectionAccess inspectionAccess;
  @Mock UserAccess users;

  MaintenanceExecutionService service;

  final UUID managerId = UUID.randomUUID();
  final UUID engineerId = UUID.randomUUID();
  final UUID otherEngineerId = UUID.randomUUID();
  final UUID providerId = UUID.randomUUID();
  final UUID ticketId = UUID.randomUUID();
  final UUID orderId = UUID.randomUUID();
  final UUID assignmentId = UUID.randomUUID();
  final UUID beforeEvidenceId = UUID.randomUUID();
  final UUID afterEvidenceId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service =
        new MaintenanceExecutionService(
            assignments, workLogs, orders, tickets, inspectionAccess, Optional.empty(), users);
  }

  @Test
  void assignEngineerTransitionsOrderAndTicket() {
    when(users.findActiveUser(managerId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(managerId, Set.of("PROVIDER_MANAGER"), providerId)));
    when(users.findActiveUser(engineerId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(engineerId, Set.of("MAINTENANCE_ENGINEER"), providerId)));

    MaintenanceOrder order =
        new MaintenanceOrder(
            UUID.randomUUID(),
            "MO-001",
            ticketId,
            UUID.randomUUID(),
            null,
            1,
            null,
            "{}",
            BigDecimal.valueOf(1000000),
            "VND",
            "Terms",
            UUID.randomUUID(),
            Instant.now());
    when(orders.findById(orderId)).thenReturn(Optional.of(order));

    MaintenanceTicket ticket =
        new MaintenanceTicket(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            MaintenancePriority.HIGH,
            Instant.now().plusSeconds(86400),
            "Instructions");
    when(tickets.findById(ticketId)).thenReturn(Optional.of(ticket));

    when(assignments.save(any(MaintenanceAssignment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var req =
        new AssignMaintenanceEngineerRequest(
            engineerId, MaintenanceAssignmentType.EXECUTION, Instant.now().plusSeconds(86400));

    service.assignEngineer(managerId, orderId, req);

    verify(assignments).save(any(MaintenanceAssignment.class));
    assertThat(order.getStatus()).isEqualTo(MaintenanceOrderStatus.IN_PROGRESS);
    assertThat(ticket.getStatus()).isEqualTo(MaintenanceTicketStatus.IN_PROGRESS);
  }

  @Test
  void submitWorkLogFailsIfBeforeAndAfterEvidenceIdentical() {
    when(users.findActiveUser(engineerId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(engineerId, Set.of("MAINTENANCE_ENGINEER"), providerId)));

    MaintenanceAssignment assignment =
        new MaintenanceAssignment(
            ticketId,
            orderId,
            engineerId,
            managerId,
            MaintenanceAssignmentType.EXECUTION,
            Instant.now().plusSeconds(86400));
    when(assignments.findById(assignmentId)).thenReturn(Optional.of(assignment));

    var req =
        new SubmitWorkLogRequest(
            assignmentId,
            Instant.now().minusSeconds(3600),
            Instant.now(),
            BigDecimal.valueOf(100),
            "Injected epoxy",
            "{}",
            BigDecimal.valueOf(4),
            null,
            null,
            beforeEvidenceId,
            beforeEvidenceId); // Identical!

    assertThatThrownBy(() -> service.submitWorkLog(engineerId, orderId, req))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            e -> assertThat(((BusinessException) e).code()).isEqualTo("EVIDENCE_PAIR_INVALID"));
  }

  @Test
  void submitWorkLogFailsIfUserNotAssignedEngineer() {
    when(users.findActiveUser(otherEngineerId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(
                    otherEngineerId, Set.of("MAINTENANCE_ENGINEER"), providerId)));

    MaintenanceAssignment assignment =
        new MaintenanceAssignment(
            ticketId,
            orderId,
            engineerId,
            managerId,
            MaintenanceAssignmentType.EXECUTION,
            Instant.now().plusSeconds(86400));
    when(assignments.findById(assignmentId)).thenReturn(Optional.of(assignment));

    var req =
        new SubmitWorkLogRequest(
            assignmentId,
            Instant.now().minusSeconds(3600),
            Instant.now(),
            BigDecimal.valueOf(100),
            "Injected epoxy",
            "{}",
            BigDecimal.valueOf(4),
            null,
            null,
            beforeEvidenceId,
            afterEvidenceId);

    assertThatThrownBy(() -> service.submitWorkLog(otherEngineerId, orderId, req))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            e -> assertThat(((BusinessException) e).code()).isEqualTo("MAINTENANCE_SCOPE_DENIED"));
  }

  @Test
  void submitWorkLogSucceedsWithDistinctEvidencePair() {
    when(users.findActiveUser(engineerId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(engineerId, Set.of("MAINTENANCE_ENGINEER"), providerId)));

    MaintenanceAssignment assignment =
        new MaintenanceAssignment(
            ticketId,
            orderId,
            engineerId,
            managerId,
            MaintenanceAssignmentType.EXECUTION,
            Instant.now().plusSeconds(86400));
    when(assignments.findById(assignmentId)).thenReturn(Optional.of(assignment));

    when(inspectionAccess.existsEvidence(beforeEvidenceId)).thenReturn(true);
    when(inspectionAccess.existsEvidence(afterEvidenceId)).thenReturn(true);

    when(workLogs.save(any(MaintenanceWorkLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var req =
        new SubmitWorkLogRequest(
            assignmentId,
            Instant.now().minusSeconds(3600),
            Instant.now(),
            BigDecimal.valueOf(100),
            "Injected epoxy",
            "{}",
            BigDecimal.valueOf(4),
            null,
            null,
            beforeEvidenceId,
            afterEvidenceId);

    service.submitWorkLog(engineerId, orderId, req);

    verify(workLogs).save(any(MaintenanceWorkLog.class));
    verify(inspectionAccess)
        .linkEvidenceToWorkLog(org.mockito.ArgumentMatchers.eq(beforeEvidenceId), any());
    verify(inspectionAccess)
        .linkEvidenceToWorkLog(org.mockito.ArgumentMatchers.eq(afterEvidenceId), any());
  }
}
