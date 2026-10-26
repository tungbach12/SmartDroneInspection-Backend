package com.smartdroneinspection.notifications.service;

import com.smartdroneinspection.notifications.api.dto.response.NotificationPageResponse;
import com.smartdroneinspection.notifications.api.dto.response.NotificationResponse;
import com.smartdroneinspection.notifications.api.dto.response.NotificationUnreadCountResponse;
import com.smartdroneinspection.notifications.domain.Notification;
import com.smartdroneinspection.notifications.repository.NotificationRepository;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import com.smartdroneinspection.users.UserAccess.ActiveUser;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

  private static final int MAX_PAGE_SIZE = 100;

  private final NotificationRepository notificationRepository;
  private final UserAccess userAccess;

  public NotificationService(NotificationRepository notificationRepository, UserAccess userAccess) {
    this.notificationRepository = notificationRepository;
    this.userAccess = userAccess;
  }

  @Transactional(readOnly = true)
  public NotificationPageResponse list(UUID userId, int page, int pageSize, boolean unreadOnly) {
    ActiveUser actor = requireActiveUser(userId);
    int safePage = Math.max(1, page);
    int safePageSize = clampPageSize(pageSize);

    PageRequest pageRequest = PageRequest.of(safePage - 1, safePageSize);
    Page<Notification> pagedResult;

    if (actor.organizationId() != null) {
      if (unreadOnly) {
        pagedResult =
            notificationRepository
                .findByRecipientUserIdAndOrganizationIdAndReadAtIsNullOrderByCreatedAtDesc(
                    actor.id(), actor.organizationId(), pageRequest);
      } else {
        pagedResult =
            notificationRepository.findByRecipientUserIdAndOrganizationIdOrderByCreatedAtDesc(
                actor.id(), actor.organizationId(), pageRequest);
      }
    } else {
      if (unreadOnly) {
        pagedResult =
            notificationRepository.findByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(
                actor.id(), pageRequest);
      } else {
        pagedResult =
            notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(
                actor.id(), pageRequest);
      }
    }

    List<NotificationResponse> items =
        pagedResult.getContent().stream().map(NotificationResponse::from).toList();

    return new NotificationPageResponse(
        items, safePage, safePageSize, pagedResult.getTotalElements(), pagedResult.getTotalPages());
  }

  @Transactional(readOnly = true)
  public NotificationUnreadCountResponse unreadCount(UUID userId) {
    ActiveUser actor = requireActiveUser(userId);
    long count =
        actor.organizationId() != null
            ? notificationRepository.countByRecipientUserIdAndOrganizationIdAndReadAtIsNull(
                actor.id(), actor.organizationId())
            : notificationRepository.countByRecipientUserIdAndReadAtIsNull(actor.id());
    return new NotificationUnreadCountResponse(count);
  }

  @Transactional
  public NotificationResponse markRead(UUID userId, UUID notificationId) {
    ActiveUser actor = requireActiveUser(userId);
    Notification notification =
        (actor.organizationId() != null
                ? notificationRepository.findByIdAndRecipientUserIdAndOrganizationId(
                    notificationId, actor.id(), actor.organizationId())
                : notificationRepository.findByIdAndRecipientUserId(notificationId, actor.id()))
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "NOTIFICATION_NOT_FOUND",
                        "Notification not found or access denied"));

    notification.markRead();
    Notification saved = notificationRepository.save(notification);
    return NotificationResponse.from(saved);
  }

  private ActiveUser requireActiveUser(UUID userId) {
    return userAccess
        .findActiveUser(userId)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.FORBIDDEN, "USER_INACTIVE", "Active user account required"));
  }

  private static int clampPageSize(int pageSize) {
    if (pageSize < 1) {
      return 20;
    }
    return Math.min(pageSize, MAX_PAGE_SIZE);
  }
}
