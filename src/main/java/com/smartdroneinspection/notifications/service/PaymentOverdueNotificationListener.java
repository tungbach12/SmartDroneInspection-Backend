package com.smartdroneinspection.notifications.service;

import com.smartdroneinspection.contracts.events.PaymentOverdueEvent;
import com.smartdroneinspection.notifications.domain.Notification;
import com.smartdroneinspection.notifications.domain.enums.NotificationChannel;
import com.smartdroneinspection.notifications.repository.NotificationRepository;
import com.smartdroneinspection.users.UserAccess;
import com.smartdroneinspection.users.UserAccess.ActiveUser;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records in-app reminders when a customer's report payment becomes overdue.
 *
 * <p>The occurrence domain already stamps {@code payment_overdue_notified_at} before this listener
 * runs, so the daily scan fires at most once per overdue window. The listener records a
 * deduplicated in-app notification for both the customer and the platform managers.
 */
@Service
public class PaymentOverdueNotificationListener {

  private static final Logger log =
      LoggerFactory.getLogger(PaymentOverdueNotificationListener.class);
  private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  private static final DateTimeFormatter VN_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private final NotificationRepository notificationRepository;
  private final UserAccess userAccess;

  public PaymentOverdueNotificationListener(
      NotificationRepository notificationRepository, UserAccess userAccess) {
    this.notificationRepository = notificationRepository;
    this.userAccess = userAccess;
  }

  @ApplicationModuleListener
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onPaymentOverdue(PaymentOverdueEvent event) {
    if (event == null || event.occurrenceId() == null) {
      return;
    }
    String dueLabel =
        event.paymentDueAt() != null
            ? VN_DATE_FORMAT.format(event.paymentDueAt().atZone(VN_ZONE))
            : null;
    String title = "Payment overdue";
    String subject =
        event.inspectionId() != null
            ? "the inspection report for inspection " + event.inspectionId()
            : "the inspection report for occurrence " + event.occurrenceId();
    String body =
        subject
            + " has not been paid"
            + (dueLabel != null ? " (payment was due " + dueLabel + ")" : "")
            + ". Please settle the outstanding invoice or contact your service manager.";

    if (event.ownerCustomerId() != null) {
      save(
          event.ownerCustomerId(),
          title,
          body,
          inspectionRoute("/client/inspections", event.inspectionId(), null),
          "PAYMENT_OVERDUE:" + event.occurrenceId() + ":" + event.ownerCustomerId());
    }

    List<ActiveUser> managers = userAccess.listActiveManagers();
    for (ActiveUser manager : managers) {
      if (manager.id() == null) {
        continue;
      }
      save(
          manager.id(),
          title,
          body,
          inspectionRoute("/manager/inspections", event.inspectionId(), "settlement"),
          "PAYMENT_OVERDUE:" + event.occurrenceId() + ":" + manager.id());
    }
  }

  /**
   * Builds an inspection route the frontend navigation guard accepts.
   *
   * <p>The guard only resolves `/inspections/:id[/subroute]` for a known inspection id, so a
   * missing id falls back to the list page rather than producing a link that silently does nothing.
   */
  private static String inspectionRoute(String base, UUID inspectionId, String subroute) {
    if (inspectionId == null) {
      return base;
    }
    return subroute == null
        ? base + "/" + inspectionId
        : base + "/" + inspectionId + "/" + subroute;
  }

  private void save(UUID recipientId, String title, String body, String targetPath, String key) {
    try {
      notificationRepository.saveAndFlush(
          new Notification(
              recipientId,
              "PAYMENT_OVERDUE",
              NotificationChannel.IN_APP,
              title,
              body,
              targetPath,
              key));
    } catch (DataIntegrityViolationException ex) {
      log.info("Duplicate payment overdue notification ignored by constraint on key {}", key);
    }
  }
}
