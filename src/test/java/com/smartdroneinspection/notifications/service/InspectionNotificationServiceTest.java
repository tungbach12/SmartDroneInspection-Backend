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
import java.time.LocalDate;
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
  void serviceRequestCreatedNotifiesEveryActiveManagerWithARequestDeepLink() {
    UUID requestId = UUID.randomUUID();
    UUID firstManagerId = UUID.randomUUID();
    UUID secondManagerId = UUID.randomUUID();
    when(userAccess.listActiveManagers())
        .thenReturn(
            java.util.List.of(
                new ActiveUser(firstManagerId, Set.of("MANAGER"), "First Manager"),
                new ActiveUser(secondManagerId, Set.of("MANAGER"), "Second Manager")));

    service.recordServiceRequestCreated(requestId, "Tower inspection", LocalDate.of(2026, 12, 25));

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository, org.mockito.Mockito.times(2)).saveAndFlush(captor.capture());

    assertThat(captor.getAllValues())
        .extracting(Notification::getRecipientUserId)
        .containsExactlyInAnyOrder(firstManagerId, secondManagerId);
    assertThat(captor.getAllValues())
        .allSatisfy(
            notification -> {
              assertThat(notification.getEventType()).isEqualTo("SERVICE_REQUEST_CREATED");
              assertThat(notification.getTargetPath())
                  .isEqualTo("/manager/contracts?requestId=" + requestId);
              assertThat(notification.getBody())
                  .contains("Tower inspection")
                  .contains("2026-12-25");
            });
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

    ActiveUser user = new ActiveUser(userId, Set.of("CUSTOMER"), orgId.toString(), "Customer Test");
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
  void recordFieldSessionAbortedCreatesNotificationForOwnerCustomerAndManagers() {
    UUID inspectionId = UUID.randomUUID();
    UUID ownerCustomerId = UUID.randomUUID();
    UUID managerId = UUID.randomUUID();
    when(userAccess.listActiveManagers())
        .thenReturn(java.util.List.of(new ActiveUser(managerId, Set.of("MANAGER"), "Manager")));

    service.recordFieldSessionAborted(
        inspectionId, ownerCustomerId, UUID.randomUUID(), "Heavy rain");

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository, org.mockito.Mockito.times(2)).saveAndFlush(captor.capture());

    assertThat(captor.getAllValues())
        .extracting(Notification::getRecipientUserId)
        .containsExactlyInAnyOrder(ownerCustomerId, managerId);
    assertThat(captor.getAllValues())
        .allSatisfy(
            n -> {
              assertThat(n.getEventType()).isEqualTo("FIELD_SESSION_ABORTED");
              assertThat(n.getBody()).contains("Heavy rain");
            });
  }

  @Test
  void recordFieldSessionAbortedSkipsWhenOwnerCustomerAndManagersMissing() {
    UUID inspectionId = UUID.randomUUID();
    when(userAccess.listActiveManagers()).thenReturn(java.util.List.of());
    service.recordFieldSessionAborted(inspectionId, null, UUID.randomUUID(), "Reason");
    verify(notificationRepository, never()).saveAndFlush(any());
  }

  @Test
  void recordFieldSessionAbortedSkipsWhenIdsMissing() {
    service.recordFieldSessionAborted(null, UUID.randomUUID(), UUID.randomUUID(), "Reason");
    verify(notificationRepository, never()).saveAndFlush(any());
  }

  @Test
  void recordDeclinedOverdueHungForceAbortRescheduledAndRevokedSendNotifications() {
    UUID inspectionId = UUID.randomUUID();
    UUID inspectorId = UUID.randomUUID();
    UUID ownerCustomerId = UUID.randomUUID();
    UUID managerId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    when(userAccess.listActiveManagers())
        .thenReturn(java.util.List.of(new ActiveUser(managerId, Set.of("MANAGER"), "Manager")));

    service.recordInspectionDeclined(inspectionId, inspectorId, "Bad weather");
    service.recordInspectionOverdue(inspectionId, inspectorId, Instant.now());
    service.recordFieldSessionHung(inspectionId, sessionId, Instant.now());
    service.recordFieldSessionForceAborted(
        inspectionId, ownerCustomerId, inspectorId, sessionId, "Timeout");
    service.recordInspectionRescheduled(
        inspectionId, inspectorId, ownerCustomerId, Instant.now().plus(1, ChronoUnit.DAYS));
    service.recordInspectionAssignmentRevoked(inspectionId, inspectorId);

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository, org.mockito.Mockito.times(8)).saveAndFlush(captor.capture());

    assertThat(captor.getAllValues())
        .extracting(Notification::getEventType)
        .containsExactlyInAnyOrder(
            "INSPECTION_DECLINED",
            "INSPECTION_OVERDUE",
            "FIELD_SESSION_HUNG",
            "FIELD_SESSION_FORCE_ABORTED",
            "FIELD_SESSION_FORCE_ABORTED",
            "INSPECTION_RESCHEDULED",
            "INSPECTION_RESCHEDULED",
            "INSPECTION_ASSIGNMENT_REVOKED");
  }

  @Test
  void reportSubmittedNotifiesManagersAndAdminsWithPortalSpecificLinks() {
    UUID inspectionId = UUID.randomUUID();
    UUID reportVersionId = UUID.randomUUID();
    UUID inspectorId = UUID.randomUUID();
    UUID managerId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();
    Instant submittedAt = Instant.parse("2026-11-19T08:00:00Z");
    when(userAccess.listActiveManagers())
        .thenReturn(java.util.List.of(new ActiveUser(managerId, Set.of("MANAGER"), "Manager")));
    when(userAccess.listActiveAdmins())
        .thenReturn(java.util.List.of(new ActiveUser(adminId, Set.of("ADMIN"), "Admin")));
    when(userAccess.findActiveUser(inspectorId))
        .thenReturn(
            java.util.Optional.of(
                new ActiveUser(inspectorId, Set.of("INSPECTOR"), "Pham Van Bay")));

    service.recordReportSubmitted(inspectionId, reportVersionId, 2, inspectorId, submittedAt);

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository, org.mockito.Mockito.times(2)).saveAndFlush(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(Notification::getRecipientUserId)
        .containsExactlyInAnyOrder(managerId, adminId);
    assertThat(captor.getAllValues())
        .allSatisfy(
            notification -> {
              assertThat(notification.getEventType()).isEqualTo("REPORT_SUBMITTED");
              assertThat(notification.getBody())
                  .contains("Report version 2", inspectionId.toString(), "Pham Van Bay");
              assertThat(notification.getDeduplicationKey())
                  .startsWith("REPORT_SUBMITTED:" + reportVersionId + ":");
            });
    assertThat(
            captor.getAllValues().stream()
                .filter(notification -> notification.getRecipientUserId().equals(managerId))
                .findFirst()
                .orElseThrow()
                .getTargetPath())
        .isEqualTo("/manager/inspections/" + inspectionId + "/review");
    assertThat(
            captor.getAllValues().stream()
                .filter(notification -> notification.getRecipientUserId().equals(adminId))
                .findFirst()
                .orElseThrow()
                .getTargetPath())
        .isEqualTo("/admin/inspections/" + inspectionId + "/review");
  }

  @Test
  void reportReturnedNotifiesAssignedInspectorWithReason() {
    UUID inspectionId = UUID.randomUUID();
    UUID reportVersionId = UUID.randomUUID();
    UUID inspectorId = UUID.randomUUID();
    Instant returnedAt = Instant.parse("2026-11-19T09:00:00Z");
    when(userAccess.findActiveUser(inspectorId))
        .thenReturn(
            Optional.of(new ActiveUser(inspectorId, Set.of("INSPECTOR"), "Inspector Test")));

    service.recordReportReturned(
        inspectionId, reportVersionId, 3, inspectorId, "Add a close-up of the crack", returnedAt);

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository).saveAndFlush(captor.capture());
    Notification saved = captor.getValue();
    assertThat(saved.getRecipientUserId()).isEqualTo(inspectorId);
    assertThat(saved.getEventType()).isEqualTo("REPORT_RETURNED");
    assertThat(saved.getBody()).contains("Add a close-up of the crack");
    assertThat(saved.getTargetPath())
        .isEqualTo("/operations/inspections/" + inspectionId + "/report");
  }

  @Test
  void reportPublishedNotifiesOwningCustomer() {
    UUID inspectionId = UUID.randomUUID();
    UUID reportVersionId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    when(userAccess.findActiveUser(customerId))
        .thenReturn(Optional.of(new ActiveUser(customerId, Set.of("CUSTOMER"), "Customer Test")));

    service.recordReportPublished(inspectionId, reportVersionId, customerId);

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository).saveAndFlush(captor.capture());
    Notification saved = captor.getValue();
    assertThat(saved.getRecipientUserId()).isEqualTo(customerId);
    assertThat(saved.getEventType()).isEqualTo("REPORT_PUBLISHED");
    assertThat(saved.getTargetPath()).isEqualTo("/client/inspections/" + inspectionId + "/review");
    assertThat(saved.getDeduplicationKey())
        .isEqualTo("REPORT_PUBLISHED:" + reportVersionId + ":" + customerId);
  }

  @Test
  void paymentSettledNotifiesManagersAndAssignedInspector() {
    UUID occurrenceId = UUID.randomUUID();
    UUID inspectionId = UUID.randomUUID();
    UUID inspectorId = UUID.randomUUID();
    UUID managerId = UUID.randomUUID();
    Instant settledAt = Instant.parse("2026-11-19T10:00:00Z");
    when(userAccess.listActiveManagers())
        .thenReturn(java.util.List.of(new ActiveUser(managerId, Set.of("MANAGER"), "Manager")));
    when(userAccess.findActiveUser(inspectorId))
        .thenReturn(
            Optional.of(new ActiveUser(inspectorId, Set.of("INSPECTOR"), "Inspector Test")));

    service.recordPaymentSettled(
        occurrenceId,
        inspectionId,
        inspectorId,
        "PAYOS-123",
        new java.math.BigDecimal("11000000.00"),
        "PAYOS",
        settledAt);

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository, org.mockito.Mockito.times(2)).saveAndFlush(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(Notification::getRecipientUserId)
        .containsExactlyInAnyOrder(managerId, inspectorId);
    assertThat(captor.getAllValues())
        .allSatisfy(
            notification -> {
              assertThat(notification.getEventType()).isEqualTo("PAYMENT_SETTLED");
              assertThat(notification.getBody()).contains("PAYOS-123", "11000000.00");
              assertThat(notification.getDeduplicationKey())
                  .startsWith("PAYMENT_SETTLED:" + occurrenceId + ":");
            });
    assertThat(
            captor.getAllValues().stream()
                .filter(notification -> notification.getRecipientUserId().equals(managerId))
                .findFirst()
                .orElseThrow()
                .getTargetPath())
        .isEqualTo("/manager/inspections/" + inspectionId + "/settlement");
    assertThat(
            captor.getAllValues().stream()
                .filter(notification -> notification.getRecipientUserId().equals(inspectorId))
                .findFirst()
                .orElseThrow()
                .getTargetPath())
        .isEqualTo("/operations/inspections/" + inspectionId);
  }

  @Test
  void acceptedCancelledCompletedApprovedWithdrawnClarificationAndVideoNotifyCorrectRoles() {
    UUID inspectionId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    UUID reportVersionId = UUID.randomUUID();
    UUID evidenceId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    UUID inspectorId = UUID.randomUUID();
    UUID managerId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();

    when(userAccess.findActiveUser(inspectorId))
        .thenReturn(Optional.of(new ActiveUser(inspectorId, Set.of("INSPECTOR"), "Pilot One")));
    when(userAccess.listActiveManagers())
        .thenReturn(java.util.List.of(new ActiveUser(managerId, Set.of("MANAGER"), "Manager One")));
    when(userAccess.listActiveAdmins())
        .thenReturn(java.util.List.of(new ActiveUser(adminId, Set.of("ADMIN"), "Admin One")));

    service.recordInspectionAccepted(inspectionId, customerId, inspectorId, Instant.now());
    service.recordInspectionCancelled(
        inspectionId, customerId, inspectorId, "Airspace closed", Instant.now());
    service.recordFieldSessionCompleted(
        inspectionId, sessionId, customerId, "Tower A", Instant.now());
    service.recordReportApprovedForPayout(
        inspectionId, reportVersionId, 1, inspectorId, Instant.now());
    service.recordReportWithdrawn(inspectionId, reportVersionId, 1, Instant.now());
    service.recordReportClarificationRequested(
        inspectionId, reportVersionId, 1, "Zoom photo needed", 1, Instant.now());
    service.recordVideoProcessingFinished(
        evidenceId, inspectionId, inspectorId, "flight.mp4", true, null, Instant.now());

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository, org.mockito.Mockito.times(12)).saveAndFlush(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(Notification::getEventType)
        .contains(
            "INSPECTION_ACCEPTED",
            "INSPECTION_CANCELLED",
            "FIELD_SESSION_COMPLETED",
            "REPORT_APPROVED_PAYOUT_ELIGIBLE",
            "REPORT_WITHDRAWN",
            "REPORT_CLARIFICATION_REQUESTED",
            "VIDEO_PROCESSING_READY");
  }
}
