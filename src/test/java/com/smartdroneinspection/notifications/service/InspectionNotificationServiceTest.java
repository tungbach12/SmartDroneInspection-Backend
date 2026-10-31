package com.smartdroneinspection.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.notifications.domain.Notification;
import com.smartdroneinspection.notifications.domain.enums.NotificationChannel;
import com.smartdroneinspection.notifications.domain.enums.NotificationStatus;
import com.smartdroneinspection.notifications.repository.NotificationRepository;
import com.smartdroneinspection.users.UserAccess;
import com.smartdroneinspection.users.UserAccess.ActiveUser;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

class InspectionNotificationServiceTest {

  private NotificationRepository notificationRepository;
  private UserAccess userAccess;
  private InspectionNotificationService service;

  @BeforeEach
  void setUp() {
    notificationRepository = mock(NotificationRepository.class);
    userAccess = mock(UserAccess.class);
    service = new InspectionNotificationService(notificationRepository, userAccess);
  }

  @Test
  void assignmentSkipsWhenRequiredIdsMissing() {
    UUID id = UUID.randomUUID();
    service.recordInspectionAssignment(null, id, id, Instant.now());
    service.recordInspectionAssignment(id, null, id, Instant.now());
    service.recordInspectionAssignment(id, id, null, Instant.now());

    verify(userAccess, never()).findActiveUser(any());
    verify(notificationRepository, never()).saveAndFlush(any());
  }

  @Test
  void assignmentSkipsWhenInspectorNotActive() {
    UUID inspectionId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    UUID inspectorId = UUID.randomUUID();

    when(userAccess.findActiveUser(inspectorId)).thenReturn(Optional.empty());

    service.recordInspectionAssignment(inspectionId, orgId, inspectorId, Instant.now());

    verify(notificationRepository, never()).saveAndFlush(any());
  }

  @Test
  void assignmentSkipsWhenOrgMismatch() {
    UUID inspectionId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    UUID otherOrgId = UUID.randomUUID();
    UUID inspectorId = UUID.randomUUID();

    ActiveUser user =
        new ActiveUser(inspectorId, Set.of("INSPECTOR"), otherOrgId, "Inspector Test");
    when(userAccess.findActiveUser(inspectorId)).thenReturn(Optional.of(user));

    service.recordInspectionAssignment(inspectionId, orgId, inspectorId, Instant.now());

    verify(notificationRepository, never()).saveAndFlush(any());
  }

  @Test
  void assignmentSkipsWhenUserLacksInspectorRole() {
    UUID inspectionId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    ActiveUser user =
        new ActiveUser(userId, Set.of("MAINTENANCE_ENGINEER"), orgId, "Engineer Test");
    when(userAccess.findActiveUser(userId)).thenReturn(Optional.of(user));

    service.recordInspectionAssignment(inspectionId, orgId, userId, Instant.now());

    verify(notificationRepository, never()).saveAndFlush(any());
  }

  @Test
  void assignmentSkipsWhenDuplicateKeyExists() {
    UUID inspectionId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    UUID inspectorId = UUID.randomUUID();
    String key = "INSPECTION_ASSIGNED:" + inspectionId + ":" + inspectorId;

    ActiveUser user = new ActiveUser(inspectorId, Set.of("INSPECTOR"), orgId, "Inspector Test");
    when(userAccess.findActiveUser(inspectorId)).thenReturn(Optional.of(user));
    when(notificationRepository.existsByDeduplicationKey(key)).thenReturn(true);

    service.recordInspectionAssignment(inspectionId, orgId, inspectorId, Instant.now());

    verify(notificationRepository, never()).saveAndFlush(any());
  }

  @Test
  void assignmentSavesNotificationSuccessfully() {
    UUID inspectionId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    UUID inspectorId = UUID.randomUUID();
    Instant plannedStart = Instant.now().plus(1, ChronoUnit.DAYS);
    String key = "INSPECTION_ASSIGNED:" + inspectionId + ":" + inspectorId;

    ActiveUser user = new ActiveUser(inspectorId, Set.of("INSPECTOR"), orgId, "Inspector Test");
    when(userAccess.findActiveUser(inspectorId)).thenReturn(Optional.of(user));
    when(notificationRepository.existsByDeduplicationKey(key)).thenReturn(false);

    service.recordInspectionAssignment(inspectionId, orgId, inspectorId, plannedStart);

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository).saveAndFlush(captor.capture());

    Notification saved = captor.getValue();
    assertThat(saved.getRecipientUserId()).isEqualTo(inspectorId);
    assertThat(saved.getOrganizationId()).isEqualTo(orgId);
    assertThat(saved.getEventType()).isEqualTo("INSPECTION_ASSIGNED");
    assertThat(saved.getChannel()).isEqualTo(NotificationChannel.IN_APP);
    assertThat(saved.getTitle()).isEqualTo("Inspection Assigned");
    assertThat(saved.getBody()).contains(inspectionId.toString());
    assertThat(saved.getTargetPath()).isEqualTo("/inspections/" + inspectionId);
    assertThat(saved.getStatus()).isEqualTo(NotificationStatus.SENT);
    assertThat(saved.getDeduplicationKey()).isEqualTo(key);
  }

  @Test
  void assignmentHandlesDataIntegrityViolationGracefully() {
    UUID inspectionId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    UUID inspectorId = UUID.randomUUID();
    String key = "INSPECTION_ASSIGNED:" + inspectionId + ":" + inspectorId;

    ActiveUser user = new ActiveUser(inspectorId, Set.of("INSPECTOR"), orgId, "Inspector Test");
    when(userAccess.findActiveUser(inspectorId)).thenReturn(Optional.of(user));
    when(notificationRepository.existsByDeduplicationKey(key)).thenReturn(false);
    doThrow(new DataIntegrityViolationException("duplicate key value"))
        .when(notificationRepository)
        .saveAndFlush(any());

    // Must not throw
    service.recordInspectionAssignment(inspectionId, orgId, inspectorId, Instant.now());
  }

  @Test
  void reminderSkipsWhenPlannedStartNotInFuture() {
    UUID inspectionId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    UUID inspectorId = UUID.randomUUID();
    Instant pastTime = Instant.now().minus(1, ChronoUnit.HOURS);

    service.recordInspectionReminder(inspectionId, orgId, inspectorId, null);
    service.recordInspectionReminder(inspectionId, orgId, inspectorId, pastTime);

    verify(userAccess, never()).findActiveUser(any());
    verify(notificationRepository, never()).saveAndFlush(any());
  }

  @Test
  void reminderSavesNotificationSuccessfullyForFutureDate() {
    UUID inspectionId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    UUID inspectorId = UUID.randomUUID();
    Instant futureStart = Instant.now().plus(2, ChronoUnit.DAYS);
    String key = "INSPECTION_REMINDER:" + inspectionId + ":" + inspectorId + ":" + futureStart;

    ActiveUser user = new ActiveUser(inspectorId, Set.of("INSPECTOR"), orgId, "Inspector Test");
    when(userAccess.findActiveUser(inspectorId)).thenReturn(Optional.of(user));
    when(notificationRepository.existsByDeduplicationKey(key)).thenReturn(false);

    service.recordInspectionReminder(inspectionId, orgId, inspectorId, futureStart);

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository).saveAndFlush(captor.capture());

    Notification saved = captor.getValue();
    assertThat(saved.getRecipientUserId()).isEqualTo(inspectorId);
    assertThat(saved.getOrganizationId()).isEqualTo(orgId);
    assertThat(saved.getEventType()).isEqualTo("INSPECTION_REMINDER");
    assertThat(saved.getChannel()).isEqualTo(NotificationChannel.IN_APP);
    assertThat(saved.getTitle()).isEqualTo("Inspection Reminder");
    assertThat(saved.getBody()).contains(inspectionId.toString());
    assertThat(saved.getTargetPath()).isEqualTo("/inspections/" + inspectionId);
    assertThat(saved.getStatus()).isEqualTo(NotificationStatus.SENT);
    assertThat(saved.getDeduplicationKey()).isEqualTo(key);
  }

  @Test
  void recordFieldSessionAbortedCreatesNotificationForManagers() {
    UUID inspectionId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    UUID managerId = UUID.randomUUID();

    UserAccess.WorkforceMember manager =
        new UserAccess.WorkforceMember(
            managerId, "manager@test.org", "Test Manager", Set.of("MANAGER"), "ACTIVE", orgId);
    when(userAccess.listWorkforceMembers(orgId)).thenReturn(java.util.List.of(manager));

    service.recordFieldSessionAborted(inspectionId, orgId, UUID.randomUUID(), "Heavy rain");

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository).saveAndFlush(captor.capture());

    Notification saved = captor.getValue();
    assertThat(saved.getRecipientUserId()).isEqualTo(managerId);
    assertThat(saved.getEventType()).isEqualTo("FIELD_SESSION_ABORTED");
    assertThat(saved.getBody()).contains("Heavy rain");
  }

  @Test
  void recordFieldSessionAbortedSkipsWhenIdsMissing() {
    service.recordFieldSessionAborted(null, UUID.randomUUID(), UUID.randomUUID(), "Reason");
    service.recordFieldSessionAborted(UUID.randomUUID(), null, UUID.randomUUID(), "Reason");
    verify(notificationRepository, never()).saveAndFlush(any());
  }
}
