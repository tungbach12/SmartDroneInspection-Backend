package com.smartdroneinspection.notifications.service;

import com.smartdroneinspection.contracts.events.CustomerReportAcceptedEvent;
import com.smartdroneinspection.contracts.events.InspectorPayoutSettledEvent;
import com.smartdroneinspection.contracts.events.InspectorReceivableRecoveryEvent;
import com.smartdroneinspection.contracts.events.OccurrenceRefundEvent;
import com.smartdroneinspection.contracts.events.ServiceQuoteAcceptedEvent;
import com.smartdroneinspection.contracts.events.ServiceQuoteSavedEvent;
import com.smartdroneinspection.contracts.events.ServiceRequestManagerChangedEvent;
import com.smartdroneinspection.contracts.events.SignedContractUploadedEvent;
import com.smartdroneinspection.notifications.domain.Notification;
import com.smartdroneinspection.notifications.domain.enums.NotificationChannel;
import com.smartdroneinspection.notifications.repository.NotificationRepository;
import com.smartdroneinspection.users.UserAccess;
import com.smartdroneinspection.users.UserAccess.ActiveUser;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContractNotificationService {

  private static final Logger log = LoggerFactory.getLogger(ContractNotificationService.class);
  private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  private static final DateTimeFormatter VN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private final NotificationRepository notificationRepository;
  private final UserAccess userAccess;

  public ContractNotificationService(
      NotificationRepository notificationRepository, UserAccess userAccess) {
    this.notificationRepository = notificationRepository;
    this.userAccess = userAccess;
  }

  @Transactional
  public void recordManagerChanged(ServiceRequestManagerChangedEvent event) {
    if (event == null || event.requestId() == null) {
      return;
    }
    long epoch = epochOf(event.occurredAt());
    String desc =
        NotificationText.safeOrFallback(event.requestDescription(), event.requestId().toString());
    if (event.newManagerId() != null) {
      String key =
          "SR_MANAGER_ASSIGNED:" + event.requestId() + ":" + event.newManagerId() + ":" + epoch;
      save(
          event.newManagerId(),
          "SERVICE_REQUEST_ASSIGNED",
          NotificationText.bilingual(
              "Được giao yêu cầu dịch vụ", "Service request assigned to you"),
          NotificationText.bilingual(
              "Admin đã phân công bạn phụ trách yêu cầu dịch vụ \"" + desc + "\".",
              "An administrator assigned you to manage service request \"" + desc + "\"."),
          "/manager/contracts?requestId=" + event.requestId(),
          key);
    }
    if (event.previousManagerId() != null
        && !event.previousManagerId().equals(event.newManagerId())) {
      String key =
          "SR_MANAGER_UNASSIGNED:"
              + event.requestId()
              + ":"
              + event.previousManagerId()
              + ":"
              + epoch;
      save(
          event.previousManagerId(),
          "SERVICE_REQUEST_UNASSIGNED",
          NotificationText.bilingual("Thay đổi phân công yêu cầu", "Service request unassigned"),
          NotificationText.bilingual(
              "Yêu cầu dịch vụ \"" + desc + "\" không còn thuộc quyền phụ trách của bạn.",
              "Service request \"" + desc + "\" is no longer assigned to you."),
          "/manager/contracts",
          key);
    }
  }

  @Transactional
  public void recordQuoteSaved(ServiceQuoteSavedEvent event) {
    if (event == null || event.requestId() == null || event.ownerCustomerId() == null) {
      return;
    }
    long epoch = epochOf(event.occurredAt());
    String desc =
        NotificationText.safeOrFallback(event.description(), event.requestId().toString());
    String priceLabel = formatAmount(event.pricePerOccurrenceVnd(), event.currency());
    String eventType = event.revised() ? "QUOTE_UPDATED" : "QUOTE_ISSUED";
    String title =
        event.revised()
            ? NotificationText.bilingual("Báo giá đã được cập nhật", "Quotation updated")
            : NotificationText.bilingual("Có báo giá mới", "New quotation available");
    String body =
        event.revised()
            ? NotificationText.bilingual(
                "Quản lý dịch vụ đã cập nhật báo giá ("
                    + priceLabel
                    + ") cho yêu cầu \""
                    + desc
                    + "\". Vui lòng xem lại và xác nhận.",
                "The service manager updated the quotation ("
                    + priceLabel
                    + ") for \""
                    + desc
                    + "\". Please review and respond.")
            : NotificationText.bilingual(
                "Quản lý dịch vụ đã gửi báo giá ("
                    + priceLabel
                    + ") cho yêu cầu \""
                    + desc
                    + "\". Vui lòng kiểm tra và chấp nhận để tiếp tục.",
                "A quotation ("
                    + priceLabel
                    + ") has been issued for \""
                    + desc
                    + "\". Please review and accept to proceed.");
    save(
        event.ownerCustomerId(),
        eventType,
        title,
        body,
        "/client/contracts?requestId=" + event.requestId(),
        eventType + ":" + event.requestId() + ":" + epoch + ":" + event.ownerCustomerId());
  }

  @Transactional
  public void recordQuoteAccepted(ServiceQuoteAcceptedEvent event) {
    if (event == null || event.requestId() == null) {
      return;
    }
    long epoch = epochOf(event.occurredAt());
    String desc =
        NotificationText.safeOrFallback(event.description(), event.requestId().toString());
    String title =
        NotificationText.bilingual(
            "Khách hàng đã chấp nhận báo giá", "Customer accepted quotation");
    String body =
        NotificationText.bilingual(
            "Khách hàng đã đồng ý báo giá cho yêu cầu \""
                + desc
                + "\". Vui lòng tải lên hợp đồng đã ký và điều phối kiểm định.",
            "The customer accepted the quotation for \""
                + desc
                + "\". Please upload the signed contract and dispatch inspection.");
    notifyAssignedOrAllManagers(
        event.assignedManagerId(),
        "QUOTE_ACCEPTED",
        title,
        body,
        "/manager/contracts?requestId=" + event.requestId(),
        "QUOTE_ACCEPTED:" + event.requestId() + ":" + epoch);
  }

  @Transactional
  public void recordSignedContractUploaded(SignedContractUploadedEvent event) {
    if (event == null || event.requestId() == null || event.ownerCustomerId() == null) {
      return;
    }
    long epoch = epochOf(event.occurredAt());
    String desc =
        NotificationText.safeOrFallback(event.description(), event.requestId().toString());
    save(
        event.ownerCustomerId(),
        "SIGNED_CONTRACT_UPLOADED",
        NotificationText.bilingual("Đã tải lên hợp đồng đã ký", "Signed contract uploaded"),
        NotificationText.bilingual(
            "Hợp đồng đã ký cho yêu cầu \""
                + desc
                + "\" đã được lưu lên hệ thống và sẵn sàng điều phối.",
            "The signed contract for \""
                + desc
                + "\" has been uploaded and is ready for dispatch."),
        "/client/contracts?requestId=" + event.requestId(),
        "SIGNED_CONTRACT_UPLOADED:"
            + event.requestId()
            + ":"
            + epoch
            + ":"
            + event.ownerCustomerId());
  }

  @Transactional
  public void recordCustomerReportAccepted(CustomerReportAcceptedEvent event) {
    if (event == null || event.occurrenceId() == null) {
      return;
    }
    long epoch = epochOf(event.occurredAt());
    String dueLabel =
        event.paymentDueAt() != null ? VN_DATE.format(event.paymentDueAt().atZone(VN_ZONE)) : null;
    String targetPath =
        event.inspectionId() != null
            ? "/manager/inspections/" + event.inspectionId() + "/settlement"
            : "/manager/contracts?requestId=" + event.requestId();
    String title =
        NotificationText.bilingual(
            "Khách hàng đã nghiệm thu báo cáo", "Customer accepted inspection report");
    String body =
        NotificationText.bilingual(
            "Khách hàng đã chấp nhận báo cáo kiểm tra"
                + (dueLabel != null ? " (hạn thanh toán đến " + dueLabel + ")" : "")
                + ". Đơn hàng đã chuyển sang trạng thái chờ thanh toán.",
            "The customer accepted the inspection report"
                + (dueLabel != null ? " (payment due by " + dueLabel + ")" : "")
                + ". The invoice is now awaiting settlement.");
    notifyAssignedOrAllManagers(
        event.assignedManagerId(),
        "REPORT_ACCEPTED_BY_CUSTOMER",
        title,
        body,
        targetPath,
        "REPORT_ACCEPTED_BY_CUSTOMER:" + event.occurrenceId() + ":" + epoch);
  }

  @Transactional
  public void recordInspectorPayoutSettled(InspectorPayoutSettledEvent event) {
    if (event == null || event.occurrenceId() == null) {
      return;
    }
    long epoch = epochOf(event.settledAt());
    String amountLabel = formatAmount(event.netPayoutAmount(), "VND");
    String refLabel = NotificationText.safeOrFallback(event.payoutReference(), "N/A");
    if (event.inspectorId() != null) {
      save(
          event.inspectorId(),
          "PAYOUT_SETTLED",
          NotificationText.bilingual("Đã chi trả thù lao kiểm định", "Inspector payout settled"),
          NotificationText.bilingual(
              "Khoản chi trả "
                  + amountLabel
                  + " đã được thanh toán (Mã đối soát: "
                  + refLabel
                  + ").",
              "Your payout of " + amountLabel + " has been settled (Ref: " + refLabel + ")."),
          "/operations/finance-history",
          "PAYOUT_SETTLED:" + event.occurrenceId() + ":" + epoch + ":" + event.inspectorId());
    }
    if (event.automaticWebhook()) {
      String targetPath =
          event.inspectionId() != null
              ? "/manager/inspections/" + event.inspectionId() + "/payout"
              : "/manager/payout-settlements";
      notifyAssignedOrAllManagers(
          event.assignedManagerId(),
          "PAYOUT_SETTLED",
          NotificationText.bilingual(
              "SePay đã xác nhận chi trả Inspector", "SePay confirmed inspector payout"),
          NotificationText.bilingual(
              "Giao dịch chi trả "
                  + amountLabel
                  + " cho kiểm định viên đã đối soát tự động (Mã: "
                  + refLabel
                  + ").",
              "Inspector payout of "
                  + amountLabel
                  + " was confirmed automatically via SePay (Ref: "
                  + refLabel
                  + ")."),
          targetPath,
          "PAYOUT_SETTLED_MGR:" + event.occurrenceId() + ":" + epoch);
    }
  }

  @Transactional
  public void recordOccurrenceRefund(OccurrenceRefundEvent event) {
    if (event == null || event.refundId() == null || event.eventKind() == null) {
      return;
    }
    long epoch = epochOf(event.occurredAt());
    String amountLabel = event.amount() != null ? formatAmount(event.amount(), "VND") : "";
    String refLabel = NotificationText.safeOrFallback(event.reference(), "");
    String reasonLabel = NotificationText.safeOrFallback(event.reason(), "");

    switch (event.eventKind()) {
      case "INITIATED" -> {
        if (event.ownerCustomerId() != null) {
          save(
              event.ownerCustomerId(),
              "REFUND_INITIATED",
              NotificationText.bilingual(
                  "Đã khởi tạo yêu cầu hoàn tiền", "Refund request initiated"),
              NotificationText.bilingual(
                  "Yêu cầu hoàn tiền "
                      + amountLabel
                      + " đang được xử lý"
                      + (reasonLabel.isEmpty() ? "." : " (Lý do: " + reasonLabel + ")."),
                  "A refund of "
                      + amountLabel
                      + " has been initiated"
                      + (reasonLabel.isEmpty() ? "." : " (Reason: " + reasonLabel + ").")),
              "/client/finance-history",
              "REFUND_INITIATED:" + event.refundId() + ":" + event.ownerCustomerId());
        }
        if (event.inspectorId() != null) {
          save(
              event.inspectorId(),
              "REFUND_INITIATED",
              NotificationText.bilingual(
                  "Tạm giữ chi trả để đối soát hoàn tiền", "Payout held for refund reconciliation"),
              NotificationText.bilingual(
                  "Một yêu cầu hoàn tiền ("
                      + amountLabel
                      + ") đã được mở trên lượt kiểm tra của bạn; khoản chi trả liên quan đang tạm giữ chờ đối soát.",
                  "A refund ("
                      + amountLabel
                      + ") was opened on your assigned occurrence; payout is held pending reconciliation."),
              "/operations/finance-history",
              "REFUND_INITIATED:" + event.refundId() + ":" + event.inspectorId());
        }
      }
      case "COMPLETED" -> {
        if (event.ownerCustomerId() != null) {
          save(
              event.ownerCustomerId(),
              "REFUND_COMPLETED",
              NotificationText.bilingual("Hoàn tiền đã hoàn tất", "Refund completed"),
              NotificationText.bilingual(
                  "Giao dịch hoàn tiền đã được chuyển khoản thành công"
                      + (refLabel.isEmpty() ? "." : " (Mã tham chiếu: " + refLabel + ")."),
                  "Your refund transfer has been completed"
                      + (refLabel.isEmpty() ? "." : " (Ref: " + refLabel + ").")),
              "/client/finance-history",
              "REFUND_COMPLETED:" + event.refundId() + ":" + event.ownerCustomerId());
        }
        if (event.inspectorId() != null) {
          save(
              event.inspectorId(),
              "REFUND_COMPLETED",
              NotificationText.bilingual(
                  "Đã chốt đối soát hoàn tiền", "Refund reconciliation completed"),
              NotificationText.bilingual(
                  "Khoản hoàn tiền khách hàng đã hoàn tất đối soát (Mã: "
                      + (refLabel.isEmpty() ? "N/A" : refLabel)
                      + "). Vui lòng kiểm tra số dư chi trả hoặc công nợ trong Lịch sử thanh toán.",
                  "Customer refund reconciliation is complete (Ref: "
                      + (refLabel.isEmpty() ? "N/A" : refLabel)
                      + "). Check your updated payout/receivable summary in Payment history."),
              "/operations/finance-history",
              "REFUND_COMPLETED:" + event.refundId() + ":" + event.inspectorId());
        }
      }
      case "CANCELLED" -> {
        if (event.ownerCustomerId() != null) {
          save(
              event.ownerCustomerId(),
              "REFUND_CANCELLED",
              NotificationText.bilingual("Yêu cầu hoàn tiền đã bị hủy", "Refund request cancelled"),
              NotificationText.bilingual(
                  "Yêu cầu hoàn tiền đã được hủy"
                      + (reasonLabel.isEmpty() ? "." : " (Lý do: " + reasonLabel + ")."),
                  "The pending refund request was cancelled"
                      + (reasonLabel.isEmpty() ? "." : " (Reason: " + reasonLabel + ").")),
              "/client/finance-history",
              "REFUND_CANCELLED:" + event.refundId() + ":" + epoch + ":" + event.ownerCustomerId());
        }
        if (event.inspectorId() != null) {
          save(
              event.inspectorId(),
              "REFUND_CANCELLED",
              NotificationText.bilingual(
                  "Đã gỡ tạm giữ chi trả sau hủy hoàn tiền",
                  "Refund cancelled; payout hold updated"),
              NotificationText.bilingual(
                  "Yêu cầu hoàn tiền đang chờ đã bị hủy; trạng thái tạm giữ chi trả đã được tính toán lại.",
                  "The pending refund was cancelled and your payout hold status has been recalculated."),
              "/operations/finance-history",
              "REFUND_CANCELLED:" + event.refundId() + ":" + epoch + ":" + event.inspectorId());
        }
      }
      default -> log.debug("Ignoring unknown OccurrenceRefundEvent kind {}", event.eventKind());
    }
  }

  @Transactional
  public void recordInspectorReceivableRecovery(InspectorReceivableRecoveryEvent event) {
    if (event == null || event.receivableId() == null || event.inspectorId() == null) {
      return;
    }
    long epoch = epochOf(event.occurredAt());
    String amountLabel = formatAmount(event.recoveredAmount(), "VND");
    String refLabel = NotificationText.safeOrFallback(event.externalReference(), "N/A");
    save(
        event.inspectorId(),
        "RECEIVABLE_RECOVERY_RECORDED",
        NotificationText.bilingual(
            "Đã ghi nhận thu hồi công nợ hoàn tiền", "Refund receivable recovery recorded"),
        NotificationText.bilingual(
            "Quản lý đã ghi nhận khoản thu hồi " + amountLabel + " (Mã: " + refLabel + ").",
            "A receivable recovery of " + amountLabel + " was recorded (Ref: " + refLabel + ")."),
        "/operations/finance-history",
        "RECEIVABLE_RECOVERY:" + event.receivableId() + ":" + epoch + ":" + event.inspectorId());
  }

  private void notifyAssignedOrAllManagers(
      UUID assignedManagerId,
      String type,
      String title,
      String body,
      String targetPath,
      String dedupPrefix) {
    if (assignedManagerId != null) {
      ActiveUser manager = userAccess.findActiveUser(assignedManagerId).orElse(null);
      if (manager != null && manager.hasRole("MANAGER")) {
        save(
            assignedManagerId,
            type,
            title,
            body,
            targetPath,
            dedupPrefix + ":" + assignedManagerId);
        return;
      }
    }
    List<ActiveUser> managers = userAccess.listActiveManagers();
    for (ActiveUser manager : managers) {
      if (manager.id() != null) {
        save(manager.id(), type, title, body, targetPath, dedupPrefix + ":" + manager.id());
      }
    }
  }

  private void save(
      UUID recipientId,
      String eventType,
      String title,
      String body,
      String targetPath,
      String dedupKey) {
    NotificationText.saveWithDeduplication(
        notificationRepository,
        new Notification(
            recipientId, eventType, NotificationChannel.IN_APP, title, body, targetPath, dedupKey),
        dedupKey,
        log);
  }

  private static long epochOf(Instant instant) {
    return instant != null ? instant.getEpochSecond() : Instant.now().getEpochSecond();
  }

  private static String formatAmount(BigDecimal amount, String currency) {
    if (amount == null) {
      return "0 " + (currency != null ? currency : "VND");
    }
    String unit = currency != null && !currency.isBlank() ? currency : "VND";
    return amount.stripTrailingZeros().toPlainString() + " " + unit;
  }
}
