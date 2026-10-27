package com.smartdroneinspection.notifications.api;

import com.smartdroneinspection.notifications.api.dto.response.NotificationPageResponse;
import com.smartdroneinspection.notifications.api.dto.response.NotificationResponse;
import com.smartdroneinspection.notifications.api.dto.response.NotificationUnreadCountResponse;
import com.smartdroneinspection.notifications.service.NotificationService;
import com.smartdroneinspection.shared.api.ApiResponse;
import com.smartdroneinspection.shared.exception.BusinessException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

  private final NotificationService notificationService;

  public NotificationController(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<NotificationPageResponse> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(defaultValue = "false") boolean unreadOnly) {
    return ApiResponse.success(notificationService.list(subject(jwt), page, pageSize, unreadOnly));
  }

  @GetMapping("/unread-count")
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<NotificationUnreadCountResponse> unreadCount(
      @AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success(notificationService.unreadCount(subject(jwt)));
  }

  @RequestMapping(
      value = "/{id}/read",
      method = {RequestMethod.PATCH, RequestMethod.PUT})
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<NotificationResponse> markRead(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return ApiResponse.success(notificationService.markRead(subject(jwt), id));
  }

  private static UUID subject(Jwt jwt) {
    if (jwt == null || jwt.getSubject() == null) {
      throw new BusinessException(
          HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
    }
    try {
      return UUID.fromString(jwt.getSubject());
    } catch (IllegalArgumentException e) {
      throw new BusinessException(
          HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Subject is not a valid UUID");
    }
  }
}
