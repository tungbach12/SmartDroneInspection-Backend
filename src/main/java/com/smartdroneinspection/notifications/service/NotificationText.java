package com.smartdroneinspection.notifications.service;

import com.smartdroneinspection.notifications.domain.Notification;
import com.smartdroneinspection.notifications.repository.NotificationRepository;
import org.slf4j.Logger;
import org.springframework.dao.DataIntegrityViolationException;

/** Formatting and deduplicated persistence helpers shared across notification services. */
final class NotificationText {

  private NotificationText() {}

  static String bilingual(String vi, String en) {
    return sanitizePart(vi) + " | " + sanitizePart(en);
  }

  static String sanitizePart(String text) {
    if (text == null) {
      return "";
    }
    return text.replace(" | ", " - ").trim();
  }

  static String safeOrFallback(String value, String fallback) {
    return value != null && !value.isBlank() ? sanitizePart(value) : fallback;
  }

  static void saveWithDeduplication(
      NotificationRepository repository,
      Notification notification,
      String deduplicationKey,
      Logger log) {
    if (deduplicationKey != null && repository.existsByDeduplicationKey(deduplicationKey)) {
      log.debug("Duplicate notification ignored for key {}", deduplicationKey);
      return;
    }
    try {
      repository.saveAndFlush(notification);
    } catch (DataIntegrityViolationException ex) {
      log.info("Duplicate notification ignored by constraint on key {}", deduplicationKey);
    }
  }
}
