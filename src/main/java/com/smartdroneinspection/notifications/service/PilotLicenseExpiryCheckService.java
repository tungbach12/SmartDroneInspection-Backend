package com.smartdroneinspection.notifications.service;

import com.smartdroneinspection.notifications.domain.Notification;
import com.smartdroneinspection.notifications.domain.enums.NotificationChannel;
import com.smartdroneinspection.notifications.repository.NotificationRepository;
import com.smartdroneinspection.users.UserAccess;
import com.smartdroneinspection.users.UserAccess.ActiveUser;
import com.smartdroneinspection.users.events.PilotLicenseUpdatedEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checks Inspector UAV pilot license expiration dates daily and immediately upon license updates,
 * recording deduplicated in-app notifications (for the Inspector and platform Managers on expiry)
 * and sending best-effort reminder emails when SMTP is configured.
 */
@Service
public class PilotLicenseExpiryCheckService {

  private static final Logger log = LoggerFactory.getLogger(PilotLicenseExpiryCheckService.class);
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

  private final UserAccess userAccess;
  private final NotificationRepository notificationRepository;
  private final ObjectProvider<JavaMailSender> mailSender;
  private final String mailFrom;
  private final Clock clock;

  @Autowired
  public PilotLicenseExpiryCheckService(
      UserAccess userAccess,
      NotificationRepository notificationRepository,
      ObjectProvider<JavaMailSender> mailSender,
      @Value("${app.auth.otp.email-from:${spring.mail.username:}}") String mailFrom) {
    this(userAccess, notificationRepository, mailSender, mailFrom, Clock.system(VN_ZONE));
  }

  public PilotLicenseExpiryCheckService(
      UserAccess userAccess,
      NotificationRepository notificationRepository,
      ObjectProvider<JavaMailSender> mailSender,
      String mailFrom,
      Clock clock) {
    this.userAccess = userAccess;
    this.notificationRepository = notificationRepository;
    this.mailSender = mailSender;
    this.mailFrom = mailFrom;
    this.clock = clock;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void checkOnStartup() {
    try {
      checkAllInspectorLicenses();
    } catch (Exception ex) {
      log.warn("Startup pilot license expiry check skipped: {}", ex.getMessage());
    }
  }

  @Scheduled(
      cron = "${app.notifications.license-expiry-check-cron:0 0 7 * * *}",
      zone = "Asia/Ho_Chi_Minh")
  @Transactional
  public void checkAllInspectorLicenses() {
    List<ActiveUser> inspectors = userAccess.listActiveInspectors();
    if (inspectors.isEmpty()) {
      return;
    }
    Instant now = clock.instant();
    LocalDate today = now.atZone(VN_ZONE).toLocalDate();
    for (ActiveUser inspector : inspectors) {
      evaluateInspectorLicense(inspector, today, now);
    }
  }

  @EventListener
  public void onPilotLicenseUpdated(PilotLicenseUpdatedEvent event) {
    if (event == null || event.inspectorUserId() == null) {
      return;
    }
    userAccess
        .findActiveUser(event.inspectorUserId())
        .ifPresent(
            inspector -> {
              Instant now = clock.instant();
              LocalDate today = now.atZone(VN_ZONE).toLocalDate();
              evaluateInspectorLicense(inspector, today, now);
            });
  }

  @Transactional
  public void evaluateInspectorLicense(ActiveUser inspector, LocalDate today, Instant now) {
    if (inspector == null || inspector.pilotLicenseExpiresAt() == null) {
      return;
    }
    LocalDate expiryDate = inspector.pilotLicenseExpiresAt().atZone(VN_ZONE).toLocalDate();
    long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);
    boolean isPastInstant = inspector.pilotLicenseExpiresAt().isBefore(now);

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

    String licenseLabel =
        inspector.pilotLicenseNumber() != null && !inspector.pilotLicenseNumber().isBlank()
            ? inspector.pilotLicenseNumber().trim()
            : "UAV";
    String formattedDate = expiryDate.format(VN_DATE_FORMAT);
    String dedupKey =
        "PILOT_LICENSE_EXPIRY:" + inspector.id() + ":" + expiryDate + ":" + tier.code();

    if (!notificationRepository.existsByDeduplicationKey(dedupKey)) {
      String title = buildInspectorTitle(tier);
      String body =
          buildInspectorBody(tier, licenseLabel, formattedDate, Math.max(0, remainingDays));
      String targetPath = "/partner-onboarding";

      Notification notification =
          new Notification(
              inspector.id(),
              "PILOT_LICENSE_" + tier.code(),
              NotificationChannel.IN_APP,
              title,
              body,
              targetPath,
              dedupKey);
      saveWithDeduplication(notification, dedupKey);
      sendEmailBestEffort(inspector, title, body);
    }

    if (tier == WarningTier.EXPIRED) {
      notifyPlatformManagersOfExpiredLicense(inspector, licenseLabel, expiryDate, formattedDate);
    }
  }

  private void notifyPlatformManagersOfExpiredLicense(
      ActiveUser inspector, String licenseLabel, LocalDate expiryDate, String formattedDate) {
    List<ActiveUser> managers = userAccess.listActivePlatformManagers();
    if (managers.isEmpty()) {
      return;
    }
    String pilotName =
        inspector.fullName() != null && !inspector.fullName().isBlank()
            ? inspector.fullName()
            : inspector.email();
    String title = "Bằng lái UAV của phi công đã hết hạn";
    String body =
        "Giấy phép "
            + licenseLabel
            + " của Inspector "
            + pilotName
            + " đã hết hạn vào ngày "
            + formattedDate
            + ". Tài khoản tạm thời bị khóa khỏi danh sách phân công bay mới cho đến khi cập nhật bằng gia hạn.";
    for (ActiveUser manager : managers) {
      String managerKey =
          "PILOT_LICENSE_EXPIRED_MANAGER:" + inspector.id() + ":" + expiryDate + ":" + manager.id();
      if (!notificationRepository.existsByDeduplicationKey(managerKey)) {
        Notification notification =
            new Notification(
                manager.id(),
                "PILOT_LICENSE_EXPIRED_ALERT",
                NotificationChannel.IN_APP,
                title,
                body,
                "/onboarding-reviews",
                managerKey);
        saveWithDeduplication(notification, managerKey);
      }
    }
  }

  private static String buildInspectorTitle(WarningTier tier) {
    return switch (tier) {
      case EXPIRED -> "Khẩn cấp: Giấy phép điều khiển UAV đã hết hạn";
      case DAYS_7 -> "Cảnh báo: Giấy phép UAV sắp hết hạn trong 7 ngày";
      case DAYS_30 -> "Nhắc nhở: Giấy phép điều khiển UAV sắp hết hạn";
    };
  }

  private static String buildInspectorBody(
      WarningTier tier, String licenseLabel, String formattedDate, long remainingDays) {
    return switch (tier) {
      case EXPIRED ->
          "Giấy phép điều khiển UAV "
              + licenseLabel
              + " của bạn đã hết hạn từ ngày "
              + formattedDate
              + ". Hệ thống đã tạm khóa quyền nhận nhiệm vụ bay và bắt đầu chuyến bay mới. Vui lòng cập nhật giấy phép gia hạn để tiếp tục hoạt động.";
      case DAYS_7 ->
          "Giấy phép điều khiển UAV "
              + licenseLabel
              + " của bạn sẽ hết hạn sau "
              + remainingDays
              + " ngày (ngày "
              + formattedDate
              + "). Hãy nhanh chóng gia hạn và tải lên bản scan mới để không bị gián đoạn lịch bay.";
      case DAYS_30 ->
          "Giấy phép điều khiển UAV "
              + licenseLabel
              + " của bạn sẽ hết hạn sau "
              + remainingDays
              + " ngày (ngày "
              + formattedDate
              + "). Vui lòng chuẩn bị hồ sơ gia hạn và cập nhật thông tin trên hệ thống.";
    };
  }

  private void sendEmailBestEffort(ActiveUser inspector, String subject, String body) {
    if (mailSender == null || mailFrom == null || mailFrom.isBlank()) {
      return;
    }
    JavaMailSender sender = mailSender.getIfAvailable();
    if (sender == null || inspector.email() == null || inspector.email().isBlank()) {
      return;
    }
    try {
      String recipientName =
          inspector.fullName() != null && !inspector.fullName().isBlank()
              ? inspector.fullName()
              : "Inspector";
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(mailFrom);
      message.setTo(inspector.email());
      message.setSubject("[SmartDroneInspection] " + subject);
      message.setText(
          "Xin chào "
              + recipientName
              + ",\n\n"
              + body
              + "\n\nVui lòng đăng nhập cổng vận hành SmartDroneInspection -> mục Hồ sơ & Hợp đồng Đối tác để cập nhật.\n\nTrân trọng,\nSmartDroneInspection");
      sender.send(message);
    } catch (Exception ex) {
      log.warn(
          "Failed to send license expiry reminder email to {}: {}",
          inspector.email(),
          ex.getMessage());
    }
  }

  private void saveWithDeduplication(Notification notification, String deduplicationKey) {
    try {
      notificationRepository.saveAndFlush(notification);
    } catch (DataIntegrityViolationException ex) {
      log.debug("Duplicate license expiry notification ignored for key {}", deduplicationKey);
    }
  }
}
