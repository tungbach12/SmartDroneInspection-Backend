package com.smartdroneinspection.notifications.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.notifications.api.dto.response.NotificationPageResponse;
import com.smartdroneinspection.notifications.api.dto.response.NotificationResponse;
import com.smartdroneinspection.notifications.api.dto.response.NotificationUnreadCountResponse;
import com.smartdroneinspection.notifications.domain.enums.NotificationChannel;
import com.smartdroneinspection.notifications.domain.enums.NotificationStatus;
import com.smartdroneinspection.notifications.service.NotificationService;
import com.smartdroneinspection.shared.api.ApiResponse;
import com.smartdroneinspection.shared.exception.BusinessException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

class NotificationControllerTest {

  private NotificationService notificationService;
  private NotificationController controller;

  @BeforeEach
  void setUp() {
    notificationService = mock(NotificationService.class);
    controller = new NotificationController(notificationService);
  }

  @Test
  void listExtractsSubjectAndCallsService() {
    UUID userId = UUID.randomUUID();
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn(userId.toString());

    NotificationPageResponse pageResponse = new NotificationPageResponse(List.of(), 1, 20, 0, 0);
    when(notificationService.list(userId, 1, 20, false)).thenReturn(pageResponse);

    ApiResponse<NotificationPageResponse> response = controller.list(jwt, 1, 20, false);

    assertThat(response.success()).isTrue();
    assertThat(response.data()).isEqualTo(pageResponse);
    verify(notificationService).list(userId, 1, 20, false);
  }

  @Test
  void unreadCountExtractsSubjectAndCallsService() {
    UUID userId = UUID.randomUUID();
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn(userId.toString());

    NotificationUnreadCountResponse countResponse = new NotificationUnreadCountResponse(7);
    when(notificationService.unreadCount(userId)).thenReturn(countResponse);

    ApiResponse<NotificationUnreadCountResponse> response = controller.unreadCount(jwt);

    assertThat(response.success()).isTrue();
    assertThat(response.data().unreadCount()).isEqualTo(7);
    verify(notificationService).unreadCount(userId);
  }

  @Test
  void markReadCallsService() {
    UUID userId = UUID.randomUUID();
    UUID notificationId = UUID.randomUUID();
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn(userId.toString());

    NotificationResponse notificationResponse =
        new NotificationResponse(
            notificationId,
            "INSPECTION_ASSIGNED",
            NotificationChannel.IN_APP,
            "Assigned",
            "Body",
            "/inspections/1",
            NotificationStatus.READ,
            Instant.now(),
            Instant.now(),
            true);
    when(notificationService.markRead(userId, notificationId)).thenReturn(notificationResponse);

    ApiResponse<NotificationResponse> response = controller.markRead(jwt, notificationId);

    assertThat(response.success()).isTrue();
    assertThat(response.data()).isEqualTo(notificationResponse);
    verify(notificationService).markRead(userId, notificationId);
  }

  @Test
  void subjectThrowsUnauthorizedWhenJwtIsNull() {
    assertThatThrownBy(() -> controller.list(null, 1, 20, false))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex -> {
              BusinessException be = (BusinessException) ex;
              assertThat(be.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
              assertThat(be.code()).isEqualTo("UNAUTHORIZED");
            });
  }

  @Test
  void subjectThrowsUnauthorizedWhenSubjectInvalidUuid() {
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("not-a-valid-uuid");

    assertThatThrownBy(() -> controller.list(jwt, 1, 20, false))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex -> {
              BusinessException be = (BusinessException) ex;
              assertThat(be.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
              assertThat(be.code()).isEqualTo("INVALID_TOKEN");
            });
  }
}
