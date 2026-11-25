package com.smartdroneinspection.notifications.service;

import com.smartdroneinspection.assets.DroneExpiryAccess;
import com.smartdroneinspection.assets.DroneExpiryAccess.PartnerDroneRegistration;
import com.smartdroneinspection.notifications.domain.Notification;
import com.smartdroneinspection.notifications.domain.enums.NotificationChannel;
import com.smartdroneinspection.notifications.repository.NotificationRepository;
import com.smartdroneinspection.users.UserAccess;
import com.smartdroneinspection.users.UserAccess.ActiveUser;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checks active partner drone registration expiration dates daily and records deduplicated in-app
 * notifications + best-effort bilingual reminder emails (30 days, 7 days, and expired).
 */
@Service
public class DroneRegistrationExpiryCheckService {

  private static final Logger log =
      LoggerFactory.getLogger(DroneRegistrationExpiryCheckService.class);
  private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  private static final DateTimeFormatter VN_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private enum WarningTier {
    DAYS_30("30D"),
    DAYS_7("7D"),
    EXPIRED("EXPIRED");

    private final String code;

    WarningTier(String code) {
      this.code = code;
    }

    String code() {
      return code;
    }
  }

  private final DroneExpiryAccess droneExpiryAccess;
  private final UserAccess userAccess;
  private final NotificationRepository notificationRepository;
  private final NotificationEmailHelper emailHelper;
  private final Clock clock;

  @Autowired
  public DroneRegistrationExpiryCheckService(
      DroneExpiryAccess droneExpiryAccess,
      UserAccess userAccess,
      NotificationRepository notificationRepository,
      NotificationEmailHelper emailHelper) {
    this(droneExpiryAccess, userAccess, notificationRepository, emailHelper, Clock.system(VN_ZONE));
  }

  public DroneRegistrationExpiryCheckService(
      DroneExpiryAccess droneExpiryAccess,
      UserAccess userAccess,
      NotificationRepository notificationRepository,
      NotificationEmailHelper emailHelper,
      Clock clock) {
    this.droneExpiryAccess = droneExpiryAccess;
    this.userAccess = userAccess;
    this.notificationRepository = notificationRepository;
    this.emailHelper = emailHelper;
    this.clock = clock;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void checkOnStartup() {
    try {
      checkAllPartnerDrones();
    } catch (Exception ex) {
      log.warn("Startup drone registration expiry check skipped: {}", ex.getMessage());
    }
  }

  @Scheduled(
      cron = "${app.notifications.drone-expiry-check-cron:0 10 7 * * *}",
      zone = "Asia/Ho_Chi_Minh")
  @Transactional
  public void checkAllPartnerDrones() {
    List<PartnerDroneRegistration> registrations =
        droneExpiryAccess.listActivePartnerDroneRegistrations();
    if (registrations.isEmpty()) {
      return;
    }
    Instant now = clock.instant();
    LocalDate today = now.atZone(VN_ZONE).toLocalDate();
    for (PartnerDroneRegistration drone : registrations) {
      evaluateDrone(drone, today, now);
    }
  }

  @Transactional
  public void evaluateDrone(PartnerDroneRegistration drone, LocalDate today, Instant now) {
    if (drone == null
        || drone.droneId() == null
        || drone.inspectorOwnerId() == null
        || drone.registrationExpiresAt() == null) {
      return;
    }
    LocalDate expiryDate = drone.registrationExpiresAt().atZone(VN_ZONE).toLocalDate();
    long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);
    boolean isPastInstant = drone.registrationExpiresAt().isBefore(now);

    WarningTier tier;
    if (isPastInstant || remainingDays < 0) {
      tier = WarningTier.EXPIRED;
    } else if (remainingDays <= 7) {
      tier = WarningTier.DAYS_7;
    } else if (remainingDays <= 30) {
      tier = WarningTier.DAYS_30;
    } else {
      return;
    }

    ActiveUser owner = userAccess.findActiveUser(drone.inspectorOwnerId()).orElse(null);
    if (owner == null) {
      return;
    }

    String droneLabel =
        NotificationText.safeOrFallback(drone.model(), "UAV")
            + " ("
            + NotificationText.safeOrFallback(drone.serialNumber(), "N/A")
            + ")";
    String formattedDate = expiryDate.format(VN_DATE_FORMAT);
    String dedupKey = "DRONE_REG_EXPIRY:" + drone.droneId() + ":" + expiryDate + ":" + tier.code();

    if (!notificationRepository.existsByDeduplicationKey(dedupKey)) {
      String viTitle = buildViTitle(tier);
      String enTitle = buildEnTitle(tier);
      String viBody = buildViBody(tier, droneLabel, formattedDate, Math.max(0, remainingDays));
      String enBody = buildEnBody(tier, droneLabel, formattedDate, Math.max(0, remainingDays));

      NotificationText.saveWithDeduplication(
          notificationRepository,
          new Notification(
              owner.id(),
              "DRONE_REGISTRATION_" + tier.code(),
              NotificationChannel.IN_APP,
              NotificationText.bilingual(viTitle, enTitle),
              NotificationText.bilingual(viBody, enBody),
              "/operations/inspector-drones",
              dedupKey),
          dedupKey,
          log);

      if (emailHelper != null) {
        emailHelper.sendBilingualBestEffort(
            owner.email(), owner.fullName(), viTitle + " / " + enTitle, viBody, enBody);
      }
    }

    if (tier == WarningTier.EXPIRED) {
      notifyManagersOfExpiredDrone(drone, owner, droneLabel, expiryDate, formattedDate);
    }
  }

  private void notifyManagersOfExpiredDrone(
      PartnerDroneRegistration drone,
      ActiveUser owner,
      String droneLabel,
      LocalDate expiryDate,
      String formattedDate) {
    String ownerName =
        NotificationText.safeOrFallback(
            owner.fullName(), NotificationText.safeOrFallback(owner.email(), "Inspector"));
    String title =
        NotificationText.bilingual(
            "Đăng ký Drone đối tác đã hết hạn", "Partner drone registration expired");
    String body =
        NotificationText.bilingual(
            "Giấy đăng ký Drone "
                + droneLabel
                + " của Inspector "
                + ownerName
                + " đã hết hạn vào ngày "
                + formattedDate
                + " và không đủ điều kiện điều phối bay mới.",
            "Drone "
                + droneLabel
                + " owned by "
                + ownerName
                + " expired on "
                + formattedDate
                + " and is ineligible for new flight dispatches.");
    for (ActiveUser manager : userAccess.listActiveManagers()) {
      if (manager.id() == null) continue;
      String managerKey =
          "DRONE_REG_EXPIRED_MGR:" + drone.droneId() + ":" + expiryDate + ":" + manager.id();
      NotificationText.saveWithDeduplication(
          notificationRepository,
          new Notification(
              manager.id(),
              "DRONE_REGISTRATION_EXPIRED_ALERT",
              NotificationChannel.IN_APP,
              title,
              body,
              "/manager/drone-registrations",
              managerKey),
          managerKey,
          log);
    }
  }

  private static String buildViTitle(WarningTier tier) {
    return switch (tier) {
      case EXPIRED -> "Khẩn cấp: Giấy đăng ký Drone đã hết hạn";
      case DAYS_7 -> "Cảnh báo: Giấy đăng ký Drone sắp hết hạn (7 ngày)";
      case DAYS_30 -> "Nhắc nhở: Giấy đăng ký Drone sắp hết hạn";
    };
  }

  private static String buildEnTitle(WarningTier tier) {
    return switch (tier) {
      case EXPIRED -> "Urgent: Drone registration expired";
      case DAYS_7 -> "Warning: Drone registration expires within 7 days";
      case DAYS_30 -> "Reminder: Drone registration expiring soon";
    };
  }

  private static String buildViBody(
      WarningTier tier, String droneLabel, String formattedDate, long remainingDays) {
    return switch (tier) {
      case EXPIRED ->
          "Giấy đăng ký của Drone "
              + droneLabel
              + " đã hết hạn từ ngày "
              + formattedDate
              + ". Drone không thể tham gia nhiệm vụ bay mới cho đến khi bạn nộp hồ sơ gia hạn và được duyệt.";
      case DAYS_7 ->
          "Giấy đăng ký của Drone "
              + droneLabel
              + " sẽ hết hạn sau "
              + remainingDays
              + " ngày (ngày "
              + formattedDate
              + "). Vui lòng gia hạn ngay để tránh gián đoạn điều phối.";
      case DAYS_30 ->
          "Giấy đăng ký của Drone "
              + droneLabel
              + " sẽ hết hạn sau "
              + remainingDays
              + " ngày (ngày "
              + formattedDate
              + "). Hãy chuẩn bị hồ sơ gia hạn trên mục Drone của tôi.";
    };
  }

  private static String buildEnBody(
      WarningTier tier, String droneLabel, String formattedDate, long remainingDays) {
    return switch (tier) {
      case EXPIRED ->
          "Registration for drone "
              + droneLabel
              + " expired on "
              + formattedDate
              + ". This aircraft cannot be dispatched until renewed and approved.";
      case DAYS_7 ->
          "Registration for drone "
              + droneLabel
              + " expires in "
              + remainingDays
              + " days ("
              + formattedDate
              + "). Please renew immediately to avoid dispatch disruption.";
      case DAYS_30 ->
          "Registration for drone "
              + droneLabel
              + " expires in "
              + remainingDays
              + " days ("
              + formattedDate
              + "). Please prepare a renewal application under My drones.";
    };
  }
}
