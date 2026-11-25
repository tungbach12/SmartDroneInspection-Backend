package com.smartdroneinspection.notifications.repository;

import com.smartdroneinspection.notifications.domain.Notification;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

  Page<Notification> findByRecipientUserIdOrderByCreatedAtDesc(
      UUID recipientUserId, Pageable pageable);

  Page<Notification> findByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(
      UUID recipientUserId, Pageable pageable);

  long countByRecipientUserIdAndReadAtIsNull(UUID recipientUserId);

  Optional<Notification> findByIdAndRecipientUserId(UUID id, UUID recipientUserId);

  Optional<Notification> findByDeduplicationKey(String deduplicationKey);

  boolean existsByDeduplicationKey(String deduplicationKey);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update Notification n
      set n.readAt = :now,
          n.sentAt = coalesce(n.sentAt, :now),
          n.status = com.smartdroneinspection.notifications.domain.enums.NotificationStatus.READ
      where n.recipientUserId = :recipientUserId
        and n.readAt is null
      """)
  int markAllReadByRecipientUserId(
      @Param("recipientUserId") UUID recipientUserId, @Param("now") Instant now);
}
