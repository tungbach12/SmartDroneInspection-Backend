package com.smartdroneinspection.inspections.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.events.InspectionAssignmentTimedOutEvent;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.shared.api.InspectionContractSyncPort;
import com.smartdroneinspection.shared.api.InspectionContractSyncPort.TimeoutRevertResult;
import com.smartdroneinspection.shared.api.PlatformOperationsPolicyPort;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class InspectionAssignmentTimeoutMonitorTest {

  private InspectionRepository inspections;
  private ApplicationEventPublisher events;
  private InspectionContractSyncPort contractSync;
  private PlatformOperationsPolicyPort policy;
  private InspectionAssignmentTimeoutMonitor monitor;

  @BeforeEach
  void setUp() {
    inspections = mock(InspectionRepository.class);
    events = mock(ApplicationEventPublisher.class);
    contractSync = mock(InspectionContractSyncPort.class);
    policy = mock(PlatformOperationsPolicyPort.class);
    monitor =
        new InspectionAssignmentTimeoutMonitor(
            inspections, events, Optional.of(contractSync), Optional.of(policy));
  }

  @Test
  void cancelsExpiredPendingAssignmentRevertsOccurrenceAndNotifiesManager() {
    when(policy.getAssignmentAcceptanceTimeoutHours()).thenReturn(12);
    UUID customerId = UUID.randomUUID();
    UUID assetId = UUID.randomUUID();
    UUID inspectorId = UUID.randomUUID();
    UUID droneId = UUID.randomUUID();
    UUID inspectionId = UUID.randomUUID();
    UUID requestId = UUID.randomUUID();
    UUID managerId = UUID.randomUUID();
    Instant now = Instant.now();

    Inspection pending =
        new Inspection(
            customerId,
            assetId,
            inspectorId,
            droneId,
            now.plus(5, ChronoUnit.DAYS),
            now.plus(5, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS));
    ReflectionTestUtils.setField(pending, "id", inspectionId);
    pending.markPendingAcceptance();

    when(inspections.findPendingAcceptanceExpiredBefore(any())).thenReturn(List.of(pending));
    when(inspections.findWithLockById(inspectionId)).thenReturn(Optional.of(pending));
    when(inspections.saveAndFlush(any(Inspection.class))).thenAnswer(inv -> inv.getArgument(0));
    when(contractSync.syncTimeoutRevertFromInspection(inspectionId, inspectorId, now))
        .thenReturn(Optional.of(new TimeoutRevertResult(requestId, managerId, 2)));

    int cancelled = monitor.expireUnacceptedAssignments(now);

    assertThat(cancelled).isEqualTo(1);
    assertThat(pending.getStatus()).isEqualTo(InspectionStatus.CANCELLED);
    assertThat(pending.getCancellationReason()).contains("12 hours");

    ArgumentCaptor<InspectionAssignmentTimedOutEvent> eventCaptor =
        ArgumentCaptor.forClass(InspectionAssignmentTimedOutEvent.class);
    verify(events).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().requestId()).isEqualTo(requestId);
    assertThat(eventCaptor.getValue().assignedManagerId()).isEqualTo(managerId);
    assertThat(eventCaptor.getValue().timeoutHours()).isEqualTo(12);
  }

  @Test
  void skipsAcceptedInspectionEvenIfReturnedByQuery() {
    when(policy.getAssignmentAcceptanceTimeoutHours()).thenReturn(24);
    UUID inspectorId = UUID.randomUUID();
    UUID inspectionId = UUID.randomUUID();
    Instant now = Instant.now();

    Inspection accepted =
        new Inspection(
            UUID.randomUUID(),
            UUID.randomUUID(),
            inspectorId,
            UUID.randomUUID(),
            now.plus(5, ChronoUnit.DAYS),
            now.plus(5, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS));
    ReflectionTestUtils.setField(accepted, "id", inspectionId);
    accepted.markPendingAcceptance();
    accepted.acceptAssignment(inspectorId);

    when(inspections.findPendingAcceptanceExpiredBefore(any())).thenReturn(List.of(accepted));
    when(inspections.findWithLockById(inspectionId)).thenReturn(Optional.of(accepted));

    int cancelled = monitor.expireUnacceptedAssignments(now);

    assertThat(cancelled).isZero();
    assertThat(accepted.getStatus()).isEqualTo(InspectionStatus.SCHEDULED);
    verify(contractSync, never()).syncTimeoutRevertFromInspection(any(), any(), any());
    verify(events, never()).publishEvent(any());
  }
}
