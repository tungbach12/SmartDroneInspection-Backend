package com.smartdroneinspection.notifications.service;

import com.smartdroneinspection.notifications.domain.Notification;
import com.smartdroneinspection.notifications.domain.enums.NotificationChannel;
import com.smartdroneinspection.notifications.repository.NotificationRepository;
import com.smartdroneinspection.users.UserAccess;
import com.smartdroneinspection.users.UserAccess.ActiveUser;
import com.smartdroneinspection.users.events.InspectorOnboardingReviewedEvent;
import com.smartdroneinspection.users.events.InspectorOnboardingUpdatedEvent;
import com.smartdroneinspection.users.events.UserSecurityAlertEvent;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Records in-app and email notifications for inspector onboarding updates/decisions and account
 * security alerts.
 */
@Component
public class UserNotificationListener {

  private static final Logger log = LoggerFactory.getLogger(UserNotificationListener.class);

  private final NotificationRepository notificationRepository;
  private final UserAccess userAccess;
  private final NotificationEmailHelper emailHelper;

  public UserNotificationListener(
      NotificationRepository notificationRepository,
      UserAccess userAccess,
      NotificationEmailHelper emailHelper) {
    this.notificationRepository = notificationRepository;
    this.userAccess = userAccess;
    this.emailHelper = emailHelper;
  }

  @ApplicationModuleListener
  public void onInspectorOnboardingUpdated(InspectorOnboardingUpdatedEvent event) {
    if (event == null || event.inspectorUserId() == null) {
      return;
    }
    long epoch = epochOf(event.occurredAt());
    ActiveUser inspector = userAccess.findActiveUser(event.inspectorUserId()).orElse(null);
    String name =
        inspector != null
            ? NotificationText.safeOrFallback(
                inspector.fullName(), event.inspectorUserId().toString())
            : event.inspectorUserId().toString();
    String docVi =
        "PARTNER_CONTRACT".equalsIgnoreCase(event.documentType())
            ? "hợp đồng đối tác"
            : "bản scan bằng lái";
    String docEn =
        "PARTNER_CONTRACT".equalsIgnoreCase(event.documentType())
            ? "partner contract"
            : "pilot license scan";
    String title =
        NotificationText.bilingual(
            "Inspector vừa cập nhật hồ sơ đối tác", "Inspector updated onboarding documents");
    String body =
        NotificationText.bilingual(
            "Inspector " + name + " đã tải lên " + docVi + " mới. Vui lòng kiểm tra và xét duyệt.",
            "Inspector " + name + " uploaded a new " + docEn + ". Please review.");
    String prefix = "INSPECTOR_ONBOARDING_UPDATED:" + event.inspectorUserId() + ":" + epoch;
    for (ActiveUser manager : userAccess.listActiveManagers()) {
      if (manager.id() == null) continue;
      String key = prefix + ":" + manager.id();
      NotificationText.saveWithDeduplication(
          notificationRepository,
          new Notification(
              manager.id(),
              "INSPECTOR_ONBOARDING_UPDATED",
              NotificationChannel.IN_APP,
              title,
              body,
              "/manager/onboarding-reviews",
              key),
          key,
          log);
    }
  }

  @ApplicationModuleListener
  public void onInspectorOnboardingReviewed(InspectorOnboardingReviewedEvent event) {
    if (event == null || event.inspectorUserId() == null) {
      return;
    }
    long epoch = epochOf(event.occurredAt());
    String reason = NotificationText.safeOrFallback(event.reason(), "");
    String eventType =
        event.approved() ? "INSPECTOR_ONBOARDING_APPROVED" : "INSPECTOR_ONBOARDING_REJECTED";
    String viTitle =
        event.approved()
            ? "Hồ sơ đối tác Inspector đã được phê duyệt"
            : "Hồ sơ đối tác Inspector cần chỉnh sửa";
    String enTitle =
        event.approved() ? "Inspector onboarding approved" : "Inspector onboarding rejected";
    String viBody =
        event.approved()
            ? "Hồ sơ pháp lý và bằng lái đối tác của bạn đã được phê duyệt. Bạn đã đủ điều kiện nhận phân công kiểm tra."
            : "Hồ sơ đối tác của bạn bị từ chối"
                + (reason.isEmpty() ? ". Vui lòng cập nhật lại." : ": " + reason + ".");
    String enBody =
        event.approved()
            ? "Your partner profile and onboarding documents have been approved. You are now eligible for inspection assignments."
            : "Your partner onboarding review was rejected"
                + (reason.isEmpty() ? ". Please update your documents." : ": " + reason + ".");
    String key = eventType + ":" + event.inspectorUserId() + ":" + epoch;
    NotificationText.saveWithDeduplication(
        notificationRepository,
        new Notification(
            event.inspectorUserId(),
            eventType,
            NotificationChannel.IN_APP,
            NotificationText.bilingual(viTitle, enTitle),
            NotificationText.bilingual(viBody, enBody),
            "/operations/partner-onboarding",
            key),
        key,
        log);

    userAccess
        .findActiveUser(event.inspectorUserId())
        .ifPresent(
            user ->
                emailHelper.sendBilingualBestEffort(
                    user.email(), user.fullName(), viTitle + " / " + enTitle, viBody, enBody));
  }

  @ApplicationModuleListener
  public void onUserSecurityAlert(UserSecurityAlertEvent event) {
    if (event == null || event.userId() == null || event.alertKind() == null) {
      return;
    }
    long epoch = epochOf(event.occurredAt());
    String detail = NotificationText.safeOrFallback(event.detail(), "");

    String eventType;
    String viTitle;
    String enTitle;
    String viBody;
    String enBody;

    switch (event.alertKind()) {
      case "PASSWORD_CHANGED" -> {
        eventType = "SECURITY_PASSWORD_CHANGED";
        viTitle = "Cảnh báo bảo mật: Mật khẩu đã thay đổi";
        enTitle = "Security alert: Password changed";
        viBody =
            "Mật khẩu tài khoản của bạn vừa được thay đổi và các phiên đăng nhập khác đã được thu hồi. Nếu không phải bạn thực hiện, hãy liên hệ quản trị viên ngay.";
        enBody =
            "Your account password was changed and other active sessions were revoked. Contact an administrator immediately if this was not you.";
      }
      case "PASSWORD_RESET_SELF" -> {
        eventType = "SECURITY_PASSWORD_RESET";
        viTitle = "Cảnh báo bảo mật: Đã đặt lại mật khẩu";
        enTitle = "Security alert: Password reset completed";
        viBody =
            "Mật khẩu của bạn vừa được đặt lại qua mã xác thực OTP và toàn bộ phiên đăng nhập cũ đã bị đăng xuất.";
        enBody =
            "Your password was reset via OTP verification and all prior active sessions were signed out.";
      }
      case "PASSWORD_RESET_BY_ADMIN" -> {
        eventType = "SECURITY_PASSWORD_RESET";
        viTitle = "Quản trị viên đã đặt lại mật khẩu tài khoản";
        enTitle = "Administrator reset your account password";
        viBody =
            "Quản trị viên đã đặt lại mật khẩu tài khoản của bạn và thu hồi các phiên đăng nhập cũ. Vui lòng kiểm tra email để nhận mật khẩu tạm mới.";
        enBody =
            "An administrator reset your password and revoked existing sessions. Check your email for the temporary password.";
      }
      case "SESSIONS_REVOKED_ALL" -> {
        eventType = "SECURITY_SESSIONS_REVOKED";
        viTitle = "Đã đăng xuất khỏi tất cả thiết bị";
        enTitle = "All active sessions signed out";
        viBody =
            "Toàn bộ phiên đăng nhập trên các thiết bị khác của bạn vừa được thu hồi theo yêu cầu.";
        enBody = "All active sessions across your devices were revoked upon request.";
      }
      case "ROLES_UPDATED" -> {
        eventType = "SECURITY_ROLES_UPDATED";
        viTitle = "Quyền hạn tài khoản đã được cập nhật";
        enTitle = "Account roles updated";
        viBody =
            "Quản trị viên đã cập nhật vai trò tài khoản của bạn"
                + (detail.isEmpty() ? "" : " (" + detail + ")")
                + ". Phiên cũ đã được thu hồi để áp dụng quyền mới.";
        enBody =
            "An administrator updated your account roles"
                + (detail.isEmpty() ? "" : " (" + detail + ")")
                + ". Existing sessions were revoked to apply the updated permissions.";
      }
      case "STATUS_UPDATED" -> {
        eventType = "SECURITY_STATUS_UPDATED";
        viTitle = "Trạng thái tài khoản đã thay đổi";
        enTitle = "Account status updated";
        viBody =
            "Trạng thái tài khoản của bạn đã được quản trị viên chuyển sang: "
                + (detail.isEmpty() ? "UPDATED" : detail)
                + ".";
        enBody =
            "An administrator changed your account status to: "
                + (detail.isEmpty() ? "UPDATED" : detail)
                + ".";
      }
      default -> {
        eventType = "SECURITY_ALERT";
        viTitle = "Cảnh báo bảo mật tài khoản";
        enTitle = "Account security alert";
        viBody = "Tài khoản của bạn vừa có thay đổi liên quan đến bảo mật.";
        enBody = "A security-sensitive change occurred on your account.";
      }
    }

    String key = eventType + ":" + event.userId() + ":" + epoch;
    NotificationText.saveWithDeduplication(
        notificationRepository,
        new Notification(
            event.userId(),
            eventType,
            NotificationChannel.IN_APP,
            NotificationText.bilingual(viTitle, enTitle),
            NotificationText.bilingual(viBody, enBody),
            "/settings",
            key),
        key,
        log);

    emailHelper.sendBilingualBestEffort(
        event.email(), event.fullName(), viTitle + " / " + enTitle, viBody, enBody);
  }

  private static long epochOf(Instant instant) {
    return instant != null ? instant.getEpochSecond() : Instant.now().getEpochSecond();
  }
}
