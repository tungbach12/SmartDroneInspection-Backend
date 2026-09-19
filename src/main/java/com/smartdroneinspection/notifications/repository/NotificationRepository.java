package com.smartdroneinspection.notifications.repository;

import com.smartdroneinspection.notifications.domain.Notification;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {}
