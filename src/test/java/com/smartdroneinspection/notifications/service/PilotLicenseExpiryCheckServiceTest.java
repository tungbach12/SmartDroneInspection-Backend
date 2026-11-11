package com.smartdroneinspection.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.notifications.domain.Notification;
import com.smartdroneinspection.notifications.repository.NotificationRepository;
import com.smartdroneinspection.users.UserAccess;
import com.smartdroneinspection.users.UserAccess.ActiveUser;
import com.smartdroneinspection.users.events.PilotLicenseUpdatedEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class PilotLicenseExpiryCheckServiceTest {

  private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  private static final Instant FIXED_NOW = Instant.parse("2026-11-11T00:00:00Z");

  private UserAccess userAccess;
  private NotificationRepository notificationRepository;
  private JavaMailSender mailSender;
  private PilotLicenseExpiryCheckService service;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    userAccess = mock(UserAccess.class);
    notificationRepository = mock(NotificationRepository.class);
    mailSender = mock(JavaMailSender.class);
    ObjectProvider<JavaMailSender> mailProvider = mock(ObjectProvider.class);
    when(mailProvider.getIfAvailable()).thenReturn(mailSender);
    when(notificationRepository.saveAndFlush(any(Notification.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    service =
        new PilotLicenseExpiryCheckService(
            userAccess,
            notificationRepository,
            mailProvider,
            "noreply@smartdrone.local",
            Clock.fixed(FIXED_NOW, VN_ZONE));
  }

  @Test
  void notifiesInspectorAt30DaysWithoutNotifyingManagers() {
    UUID inspectorId = UUID.randomUUID();
    // 2026-11-11 07:00 ICT -> +20 days = 2026-12-01
    Instant expiresIn20Days = Instant.parse("2026-12-01T00:00:00Z");
    ActiveUser inspector =
        new ActiveUser(
            inspectorId,
            Set.of("INSPECTOR"),
            null,
            "INSPECTOR_PARTNER",
            "Nguyen Van Pilot",
            "pilot@example.com",
            "DC-2026-0001234",
            "Hạng B",
            expiresIn20Days,
            "licenses/scan.pdf");

    when(userAccess.listActiveInspectors()).thenReturn(List.of(inspector));
    when(notificationRepository.existsByDeduplicationKey(any())).thenReturn(false);

    service.checkAllInspectorLicenses();

    ArgumentCaptor<Notification> notifCaptor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository, times(1)).saveAndFlush(notifCaptor.capture());
    Notification saved = notifCaptor.getValue();
    assertThat(saved.getRecipientUserId()).isEqualTo(inspectorId);
    assertThat(saved.getEventType()).isEqualTo("PILOT_LICENSE_30D");
    assertThat(saved.getTargetPath()).isEqualTo("/partner-onboarding");
    assertThat(saved.getDeduplicationKey())
        .isEqualTo("PILOT_LICENSE_EXPIRY:" + inspectorId + ":2026-12-01:30D");
    assertThat(saved.getBody()).contains("DC-2026-0001234").contains("20 ngày");

    verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    verify(userAccess, never()).listActivePlatformManagers();
  }

  @Test
  void notifiesInspectorAt7DaysAndDeduplicatesRepeats() {
    UUID inspectorId = UUID.randomUUID();
    Instant expiresIn5Days = Instant.parse("2026-11-16T00:00:00Z");
    ActiveUser inspector =
        new ActiveUser(
            inspectorId,
            Set.of("INSPECTOR"),
            null,
            "INSPECTOR_PARTNER",
            "Le Thi Bay",
            "bay@example.com",
            "DC-2026-0005678",
            "Hạng A",
            expiresIn5Days,
            "licenses/scan.pdf");

    when(userAccess.listActiveInspectors()).thenReturn(List.of(inspector));
    String expectedKey = "PILOT_LICENSE_EXPIRY:" + inspectorId + ":2026-11-16:7D";
    when(notificationRepository.existsByDeduplicationKey(expectedKey)).thenReturn(false, true);

    service.checkAllInspectorLicenses();
    service.checkAllInspectorLicenses();

    ArgumentCaptor<Notification> notifCaptor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository, times(1)).saveAndFlush(notifCaptor.capture());
    assertThat(notifCaptor.getValue().getEventType()).isEqualTo("PILOT_LICENSE_7D");
    verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
  }

  @Test
  void notifiesInspectorAndManagersWhenLicenseExpired() {
    UUID inspectorId = UUID.randomUUID();
    UUID managerId = UUID.randomUUID();
    Instant expiredYesterday = Instant.parse("2026-11-10T00:00:00Z");
    ActiveUser inspector =
        new ActiveUser(
            inspectorId,
            Set.of("INSPECTOR"),
            null,
            "INSPECTOR_PARTNER",
            "Tran Expired",
            "expired@example.com",
            "DC-2025-0009999",
            "Hạng B",
            expiredYesterday,
            "licenses/scan.pdf");
    ActiveUser manager =
        new ActiveUser(managerId, Set.of("MANAGER"), null, "Platform Manager", "mgr@example.com");

    when(userAccess.listActiveInspectors()).thenReturn(List.of(inspector));
    when(userAccess.listActivePlatformManagers()).thenReturn(List.of(manager));
    when(notificationRepository.existsByDeduplicationKey(any())).thenReturn(false);

    service.checkAllInspectorLicenses();

    ArgumentCaptor<Notification> notifCaptor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository, times(2)).saveAndFlush(notifCaptor.capture());
    List<Notification> saved = notifCaptor.getAllValues();
    assertThat(saved)
        .extracting(Notification::getRecipientUserId)
        .containsExactlyInAnyOrder(inspectorId, managerId);
    assertThat(saved)
        .filteredOn(n -> n.getRecipientUserId().equals(inspectorId))
        .first()
        .satisfies(
            n -> {
              assertThat(n.getEventType()).isEqualTo("PILOT_LICENSE_EXPIRED");
              assertThat(n.getTargetPath()).isEqualTo("/partner-onboarding");
            });
    assertThat(saved)
        .filteredOn(n -> n.getRecipientUserId().equals(managerId))
        .first()
        .satisfies(
            n -> {
              assertThat(n.getEventType()).isEqualTo("PILOT_LICENSE_EXPIRED_ALERT");
              assertThat(n.getTargetPath()).isEqualTo("/onboarding-reviews");
            });
  }

  @Test
  void ignoresLicensesValidBeyond30Days() {
    UUID inspectorId = UUID.randomUUID();
    Instant valid90Days = Instant.parse("2027-02-10T00:00:00Z");
    ActiveUser inspector =
        new ActiveUser(
            inspectorId,
            Set.of("INSPECTOR"),
            null,
            "INSPECTOR_PARTNER",
            "Valid Pilot",
            "valid@example.com",
            "DC-2026-0001111",
            "Hạng B",
            valid90Days,
            "licenses/scan.pdf");

    when(userAccess.listActiveInspectors()).thenReturn(List.of(inspector));

    service.checkAllInspectorLicenses();

    verify(notificationRepository, never()).saveAndFlush(any());
    verify(mailSender, never()).send(any(SimpleMailMessage.class));
  }

  @Test
  void evaluatesImmediatelyWhenPilotLicenseUpdatedEventFires() {
    UUID inspectorId = UUID.randomUUID();
    Instant expiresIn3Days = Instant.parse("2026-11-14T00:00:00Z");
    ActiveUser inspector =
        new ActiveUser(
            inspectorId,
            Set.of("INSPECTOR"),
            null,
            "INSPECTOR_PARTNER",
            "Event Pilot",
            "event@example.com",
            "DC-2026-0002222",
            "Hạng B",
            expiresIn3Days,
            "licenses/scan.pdf");

    when(userAccess.findActiveUser(inspectorId)).thenReturn(Optional.of(inspector));
    when(notificationRepository.existsByDeduplicationKey(any())).thenReturn(false);

    service.onPilotLicenseUpdated(new PilotLicenseUpdatedEvent(inspectorId, expiresIn3Days));

    verify(notificationRepository, times(1)).saveAndFlush(any(Notification.class));
  }
}
