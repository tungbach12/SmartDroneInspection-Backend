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
  void assignmentSkipsWhenInspectorOrgMismatch() {
    UUID inspectionId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    UUID otherOrgId = UUID.randomUUID();
    UUID inspectorId = UUID.randomUUID();

    ActiveUser user =
        new ActiveUser(inspectorId, Set.of("INSPECTOR"), otherOrgId.toString(), "Inspector Test");
    when(userAccess.findActiveUser(inspectorId)).thenReturn(Optional.of(user));

    service.recordInspectionAssignment(inspectionId, orgId, inspectorId, Instant.now());

    // Assignment notification recipient is the inspector, not the organization; org no longer gates
    // it.
    verify(notificationRepository).saveAndFlush(any());
  }

  @Test
  void assignmentSkipsWhenUserLacksInspectorRole() {
    UUID inspectionId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    ActiveUser user =
        new ActiveUser(userId, Set.of("MAINTENANCE_ENGINEER"), orgId.toString(), "Engineer Test");
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

    ActiveUser user =
        new ActiveUser(inspectorId, Set.of("INSPECTOR"), orgId.toString(), "Inspector Test");
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

    ActiveUser user =
        new ActiveUser(inspectorId, Set.of("INSPECTOR"), orgId.toString(), "Inspector Test");
    when(userAccess.findActiveUser(inspectorId)).thenReturn(Optional.of(user));
    when(notificationRepository.existsByDeduplicationKey(key)).thenReturn(false);

    service.recordInspectionAssignment(inspectionId, orgId, inspectorId, plannedStart);

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository).saveAndFlush(captor.capture());

    Notification saved = captor.getValue();
    assertThat(saved.getRecipientUserId()).isEqualTo(inspectorId);
    assertThat(saved.getEventType()).isEqualTo("INSPECTION_ASSIGNED");
    assertThat(saved.getChannel()).isEqualTo(NotificationChannel.IN_APP);
    assertThat(saved.getTitle()).isEqualTo("Inspection Assigned");
    assertThat(saved.getBody()).contains(inspectionId.toString());
    assertThat(saved.getTargetPath()).isEqualTo("/operations/inspections/" + inspectionId);
    assertThat(saved.getStatus()).isEqualTo(NotificationStatus.SENT);
    assertThat(saved.getDeduplicationKey()).isEqualTo(key);
  }

  @Test
  void assignmentHandlesDataIntegrityViolationGracefully() {
    UUID inspectionId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    UUID inspectorId = UUID.randomUUID();
    String key = "INSPECTION_ASSIGNED:" + inspectionId + ":" + inspectorId;

    ActiveUser user =
        new ActiveUser(inspectorId, Set.of("INSPECTOR"), orgId.toString(), "Inspector Test");
    when(userAccess.findActiveUser(inspectorId)).thenReturn(Optional.of(user));
    when(notificationRepository.existsByDeduplicationKey(key)).thenReturn(false);
    doThrow(new DataIntegrityViolationException("duplicate key value"))
        .when(notificationRepository)
        .saveAndFlush(any());

    // Must not throw
    service.recordInspectionAssignment(inspectionId, orgId, inspectorId, Instant.now());
  }

  @Test
  void recordFieldSessionAbortedCreatesNotificationForOwnerCustomer() {
    UUID inspectionId = UUID.randomUUID();
    UUID ownerCustomerId = UUID.randomUUID();

    service.recordFieldSessionAborted(
        inspectionId, ownerCustomerId, UUID.randomUUID(), "Heavy rain");

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository).saveAndFlush(captor.capture());

    Notification saved = captor.getValue();
    assertThat(saved.getRecipientUserId()).isEqualTo(ownerCustomerId);
    assertThat(saved.getEventType()).isEqualTo("FIELD_SESSION_ABORTED");
    assertThat(saved.getBody()).contains("Heavy rain");
  }

  @Test
  void recordFieldSessionAbortedSkipsWhenOwnerCustomerMissing() {
    UUID inspectionId = UUID.randomUUID();
    service.recordFieldSessionAborted(inspectionId, null, UUID.randomUUID(), "Reason");
    verify(notificationRepository, never()).saveAndFlush(any());
  }

  @Test
  void recordFieldSessionAbortedSkipsWhenIdsMissing() {
    service.recordFieldSessionAborted(null, UUID.randomUUID(), UUID.randomUUID(), "Reason");
    verify(notificationRepository, never()).saveAndFlush(any());
  }
}
