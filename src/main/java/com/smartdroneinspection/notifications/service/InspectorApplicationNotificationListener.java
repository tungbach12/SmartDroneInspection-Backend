package com.smartdroneinspection.notifications.service;

import com.smartdroneinspection.notifications.domain.Notification;
import com.smartdroneinspection.notifications.domain.enums.NotificationChannel;
import com.smartdroneinspection.notifications.repository.NotificationRepository;
import com.smartdroneinspection.users.UserAccess;
import com.smartdroneinspection.users.events.InspectorApplicationSubmittedEvent;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Stores Manager inbox notifications for newly submitted Inspector applications. */
@Component
public class InspectorApplicationNotificationListener {

  private final UserAccess users;
  private final NotificationRepository notifications;

  public InspectorApplicationNotificationListener(
      UserAccess users, NotificationRepository notifications) {
    this.users = users;
    this.notifications = notifications;
  }

  @ApplicationModuleListener
  public void onApplicationSubmitted(InspectorApplicationSubmittedEvent event) {
    if (event == null || event.applicationId() == null) {
      return;
    }
    List<UserAccess.ActiveUser> managers = users.listActivePlatformManagers();
    for (UserAccess.ActiveUser manager : managers) {
      String key = "INSPECTOR_APPLICATION:" + event.applicationId() + ":" + manager.id();
      if (notifications.existsByDeduplicationKey(key)) {
        continue;
      }
      try {
        notifications.saveAndFlush(
            new Notification(
                manager.id(),
                "INSPECTOR_APPLICATION_SUBMITTED",
                NotificationChannel.IN_APP,
                "Inspector application to review",
                "A new Inspector application is waiting for review.",
                "/manager/onboarding-reviews",
                key));
      } catch (DataIntegrityViolationException duplicate) {
        // Another event delivery already wrote the deduplicated notification.
      }
    }
  }
}
