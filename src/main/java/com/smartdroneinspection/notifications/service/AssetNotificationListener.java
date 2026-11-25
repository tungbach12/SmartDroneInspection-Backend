package com.smartdroneinspection.notifications.service;

import com.smartdroneinspection.assets.events.DroneApplicationReviewedEvent;
import com.smartdroneinspection.assets.events.DroneApplicationSubmittedEvent;
import com.smartdroneinspection.notifications.domain.Notification;
import com.smartdroneinspection.notifications.domain.enums.NotificationChannel;
import com.smartdroneinspection.notifications.repository.NotificationRepository;
import com.smartdroneinspection.users.UserAccess;
import com.smartdroneinspection.users.UserAccess.ActiveUser;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Records in-app notifications for drone registration submission and review outcomes. */
@Component
public class AssetNotificationListener {

  private static final Logger log = LoggerFactory.getLogger(AssetNotificationListener.class);

  private final NotificationRepository notificationRepository;
  private final UserAccess userAccess;

  public AssetNotificationListener(
      NotificationRepository notificationRepository, UserAccess userAccess) {
    this.notificationRepository = notificationRepository;
    this.userAccess = userAccess;
  }

  @ApplicationModuleListener
  public void onDroneApplicationSubmitted(DroneApplicationSubmittedEvent event) {
    if (event == null || event.applicationId() == null) {
      return;
    }
    long epoch =
        event.occurredAt() != null
            ? event.occurredAt().getEpochSecond()
            : Instant.now().getEpochSecond();
    String droneLabel =
        NotificationText.safeOrFallback(event.model(), "UAV")
            + " ("
            + NotificationText.safeOrFallback(event.serialNumber(), "N/A")
            + ")";
    String title =
        event.resubmitted()
            ? NotificationText.bilingual(
                "Hồ sơ đăng ký Drone vừa nộp lại", "Drone registration application resubmitted")
            : NotificationText.bilingual(
                "Hồ sơ đăng ký Drone mới", "New drone registration application");
    String body =
        event.resubmitted()
            ? NotificationText.bilingual(
                "Kiểm định viên đã bổ sung và nộp lại hồ sơ Drone "
                    + droneLabel
                    + " chờ xét duyệt.",
                "An inspector resubmitted drone registration for "
                    + droneLabel
                    + " awaiting review.")
            : NotificationText.bilingual(
                "Kiểm định viên vừa nộp hồ sơ đăng ký Drone " + droneLabel + " chờ xét duyệt.",
                "An inspector submitted a new drone registration for "
                    + droneLabel
                    + " awaiting review.");

    String prefix = "DRONE_APP_SUBMITTED:" + event.applicationId() + ":" + epoch;
    for (ActiveUser manager : userAccess.listActiveManagers()) {
      if (manager.id() == null) continue;
      String key = prefix + ":" + manager.id();
      NotificationText.saveWithDeduplication(
          notificationRepository,
          new Notification(
              manager.id(),
              "DRONE_APPLICATION_SUBMITTED",
              NotificationChannel.IN_APP,
              title,
              body,
              "/manager/drone-registrations",
              key),
          key,
          log);
    }
    for (ActiveUser admin : userAccess.listActiveAdmins()) {
      if (admin.id() == null) continue;
      String key = prefix + ":" + admin.id();
      NotificationText.saveWithDeduplication(
          notificationRepository,
          new Notification(
              admin.id(),
              "DRONE_APPLICATION_SUBMITTED",
              NotificationChannel.IN_APP,
              title,
              body,
              "/admin/drone-registrations",
              key),
          key,
          log);
    }
  }

  @ApplicationModuleListener
  public void onDroneApplicationReviewed(DroneApplicationReviewedEvent event) {
    if (event == null || event.applicationId() == null || event.inspectorUserId() == null) {
      return;
    }
    long epoch =
        event.occurredAt() != null
            ? event.occurredAt().getEpochSecond()
            : Instant.now().getEpochSecond();
    String droneLabel =
        NotificationText.safeOrFallback(event.model(), "UAV")
            + " ("
            + NotificationText.safeOrFallback(event.serialNumber(), "N/A")
            + ")";
    String eventType =
        event.approved() ? "DRONE_APPLICATION_APPROVED" : "DRONE_APPLICATION_REJECTED";
    String title =
        event.approved()
            ? NotificationText.bilingual("Hồ sơ Drone đã được duyệt", "Drone registration approved")
            : NotificationText.bilingual("Hồ sơ Drone bị từ chối", "Drone registration rejected");
    String reason = NotificationText.safeOrFallback(event.reviewNote(), "");
    String body =
        event.approved()
            ? NotificationText.bilingual(
                "Hồ sơ đăng ký Drone "
                    + droneLabel
                    + " đã được phê duyệt và kích hoạt sẵn sàng bay.",
                "Your drone registration for "
                    + droneLabel
                    + " has been approved and is now active.")
            : NotificationText.bilingual(
                "Hồ sơ đăng ký Drone "
                    + droneLabel
                    + " đã bị từ chối"
                    + (reason.isEmpty() ? ". Vui lòng kiểm tra và nộp lại." : ": " + reason + "."),
                "Your drone registration for "
                    + droneLabel
                    + " was rejected"
                    + (reason.isEmpty() ? ". Please review and resubmit." : ": " + reason + "."));
    String key =
        eventType + ":" + event.applicationId() + ":" + epoch + ":" + event.inspectorUserId();
    NotificationText.saveWithDeduplication(
        notificationRepository,
        new Notification(
            event.inspectorUserId(),
            eventType,
            NotificationChannel.IN_APP,
            title,
            body,
            "/operations/inspector-drones",
            key),
        key,
        log);
  }
}
