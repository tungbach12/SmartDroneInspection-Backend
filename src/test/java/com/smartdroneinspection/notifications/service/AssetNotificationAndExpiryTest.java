package com.smartdroneinspection.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.assets.DroneExpiryAccess;
import com.smartdroneinspection.assets.DroneExpiryAccess.PartnerDroneRegistration;
import com.smartdroneinspection.assets.events.DroneApplicationReviewedEvent;
import com.smartdroneinspection.assets.events.DroneApplicationSubmittedEvent;
import com.smartdroneinspection.notifications.domain.Notification;
import com.smartdroneinspection.notifications.repository.NotificationRepository;
import com.smartdroneinspection.users.UserAccess;
import com.smartdroneinspection.users.UserAccess.ActiveUser;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AssetNotificationAndExpiryTest {

  private NotificationRepository notificationRepository;
  private UserAccess userAccess;
  private DroneExpiryAccess droneExpiryAccess;
  private NotificationEmailHelper emailHelper;

  @BeforeEach
  void setUp() {
    notificationRepository = mock(NotificationRepository.class);
    userAccess = mock(UserAccess.class);
    droneExpiryAccess = mock(DroneExpiryAccess.class);
    emailHelper = mock(NotificationEmailHelper.class);
  }

  @Test
  void listenerNotifiesManagersAdminsOnSubmitAndInspectorOnReview() {
    AssetNotificationListener listener =
        new AssetNotificationListener(notificationRepository, userAccess);
    UUID appId = UUID.randomUUID();
    UUID droneId = UUID.randomUUID();
    UUID inspectorId = UUID.randomUUID();
    UUID managerId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();

    when(userAccess.listActiveManagers())
        .thenReturn(List.of(new ActiveUser(managerId, Set.of("MANAGER"), "Mgr")));
    when(userAccess.listActiveAdmins())
        .thenReturn(List.of(new ActiveUser(adminId, Set.of("ADMIN"), "Adm")));

    listener.onDroneApplicationSubmitted(
        new DroneApplicationSubmittedEvent(
            appId, null, inspectorId, "Matrice 350", "SN-001", false, Instant.now()));
    listener.onDroneApplicationReviewed(
        new DroneApplicationReviewedEvent(
            appId,
            droneId,
            inspectorId,
            "Matrice 350",
            "SN-001",
            false,
            "Blurry scan",
            Instant.now()));

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository, times(3)).saveAndFlush(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(Notification::getRecipientUserId, Notification::getEventType)
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple(managerId, "DRONE_APPLICATION_SUBMITTED"),
            org.assertj.core.groups.Tuple.tuple(adminId, "DRONE_APPLICATION_SUBMITTED"),
            org.assertj.core.groups.Tuple.tuple(inspectorId, "DRONE_APPLICATION_REJECTED"));
  }

  @Test
  void expiryCheckNotifiesInspectorAndManagersAndSendsEmailOnExpiredDrone() {
    ZoneId vn = ZoneId.of("Asia/Ho_Chi_Minh");
    Instant now = LocalDate.of(2026, 11, 25).atStartOfDay(vn).toInstant();
    Clock fixedClock = Clock.fixed(now, vn);
    DroneRegistrationExpiryCheckService checkService =
        new DroneRegistrationExpiryCheckService(
            droneExpiryAccess, userAccess, notificationRepository, emailHelper, fixedClock);

    UUID droneId = UUID.randomUUID();
    UUID inspectorId = UUID.randomUUID();
    UUID managerId = UUID.randomUUID();
    Instant yesterday = LocalDate.of(2026, 11, 24).atStartOfDay(vn).toInstant();

    when(droneExpiryAccess.listActivePartnerDroneRegistrations())
        .thenReturn(
            List.of(
                new PartnerDroneRegistration(
                    droneId, inspectorId, "SN-EXP", "Mavic 3T", "REG-99", yesterday)));
    when(userAccess.findActiveUser(inspectorId))
        .thenReturn(
            Optional.of(
                new ActiveUser(inspectorId, Set.of("INSPECTOR"), "Pilot A", "pilot@example.com")));
    when(userAccess.listActiveManagers())
        .thenReturn(List.of(new ActiveUser(managerId, Set.of("MANAGER"), "Manager A")));

    checkService.checkAllPartnerDrones();

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository, times(2)).saveAndFlush(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(Notification::getRecipientUserId, Notification::getEventType)
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple(inspectorId, "DRONE_REGISTRATION_EXPIRED"),
            org.assertj.core.groups.Tuple.tuple(managerId, "DRONE_REGISTRATION_EXPIRED_ALERT"));
    verify(emailHelper)
        .sendBilingualBestEffort(
            eq("pilot@example.com"), eq("Pilot A"), anyString(), anyString(), anyString());
  }
}
