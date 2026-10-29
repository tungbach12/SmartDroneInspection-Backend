package com.smartdroneinspection.subscriptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.subscriptions.domain.Subscription;
import com.smartdroneinspection.subscriptions.repository.SubscriptionRepository;
import com.smartdroneinspection.users.domain.Organization;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.OrganizationRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SubscriptionActivationApiIntegrationTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired OrganizationRepository organizations;
  @Autowired UserRepository users;
  @Autowired SubscriptionRepository subscriptions;
  @Autowired SubscriptionEntitlement entitlement;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired ObjectMapper objectMapper;

  private MockMvc mockMvc;
  private UUID organizationId;
  private UUID adminId;
  private UUID orgAdminId;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply(
                org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                    .springSecurity())
            .build();

    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    Organization organization =
        organizations.saveAndFlush(new Organization("Subscription E2E", "SUB" + suffix, null));
    organizationId = organization.getId();

    User admin =
        new User(
            "platform-admin-" + suffix + "@example.test",
            "Platform Administrator",
            "{argon2}encoded-password",
            UserStatus.ACTIVE,
            ActorZone.PLATFORM,
            null);
    admin.addRole(UserRole.PLATFORM_MANAGER);
    adminId = users.saveAndFlush(admin).getId();

    User orgAdmin =
        new User(
            "org-admin-" + suffix + "@example.test",
            "Organization Administrator",
            "{argon2}encoded-password",
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            organizationId);
    orgAdmin.addRole(UserRole.ORG_MANAGER);
    orgAdminId = users.saveAndFlush(orgAdmin).getId();
  }

  @Test
  void platformAdminActivatesEnterpriseSubscriptionAndAuditAttribution() throws Exception {
    Instant startsAt = Instant.now().minusSeconds(60);
    Instant endsAt = startsAt.plusSeconds(86_400);
    String request =
        """
        {
          "planCode": "ENTERPRISE",
          "billingPeriodMonths": 1,
          "startsAt": "%s",
          "endsAt": "%s",
          "externalReference": "e2e-run",
          "reason": "Disposable local E2E entitlement"
        }
        """
            .formatted(startsAt, endsAt);

    String response =
        mockMvc
            .perform(
                post(
                        "/api/v1/platform/organizations/{organizationId}/subscriptions",
                        organizationId)
                    .with(principal(adminId, "MANAGER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.organizationId").value(organizationId.toString()))
            .andExpect(jsonPath("$.data.planCode").value("ENTERPRISE"))
            .andExpect(jsonPath("$.data.billingPeriodMonths").value(1))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    UUID subscriptionId =
        UUID.fromString(objectMapper.readTree(response).path("data").path("id").asString());
    assertThatCode(() -> entitlement.requireActive(organizationId, Instant.now()))
        .doesNotThrowAnyException();
    UUID activatedBy =
        jdbcTemplate.queryForObject(
            "SELECT confirmed_by_user_id FROM subscriptions WHERE id = ?",
            UUID.class,
            subscriptionId);
    assert activatedBy.equals(adminId);
    Integer auditRows =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM audit_events WHERE aggregate_id = ? AND actor_user_id = ? AND action = 'SUBSCRIPTION_ACTIVATED' AND aggregate_type = 'SUBSCRIPTION'",
            Integer.class,
            subscriptionId,
            adminId);
    assert auditRows == 1;
    String auditReason =
        jdbcTemplate.queryForObject(
            "SELECT reason FROM audit_events WHERE aggregate_id = ? AND action = 'SUBSCRIPTION_ACTIVATED'",
            String.class,
            subscriptionId);
    assertThat(auditReason).isEqualTo("Disposable local E2E entitlement");
    Boolean auditHasTimestamp =
        jdbcTemplate.queryForObject(
            "SELECT created_at IS NOT NULL FROM audit_events WHERE aggregate_id = ? AND action = 'SUBSCRIPTION_ACTIVATED'",
            Boolean.class,
            subscriptionId);
    assertThat(auditHasTimestamp).isTrue();
  }

  @Test
  void rejectsUnsupportedBillingPeriodWithoutPersistingSubscription() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/platform/organizations/{organizationId}/subscriptions", organizationId)
                .with(principal(adminId, "MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "billingPeriodMonths": 3,
                      "externalReference": "e2e-invalid-period",
                      "reason": "Unsupported term"
                    }
                    """))
        .andExpect(status().isBadRequest());

    assertThat(
            subscriptions.findFirstByOrganizationIdAndStatus(
                organizationId, Subscription.Status.ACTIVE))
        .isEmpty();
  }

  @Test
  void rejectsInvalidSubscriptionWindowWithoutPersistingSubscription() throws Exception {
    Instant startsAt = Instant.now();
    Instant endsAt = startsAt.minusSeconds(1);
    String request =
        """
        {
          "billingPeriodMonths": 1,
          "startsAt": "%s",
          "endsAt": "%s",
          "externalReference": "e2e-invalid-window",
          "reason": "End precedes start"
        }
        """
            .formatted(startsAt, endsAt);

    mockMvc
        .perform(
            post("/api/v1/platform/organizations/{organizationId}/subscriptions", organizationId)
                .with(principal(adminId, "MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isBadRequest());

    assertThat(
            subscriptions.findFirstByOrganizationIdAndStatus(
                organizationId, Subscription.Status.ACTIVE))
        .isEmpty();
  }

  @Test
  void anAlreadyActiveSubscriptionCannotBeDuplicated() throws Exception {
    UUID firstSubscriptionId = activateSubscription("first-activation");
    mockMvc
        .perform(
            post("/api/v1/platform/organizations/{organizationId}/subscriptions", organizationId)
                .with(principal(adminId, "MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(subscriptionRequest("second-activation")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SUBSCRIPTION_ALREADY_ACTIVE"));

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM subscriptions WHERE organization_id = ? AND status = 'ACTIVE'",
                Integer.class,
                organizationId))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT id FROM subscriptions WHERE organization_id = ? AND status = 'ACTIVE'",
                UUID.class,
                organizationId))
        .isEqualTo(firstSubscriptionId);
  }

  @Test
  void reactivatesOrganizationAfterExpiringItsEndedSubscription() throws Exception {
    Instant endedAt = Instant.now().minusSeconds(60);
    Subscription ended =
        subscriptions.saveAndFlush(
            Subscription.activate(
                organizationId,
                1,
                endedAt.minusSeconds(86_400),
                endedAt,
                "previous-term",
                "{}",
                adminId));

    mockMvc
        .perform(
            post("/api/v1/platform/organizations/{organizationId}/subscriptions", organizationId)
                .with(principal(adminId, "MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(subscriptionRequest("renewed-term")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status").value("ACTIVE"));

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM subscriptions WHERE id = ?", String.class, ended.getId()))
        .isEqualTo(Subscription.Status.EXPIRED.name());
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM subscriptions WHERE organization_id = ? AND status = 'ACTIVE'",
                Integer.class,
                organizationId))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_events WHERE aggregate_id = ? AND action = 'SUBSCRIPTION_EXPIRED'",
                Integer.class,
                ended.getId()))
        .isEqualTo(1);
  }

  @Test
  void rejectsUnknownOrganizationWithoutPersistingSubscription() throws Exception {
    UUID unknownOrganizationId = UUID.randomUUID();

    mockMvc
        .perform(
            post(
                    "/api/v1/platform/organizations/{organizationId}/subscriptions",
                    unknownOrganizationId)
                .with(principal(adminId, "MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(subscriptionRequest("unknown-organization")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ORGANIZATION_NOT_FOUND"));

    assertThat(
            subscriptions.findFirstByOrganizationIdAndStatus(
                unknownOrganizationId, Subscription.Status.ACTIVE))
        .isEmpty();
  }

  @Test
  void activatesSubscriptionForRegisteredE2eOrganization() throws Exception {
    String unique = UUID.randomUUID().toString().replace("-", "");
    String email = "e2e-admin-" + unique + "@example.test";
    String code = "E2E-" + unique.substring(0, 16);
    String registrationBody =
        """
        {
          "email": "%s",
          "fullName": "E2E Organization Admin",
          "organizationName": "E2E Organization",
          "organizationCode": "%s",
          "password": "a sufficiently long e2e passphrase"
        }
        """
            .formatted(email, code);
    String registrationResponse =
        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .with(
                        org.springframework.security.test.web.servlet.request
                            .SecurityMockMvcRequestPostProcessors.csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registrationBody))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID registeredOrganizationId =
        UUID.fromString(
            objectMapper
                .readTree(registrationResponse)
                .path("data")
                .path("organizationId")
                .asString());

    mockMvc
        .perform(
            post(
                    "/api/v1/platform/organizations/{organizationId}/subscriptions",
                    registeredOrganizationId)
                .with(principal(adminId, "MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(subscriptionRequest("registered-e2e-organization")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.organizationId").value(registeredOrganizationId.toString()));

    assertThatCode(() -> entitlement.requireActive(registeredOrganizationId, Instant.now()))
        .doesNotThrowAnyException();
  }

  @Test
  void subscriptionActivationUnlocksOrganizationDroneWrites() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/drones")
                .with(principal(orgAdminId, "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"serialNumber\":\"E2E-DRONE-BEFORE\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("SUBSCRIPTION_INACTIVE"));

    activateSubscription("e2e-drone-entitlement");

    mockMvc
        .perform(
            post("/api/v1/drones")
                .with(principal(orgAdminId, "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"serialNumber\":\"E2E-DRONE-AFTER\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.serialNumber").value("E2E-DRONE-AFTER"));
  }

  @Test
  void inactivePlatformAdminCannotActivateSubscription() throws Exception {
    User inactiveAdmin =
        new User(
            "inactive-platform-admin-" + UUID.randomUUID() + "@example.test",
            "Inactive Platform Administrator",
            "{argon2}encoded-password",
            UserStatus.SUSPENDED,
            ActorZone.PLATFORM,
            null);
    inactiveAdmin.addRole(UserRole.PLATFORM_MANAGER);
    UUID inactiveAdminId = users.saveAndFlush(inactiveAdmin).getId();

    mockMvc
        .perform(
            post("/api/v1/platform/organizations/{organizationId}/subscriptions", organizationId)
                .with(principal(inactiveAdminId, "MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(subscriptionRequest("inactive-admin")))
        .andExpect(status().isForbidden());

    assertThat(
            subscriptions.findFirstByOrganizationIdAndStatus(
                organizationId, Subscription.Status.ACTIVE))
        .isEmpty();
  }

  @Test
  void orgAdminCannotSelfActivateOrganizationSubscription() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/platform/organizations/{organizationId}/subscriptions", organizationId)
                .with(principal(orgAdminId, "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(subscriptionRequest("org-admin-must-be-refused")))
        .andExpect(status().isForbidden());

    org.assertj.core.api.Assertions.assertThat(
            subscriptions.findFirstByOrganizationIdAndStatus(
                organizationId, Subscription.Status.ACTIVE))
        .isEmpty();
  }

  private UUID activateSubscription(String externalReference) throws Exception {
    String response =
        mockMvc
            .perform(
                post(
                        "/api/v1/platform/organizations/{organizationId}/subscriptions",
                        organizationId)
                    .with(principal(adminId, "MANAGER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(subscriptionRequest(externalReference)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(objectMapper.readTree(response).path("data").path("id").asString());
  }

  private static String subscriptionRequest(String externalReference) {
    Instant startsAt = Instant.now().minusSeconds(30);
    Instant endsAt = startsAt.plusSeconds(86_400);
    return """
        {
          "billingPeriodMonths": 1,
          "startsAt": "%s",
          "endsAt": "%s",
          "externalReference": "%s",
          "reason": "Disposable local E2E entitlement"
        }
        """
        .formatted(startsAt, endsAt, externalReference);
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor principal(
      UUID userId, String role) {
    return SecurityMockMvcRequestPostProcessors.jwt()
        .jwt(token -> token.subject(userId.toString()))
        .authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role));
  }
}
