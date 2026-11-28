package com.smartdroneinspection.notifications.service;

import com.smartdroneinspection.notifications.domain.Notification;
import com.smartdroneinspection.notifications.domain.enums.NotificationChannel;
import com.smartdroneinspection.notifications.repository.NotificationRepository;
import com.smartdroneinspection.users.UserAccess;
import com.smartdroneinspection.users.UserAccess.ActiveUser;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

  @Transactional
  public void recordAssignmentAcceptanceTimeout(
      UUID inspectionId,
      UUID inspectorId,
      UUID requestId,
      UUID assignedManagerId,
      int timeoutHours,
      Instant timedOutAt) {
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
    String hourLabel = timeoutHours == 1 ? "1 hour" : timeoutHours + " hours";
    String targetPath =
        requestId != null
            ? "/manager/contracts?requestId=" + requestId
            : "/manager/inspections/" + inspectionId;
    long epoch = timedOutAt != null ? timedOutAt.getEpochSecond() : Instant.now().getEpochSecond();
    String managerKeyPrefix = "ASSIGNMENT_TIMEOUT:" + inspectionId + ":" + epoch;
    String title = "Inspector assignment timed out";
    String managerBody =
        inspectorLabel
            + " did not accept the assignment within "
            + hourLabel
            + ". The assignment has been automatically cancelled; please assign another inspector.";
    if (assignedManagerId != null) {
      String key = managerKeyPrefix + ":" + assignedManagerId;
      saveWithDeduplication(
          new Notification(
              assignedManagerId,
              "INSPECTION_ASSIGNMENT_TIMEOUT",
              NotificationChannel.IN_APP,
              title,
              managerBody,
              targetPath,
              key),
          key);
    } else {
      notifyActiveManagers(
          "INSPECTION_ASSIGNMENT_TIMEOUT", title, managerBody, targetPath, managerKeyPrefix);
    }
    if (inspectorId != null) {
      String inspectorKey = managerKeyPrefix + ":inspector:" + inspectorId;
      saveWithDeduplication(
          new Notification(
              inspectorId,
              "INSPECTION_ASSIGNMENT_TIMEOUT",
              NotificationChannel.IN_APP,
              "Assignment expired",
              "Assignment for inspection "
                  + inspectionId
                  + " was automatically cancelled because it was not accepted within "
                  + hourLabel
                  + ".",
              "/operations/inspections",
              inspectorKey),
          inspectorKey);
    }
  }

  @Transactional
  public void recordFlightPermitSubmitted(
      UUID inspectionId, String permitNumber, String issuingAuthority, Instant submittedAt) {
    if (inspectionId == null) {
      return;
    }
    long epoch =
        submittedAt != null ? submittedAt.getEpochSecond() : Instant.now().getEpochSecond();
    String permitLabel = permitNumber != null && !permitNumber.isBlank() ? permitNumber : "N/A";
    String authorityLabel =
        issuingAuthority != null && !issuingAuthority.isBlank() ? issuingAuthority : "";
    String validityNotice =
        " was uploaded by the assigned inspector, who is responsible for its validity.";
    String body =
        authorityLabel.isEmpty()
            ? "Flight permit " + permitLabel + " for inspection " + inspectionId + validityNotice
            : "Flight permit "
                + permitLabel
                + " ("
                + authorityLabel
                + ") for inspection "
                + inspectionId
                + validityNotice;
    notifyActiveManagers(
        "FLIGHT_PERMIT_SUBMITTED",
        "Flight permit uploaded by assigned inspector",
        body,
        "/manager/inspections/" + inspectionId + "/audit",
        "PERMIT_SUBMITTED:" + inspectionId + ":" + epoch);
  }

  @Transactional
  public void recordReportSubmitted(
      UUID inspectionId,
      UUID reportVersionId,
      int versionNo,
      UUID submittedByUserId,
      Instant submittedAt) {
    if (inspectionId == null || reportVersionId == null) {
      log.warn("Skipping report submitted notification: required identifiers missing");
      return;
    }
    long epoch =
        submittedAt != null ? submittedAt.getEpochSecond() : Instant.now().getEpochSecond();
    String deduplicationPrefix = "REPORT_SUBMITTED:" + reportVersionId + ":" + epoch;
    String submitterName =
        submittedByUserId != null
            ? userAccess
                .findActiveUser(submittedByUserId)
                .map(ActiveUser::fullName)
                .filter(name -> name != null && !name.isBlank())
                .orElse(null)
            : null;
    String title = "Report submitted for review";
    String body =
        submitterName != null
            ? "Report version "
                + versionNo
                + " for inspection "
                + inspectionId
                + " was submitted by "
                + submitterName
                + " and is awaiting review."
            : "Report version "
                + versionNo
                + " for inspection "
                + inspectionId
                + " is awaiting review.";

    for (ActiveUser reviewer : activeReviewers()) {
      if (reviewer.id() == null
          || (submittedByUserId != null && submittedByUserId.equals(reviewer.id()))) {
        continue;
      }
      String targetPath =
          reviewer.hasRole("ADMIN")
              ? "/admin/inspections/" + inspectionId + "/review"
              : "/manager/inspections/" + inspectionId + "/review";
      String key = deduplicationPrefix + ":" + reviewer.id();
      saveWithDeduplication(
          new Notification(
              reviewer.id(),
              "REPORT_SUBMITTED",
              NotificationChannel.IN_APP,
              title,
              body,
              targetPath,
              key),
          key);
    }
  }

  @Transactional
  public void recordReportReturned(
      UUID inspectionId,
      UUID reportVersionId,
      int versionNo,
      UUID inspectorId,
      String reason,
      Instant returnedAt) {
    if (inspectionId == null || reportVersionId == null || inspectorId == null) {
      log.warn("Skipping report returned notification: required identifiers missing");
      return;
    }
    ActiveUser recipient = userAccess.findActiveUser(inspectorId).orElse(null);
    if (recipient == null || !recipient.hasRole("INSPECTOR")) {
      log.warn(
          "Cannot record report returned notification: inspector {} is not active", inspectorId);
      return;
    }
    long epoch = returnedAt != null ? returnedAt.getEpochSecond() : Instant.now().getEpochSecond();
    String reasonText =
        reason != null && !reason.isBlank() ? reason : "Revision requested by the reviewer.";
    String title = "Report returned for revision";
    String body =
        "Report version "
            + versionNo
            + " for inspection "
            + inspectionId
            + " was returned: "
            + reasonText;
    String key = "REPORT_RETURNED:" + reportVersionId + ":" + epoch + ":" + inspectorId;
    saveWithDeduplication(
        new Notification(
            inspectorId,
            "REPORT_RETURNED",
            NotificationChannel.IN_APP,
            title,
            body,
            "/operations/inspections/" + inspectionId + "/report",
            key),
        key);
  }

  @Transactional
  public void recordReportPublished(UUID inspectionId, UUID reportVersionId, UUID ownerCustomerId) {
    if (inspectionId == null || reportVersionId == null || ownerCustomerId == null) {
      log.warn("Skipping report published notification: required identifiers missing");
      return;
    }
    ActiveUser recipient = userAccess.findActiveUser(ownerCustomerId).orElse(null);
    if (recipient == null || !recipient.hasRole("CUSTOMER")) {
      log.warn(
          "Cannot record report published notification: customer {} is not active",
          ownerCustomerId);
      return;
    }
    String title = "Inspection report published";
    String body =
        "The inspection report for inspection "
            + inspectionId
            + " has been published. Review and accept it to start the payment window.";
    String key = "REPORT_PUBLISHED:" + reportVersionId + ":" + ownerCustomerId;
    saveWithDeduplication(
        new Notification(
            ownerCustomerId,
            "REPORT_PUBLISHED",
            NotificationChannel.IN_APP,
            title,
            body,
            "/client/inspections/" + inspectionId + "/review",
            key),
        key);
  }

  @Transactional
  public void recordPaymentSettled(
      UUID occurrenceId,
      UUID inspectionId,
      UUID inspectorId,
      String paymentReference,
      BigDecimal amount,
      String paymentMethod,
      Instant settledAt) {
    if (occurrenceId == null) {
      log.warn("Skipping payment settled notification: occurrence identifier missing");
      return;
    }
    long epoch = settledAt != null ? settledAt.getEpochSecond() : Instant.now().getEpochSecond();
    String subject =
        inspectionId != null ? "inspection " + inspectionId : "occurrence " + occurrenceId;
    String amountText = amount != null ? amount.toPlainString() : "the agreed amount";
    String methodText =
        paymentMethod != null && !paymentMethod.isBlank() ? paymentMethod : "manual confirmation";
    String body =
        "Payment for "
            + subject
            + " was settled via "
            + methodText
            + " ("
            + amountText
            + ")"
            + (paymentReference != null && !paymentReference.isBlank()
                ? ", reference " + paymentReference
                : "")
            + ".";
    String title = "Payment settled";

    Map<UUID, String> recipients = new LinkedHashMap<>();
    for (ActiveUser manager : userAccess.listActiveManagers()) {
      if (manager.id() != null) {
        recipients.put(
            manager.id(),
            inspectionId != null
                ? "/manager/inspections/" + inspectionId + "/settlement"
                : "/manager/contracts");
      }
    }
    if (inspectorId != null) {
      ActiveUser inspector = userAccess.findActiveUser(inspectorId).orElse(null);
      if (inspector != null && inspector.hasRole("INSPECTOR")) {
        recipients.putIfAbsent(
            inspectorId,
            inspectionId != null
                ? "/operations/inspections/" + inspectionId
                : "/operations/inspections");
      }
    }

    for (Map.Entry<UUID, String> recipient : recipients.entrySet()) {
      String key = "PAYMENT_SETTLED:" + occurrenceId + ":" + epoch + ":" + recipient.getKey();
      saveWithDeduplication(
          new Notification(
              recipient.getKey(),
              "PAYMENT_SETTLED",
              NotificationChannel.IN_APP,
              title,
              body,
              recipient.getValue(),
              key),
          key);
    }
  }

  @Transactional
  public void recordServiceRequestCreated(
      UUID requestId, String description, LocalDate requestedFrom) {
    if (requestId == null) {
      log.warn("Skipping service request created notification: request identifier missing");
      return;
    }
    String requestLabel =
        description != null && !description.isBlank() ? description : "A new service request";
    String dateLabel = requestedFrom != null ? " for " + requestedFrom : "";
    notifyActiveManagers(
        "SERVICE_REQUEST_CREATED",
        "New service request",
        requestLabel + dateLabel + " is awaiting Manager assignment.",
        "/manager/contracts?requestId=" + requestId,
        "SERVICE_REQUEST_CREATED:" + requestId);
  }

  @Transactional
  public void recordInspectorRated(
      UUID ratingId,
      UUID inspectionId,
      UUID inspectorId,
      String raterRole,
      int overallScore,
      boolean updated,
      Instant occurredAt) {
    if (ratingId == null || inspectionId == null || inspectorId == null) {
      return;
    }
    String roleLabel = "MANAGER".equalsIgnoreCase(raterRole) ? "Manager" : "Customer";
    String title = updated ? "Inspector rating updated" : "New inspector rating received";
    String body =
        roleLabel
            + (updated ? " updated their rating to " : " rated your inspection performance ")
            + overallScore
            + "/5 stars (inspection "
            + inspectionId
            + ").";
    long epoch = occurredAt != null ? occurredAt.getEpochSecond() : Instant.now().getEpochSecond();
    String key =
        "INSPECTOR_RATED:"
            + ratingId
            + ":"
            + (updated ? "UPDATE:" + epoch : "CREATE")
            + ":"
            + inspectorId;
    saveWithDeduplication(
        new Notification(
            inspectorId,
            "INSPECTOR_RATED",
            NotificationChannel.IN_APP,
            title,
            body,
            "/operations/dashboard?tab=ratings",
            key),
        key);
  }

  @Transactional
  public void recordInspectionAccepted(
      UUID inspectionId, UUID ownerCustomerId, UUID inspectorId, Instant acceptedAt) {
    if (inspectionId == null) {
      return;
    }
    long epoch = acceptedAt != null ? acceptedAt.getEpochSecond() : Instant.now().getEpochSecond();
    String inspectorLabel =
        inspectorId != null
            ? userAccess
                .findActiveUser(inspectorId)
                .map(ActiveUser::fullName)
                .filter(name -> name != null && !name.isBlank())
                .orElse("Inspector")
            : "Inspector";
    String title =
        NotificationText.bilingual(
            "Inspector đã xác nhận nhiệm vụ bay", "Inspector accepted mission assignment");
    String body =
        NotificationText.bilingual(
            inspectorLabel
                + " đã xác nhận nhận nhiệm vụ kiểm tra "
                + inspectionId
                + " và sẵn sàng chuẩn bị phép bay.",
            inspectorLabel
                + " accepted inspection "
                + inspectionId
                + " and can now proceed with flight preparation.");
    String prefix = "INSPECTION_ACCEPTED:" + inspectionId + ":" + epoch;
    if (ownerCustomerId != null) {
      String key = prefix + ":" + ownerCustomerId;
      saveWithDeduplication(
          new Notification(
              ownerCustomerId,
              "INSPECTION_ACCEPTED",
              NotificationChannel.IN_APP,
              title,
              body,
              "/client/inspections/" + inspectionId,
              key),
          key);
    }
    notifyActiveManagers(
        "INSPECTION_ACCEPTED", title, body, "/manager/inspections/" + inspectionId, prefix);
  }

  @Transactional
  public void recordInspectionCancelled(
      UUID inspectionId,
      UUID ownerCustomerId,
      UUID inspectorId,
      String reason,
      Instant cancelledAt) {
    if (inspectionId == null) {
      return;
    }
    long epoch =
        cancelledAt != null ? cancelledAt.getEpochSecond() : Instant.now().getEpochSecond();
    String reasonText = NotificationText.safeOrFallback(reason, "");
    String title = NotificationText.bilingual("Đợt kiểm tra đã bị hủy", "Inspection cancelled");
    String body =
        NotificationText.bilingual(
            "Đợt kiểm tra "
                + inspectionId
                + " đã bị hủy"
                + (reasonText.isEmpty() ? "." : " với lý do: " + reasonText + "."),
            "Inspection "
                + inspectionId
                + " was cancelled"
                + (reasonText.isEmpty() ? "." : ": " + reasonText + "."));
    String prefix = "INSPECTION_CANCELLED:" + inspectionId + ":" + epoch;
    if (inspectorId != null) {
      String key = prefix + ":" + inspectorId;
      saveWithDeduplication(
          new Notification(
              inspectorId,
              "INSPECTION_CANCELLED",
              NotificationChannel.IN_APP,
              title,
              body,
              "/operations/inspections",
              key),
          key);
    }
    if (ownerCustomerId != null) {
      String key = prefix + ":" + ownerCustomerId;
      saveWithDeduplication(
          new Notification(
              ownerCustomerId,
              "INSPECTION_CANCELLED",
              NotificationChannel.IN_APP,
              title,
              body,
              "/client/inspections/" + inspectionId,
              key),
          key);
    }
  }

  @Transactional
  public void recordFieldSessionCompleted(
      UUID inspectionId,
      UUID sessionId,
      UUID ownerCustomerId,
      String missionTitle,
      Instant endedAt) {
    if (inspectionId == null || sessionId == null) {
      return;
    }
    String label = NotificationText.safeOrFallback(missionTitle, inspectionId.toString());
    String title =
        NotificationText.bilingual(
            "Phiên bay thực địa đã hoàn tất", "Field flight session completed");
    String body =
        NotificationText.bilingual(
            "Phiên bay thực địa cho \""
                + label
                + "\" đã hoàn thành. Dữ liệu ảnh/video đang được tổng hợp để lập báo cáo.",
            "The field flight session for \""
                + label
                + "\" has finished. Captured evidence is being compiled for reporting.");
    String prefix = "FIELD_SESSION_COMPLETED:" + sessionId;
    if (ownerCustomerId != null) {
      String key = prefix + ":" + ownerCustomerId;
      saveWithDeduplication(
          new Notification(
              ownerCustomerId,
              "FIELD_SESSION_COMPLETED",
              NotificationChannel.IN_APP,
              title,
              body,
              "/client/inspections/" + inspectionId,
              key),
          key);
    }
    notifyActiveManagers(
        "FIELD_SESSION_COMPLETED", title, body, "/manager/inspections/" + inspectionId, prefix);
  }

  @Transactional
  public void recordReportApprovedForPayout(
      UUID inspectionId,
      UUID reportVersionId,
      int versionNo,
      UUID inspectorId,
      Instant approvedAt) {
    if (inspectionId == null || reportVersionId == null) {
      return;
    }
    long epoch = approvedAt != null ? approvedAt.getEpochSecond() : Instant.now().getEpochSecond();
    if (inspectorId != null) {
      String key = "REPORT_APPROVED_PAYOUT:" + reportVersionId + ":" + inspectorId;
      saveWithDeduplication(
          new Notification(
              inspectorId,
              "REPORT_APPROVED_PAYOUT_ELIGIBLE",
              NotificationChannel.IN_APP,
              NotificationText.bilingual(
                  "Báo cáo đã được duyệt — đủ điều kiện chi trả",
                  "Report approved — payout unlocked"),
              NotificationText.bilingual(
                  "Báo cáo phiên bản "
                      + versionNo
                      + " cho đợt kiểm tra "
                      + inspectionId
                      + " đã được phê duyệt. Khoản chi trả của bạn đang chờ Quản lý giải ngân trước khi phát hành tới khách hàng.",
                  "Report version "
                      + versionNo
                      + " for inspection "
                      + inspectionId
                      + " was approved. Your payout is now unlocked for settlement prior to customer publication."),
              "/operations/finance-history",
              key),
          key);
    }
    notifyActiveManagers(
        "REPORT_APPROVED_PAYOUT_ELIGIBLE",
        NotificationText.bilingual(
            "Báo cáo đã duyệt — cần chi trả Inspector trước khi xuất bản",
            "Report approved — settle inspector payout to publish"),
        NotificationText.bilingual(
            "Báo cáo phiên bản "
                + versionNo
                + " cho đợt kiểm tra "
                + inspectionId
                + " đã được duyệt. Vui lòng hoàn tất chi trả cho Inspector để mở khóa phát hành báo cáo.",
            "Report version "
                + versionNo
                + " for inspection "
                + inspectionId
                + " is approved. Settle the inspector payout to unlock publishing to the customer."),
        "/manager/inspections/" + inspectionId + "/payout",
        "REPORT_APPROVED_PAYOUT_MGR:" + reportVersionId + ":" + epoch);
  }

  @Transactional
  public void recordReportWithdrawn(
      UUID inspectionId, UUID reportVersionId, int versionNo, Instant withdrawnAt) {
    if (inspectionId == null || reportVersionId == null) {
      return;
    }
    long epoch =
        withdrawnAt != null ? withdrawnAt.getEpochSecond() : Instant.now().getEpochSecond();
    String prefix = "REPORT_WITHDRAWN:" + reportVersionId + ":" + epoch;
    String title =
        NotificationText.bilingual(
            "Inspector đã rút lại báo cáo chờ duyệt", "Submitted report withdrawn by inspector");
    String body =
        NotificationText.bilingual(
            "Báo cáo phiên bản "
                + versionNo
                + " của đợt kiểm tra "
                + inspectionId
                + " đã được kiểm định viên rút lại để chỉnh sửa trước khi thẩm định.",
            "Report version "
                + versionNo
                + " for inspection "
                + inspectionId
                + " was withdrawn by the inspector for revision before review.");
    for (ActiveUser reviewer : activeReviewers()) {
      if (reviewer.id() == null) continue;
      String targetPath =
          reviewer.hasRole("ADMIN")
              ? "/admin/inspections/" + inspectionId + "/review"
              : "/manager/inspections/" + inspectionId + "/review";
      String key = prefix + ":" + reviewer.id();
      saveWithDeduplication(
          new Notification(
              reviewer.id(),
              "REPORT_WITHDRAWN",
              NotificationChannel.IN_APP,
              title,
              body,
              targetPath,
              key),
          key);
    }
  }

  @Transactional
  public void recordReportClarificationRequested(
      UUID inspectionId,
      UUID reportVersionId,
      int versionNo,
      String reason,
      int clarificationCount,
      Instant requestedAt) {
    if (inspectionId == null || reportVersionId == null) {
      return;
    }
    long epoch =
        requestedAt != null ? requestedAt.getEpochSecond() : Instant.now().getEpochSecond();
    String reasonText = NotificationText.safeOrFallback(reason, "Clarification requested");
    String title =
        NotificationText.bilingual(
            "Khách hàng yêu cầu làm rõ báo cáo", "Customer requested report clarification");
    String body =
        NotificationText.bilingual(
            "Khách hàng đã gửi yêu cầu làm rõ (lần "
                + clarificationCount
                + "/2) cho báo cáo phiên bản "
                + versionNo
                + " (đợt kiểm tra "
                + inspectionId
                + "): "
                + reasonText,
            "The customer requested clarification ("
                + clarificationCount
                + "/2) on report version "
                + versionNo
                + " (inspection "
                + inspectionId
                + "): "
                + reasonText);
    notifyActiveManagers(
        "REPORT_CLARIFICATION_REQUESTED",
        title,
        body,
        "/manager/inspections/" + inspectionId + "/review",
        "REPORT_CLARIFICATION:" + reportVersionId + ":" + epoch);
  }

  @Transactional
  public void recordVideoProcessingFinished(
      UUID evidenceId,
      UUID inspectionId,
      UUID inspectorId,
      String fileName,
      boolean success,
      String errorMessage,
      Instant finishedAt) {
    if (evidenceId == null || inspectionId == null || inspectorId == null) {
      return;
    }
    long epoch = finishedAt != null ? finishedAt.getEpochSecond() : Instant.now().getEpochSecond();
    String safeFile = NotificationText.safeOrFallback(fileName, "video.mp4");
    String eventType = success ? "VIDEO_PROCESSING_READY" : "VIDEO_PROCESSING_FAILED";
    String title =
        success
            ? NotificationText.bilingual(
                "Video thực địa đã xử lý xong", "Inspection video processing complete")
            : NotificationText.bilingual(
                "Xử lý video thực địa thất bại", "Inspection video processing failed");
    String err = NotificationText.safeOrFallback(errorMessage, "Transcode error");
    String body =
        success
            ? NotificationText.bilingual(
                "Video \"" + safeFile + "\" đã chuyển mã 1080p xong và sẵn sàng quét AI.",
                "Video \"" + safeFile + "\" finished 1080p transcoding and is ready for AI scan.")
            : NotificationText.bilingual(
                "Không thể xử lý video \""
                    + safeFile
                    + "\": "
                    + err
                    + ". Vui lòng thử lại hoặc tải lên tệp khác.",
                "Failed to process video \""
                    + safeFile
                    + "\": "
                    + err
                    + ". Please retry or upload another file.");
    String key = eventType + ":" + evidenceId + ":" + epoch + ":" + inspectorId;
    saveWithDeduplication(
        new Notification(
            inspectorId,
            eventType,
            NotificationChannel.IN_APP,
            title,
            body,
            "/operations/inspections/" + inspectionId + "/evidence",
            key),
        key);
  }

  private List<ActiveUser> activeReviewers() {
    Map<UUID, ActiveUser> reviewers = new LinkedHashMap<>();
    for (ActiveUser manager : userAccess.listActiveManagers()) {
      if (manager.id() != null) {
        reviewers.putIfAbsent(manager.id(), manager);
      }
    }
    for (ActiveUser admin : userAccess.listActiveAdmins()) {
      if (admin.id() != null) {
        reviewers.putIfAbsent(admin.id(), admin);
      }
    }
    return List.copyOf(reviewers.values());
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
