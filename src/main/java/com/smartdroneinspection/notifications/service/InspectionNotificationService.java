package com.smartdroneinspection.notifications.service;

import com.smartdroneinspection.notifications.domain.Notification;
import com.smartdroneinspection.notifications.domain.enums.NotificationChannel;
import com.smartdroneinspection.notifications.repository.NotificationRepository;
import com.smartdroneinspection.users.UserAccess;
import com.smartdroneinspection.users.UserAccess.ActiveUser;
import java.time.Instant;
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
      UUID inspectionId, UUID organizationId, UUID inspectorId, Instant plannedStartAt) {
    if (inspectionId == null || organizationId == null || inspectorId == null) {
      log.warn(
          "Skipping inspection assignment notification: required identifiers missing (inspectionId={}, organizationId={}, inspectorId={})",
          inspectionId,
          organizationId,
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

    if (!organizationId.equals(recipient.organizationId())) {
      log.warn(
          "Cannot record inspection assignment notification: organization mismatch for inspector {}",
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
    String targetPath = "/inspections/" + inspectionId;

    Notification notification =
        new Notification(
            inspectorId,
            organizationId,
            "INSPECTION_ASSIGNED",
            NotificationChannel.IN_APP,
            title,
            body,
            targetPath,
            deduplicationKey);

    saveWithDeduplication(notification, deduplicationKey);
  }

  @Transactional
  public void recordInspectionReminder(
      UUID inspectionId, UUID organizationId, UUID inspectorId, Instant plannedStartAt) {
    if (inspectionId == null || organizationId == null || inspectorId == null) {
      log.warn(
          "Skipping inspection reminder notification: required identifiers missing (inspectionId={}, organizationId={}, inspectorId={})",
          inspectionId,
          organizationId,
          inspectorId);
      return;
    }

    if (plannedStartAt == null || !plannedStartAt.isAfter(Instant.now())) {
      log.warn(
          "Skipping inspection reminder notification: plannedStartAt {} is not in the future for inspection {}",
          plannedStartAt,
          inspectionId);
      return;
    }

    ActiveUser recipient = userAccess.findActiveUser(inspectorId).orElse(null);
    if (recipient == null) {
      log.warn(
          "Cannot record inspection reminder notification: inspector {} is not active",
          inspectorId);
      return;
    }

    if (!organizationId.equals(recipient.organizationId())) {
      log.warn(
          "Cannot record inspection reminder notification: organization mismatch for inspector {}",
          inspectorId);
      return;
    }

    if (!recipient.hasRole("INSPECTOR")) {
      log.warn(
          "Cannot record inspection reminder notification: user {} lacks INSPECTOR role",
          inspectorId);
      return;
    }

    String deduplicationKey =
        "INSPECTION_REMINDER:" + inspectionId + ":" + inspectorId + ":" + plannedStartAt;
    if (notificationRepository.existsByDeduplicationKey(deduplicationKey)) {
      log.info("Duplicate inspection reminder notification ignored for key {}", deduplicationKey);
      return;
    }

    String title = "Inspection Reminder";
    String body = "Upcoming inspection " + inspectionId + " is scheduled for " + plannedStartAt;
    String targetPath = "/inspections/" + inspectionId;

    Notification notification =
        new Notification(
            inspectorId,
            organizationId,
            "INSPECTION_REMINDER",
            NotificationChannel.IN_APP,
            title,
            body,
            targetPath,
            deduplicationKey);

    saveWithDeduplication(notification, deduplicationKey);
  }

  @Transactional
  public void recordFieldSessionAborted(
      UUID inspectionId, UUID organizationId, UUID inspectorId, String abortReason) {
    if (inspectionId == null || organizationId == null) {
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

    java.util.List<UserAccess.WorkforceMember> members =
        userAccess.listWorkforceMembers(organizationId);
    for (UserAccess.WorkforceMember m : members) {
      if (m.roles().contains("MANAGER")
          || m.roles().contains("CUSTOMER")
          || m.roles().contains("ADMIN")) {
        String key = deduplicationKey + ":" + m.id();
        Notification notification =
            new Notification(
                m.id(),
                organizationId,
                "FIELD_SESSION_ABORTED",
                NotificationChannel.IN_APP,
                title,
                body,
                targetPath,
                key);
        saveWithDeduplication(notification, key);
      }
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
