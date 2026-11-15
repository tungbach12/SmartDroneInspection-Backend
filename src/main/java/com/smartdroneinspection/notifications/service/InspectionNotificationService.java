package com.smartdroneinspection.notifications.service;

import com.smartdroneinspection.notifications.domain.Notification;
import com.smartdroneinspection.notifications.domain.enums.NotificationChannel;
import com.smartdroneinspection.notifications.repository.NotificationRepository;
import com.smartdroneinspection.users.UserAccess;
import com.smartdroneinspection.users.UserAccess.ActiveUser;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspectionNotificationService {

  private static final Logger log = LoggerFactory.getLogger(InspectionNotificationService.class);

  private final NotificationRepository notificationRepository;
  private final UserAccess userAccess;

  public InspectionNotificationService(
      NotificationRepository notificationRepository, UserAccess userAccess) {
    this.notificationRepository = notificationRepository;
    this.userAccess = userAccess;
  }

  @Transactional
  public void recordInspectionAssignment(
      UUID inspectionId, UUID ownerCustomerId, UUID inspectorId, Instant plannedStartAt) {
    if (inspectionId == null || inspectorId == null) {
      log.warn(
          "Skipping inspection assignment notification: required identifiers missing (inspectionId={}, inspectorId={})",
          inspectionId,
          inspectorId);
      return;
    }

    ActiveUser recipient = userAccess.findActiveUser(inspectorId).orElse(null);
    if (recipient == null) {
      log.warn(
          "Cannot record inspection assignment notification: inspector {} is not active",
          inspectorId);
      return;
    }

    if (!recipient.hasRole("INSPECTOR")) {
      log.warn(
          "Cannot record inspection assignment notification: user {} lacks INSPECTOR role",
          inspectorId);
      return;
    }

    String deduplicationKey = "INSPECTION_ASSIGNED:" + inspectionId + ":" + inspectorId;
    if (notificationRepository.existsByDeduplicationKey(deduplicationKey)) {
      log.info("Duplicate inspection assignment notification ignored for key {}", deduplicationKey);
      return;
    }

    String title = "Inspection Assigned";
    String body = "You have been assigned to inspection " + inspectionId;
    String targetPath =
        recipient.isInspectorPartner()
            ? "/operations/inspections/" + inspectionId
            : "/inspections/" + inspectionId;

    Notification notification =
        new Notification(
            inspectorId,
            "INSPECTION_ASSIGNED",
            NotificationChannel.IN_APP,
            title,
            body,
            targetPath,
            deduplicationKey);

    saveWithDeduplication(notification, deduplicationKey);
  }

  @Transactional
  public void recordFieldSessionAborted(
      UUID inspectionId, UUID ownerCustomerId, UUID inspectorId, String abortReason) {
    if (inspectionId == null) {
      log.warn("Skipping field session aborted notification: required identifiers missing");
      return;
    }

    String deduplicationKey =
        "SESSION_ABORTED:" + inspectionId + ":" + Instant.now().getEpochSecond();
    String title = "Phiên bay bị dừng";
    String body =
        "Phiên bay của đợt kiểm tra bị dừng do: "
            + (abortReason != null && !abortReason.isBlank() ? abortReason : "sự cố thực địa")
            + ". Vui lòng điều phối hoặc dời lịch.";
    String targetPath = "/operations/inspections/" + inspectionId;

    if (ownerCustomerId != null) {
      String key = deduplicationKey + ":" + ownerCustomerId;
      Notification notification =
          new Notification(
              ownerCustomerId,
              "FIELD_SESSION_ABORTED",
              NotificationChannel.IN_APP,
              title,
              body,
              "/client/inspections/" + inspectionId,
              key);
      saveWithDeduplication(notification, key);
    }

    notifyActiveManagers(
        "FIELD_SESSION_ABORTED",
        title,
        body,
        "/manager/inspections/" + inspectionId,
        deduplicationKey);
  }

  @Transactional
  public void recordInspectionDeclined(UUID inspectionId, UUID inspectorId, String declineReason) {
    if (inspectionId == null) {
      return;
    }
    String inspectorLabel =
        inspectorId != null
            ? userAccess
                .findActiveUser(inspectorId)
                .map(ActiveUser::fullName)
                .filter(name -> name != null && !name.isBlank())
                .orElse(inspectorId.toString())
            : "Assigned inspector";
    String title = "Inspector declined scheduled flight";
    String body =
        inspectorLabel
            + " declined inspection "
            + inspectionId
            + " due to: "
            + (declineReason != null && !declineReason.isBlank() ? declineReason : "unspecified")
            + ". Please reassign another inspector or reschedule.";
    String dedupPrefix =
        "INSPECTION_DECLINED:" + inspectionId + ":" + Instant.now().getEpochSecond();
    notifyActiveManagers(
        "INSPECTION_DECLINED", title, body, "/manager/inspections/" + inspectionId, dedupPrefix);
  }

  @Transactional
  public void recordInspectionOverdue(UUID inspectionId, UUID inspectorId, Instant plannedEndAt) {
    if (inspectionId == null) {
      return;
    }
    String title = "Inspection overdue (no flight session started)";
    String body =
        "Inspection "
            + inspectionId
            + " passed its scheduled window"
            + (plannedEndAt != null ? " (" + plannedEndAt + ")" : "")
            + " without a started field session. Please follow up or reassign.";
    notifyActiveManagers(
        "INSPECTION_OVERDUE",
        title,
        body,
        "/manager/inspections/" + inspectionId,
        "INSPECTION_OVERDUE:" + inspectionId);
  }

  @Transactional
  public void recordFieldSessionHung(UUID inspectionId, UUID sessionId, Instant startedAt) {
    if (inspectionId == null || sessionId == null) {
      return;
    }
    String title = "Field session appears hung";
    String body =
        "Field session "
            + sessionId
            + " on inspection "
            + inspectionId
            + " remains IN_PROGRESS well past the planned window. You may force-abort to recover dispatch.";
    notifyActiveManagers(
        "FIELD_SESSION_HUNG",
        title,
        body,
        "/manager/inspections/" + inspectionId,
        "SESSION_HUNG:" + sessionId);
  }

  @Transactional
  public void recordFieldSessionForceAborted(
      UUID inspectionId, UUID ownerCustomerId, UUID inspectorId, UUID sessionId, String reason) {
    if (inspectionId == null || sessionId == null) {
      return;
    }
    String suffix = reason != null && !reason.isBlank() ? ": " + reason : ".";
    if (inspectorId != null) {
      String key = "SESSION_FORCE_ABORTED:" + sessionId + ":" + inspectorId;
      saveWithDeduplication(
          new Notification(
              inspectorId,
              "FIELD_SESSION_FORCE_ABORTED",
              NotificationChannel.IN_APP,
              "Field session force-aborted by manager",
              "Your field session on inspection "
                  + inspectionId
                  + " was closed by a manager"
                  + suffix,
              "/operations/inspections/" + inspectionId,
              key),
          key);
    }
    if (ownerCustomerId != null) {
      String key = "SESSION_FORCE_ABORTED:" + sessionId + ":" + ownerCustomerId;
      saveWithDeduplication(
          new Notification(
              ownerCustomerId,
              "FIELD_SESSION_FORCE_ABORTED",
              NotificationChannel.IN_APP,
              "Field session stopped by manager",
              "The active field session for inspection "
                  + inspectionId
                  + " was closed for recovery"
                  + suffix,
              "/client/inspections/" + inspectionId,
              key),
          key);
    }
  }

  @Transactional
  public void recordInspectionRescheduled(
      UUID inspectionId, UUID inspectorId, UUID ownerCustomerId, Instant plannedStartAt) {
    if (inspectionId == null) {
      return;
    }
    String dedupPrefix =
        "INSPECTION_RESCHEDULED:" + inspectionId + ":" + Instant.now().getEpochSecond();
    String body =
        "Inspection "
            + inspectionId
            + " has been rescheduled"
            + (plannedStartAt != null ? " to start at " + plannedStartAt : "")
            + ".";
    if (inspectorId != null) {
      String key = dedupPrefix + ":" + inspectorId;
      saveWithDeduplication(
          new Notification(
              inspectorId,
              "INSPECTION_RESCHEDULED",
              NotificationChannel.IN_APP,
              "Inspection Rescheduled",
              body,
              "/operations/inspections/" + inspectionId,
              key),
          key);
    }
    if (ownerCustomerId != null) {
      String key = dedupPrefix + ":" + ownerCustomerId;
      saveWithDeduplication(
          new Notification(
              ownerCustomerId,
              "INSPECTION_RESCHEDULED",
              NotificationChannel.IN_APP,
              "Inspection Rescheduled",
              body,
              "/client/inspections/" + inspectionId,
              key),
          key);
    }
  }

  @Transactional
  public void recordInspectionAssignmentRevoked(UUID inspectionId, UUID previousInspectorId) {
    if (inspectionId == null || previousInspectorId == null) {
      return;
    }
    String key =
        "INSPECTION_REVOKED:"
            + inspectionId
            + ":"
            + previousInspectorId
            + ":"
            + Instant.now().getEpochSecond();
    saveWithDeduplication(
        new Notification(
            previousInspectorId,
            "INSPECTION_ASSIGNMENT_REVOKED",
            NotificationChannel.IN_APP,
            "Inspection Reassigned",
            "Inspection " + inspectionId + " has been reassigned to another inspector.",
            "/operations/inspections",
            key),
        key);
  }

  private void notifyActiveManagers(
      String type, String title, String body, String targetPath, String deduplicationPrefix) {
    List<ActiveUser> managers = userAccess.listActiveManagers();
    for (ActiveUser manager : managers) {
      if (manager.id() == null) {
        continue;
      }
      String key = deduplicationPrefix + ":" + manager.id();
      saveWithDeduplication(
          new Notification(
              manager.id(), type, NotificationChannel.IN_APP, title, body, targetPath, key),
          key);
    }
  }

  private void saveWithDeduplication(Notification notification, String deduplicationKey) {
    try {
      notificationRepository.saveAndFlush(notification);
    } catch (DataIntegrityViolationException ex) {
      log.info("Duplicate notification ignored by constraint on key {}", deduplicationKey);
    }
  }
}
