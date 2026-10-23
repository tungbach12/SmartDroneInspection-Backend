package com.smartdroneinspection.notifications.service;

import com.smartdroneinspection.maintenance.events.AcceptanceRecordedEvent;
import com.smartdroneinspection.maintenance.events.ChangeOrderApprovedEvent;
import com.smartdroneinspection.maintenance.events.EstimateApprovedEvent;
import com.smartdroneinspection.maintenance.events.WorkOrderAssignedEvent;
import com.smartdroneinspection.maintenance.events.WorkOrderClosedEvent;
import com.smartdroneinspection.notifications.domain.Notification;
import com.smartdroneinspection.notifications.domain.enums.NotificationChannel;
import com.smartdroneinspection.notifications.repository.NotificationRepository;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** MF4-04/13/18/21: Records in-app notifications for maintenance workflow milestones. */
@Component
public class MaintenanceNotificationListener {

  private static final Logger log = LoggerFactory.getLogger(MaintenanceNotificationListener.class);

  private final NotificationRepository notificationRepository;

  public MaintenanceNotificationListener(NotificationRepository notificationRepository) {
    this.notificationRepository = notificationRepository;
  }

  @ApplicationModuleListener
  public void onWorkOrderAssigned(WorkOrderAssignedEvent event) {
    log.info("Recording notifications for work order assignment {}", event.workOrderId());
    Set<UUID> recipients = new HashSet<>();
    if (event.leadUserId() != null) recipients.add(event.leadUserId());
    if (event.reportAuthorUserId() != null) recipients.add(event.reportAuthorUserId());
    if (event.acceptingReviewerUserId() != null) recipients.add(event.acceptingReviewerUserId());
    if (event.memberUserIds() != null) recipients.addAll(event.memberUserIds());

    for (UUID recipientId : recipients) {
      notificationRepository.save(
          new Notification(
              recipientId,
              event.organizationId(),
              "WORK_ORDER_ASSIGNED",
              NotificationChannel.IN_APP,
              "Assigned to Maintenance Work Order",
              "You have been assigned to work order "
                  + event.workOrderId()
                  + " for asset "
                  + event.assetId(),
              "/maintenance/work-orders/" + event.workOrderId()));
    }
  }

  @ApplicationModuleListener
  public void onEstimateApproved(EstimateApprovedEvent event) {
    log.info("Recording notification for estimate approval on work order {}", event.workOrderId());
    if (event.teamLeadUserId() != null) {
      notificationRepository.save(
          new Notification(
              event.teamLeadUserId(),
              event.organizationId(),
              "ESTIMATE_APPROVED",
              NotificationChannel.IN_APP,
              "Maintenance Estimate Approved",
              "Estimate v"
                  + event.versionNo()
                  + " approved for "
                  + event.currency()
                  + " "
                  + event.baselineTotal(),
              "/maintenance/work-orders/" + event.workOrderId()));
    }
  }

  @ApplicationModuleListener
  public void onChangeOrderApproved(ChangeOrderApprovedEvent event) {
    log.info(
        "Recording notification for change order approval on work order {}", event.workOrderId());
    Set<UUID> recipients = new HashSet<>();
    if (event.requestedByUserId() != null) recipients.add(event.requestedByUserId());
    if (event.teamLeadUserId() != null) recipients.add(event.teamLeadUserId());

    for (UUID recipientId : recipients) {
      notificationRepository.save(
          new Notification(
              recipientId,
              event.organizationId(),
              "CHANGE_ORDER_APPROVED",
              NotificationChannel.IN_APP,
              "Change Order #" + event.changeNumber() + " Approved",
              "Change order delta of "
                  + event.proposedDelta()
                  + " was approved on work order "
                  + event.workOrderId(),
              "/maintenance/work-orders/" + event.workOrderId()));
    }
  }

  @ApplicationModuleListener
  public void onAcceptanceRecorded(AcceptanceRecordedEvent event) {
    log.info(
        "Recording notification for acceptance decision on work order {}", event.workOrderId());
    Set<UUID> recipients = new HashSet<>();
    if (event.teamLeadUserId() != null) recipients.add(event.teamLeadUserId());
    if (event.reportAuthorUserId() != null) recipients.add(event.reportAuthorUserId());

    for (UUID recipientId : recipients) {
      notificationRepository.save(
          new Notification(
              recipientId,
              event.organizationId(),
              "ACCEPTANCE_DECISION_" + event.decision(),
              NotificationChannel.IN_APP,
              "Acceptance Decision: " + event.decision(),
              "Reviewer recorded decision: "
                  + event.decision()
                  + (event.technicalComments() != null
                      ? " (" + event.technicalComments() + ")"
                      : ""),
              "/maintenance/work-orders/" + event.workOrderId()));
    }
  }

  @ApplicationModuleListener
  public void onWorkOrderClosed(WorkOrderClosedEvent event) {
    log.info("Recording notification for work order closure {}", event.workOrderId());
    if (event.closedByUserId() != null) {
      notificationRepository.save(
          new Notification(
              event.closedByUserId(),
              event.organizationId(),
              "WORK_ORDER_CLOSED",
              NotificationChannel.IN_APP,
              "Work Order Closed",
              "Work order "
                  + event.workOrderId()
                  + " has completed cost reconciliation and is closed",
              "/maintenance/work-orders/" + event.workOrderId()));
    }
  }
}
