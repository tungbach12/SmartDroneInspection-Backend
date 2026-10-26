package com.smartdroneinspection.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.notifications.api.dto.response.NotificationPageResponse;
import com.smartdroneinspection.notifications.api.dto.response.NotificationResponse;
import com.smartdroneinspection.notifications.api.dto.response.NotificationUnreadCountResponse;
import com.smartdroneinspection.notifications.domain.Notification;
import com.smartdroneinspection.notifications.domain.enums.NotificationChannel;
import com.smartdroneinspection.notifications.domain.enums.NotificationStatus;
import com.smartdroneinspection.notifications.repository.NotificationRepository;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import com.smartdroneinspection.users.UserAccess.ActiveUser;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

class NotificationServiceTest {

  private NotificationRepository notificationRepository;
  private UserAccess userAccess;
  private NotificationService notificationService;

  @BeforeEach
  void setUp() {
    notificationRepository = mock(NotificationRepository.class);
    userAccess = mock(UserAccess.class);
    notificationService = new NotificationService(notificationRepository, userAccess);
  }

  @Test
  void listThrowsWhenUserIsInactive() {
    UUID userId = UUID.randomUUID();
    when(userAccess.findActiveUser(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> notificationService.list(userId, 1, 20, false))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex -> {
              BusinessException be = (BusinessException) ex;
              assertThat(be.status()).isEqualTo(HttpStatus.FORBIDDEN);
              assertThat(be.code()).isEqualTo("USER_INACTIVE");
            });

    verify(notificationRepository, never())
        .findByRecipientUserIdAndOrganizationIdOrderByCreatedAtDesc(any(), any(), any());
  }

  @Test
  void listWithOrgReturnsPaginatedNotifications() {
    UUID userId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    ActiveUser user = new ActiveUser(userId, Set.of("INSPECTOR"), orgId, "Inspector Test");
    when(userAccess.findActiveUser(userId)).thenReturn(Optional.of(user));

    Notification notification =
        new Notification(
            userId,
            orgId,
            "INSPECTION_ASSIGNED",
            NotificationChannel.IN_APP,
            "Title",
            "Body",
            "/inspections/1");

    PageRequest expectedPageRequest = PageRequest.of(0, 20);
    when(notificationRepository.findByRecipientUserIdAndOrganizationIdOrderByCreatedAtDesc(
            userId, orgId, expectedPageRequest))
        .thenReturn(new PageImpl<>(List.of(notification), expectedPageRequest, 1));

    NotificationPageResponse response = notificationService.list(userId, 1, 20, false);

    assertThat(response.items()).hasSize(1);
    assertThat(response.page()).isEqualTo(1);
    assertThat(response.pageSize()).isEqualTo(20);
    assertThat(response.totalCount()).isEqualTo(1);
    assertThat(response.totalPages()).isEqualTo(1);

    NotificationResponse item = response.items().get(0);
    assertThat(item.eventType()).isEqualTo("INSPECTION_ASSIGNED");
    assertThat(item.channel()).isEqualTo(NotificationChannel.IN_APP);
    assertThat(item.status()).isEqualTo(NotificationStatus.SENT);
    assertThat(item.read()).isFalse();
    assertThat(item.isRead()).isFalse();
  }

  @Test
  void listWithOrgAndUnreadOnlyQueriesUnread() {
    UUID userId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    ActiveUser user = new ActiveUser(userId, Set.of("INSPECTOR"), orgId, "Inspector Test");
    when(userAccess.findActiveUser(userId)).thenReturn(Optional.of(user));

    PageRequest expectedPageRequest = PageRequest.of(0, 10);
    when(notificationRepository
            .findByRecipientUserIdAndOrganizationIdAndReadAtIsNullOrderByCreatedAtDesc(
                userId, orgId, expectedPageRequest))
        .thenReturn(new PageImpl<>(List.of(), expectedPageRequest, 0));

    NotificationPageResponse response = notificationService.list(userId, 1, 10, true);

    assertThat(response.items()).isEmpty();
    assertThat(response.totalCount()).isZero();
    verify(notificationRepository)
        .findByRecipientUserIdAndOrganizationIdAndReadAtIsNullOrderByCreatedAtDesc(
            userId, orgId, expectedPageRequest);
  }

  @Test
  void listWithoutOrgQueriesPlatformWide() {
    UUID adminId = UUID.randomUUID();
    ActiveUser admin = new ActiveUser(adminId, Set.of("ADMIN"), null, "Platform Admin");
    when(userAccess.findActiveUser(adminId)).thenReturn(Optional.of(admin));

    PageRequest expectedPageRequest = PageRequest.of(0, 20);
    when(notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(
            adminId, expectedPageRequest))
        .thenReturn(new PageImpl<>(List.of(), expectedPageRequest, 0));

    NotificationPageResponse response = notificationService.list(adminId, 1, 20, false);

    assertThat(response.items()).isEmpty();
    verify(notificationRepository)
        .findByRecipientUserIdOrderByCreatedAtDesc(adminId, expectedPageRequest);
  }

  @Test
  void listWithoutOrgAndUnreadOnlyQueriesPlatformWideUnread() {
    UUID adminId = UUID.randomUUID();
    ActiveUser admin = new ActiveUser(adminId, Set.of("ADMIN"), null, "Platform Admin");
    when(userAccess.findActiveUser(adminId)).thenReturn(Optional.of(admin));

    PageRequest expectedPageRequest = PageRequest.of(0, 20);
    when(notificationRepository.findByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(
            adminId, expectedPageRequest))
        .thenReturn(new PageImpl<>(List.of(), expectedPageRequest, 0));

    NotificationPageResponse response = notificationService.list(adminId, 1, 20, true);

    assertThat(response.items()).isEmpty();
    verify(notificationRepository)
        .findByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(adminId, expectedPageRequest);
  }

  @Test
  void unreadCountReturnsCorrectCount() {
    UUID userId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    ActiveUser user = new ActiveUser(userId, Set.of("INSPECTOR"), orgId, "Inspector Test");
    when(userAccess.findActiveUser(userId)).thenReturn(Optional.of(user));
    when(notificationRepository.countByRecipientUserIdAndOrganizationIdAndReadAtIsNull(
            userId, orgId))
        .thenReturn(5L);

    NotificationUnreadCountResponse response = notificationService.unreadCount(userId);

    assertThat(response.unreadCount()).isEqualTo(5L);
  }

  @Test
  void unreadCountWithoutOrgReturnsCount() {
    UUID adminId = UUID.randomUUID();
    ActiveUser admin = new ActiveUser(adminId, Set.of("ADMIN"), null, "Platform Admin");
    when(userAccess.findActiveUser(adminId)).thenReturn(Optional.of(admin));
    when(notificationRepository.countByRecipientUserIdAndReadAtIsNull(adminId)).thenReturn(3L);

    NotificationUnreadCountResponse response = notificationService.unreadCount(adminId);

    assertThat(response.unreadCount()).isEqualTo(3L);
  }

  @Test
  void markReadThrowsNotFoundWhenNotificationDoesNotExist() {
    UUID userId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    UUID notificationId = UUID.randomUUID();
    ActiveUser user = new ActiveUser(userId, Set.of("INSPECTOR"), orgId, "Inspector Test");
    when(userAccess.findActiveUser(userId)).thenReturn(Optional.of(user));
    when(notificationRepository.findByIdAndRecipientUserIdAndOrganizationId(
            notificationId, userId, orgId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> notificationService.markRead(userId, notificationId))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex -> {
              BusinessException be = (BusinessException) ex;
              assertThat(be.status()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(be.code()).isEqualTo("NOTIFICATION_NOT_FOUND");
            });

    verify(notificationRepository, never()).save(any());
  }

  @Test
  void markReadUpdatesNotificationSuccessfully() {
    UUID userId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    UUID notificationId = UUID.randomUUID();
    ActiveUser user = new ActiveUser(userId, Set.of("INSPECTOR"), orgId, "Inspector Test");
    when(userAccess.findActiveUser(userId)).thenReturn(Optional.of(user));

    Notification notification =
        new Notification(
            userId,
            orgId,
            "INSPECTION_ASSIGNED",
            NotificationChannel.IN_APP,
            "Title",
            "Body",
            "/inspections/1");

    when(notificationRepository.findByIdAndRecipientUserIdAndOrganizationId(
            notificationId, userId, orgId))
        .thenReturn(Optional.of(notification));
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    NotificationResponse response = notificationService.markRead(userId, notificationId);

    assertThat(response.status()).isEqualTo(NotificationStatus.READ);
    assertThat(response.readAt()).isNotNull();
    assertThat(response.read()).isTrue();
    assertThat(response.isRead()).isTrue();

    verify(notificationRepository).save(notification);
  }
}
