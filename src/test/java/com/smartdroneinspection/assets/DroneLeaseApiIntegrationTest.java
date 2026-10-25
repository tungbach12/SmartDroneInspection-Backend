package com.smartdroneinspection.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.domain.DroneLease;
import com.smartdroneinspection.assets.domain.PlatformDrone;
import com.smartdroneinspection.assets.domain.enums.DroneLeaseStatus;
import com.smartdroneinspection.assets.domain.enums.PlatformDroneStatus;
import com.smartdroneinspection.assets.repository.DroneLeaseRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.assets.repository.PlatformDroneRepository;
import com.smartdroneinspection.shared.auth.Roles;
import java.math.BigDecimal;
import java.util.Map;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class DroneLeaseApiIntegrationTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired PlatformDroneRepository platformDroneRepository;
  @Autowired DroneLeaseRepository droneLeaseRepository;
  @Autowired DroneRepository droneRepository;
  @Autowired ObjectMapper objectMapper;

  private MockMvc mockMvc;
  private UUID orgAId;
  private UUID orgBId;
  private UUID orgManagerAId;
  private UUID orgManagerBId;
  private UUID platformManagerId;
  private UUID platformAdminId;

  @BeforeEach
  void setUp() {
    mockMvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();

    orgAId = persistOrganization("Apex Infrastructure", "APEX");
    orgBId = persistOrganization("Beacon Grid", "BEAC");
    grantEntitlement(orgAId);
    grantEntitlement(orgBId);

    orgManagerAId =
        persistUser("manager.a@apex.test", "Alice OrgManager", Roles.ORG_MANAGER, orgAId);
    orgManagerBId =
        persistUser("manager.b@beacon.test", "Bob OrgManager", Roles.ORG_MANAGER, orgBId);
    platformManagerId =
        persistUser(
            "platform.manager@sdi.test", "Diana PlatformManager", Roles.PLATFORM_MANAGER, null);
    platformAdminId =
        persistUser("platform.admin@sdi.test", "Charlie PlatformAdmin", Roles.ADMIN, null);
  }

  @Test
  void platformManagerManagesPoolWhileAdminHasReadOnlyVisibility() throws Exception {
    String serial = "SDI-POOL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    String createPayload =
        objectMapper.writeValueAsString(
            Map.of(
                "serialNumber", serial,
                "model", "DJI Matrice 350 RTK",
                "manufacturer", "DJI",
                "monthlyRateUsd", 1250.00,
                "notes", "Equipped with Zenmuse H20T"));

    // ADMIN is forbidden from creating a platform drone in the pool
    mockMvc
        .perform(
            post("/api/v1/platform/drone-leases/pool")
                .with(jwtUser(platformAdminId, Roles.ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload))
        .andExpect(status().isForbidden());

    // PLATFORM_MANAGER succeeds in creating a platform drone in the pool
    String createdBody =
        mockMvc
            .perform(
                post("/api/v1/platform/drone-leases/pool")
                    .with(jwtUser(platformManagerId, Roles.PLATFORM_MANAGER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createPayload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.serialNumber").value(serial))
            .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
            .andExpect(jsonPath("$.data.monthlyRateUsd").value(1250.0))
            .andReturn()
            .getResponse()
            .getContentAsString();

    UUID platformDroneId =
        UUID.fromString(objectMapper.readTree(createdBody).path("data").path("id").asString());

    // ADMIN can read the pool and lease lists (200 OK)
    mockMvc
        .perform(
            get("/api/v1/platform/drone-leases/pool").with(jwtUser(platformAdminId, Roles.ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[?(@.id == '%s')]".formatted(platformDroneId)).exists());

    mockMvc
        .perform(get("/api/v1/platform/drone-leases").with(jwtUser(platformAdminId, Roles.ADMIN)))
        .andExpect(status().isOk());

    // ADMIN is forbidden from mutating pool status
    mockMvc
        .perform(
            patch("/api/v1/platform/drone-leases/pool/{id}/status", platformDroneId)
                .with(jwtUser(platformAdminId, Roles.ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"MAINTENANCE\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void orgManagerRequestsLeaseAndPlatformManagerConfirmsWirePaymentProvisioningCustomerDrone()
      throws Exception {
    UUID platformDroneId = createPoolDrone("SDI-LEASE-M350-01", BigDecimal.valueOf(1500));

    // ORG_MANAGER lists available drones
    mockMvc
        .perform(
            get("/api/v1/drone-leases/available-drones")
                .with(jwtUser(orgManagerAId, Roles.ORG_MANAGER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[?(@.id == '%s')]".formatted(platformDroneId)).exists());

    // ORG_MANAGER requests a 6-month lease
    String leaseRes =
        mockMvc
            .perform(
                post("/api/v1/drone-leases")
                    .with(jwtUser(orgManagerAId, Roles.ORG_MANAGER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "platformDroneId",
                                platformDroneId.toString(),
                                "leasePeriodMonths",
                                6,
                                "notes",
                                "For Q3-Q4 transmission line inspections"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andExpect(jsonPath("$.data.agreedAmountUsd").value(9000.0))
            .andExpect(
                jsonPath("$.data.wireReferenceCode")
                    .value(org.hamcrest.Matchers.startsWith("SDI-LEASE-")))
            .andReturn()
            .getResponse()
            .getContentAsString();

    UUID leaseId =
        UUID.fromString(objectMapper.readTree(leaseRes).path("data").path("id").asString());

    // ADMIN cannot confirm lease payment (403 Forbidden)
    mockMvc
        .perform(
            post("/api/v1/platform/drone-leases/{leaseId}/confirm-payment", leaseId)
                .with(jwtUser(platformAdminId, Roles.ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"externalReference\":\"WIRE-APEX-001\"}"))
        .andExpect(status().isForbidden());

    // PLATFORM_MANAGER confirms wire payment -> lease ACTIVE, pool drone LEASED, customer drone
    // provisioned
    mockMvc
        .perform(
            post("/api/v1/platform/drone-leases/{leaseId}/confirm-payment", leaseId)
                .with(jwtUser(platformManagerId, Roles.PLATFORM_MANAGER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "externalReference", "WIRE-APEX-2026-9000",
                            "notes", "Settled via corporate wire"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.customerDroneId").isNotEmpty())
        .andExpect(jsonPath("$.data.startsAt").isNotEmpty())
        .andExpect(jsonPath("$.data.endsAt").isNotEmpty());

    PlatformDrone refreshedPoolDrone =
        platformDroneRepository.findById(platformDroneId).orElseThrow();
    assertThat(refreshedPoolDrone.getStatus()).isEqualTo(PlatformDroneStatus.LEASED);

    // Customer's GET /api/v1/drones contains the provisioned LEASED drone
    mockMvc
        .perform(get("/api/v1/drones").with(jwtUser(orgManagerAId, Roles.ORG_MANAGER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].serialNumber").value("SDI-LEASE-M350-01"))
        .andExpect(jsonPath("$.data[0].ownershipType").value("LEASED"))
        .andExpect(jsonPath("$.data[0].serviceability").value("ACTIVE"))
        .andExpect(jsonPath("$.data[0].leaseId").value(leaseId.toString()))
        .andExpect(jsonPath("$.data[0].leaseExpiresAt").isNotEmpty());
  }

  @Test
  void concurrentLeaseApprovalForAlreadyLeasedDroneReturns409Conflict() throws Exception {
    UUID platformDroneId = createPoolDrone("SDI-CONTENDED-01", BigDecimal.valueOf(1000));

    UUID leaseAId = requestPendingLease(orgManagerAId, platformDroneId, 3);
    UUID leaseBId = requestPendingLease(orgManagerBId, platformDroneId, 6);

    // Confirm Lease A first -> succeeds
    mockMvc
        .perform(
            post("/api/v1/platform/drone-leases/{leaseId}/confirm-payment", leaseAId)
                .with(jwtUser(platformManagerId, Roles.PLATFORM_MANAGER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"externalReference\":\"WIRE-A-001\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ACTIVE"));

    // Confirming Lease B for the same drone returns 409 Conflict
    mockMvc
        .perform(
            post("/api/v1/platform/drone-leases/{leaseId}/confirm-payment", leaseBId)
                .with(jwtUser(platformManagerId, Roles.PLATFORM_MANAGER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"externalReference\":\"WIRE-B-001\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DRONE_NOT_AVAILABLE"));

    DroneLease refreshedB = droneLeaseRepository.findById(leaseBId).orElseThrow();
    assertThat(refreshedB.getStatus()).isEqualTo(DroneLeaseStatus.CANCELLED);
  }

  @Test
  void
      reclaimTransitionsCustomerDroneToRetiredAndReLeasingReactivatesSameRowWithoutDuplicateSerial()
          throws Exception {
    String serial = "SDI-RECLAIM-CYCLE-01";
    UUID platformDroneId = createPoolDrone(serial, BigDecimal.valueOf(1200));

    // 1. First lease by Organization A
    UUID firstLeaseId = requestPendingLease(orgManagerAId, platformDroneId, 3);
    String confirmBody1 =
        mockMvc
            .perform(
                post("/api/v1/platform/drone-leases/{leaseId}/confirm-payment", firstLeaseId)
                    .with(jwtUser(platformManagerId, Roles.PLATFORM_MANAGER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"externalReference\":\"WIRE-CYCLE-1\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID customerDroneId1 =
        UUID.fromString(
            objectMapper.readTree(confirmBody1).path("data").path("customerDroneId").asString());

    // 2. ADMIN cannot reclaim (403 Forbidden)
    mockMvc
        .perform(
            post("/api/v1/platform/drone-leases/{leaseId}/reclaim", firstLeaseId)
                .with(jwtUser(platformAdminId, Roles.ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notes\":\"Attempt by Admin\"}"))
        .andExpect(status().isForbidden());

    // 3. PLATFORM_MANAGER reclaims the active lease
    mockMvc
        .perform(
            post("/api/v1/platform/drone-leases/{leaseId}/reclaim", firstLeaseId)
                .with(jwtUser(platformManagerId, Roles.PLATFORM_MANAGER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notes\":\"Lease term completed and airframe returned\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("RECLAIMED"))
        .andExpect(jsonPath("$.data.reclaimedAt").isNotEmpty());

    // Platform drone is AVAILABLE again
    assertThat(platformDroneRepository.findById(platformDroneId).orElseThrow().getStatus())
        .isEqualTo(PlatformDroneStatus.AVAILABLE);

    // Customer drone is RETIRED, and ORG_MANAGER cannot reactivate it manually
    mockMvc
        .perform(
            patch("/api/v1/drones/{droneId}/serviceability", customerDroneId1)
                .with(jwtUser(orgManagerAId, Roles.ORG_MANAGER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"serviceability\":\"ACTIVE\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_STATE"));

    // 4. Re-leasing the same PlatformDrone to Organization A reactivates the existing customer
    // Drone row
    UUID secondLeaseId = requestPendingLease(orgManagerAId, platformDroneId, 12);
    String confirmBody2 =
        mockMvc
            .perform(
                post("/api/v1/platform/drone-leases/{leaseId}/confirm-payment", secondLeaseId)
                    .with(jwtUser(platformManagerId, Roles.PLATFORM_MANAGER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"externalReference\":\"WIRE-CYCLE-2\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID customerDroneId2 =
        UUID.fromString(
            objectMapper.readTree(confirmBody2).path("data").path("customerDroneId").asString());

    assertThat(customerDroneId2).isEqualTo(customerDroneId1);
    assertThat(droneRepository.findAllByOrganizationId(orgAId)).hasSize(1);
    var reactivated = droneRepository.findById(customerDroneId1).orElseThrow();
    assertThat(reactivated.getServiceability().name()).isEqualTo("ACTIVE");
    assertThat(reactivated.getOwnershipType().name()).isEqualTo("LEASED");
    assertThat(reactivated.getLeaseId()).isEqualTo(secondLeaseId);
  }

  private UUID createPoolDrone(String serial, BigDecimal monthlyRateUsd) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/platform/drone-leases/pool")
                    .with(jwtUser(platformManagerId, Roles.PLATFORM_MANAGER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "serialNumber",
                                serial,
                                "model",
                                "DJI Matrice 350 RTK",
                                "manufacturer",
                                "DJI",
                                "monthlyRateUsd",
                                monthlyRateUsd))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode node = objectMapper.readTree(body);
    return UUID.fromString(node.path("data").path("id").asString());
  }

  private UUID requestPendingLease(UUID orgManagerId, UUID platformDroneId, int months)
      throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/drone-leases")
                    .with(jwtUser(orgManagerId, Roles.ORG_MANAGER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "platformDroneId",
                                platformDroneId.toString(),
                                "leasePeriodMonths",
                                months))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(objectMapper.readTree(body).path("data").path("id").asString());
  }

  private static RequestPostProcessor jwtUser(UUID userId, String role) {
    return jwt()
        .jwt(token -> token.subject(userId.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_" + role));
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

  private void grantEntitlement(UUID organizationId) {
    jdbcTemplate.update(
        """
        INSERT INTO subscriptions (organization_id, plan_code, billing_period_months, status, starts_at)
        VALUES (?, 'ENTERPRISE', 12, 'ACTIVE', CURRENT_TIMESTAMP - INTERVAL '1 hour')
        """,
        organizationId);
  }

  private UUID persistUser(String email, String fullName, String role, UUID organizationId) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO users (id, email, normalized_email, full_name, password_hash, status, actor_zone, organization_id, auth_version, failed_login_count, must_change_password, roles)
        VALUES (?, ?, ?, ?, '$argon2id$v=19$m=65536,t=3,p=1$fakeHashForTests$hash', 'ACTIVE', ?, ?, 1, 0, false, ?)
        """,
        id,
        email,
        email.toLowerCase().trim(),
        fullName,
        organizationId == null ? "PLATFORM" : "CUSTOMER_ORGANIZATION",
        organizationId,
        role);
    return id;
  }
}
