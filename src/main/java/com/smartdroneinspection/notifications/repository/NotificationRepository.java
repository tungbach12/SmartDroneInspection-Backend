package com.smartdroneinspection.notifications.repository;

import com.smartdroneinspection.notifications.domain.Notification;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

  Page<Notification> findByRecipientUserIdAndOrganizationIdOrderByCreatedAtDesc(
      UUID recipientUserId, UUID organizationId, Pageable pageable);

  Page<Notification> findByRecipientUserIdAndOrganizationIdAndReadAtIsNullOrderByCreatedAtDesc(
      UUID recipientUserId, UUID organizationId, Pageable pageable);

  long countByRecipientUserIdAndOrganizationIdAndReadAtIsNull(
      UUID recipientUserId, UUID organizationId);

  Optional<Notification> findByIdAndRecipientUserIdAndOrganizationId(
      UUID id, UUID recipientUserId, UUID organizationId);

  Page<Notification> findByRecipientUserIdOrderByCreatedAtDesc(
      UUID recipientUserId, Pageable pageable);

  Page<Notification> findByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(
      UUID recipientUserId, Pageable pageable);

  long countByRecipientUserIdAndReadAtIsNull(UUID recipientUserId);

  Optional<Notification> findByIdAndRecipientUserId(UUID id, UUID recipientUserId);

  Optional<Notification> findByDeduplicationKey(String deduplicationKey);

  boolean existsByDeduplicationKey(String deduplicationKey);
}
