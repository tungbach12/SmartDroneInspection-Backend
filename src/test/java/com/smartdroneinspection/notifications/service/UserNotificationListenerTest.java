package com.smartdroneinspection.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.notifications.domain.Notification;
import com.smartdroneinspection.notifications.repository.NotificationRepository;
import com.smartdroneinspection.users.UserAccess;
import com.smartdroneinspection.users.UserAccess.ActiveUser;
import com.smartdroneinspection.users.events.InspectorOnboardingReviewedEvent;
import com.smartdroneinspection.users.events.InspectorOnboardingUpdatedEvent;
import com.smartdroneinspection.users.events.ProspectiveCustomerAirspaceRequestedEvent;
import com.smartdroneinspection.users.events.ProspectiveCustomerAirspaceReviewedEvent;
import com.smartdroneinspection.users.events.UserSecurityAlertEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UserNotificationListenerTest {

  private NotificationRepository notificationRepository;
  private UserAccess userAccess;
  private NotificationEmailHelper emailHelper;
  private UserNotificationListener listener;

  @BeforeEach
  void setUp() {
    notificationRepository = mock(NotificationRepository.class);
    userAccess = mock(UserAccess.class);
    emailHelper = mock(NotificationEmailHelper.class);
    listener = new UserNotificationListener(notificationRepository, userAccess, emailHelper);
  }

  @Test
  void airspaceRequestedAndReviewedNotifyAdminsAndCustomerWithEmail() {
    UUID regId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();
    when(userAccess.listActiveAdmins())
        .thenReturn(List.of(new ActiveUser(adminId, Set.of("ADMIN"), "Admin")));

    listener.onAirspaceReviewRequested(
        new ProspectiveCustomerAirspaceRequestedEvent(
            regId, customerId, "Acme Grid", "AST-1", "RESTRICTED_REVIEW", Instant.now()));
    listener.onAirspaceReviewed(
        new ProspectiveCustomerAirspaceReviewedEvent(
            regId,
            customerId,
            "cust@acme.vn",
            "Acme Owner",
            "Acme Grid",
            "AST-1",
            true,
            "Cleared by CAAV",
            Instant.now()));

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository, times(2)).saveAndFlush(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(Notification::getRecipientUserId, Notification::getEventType)
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple(adminId, "CUSTOMER_AIRSPACE_REVIEW_REQUESTED"),
            org.assertj.core.groups.Tuple.tuple(customerId, "CUSTOMER_AIRSPACE_APPROVED"));
    verify(emailHelper)
        .sendBilingualBestEffort(
            eq("cust@acme.vn"), eq("Acme Owner"), anyString(), anyString(), anyString());
  }

  @Test
  void onboardingAndSecurityAlertsCreateInAppAndSendEmail() {
    UUID inspectorId = UUID.randomUUID();
    UUID managerId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    when(userAccess.listActiveManagers())
        .thenReturn(List.of(new ActiveUser(managerId, Set.of("MANAGER"), "Mgr")));
    when(userAccess.findActiveUser(inspectorId))
        .thenReturn(
            Optional.of(
                new ActiveUser(inspectorId, Set.of("INSPECTOR"), "Pilot B", "pilotb@example.com")));

    listener.onInspectorOnboardingUpdated(
        new InspectorOnboardingUpdatedEvent(inspectorId, "LICENSE_SCAN", Instant.now()));
    listener.onInspectorOnboardingReviewed(
        new InspectorOnboardingReviewedEvent(inspectorId, true, null, Instant.now()));
    listener.onUserSecurityAlert(
        new UserSecurityAlertEvent(
            userId, "user@example.com", "User Test", "PASSWORD_CHANGED", null, Instant.now()));

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository, times(3)).saveAndFlush(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(Notification::getEventType)
        .containsExactlyInAnyOrder(
            "INSPECTOR_ONBOARDING_UPDATED",
            "INSPECTOR_ONBOARDING_APPROVED",
            "SECURITY_PASSWORD_CHANGED");
    verify(emailHelper)
        .sendBilingualBestEffort(
            eq("pilotb@example.com"), eq("Pilot B"), anyString(), anyString(), anyString());
    verify(emailHelper)
        .sendBilingualBestEffort(
            eq("user@example.com"), eq("User Test"), anyString(), anyString(), anyString());
  }
}
