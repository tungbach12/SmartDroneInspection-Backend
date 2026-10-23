package com.smartdroneinspection.subscriptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.subscriptions.api.dto.ConfirmPaymentRequest;
import com.smartdroneinspection.subscriptions.api.dto.CreatePayPalOrderRequest;
import com.smartdroneinspection.subscriptions.api.dto.CreateSubscriptionRequest;
import com.smartdroneinspection.subscriptions.domain.Subscription;
import com.smartdroneinspection.subscriptions.repository.PaymentTransactionRepository;
import com.smartdroneinspection.subscriptions.repository.SubscriptionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SubscriptionApiIntegrationTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired SubscriptionRepository subscriptionRepository;
  @Autowired PaymentTransactionRepository paymentTransactionRepository;
  @Autowired SubscriptionEntitlement entitlement;
  @Autowired ObjectMapper objectMapper;

  private MockMvc mockMvc;
  private UUID orgId;
  private UUID orgAdminUserId;
  private UUID platformAdminUserId;
  private UUID inspectorUserId;

  @BeforeEach
  void setUp() {
    mockMvc =
        webAppContextSetup(webApplicationContext)
            .apply(
                org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                    .springSecurity())
            .build();

    orgId = persistOrganization("Alpha Drone Services", "ALPHA");
    orgAdminUserId = persistUser("org.admin@alpha.test", "Alice Admin", Roles.ORG_ADMIN, orgId);
    inspectorUserId = persistUser("inspector@alpha.test", "Bob Inspector", Roles.INSPECTOR, orgId);
    platformAdminUserId =
        persistUser("platform.admin@platform.test", "Charlie Platform", Roles.ADMIN, null);
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor jwtUser(
      UUID userId, String role) {
    return jwt()
        .jwt(token -> token.subject(userId.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_" + role));
  }

  @Test
  void orgAdminCanViewTermsAndCurrentStatus() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/subscriptions/current").with(jwtUser(orgAdminUserId, Roles.ORG_ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.termsPreview.version").value("2026.1"))
        .andExpect(jsonPath("$.data.termsPreview.pricingTiers.length()").value(3))
        .andExpect(jsonPath("$.data.paymentInstructions.bankName").isNotEmpty())
        .andExpect(
            jsonPath("$.data.paymentInstructions.referenceCode")
                .value(org.hamcrest.Matchers.startsWith("SDI-SUB-ALPHA-")));
  }

  @Test
  void orgAdminCanCreatePendingSubscriptionWithAgreedTerms() throws Exception {
    CreateSubscriptionRequest request = new CreateSubscriptionRequest(12, true, "2026.1");

    mockMvc
        .perform(
            post("/api/v1/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwtUser(orgAdminUserId, Roles.ORG_ADMIN)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.status").value("PENDING"))
        .andExpect(jsonPath("$.data.planCode").value("ENTERPRISE"))
        .andExpect(jsonPath("$.data.billingPeriodMonths").value(12))
        .andExpect(jsonPath("$.data.termsSnapshot.termsVersion").value("2026.1"))
        .andExpect(
            jsonPath("$.data.termsSnapshot.agreedByUserEmail").value("org.admin@alpha.test"));

    List<Subscription> subscriptions =
        subscriptionRepository.findAllByOrganizationIdOrderByCreatedAtDesc(orgId);
    assertThat(subscriptions).hasSize(1);
    assertThat(subscriptions.get(0).getStatus()).isEqualTo(Subscription.Status.PENDING);
    assertThat(subscriptions.get(0).getBillingPeriodMonths()).isEqualTo(12);
  }

  @Test
  void orgAdminCanRequestCustomMonthsAndDealPrice() throws Exception {
    CreateSubscriptionRequest request =
        new CreateSubscriptionRequest(
            9, true, "2026.1", BigDecimal.valueOf(8000), "Special enterprise pilot discount");

    mockMvc
        .perform(
            post("/api/v1/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwtUser(orgAdminUserId, Roles.ORG_ADMIN)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status").value("PENDING"))
        .andExpect(jsonPath("$.data.billingPeriodMonths").value(9))
        .andExpect(jsonPath("$.data.agreedAmountUsd").doesNotExist())
        .andExpect(jsonPath("$.data.termsSnapshot.proposedPriceUsd").value(8000))
        .andExpect(
            jsonPath("$.data.termsSnapshot.negotiationNotes")
                .value("Special enterprise pilot discount"));
  }

  @Test
  void orgAdminCannotCreateSubscriptionWithInvalidPeriodOrWithoutAgreement() throws Exception {
    CreateSubscriptionRequest invalidPeriod = new CreateSubscriptionRequest(0, true, "2026.1");

    mockMvc
        .perform(
            post("/api/v1/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidPeriod))
                .with(jwtUser(orgAdminUserId, Roles.ORG_ADMIN)))
        .andExpect(status().isBadRequest());

    CreateSubscriptionRequest noAgreement = new CreateSubscriptionRequest(6, false, "2026.1");

    mockMvc
        .perform(
            post("/api/v1/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(noAgreement))
                .with(jwtUser(orgAdminUserId, Roles.ORG_ADMIN)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void platformAdminCanListFilterAndConfirmPaymentToActivateSubscription() throws Exception {
    Subscription pending =
        Subscription.requestEnterprise(orgId, 6, "{\"termsVersion\":\"2026.1\"}");
    Subscription savedPending = subscriptionRepository.save(pending);

    // List all subscriptions as Platform Admin
    mockMvc
        .perform(
            get("/api/v1/platform/subscriptions").with(jwtUser(platformAdminUserId, Roles.ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].organizationName").value("Alpha Drone Services"));

    // Confirm payment and activate with negotiated deal amount
    ConfirmPaymentRequest confirmRequest =
        new ConfirmPaymentRequest(
            "WIRE-2026-CHASE-0089", null, null, BigDecimal.valueOf(5500), "Approved deal discount");

    mockMvc
        .perform(
            post("/api/v1/platform/subscriptions/" + savedPending.getId() + "/confirm-payment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(confirmRequest))
                .with(jwtUser(platformAdminUserId, Roles.ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.agreedAmountUsd").value(5500))
        .andExpect(jsonPath("$.data.externalReference").value("WIRE-2026-CHASE-0089"))
        .andExpect(jsonPath("$.data.confirmedByUserId").value(platformAdminUserId.toString()));

    // Verify entitlement is now ACTIVE
    entitlement.requireActive(orgId, Instant.now());
  }

  @Test
  void activatingNewSubscriptionEnforcesSingleActiveConstraintSeamlessly() throws Exception {
    // S1 is currently ACTIVE
    Subscription s1 = Subscription.requestEnterprise(orgId, 1, "{\"termsVersion\":\"2026.1\"}");
    s1.confirmPayment(
        "WIRE-OLD-001",
        Instant.now().minusSeconds(86400),
        Instant.now().plusSeconds(86400 * 30),
        platformAdminUserId);
    subscriptionRepository.saveAndFlush(s1);

    // S2 is a renewal request PENDING
    Subscription s2 = Subscription.requestEnterprise(orgId, 12, "{\"termsVersion\":\"2026.1\"}");
    Subscription savedS2 = subscriptionRepository.saveAndFlush(s2);

    // Platform Admin confirms payment for S2
    ConfirmPaymentRequest confirmRequest = new ConfirmPaymentRequest("WIRE-NEW-002", null, null);

    mockMvc
        .perform(
            post("/api/v1/platform/subscriptions/" + savedS2.getId() + "/confirm-payment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(confirmRequest))
                .with(jwtUser(platformAdminUserId, Roles.ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ACTIVE"));

    Subscription refreshedS1 = subscriptionRepository.findById(s1.getId()).orElseThrow();
    Subscription refreshedS2 = subscriptionRepository.findById(savedS2.getId()).orElseThrow();

    assertThat(refreshedS1.getStatus()).isEqualTo(Subscription.Status.EXPIRED);
    assertThat(refreshedS2.getStatus()).isEqualTo(Subscription.Status.ACTIVE);

    // Entitlement is active
    entitlement.requireActive(orgId, Instant.now());
  }

  @Test
  void captureRequestCannotClaimClientSuppliedCaptureDataOrActivateSubscription() throws Exception {
    Subscription pending =
        Subscription.requestEnterprise(
            orgId, 6, BigDecimal.valueOf(5500), "{\"termsVersion\":\"2026.1\"}");
    Subscription savedPending = subscriptionRepository.saveAndFlush(pending);

    // A request that tries to supply a capture id and amount has no server contract: validation
    // and the provider response, not caller-supplied data, determine whether payment completed.
    String forgedCapture =
        "{\"subscriptionId\":\"%s\",\"paypalOrderId\":\"FORGED-ORDER\","
            + "\"paypalCaptureId\":\"FORGED-CAPTURE\",\"amount\":0.01,\"currency\":\"USD\"}"
                .formatted(savedPending.getId());

    mockMvc
        .perform(
            post("/api/v1/subscriptions/paypal/capture-order")
                .contentType(MediaType.APPLICATION_JSON)
                .content(forgedCapture)
                .with(jwtUser(orgAdminUserId, Roles.ORG_ADMIN)))
        .andExpect(status().is4xxClientError());

    Subscription unchanged = subscriptionRepository.findById(savedPending.getId()).orElseThrow();
    assertThat(unchanged.getStatus()).isEqualTo(Subscription.Status.PENDING);
    assertThat(
            paymentTransactionRepository.findAllBySubscriptionIdOrderByCapturedAtDesc(
                savedPending.getId()))
        .isEmpty();
  }

  @Test
  void paymentEndpointsAreUnavailableWhenPayPalIsNotConfigured() throws Exception {
    Subscription pending =
        Subscription.requestEnterprise(
            orgId, 6, BigDecimal.valueOf(5500), "{\"termsVersion\":\"2026.1\"}");
    Subscription savedPending = subscriptionRepository.saveAndFlush(pending);

    mockMvc
        .perform(
            post("/api/v1/subscriptions/paypal/create-order")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CreatePayPalOrderRequest(savedPending.getId())))
                .with(jwtUser(orgAdminUserId, Roles.ORG_ADMIN)))
        .andExpect(status().isServiceUnavailable());

    assertThat(subscriptionRepository.findById(savedPending.getId()).orElseThrow().getStatus())
        .isEqualTo(Subscription.Status.PENDING);
  }

  @Test
  void roleEnforcementRestrictsEndpoints() throws Exception {
    // Inspector cannot access subscriptions endpoints
    mockMvc
        .perform(
            get("/api/v1/subscriptions/current").with(jwtUser(inspectorUserId, Roles.INSPECTOR)))
        .andExpect(status().isForbidden());

    // ORG_ADMIN cannot access platform admin endpoints
    mockMvc
        .perform(
            get("/api/v1/platform/subscriptions").with(jwtUser(orgAdminUserId, Roles.ORG_ADMIN)))
        .andExpect(status().isForbidden());
  }

  private UUID persistOrganization(String name, String code) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO organizations (id, legal_name, display_name, registration_code, timezone, status)
        VALUES (?, ?, ?, ?, 'Asia/Ho_Chi_Minh', 'ACTIVE')
        """,
        id,
        name,
        name,
        code + "-" + id.toString().substring(0, 8));
    return id;
  }

  private UUID persistUser(String email, String fullName, String role, UUID organizationId) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO users (id, email, normalized_email, full_name, password_hash, status, actor_zone, organization_id, auth_version, failed_login_count, must_change_password)
        VALUES (?, ?, ?, ?, '$argon2id$v=19$m=65536,t=3,p=1$fakeHashForTests$hash', 'ACTIVE', ?, ?, 1, 0, false)
        """,
        id,
        email,
        email.toLowerCase().trim(),
        fullName,
        organizationId == null ? "PLATFORM" : "CUSTOMER_ORGANIZATION",
        organizationId);

    jdbcTemplate.update(
        """
        INSERT INTO user_roles (user_id, role)
        VALUES (?, ?)
        """,
        id,
        role);

    return id;
  }
}
