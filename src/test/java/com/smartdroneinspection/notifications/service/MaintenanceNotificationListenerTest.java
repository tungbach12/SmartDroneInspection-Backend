package com.smartdroneinspection.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.smartdroneinspection.maintenance.events.AcceptanceRecordedEvent;
import com.smartdroneinspection.maintenance.events.ChangeOrderApprovedEvent;
import com.smartdroneinspection.maintenance.events.EstimateApprovedEvent;
import com.smartdroneinspection.maintenance.events.WorkOrderAssignedEvent;
import com.smartdroneinspection.maintenance.events.WorkOrderClosedEvent;
import com.smartdroneinspection.notifications.domain.Notification;
import com.smartdroneinspection.notifications.repository.NotificationRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MaintenanceNotificationListenerTest {

  private NotificationRepository notificationRepository;
  private MaintenanceNotificationListener listener;

  @BeforeEach
  void setUp() {
    notificationRepository = mock(NotificationRepository.class);
    listener = new MaintenanceNotificationListener(notificationRepository);
  }

  @Test
  void notifiesAllAssignedTeamMembersOnAssignment() {
    UUID workOrderId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    UUID assetId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();
    UUID reviewerId = UUID.randomUUID();
    UUID memberId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();

    WorkOrderAssignedEvent event =
        new WorkOrderAssignedEvent(
            workOrderId,
            orgId,
            assetId,
            leadId,
            authorId,
            reviewerId,
            List.of(memberId),
            adminId,
            Instant.now());

    listener.onWorkOrderAssigned(event);

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository, times(4)).save(captor.capture());

    List<Notification> saved = captor.getAllValues();
    List<UUID> recipients = saved.stream().map(Notification::getRecipientUserId).toList();
    assertThat(recipients).containsExactlyInAnyOrder(leadId, authorId, reviewerId, memberId);
    assertThat(saved.get(0).getEventType()).isEqualTo("WORK_ORDER_ASSIGNED");
    assertThat(saved.get(0).getTargetPath()).contains(workOrderId.toString());
  }

  @Test
  void notifiesLeadOnEstimateApproval() {
    UUID workOrderId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    UUID approverId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();

    EstimateApprovedEvent event =
        new EstimateApprovedEvent(
            workOrderId,
            1,
            orgId,
            approverId,
            leadId,
            BigDecimal.valueOf(1500),
            "USD",
            Instant.now());

    listener.onEstimateApproved(event);

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository).save(captor.capture());

    Notification saved = captor.getValue();
    assertThat(saved.getRecipientUserId()).isEqualTo(leadId);
    assertThat(saved.getEventType()).isEqualTo("ESTIMATE_APPROVED");
  }

  @Test
  void notifiesRequesterAndLeadOnChangeOrderApproval() {
    UUID workOrderId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    UUID approverId = UUID.randomUUID();
    UUID requestedById = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();

    ChangeOrderApprovedEvent event =
        new ChangeOrderApprovedEvent(
            workOrderId,
            1,
            orgId,
            approverId,
            requestedById,
            leadId,
            BigDecimal.valueOf(200),
            Instant.now());

    listener.onChangeOrderApproved(event);

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository, times(2)).save(captor.capture());

    List<UUID> recipients =
        captor.getAllValues().stream().map(Notification::getRecipientUserId).toList();
    assertThat(recipients).containsExactlyInAnyOrder(requestedById, leadId);
  }

  @Test
  void notifiesTeamOnAcceptanceRecorded() {
    UUID workOrderId = UUID.randomUUID();
    UUID reportVersionId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    UUID assetId = UUID.randomUUID();
    UUID reviewerId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();

    AcceptanceRecordedEvent event =
        new AcceptanceRecordedEvent(
            workOrderId,
            reportVersionId,
            orgId,
            assetId,
            reviewerId,
            "ACCEPTED",
            "Good work",
            leadId,
            authorId,
            Instant.now());

    listener.onAcceptanceRecorded(event);

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository, times(2)).save(captor.capture());

    List<UUID> recipients =
        captor.getAllValues().stream().map(Notification::getRecipientUserId).toList();
    assertThat(recipients).containsExactlyInAnyOrder(leadId, authorId);
  }

  @Test
  void notifiesCloserOnWorkOrderClosed() {
    UUID workOrderId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    UUID assetId = UUID.randomUUID();
    UUID closedById = UUID.randomUUID();

    WorkOrderClosedEvent event =
        new WorkOrderClosedEvent(workOrderId, orgId, assetId, closedById, Instant.now());

    listener.onWorkOrderClosed(event);

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository).save(captor.capture());

    Notification saved = captor.getValue();
    assertThat(saved.getRecipientUserId()).isEqualTo(closedById);
    assertThat(saved.getEventType()).isEqualTo("WORK_ORDER_CLOSED");
  }
}
