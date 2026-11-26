package com.smartdroneinspection.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.events.DroneApplicationSubmittedEvent;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.contracts.events.ServiceQuoteSavedEvent;
import com.smartdroneinspection.contracts.events.ServiceRequestManagerChangedEvent;
import com.smartdroneinspection.inspections.events.InspectionAcceptedEvent;
import com.smartdroneinspection.inspections.events.VideoProcessingFinishedEvent;
import com.smartdroneinspection.notifications.domain.Notification;
import com.smartdroneinspection.notifications.repository.NotificationRepository;
import com.smartdroneinspection.notifications.service.DroneRegistrationExpiryCheckService;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.events.UserSecurityAlertEvent;
import com.smartdroneinspection.users.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class NotificationWorkflowIntegrationTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired UserRepository users;
  @Autowired DroneRepository drones;
  @Autowired NotificationRepository notifications;
  @Autowired DroneRegistrationExpiryCheckService droneRegistrationExpiryCheckService;
  @Autowired ApplicationEventPublisher eventPublisher;
  @Autowired TransactionTemplate transactionTemplate;
  @Autowired JdbcTemplate jdbcTemplate;

  private MockMvc mockMvc;

  private UUID adminId;
  private UUID managerId;
  private UUID customerId;
  private UUID inspectorId;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();

    jdbcTemplate.execute("DELETE FROM notifications");
    jdbcTemplate.execute("DELETE FROM event_publication");

    adminId = createUser(UserRole.ADMIN, "notif-admin-" + UUID.randomUUID() + "@test.local");
    managerId = createUser(UserRole.MANAGER, "notif-manager-" + UUID.randomUUID() + "@test.local");
    customerId =
        createUser(UserRole.CUSTOMER, "notif-customer-" + UUID.randomUUID() + "@test.local");
    inspectorId =
        createUser(UserRole.INSPECTOR, "notif-inspector-" + UUID.randomUUID() + "@test.local");
  }

  @Test
  void
      publishesAllFiveWorkflowNotificationGroupsDeduplicatesAndMarksAllReadRespectingCheckConstraint()
          throws Exception {
    UUID requestId = UUID.randomUUID();
    UUID inspectionId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID evidenceId = UUID.randomUUID();
    UUID registrationId = UUID.randomUUID();
    Instant now = Instant.now();

    drones.saveAndFlush(
        Drone.createInspectorOwned(
            inspectorId,
            "SN-EXPIRY-IT-" + UUID.randomUUID().toString().substring(0, 8),
            "Matrice 350 RTK",
            "DJI",
            "REG-EXP-350",
            "drones/reg.pdf",
            now.plus(5, ChronoUnit.DAYS)));

    transactionTemplate.executeWithoutResult(
        status -> {
          eventPublisher.publishEvent(
              new ServiceRequestManagerChangedEvent(
                  requestId, customerId, null, managerId, "ASSIGNED", "Bridge", now));
          eventPublisher.publishEvent(
              new ServiceQuoteSavedEvent(
                  requestId, customerId, "Bridge", false, new BigDecimal("12000000"), "VND", now));
          eventPublisher.publishEvent(
              new InspectionAcceptedEvent(inspectionId, customerId, inspectorId, now, now));
          eventPublisher.publishEvent(
              new VideoProcessingFinishedEvent(
                  evidenceId, inspectionId, inspectorId, "flight.mp4", true, null, now));
          eventPublisher.publishEvent(
              new DroneApplicationSubmittedEvent(
                  applicationId, null, inspectorId, "Matrice 350 RTK", "SN-01", false, now));
          eventPublisher.publishEvent(
              new UserSecurityAlertEvent(
                  customerId, "customer@test.local", "Org Owner", "PASSWORD_CHANGED", null, now));
        });

    // Replay one event with the same ID to verify uq_notifications_deduplication_key protection.
    transactionTemplate.executeWithoutResult(
        status ->
            eventPublisher.publishEvent(
                new ServiceQuoteSavedEvent(
                    requestId,
                    customerId,
                    "Bridge",
                    false,
                    new BigDecimal("12000000"),
                    "VND",
                    now)));

    droneRegistrationExpiryCheckService.checkAllPartnerDrones();
    // Second expiry run must deduplicate cleanly.
    droneRegistrationExpiryCheckService.checkAllPartnerDrones();

    awaitUntil(
        Duration.ofSeconds(10),
        () ->
            notifications.countByRecipientUserIdAndReadAtIsNull(customerId) == 3
                && notifications.countByRecipientUserIdAndReadAtIsNull(managerId) == 3
                && notifications.countByRecipientUserIdAndReadAtIsNull(inspectorId) == 2
                && notifications.countByRecipientUserIdAndReadAtIsNull(adminId) == 1);

    List<Notification> customerNotifications =
        notifications
            .findByRecipientUserIdOrderByCreatedAtDesc(
                customerId, org.springframework.data.domain.PageRequest.of(0, 20))
            .getContent();
    assertThat(customerNotifications)
        .extracting(Notification::getEventType)
        .containsExactlyInAnyOrder(
            "QUOTE_ISSUED", "INSPECTION_ACCEPTED", "SECURITY_PASSWORD_CHANGED");
    assertThat(customerNotifications).allSatisfy(n -> assertThat(n.getTitle()).contains(" | "));

    mockMvc
        .perform(get("/api/v1/notifications/unread-count").with(principal(customerId, "CUSTOMER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.unreadCount").value(3));

    mockMvc
        .perform(patch("/api/v1/notifications/read-all").with(principal(customerId, "CUSTOMER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.updatedCount").value(3));

    mockMvc
        .perform(get("/api/v1/notifications/unread-count").with(principal(customerId, "CUSTOMER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.unreadCount").value(0));

    // Other recipients' unread notifications remain untouched.
    mockMvc
        .perform(
            get("/api/v1/notifications/unread-count").with(principal(inspectorId, "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.unreadCount").value(2));
  }

  private UUID createUser(UserRole role, String email) {
    User user = new User(email, role.name() + " User", null, UserStatus.ACTIVE);
    user.addRole(role);
    return users.saveAndFlush(user).getId();
  }

  private static RequestPostProcessor principal(UUID id, String role) {
    return jwt()
        .jwt(token -> token.subject(id.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_" + role));
  }

  private static void awaitUntil(Duration timeout, BooleanSupplier condition)
      throws InterruptedException {
    Instant deadline = Instant.now().plus(timeout);
    while (Instant.now().isBefore(deadline)) {
      if (condition.getAsBoolean()) {
        return;
      }
      Thread.sleep(100);
    }
    assertThat(condition.getAsBoolean()).isTrue();
  }
}
