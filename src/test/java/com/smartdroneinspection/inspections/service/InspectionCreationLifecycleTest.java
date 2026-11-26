package com.smartdroneinspection.inspections.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.inspections.domain.FieldSession;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.events.InspectionDeclinedEvent;
import com.smartdroneinspection.inspections.repository.FieldSessionRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class InspectionCreationLifecycleTest {

  private static final UUID INSPECTOR_ID = UUID.randomUUID();
  private static final UUID MANAGER_ID = UUID.randomUUID();
  private static final UUID INSPECTION_ID = UUID.randomUUID();
  private static final UUID ASSET_ID = UUID.randomUUID();

  private InspectionRepository inspections;
  private AssetRepository assets;
  private UserAccess userAccess;
  private ApplicationEventPublisher events;
  private FieldSessionRepository sessions;
  private InspectionCreationService service;
  private Inspection inspection;
  private Asset asset;

  @BeforeEach
  void setUp() {
    inspections = mock(InspectionRepository.class);
    assets = mock(AssetRepository.class);
    userAccess = mock(UserAccess.class);
    events = mock(ApplicationEventPublisher.class);
    sessions = mock(FieldSessionRepository.class);
    service =
        new InspectionCreationService(
            inspections,
            assets,
            mock(DroneRepository.class),
            userAccess,
            Optional.empty(),
            events,
            new StaticListableBeanFactory(Map.of("fieldSessions", sessions))
                .getBeanProvider(FieldSessionRepository.class),
            null,
            null,
            null);

    inspection =
        new Inspection(
            UUID.randomUUID(),
            ASSET_ID,
            INSPECTOR_ID,
            UUID.randomUUID(),
            Instant.parse("2027-01-01T08:00:00Z"),
            Instant.parse("2027-01-01T10:00:00Z"));
    ReflectionTestUtils.setField(inspection, "id", INSPECTION_ID);
    asset = mock(Asset.class);
    when(asset.getName()).thenReturn("Bridge");

    when(userAccess.findActiveUser(INSPECTOR_ID))
        .thenReturn(
            Optional.of(new UserAccess.ActiveUser(INSPECTOR_ID, Set.of(Roles.INSPECTOR), "Pilot")));
    when(userAccess.findActiveUser(MANAGER_ID))
        .thenReturn(
            Optional.of(new UserAccess.ActiveUser(MANAGER_ID, Set.of(Roles.MANAGER), "Manager")));
    when(inspections.findAssignedByInspectorWithLock(INSPECTION_ID, INSPECTOR_ID))
        .thenReturn(Optional.of(inspection));
    when(inspections.findWithLockById(INSPECTION_ID)).thenReturn(Optional.of(inspection));
    when(inspections.saveAndFlush(any(Inspection.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(assets.findById(ASSET_ID)).thenReturn(Optional.of(asset));
  }

  @Test
  void inspectorAcceptsScheduledAssignment() {
    var response = service.acceptAssignment(INSPECTOR_ID, INSPECTION_ID);

    assertThat(response.id()).isEqualTo(INSPECTION_ID);
    assertThat(inspection.getAssignmentStatus().name()).isEqualTo("ACCEPTED");
    assertThat(inspection.getAssignmentAcceptedAt()).isNotNull();
    verify(inspections).saveAndFlush(inspection);
  }

  @Test
  void acceptAssignmentRejectsNonInspectorAndWrongState() {
    UserAccess.ActiveUser customer =
        new UserAccess.ActiveUser(UUID.randomUUID(), Set.of(Roles.CUSTOMER), "Customer");
    UUID customerId = UUID.randomUUID();
    when(userAccess.findActiveUser(customerId)).thenReturn(Optional.of(customer));

    assertBusinessCode(() -> service.acceptAssignment(customerId, INSPECTION_ID), "FORBIDDEN");

    ReflectionTestUtils.setField(inspection, "status", InspectionStatus.IN_PROGRESS);
    assertBusinessCode(
        () -> service.acceptAssignment(INSPECTOR_ID, INSPECTION_ID), "INVALID_STATE");
  }

  @Test
  void decliningRequiresReasonAndPublishesEvent() {
    inspection.markPendingAcceptance();
    assertBusinessCode(
        () -> service.decline(INSPECTOR_ID, INSPECTION_ID, " "), "DECLINE_REASON_REQUIRED");

    var response = service.decline(INSPECTOR_ID, INSPECTION_ID, "Schedule conflict");

    assertThat(response.id()).isEqualTo(INSPECTION_ID);
    assertThat(inspection.getDeclineReason()).isEqualTo("Schedule conflict");
    assertThat(inspection.isReassignmentRequired()).isTrue();
    verify(events).publishEvent(any(InspectionDeclinedEvent.class));
  }

  @Test
  void cannotWithdrawAfterAbortingFieldWorkOnTheCurrentAssignment() {
    inspection.acceptAssignment(INSPECTOR_ID);
    var session =
        new FieldSession(INSPECTION_ID, FieldSession.ABORTED, inspection.getAssignmentAcceptedAt());
    when(sessions.findByInspectionIdOrderByCreatedAtAsc(INSPECTION_ID))
        .thenReturn(List.of(session));

    assertBusinessCode(
        () -> service.decline(INSPECTOR_ID, INSPECTION_ID, "Permit issue"),
        "ASSIGNMENT_FIELD_WORK_STARTED");

    assertThat(inspection.isReassignmentRequired()).isFalse();
    verify(inspections, never()).saveAndFlush(inspection);
    verify(events, never()).publishEvent(any(InspectionDeclinedEvent.class));
  }

  @Test
  void previousAssignmentsClosedSessionsDoNotBlockWithdrawal() {
    inspection.acceptAssignment(INSPECTOR_ID);
    var session =
        new FieldSession(
            INSPECTION_ID,
            FieldSession.ABORTED,
            inspection.getAssignmentAcceptedAt().minusSeconds(1));
    when(sessions.findByInspectionIdOrderByCreatedAtAsc(INSPECTION_ID))
        .thenReturn(List.of(session));

    var response = service.decline(INSPECTOR_ID, INSPECTION_ID, "Permit issue");

    assertThat(response.status()).isEqualTo("SCHEDULED");
    assertThat(response.reassignmentRequired()).isTrue();
  }

  @Test
  void declineRejectsNonInspectorAndNonScheduledInspection() {
    UUID customerId = UUID.randomUUID();
    when(userAccess.findActiveUser(customerId))
        .thenReturn(
            Optional.of(new UserAccess.ActiveUser(customerId, Set.of(Roles.CUSTOMER), "Customer")));

    assertBusinessCode(() -> service.decline(customerId, INSPECTION_ID, "reason"), "FORBIDDEN");

    ReflectionTestUtils.setField(inspection, "status", InspectionStatus.IN_PROGRESS);
    assertBusinessCode(
        () -> service.decline(INSPECTOR_ID, INSPECTION_ID, "reason"), "INVALID_STATE");
  }

  @Test
  void managerCancelsScheduledInspection() {
    var response = service.cancel(MANAGER_ID, INSPECTION_ID, "Customer request");

    assertThat(response.id()).isEqualTo(INSPECTION_ID);
    assertThat(inspection.getStatus()).isEqualTo(InspectionStatus.CANCELLED);
    verify(inspections).saveAndFlush(inspection);
  }

  @Test
  void cancelRejectsNonManagerCompletedAndMissingInspection() {
    assertBusinessCode(() -> service.cancel(INSPECTOR_ID, INSPECTION_ID, "reason"), "FORBIDDEN");

    ReflectionTestUtils.setField(inspection, "status", InspectionStatus.COMPLETED);
    assertBusinessCode(() -> service.cancel(MANAGER_ID, INSPECTION_ID, "reason"), "INVALID_STATE");

    ReflectionTestUtils.setField(inspection, "status", InspectionStatus.CANCELLED);
    assertBusinessCode(() -> service.cancel(MANAGER_ID, INSPECTION_ID, "reason"), "INVALID_STATE");

    when(inspections.findWithLockById(INSPECTION_ID)).thenReturn(Optional.empty());
    assertBusinessCode(() -> service.cancel(MANAGER_ID, INSPECTION_ID, "reason"), "NOT_FOUND");
  }

  @Test
  void missingAssignedInspectionIsNotFound() {
    when(inspections.findAssignedByInspectorWithLock(eq(INSPECTION_ID), eq(INSPECTOR_ID)))
        .thenReturn(Optional.empty());

    assertBusinessCode(() -> service.acceptAssignment(INSPECTOR_ID, INSPECTION_ID), "NOT_FOUND");
  }

  private static void assertBusinessCode(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, String code) {
    assertThatThrownBy(callable)
        .isInstanceOfSatisfying(
            BusinessException.class, exception -> assertThat(exception.code()).isEqualTo(code));
  }
}
