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
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** Organization-scoped drone inventory: CRUD, serviceability lifecycle, and ACTIVE selection. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class DroneApiIntegrationTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired AssetCategoryRepository categories;
  @Autowired AssetRepository assets;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired EntityManager entityManager;

  private AssetTestFixture.Data fixture;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    fixture = new AssetTestFixture(categories, assets, users).create();
  }

  @Test
  void createsListsAndReadsDronesForOwnOrganizationOnly() throws Exception {
    String serial = serial();

    mockMvc
        .perform(
            post("/api/v1/drones")
                .with(orgAdmin(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"serialNumber\":\"%s\",\"model\":\"Mavic 3\",\"manufacturer\":\"DJI\","
                            .formatted(serial)
                        + "\"notes\":\"Primary airframe\",\"registrationNumber\":\"REG-01\","
                        + "\"registrationFileKey\":\"drones/reg-01.pdf\",\"registrationExpiresAt\":\"2030-01-01T00:00:00Z\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.serialNumber").value(serial))
        .andExpect(jsonPath("$.data.serviceability").value("ACTIVE"));

    mockMvc
        .perform(get("/api/v1/drones").with(orgAdmin(fixture.clientId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].serialNumber").value(serial));

    // The other organization sees nothing, and the drone row is tenant-bound to the creator.
    mockMvc
        .perform(get("/api/v1/drones").with(orgAdmin(fixture.otherClientId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0));

    String owner =
        jdbcTemplate.queryForObject(
            "SELECT owner_customer_id FROM drones WHERE serial_number = ?", String.class, serial);
    assertThat(owner).isEqualTo(fixture.clientId().toString());
  }

  @Test
  void rejectsMalformedPayloadMetadataAsAClientErrorWithoutPersistingDrone() throws Exception {
    long before = droneCount();

    mockMvc
        .perform(
            post("/api/v1/drones")
                .with(orgAdmin(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"serialNumber\":\"bad-json-%s\",\"payloadMetadata\":\"not-json\"}"
                        .formatted(serial())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    assertThat(droneCount()).isEqualTo(before);
  }

  @Test
  void acceptsStructuredPayloadMetadata() throws Exception {
    String serial = serial();

    mockMvc
        .perform(
            post("/api/v1/drones")
                .with(orgAdmin(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    ("{\"serialNumber\":\"%s\",\"payloadMetadata\":\"{\\\"camera\\\":true}\","
                            + "\"registrationNumber\":\"REG-01\",\"registrationFileKey\":\"drones/reg-01.pdf\","
                            + "\"registrationExpiresAt\":\"2030-01-01T00:00:00Z\"}")
                        .formatted(serial)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.serialNumber").value(serial));
  }

  @Test
  void duplicateSerialWithinOrganizationIsRejectedAsConflict() throws Exception {
    String serial = serial();
    createDrone(fixture.clientId(), serial).andExpect(status().isCreated());

    long before = droneCount();

    createDrone(fixture.managerId(), serial).andExpect(status().isConflict());

    assertThat(droneCount()).isEqualTo(before);
  }

  @Test
  void sameSerialIsRejectedInAnotherOrganization() throws Exception {
    String serial = serial();
    long before = droneCount();

    createDrone(fixture.clientId(), serial).andExpect(status().isCreated());
    mockMvc
        .perform(
            post("/api/v1/drones")
                .with(orgAdmin(fixture.otherClientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    ("{\"serialNumber\":\"%s\",\"registrationNumber\":\"REG-02\","
                            + "\"registrationFileKey\":\"drones/reg-02.pdf\","
                            + "\"registrationExpiresAt\":\"2030-01-01T00:00:00Z\"}")
                        .formatted(serial)))
        .andExpect(status().isConflict());

    assertThat(droneCount()).isEqualTo(before + 1);
  }

  @Test
  void foreignOrganizationDroneIsIndistinguishableFromAMissingOne() throws Exception {
    UUID foreignId = createDroneId(fixture.otherClientId(), serial());
    UUID missingId = UUID.randomUUID();

    // Per-request diagnostics (timestamp, traceId) necessarily differ, so compare the fields that
    // could disclose anything: status, code and detail.
    Map<String, Object> foreign =
        disclosiveFields(errorBody(fixture.clientId(), foreignId), foreignId);
    Map<String, Object> missing =
        disclosiveFields(errorBody(fixture.clientId(), missingId), missingId);

    assertThat(foreign).isEqualTo(missing);
    assertThat(foreign)
        .containsEntry("status", 404)
        .containsEntry("code", "DRONE_NOT_FOUND")
        .containsEntry("detail", "Drone not found.");
  }

  /**
   * Drops the per-request diagnostics and the requested path, leaving only the fields that could
   * disclose whether a foreign drone exists.
   */
  private static Map<String, Object> disclosiveFields(String body, UUID requestedId) {
    Map<String, Object> fields = new LinkedHashMap<>(readProblem(body));
    fields.keySet().removeAll(Set.of("timestamp", "traceId", "instance"));
    assertThat(fields.values())
        .noneMatch(value -> String.valueOf(value).contains(requestedId.toString()));
    return fields;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> readProblem(String body) {
    return tools.jackson.databind.json.JsonMapper.shared().readValue(body, Map.class);
  }

  @Test
  void foreignOrganizationDroneCannotChangeServiceability() throws Exception {
    UUID foreignId = createDroneId(fixture.otherClientId(), serial());

    mockMvc
        .perform(
            patch("/api/v1/drones/{id}/serviceability", foreignId)
                .with(orgAdmin(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"serviceability\":\"SUSPENDED\"}"))
        .andExpect(status().isNotFound());

    assertThat(serviceabilityOf(foreignId)).isEqualTo("ACTIVE");
  }

  @Test
  void serviceabilityFollowsValidTransitionsAndRetirementIsTerminal() throws Exception {
    UUID droneId = createDroneId(fixture.clientId(), serial());

    patchServiceability(droneId, "MAINTENANCE")
        .andExpect(jsonPath("$.data.serviceability").value("MAINTENANCE"));
    patchServiceability(droneId, "ACTIVE")
        .andExpect(jsonPath("$.data.serviceability").value("ACTIVE"));
    patchServiceability(droneId, "SUSPENDED")
        .andExpect(jsonPath("$.data.serviceability").value("SUSPENDED"));
    patchServiceability(droneId, "RETIRED")
        .andExpect(jsonPath("$.data.serviceability").value("RETIRED"));

    long before = droneCount();
    patchServiceability(droneId, "ACTIVE").andExpect(status().isBadRequest());
    assertThat(droneCount()).isEqualTo(before);
  }

  @Test
  void unknownServiceabilityIsRejected() throws Exception {
    UUID droneId = createDroneId(fixture.clientId(), serial());

    patchServiceability(droneId, "READY_TO_FLY").andExpect(status().isBadRequest());
  }

  @Test
  void rejectsDroneRegistrationWithoutExpiryEvidenceOrNumber() throws Exception {
    long before = droneCount();
    mockMvc
        .perform(
            post("/api/v1/drones")
                .with(orgAdmin(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"serialNumber\":\"" + serial() + "\",\"model\":\"Mavic 3\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("DRONE_REGISTRATION_INVALID"));
    assertThat(droneCount()).isEqualTo(before);
  }

  @Test
  void selectionReturnsOnlyActiveDronesOfTheOwnOrganization() throws Exception {
    UUID active = createDroneId(fixture.clientId(), serial());
    UUID inMaintenance = createDroneId(fixture.clientId(), serial());
    UUID suspended = createDroneId(fixture.clientId(), serial());
    UUID retired = createDroneId(fixture.clientId(), serial());
    createDroneId(fixture.otherClientId(), serial());

    patchServiceability(inMaintenance, "MAINTENANCE").andExpect(status().isOk());
    patchServiceability(suspended, "SUSPENDED").andExpect(status().isOk());
    patchServiceability(retired, "RETIRED").andExpect(status().isOk());

    String body =
        mockMvc
            .perform(get("/api/v1/drones/selection").with(orgAdmin(fixture.clientId())))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body).contains(active.toString());
    assertThat(body).doesNotContain(inMaintenance.toString());
    assertThat(body).doesNotContain(suspended.toString());
    assertThat(body).doesNotContain(retired.toString());
  }

  @Test
  void selectionExcludesDroneWhenRegistrationExpiresOrIsRemoved() throws Exception {
    UUID expired = createDroneId(fixture.clientId(), serial());
    UUID removedRegistration = createDroneId(fixture.clientId(), serial());
    jdbcTemplate.update(
        "UPDATE drones SET registration_expires_at = ? WHERE id = ?",
        Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)),
        expired);
    jdbcTemplate.update(
        "UPDATE drones SET registration_expires_at = NULL WHERE id = ?", removedRegistration);
    entityManager.flush();
    entityManager.clear();

    String body =
        mockMvc
            .perform(get("/api/v1/drones/selection").with(orgAdmin(fixture.clientId())))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body).doesNotContain(expired.toString());
    assertThat(body).doesNotContain(removedRegistration.toString());
  }

  @Test
  void updateRegistrationAllowsPartialExpiryRenewalAndRejectsPastExpiry() throws Exception {
    UUID droneId = createDroneId(fixture.clientId(), serial());
    Instant renewedExpiry =
        Instant.now().plus(730, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                    "/api/v1/drones/{id}/registration", droneId)
                .with(orgAdmin(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"registrationExpiresAt\":\"" + renewedExpiry + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.registrationNumber").value("REG-01"))
        .andExpect(jsonPath("$.data.registrationExpiresAt").value(renewedExpiry.toString()));

    Instant pastExpiry = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                    "/api/v1/drones/{id}/registration", droneId)
                .with(orgAdmin(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"registrationExpiresAt\":\"" + pastExpiry + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("DRONE_REGISTRATION_INVALID"));
  }

  @Test
  void selectionDoesNotClaimTheDroneIsClearedToFly() throws Exception {
    createDroneId(fixture.clientId(), serial());

    String body =
        mockMvc
            .perform(get("/api/v1/drones/selection").with(orgAdmin(fixture.clientId())))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Serviceability is a maintenance fact only. Permit validity and flight readiness belong to the
    // permit and readiness flows, so no field here may imply a legal clearance to fly.
    assertThat(body).doesNotContainIgnoringCase("permit");
    assertThat(body).doesNotContainIgnoringCase("clearedToFly");
    assertThat(body).doesNotContainIgnoringCase("readyToFly");
    assertThat(body).doesNotContainIgnoringCase("authorized");
  }

  @Test
  void readsRemainAvailableWithoutAnActiveSubscription() throws Exception {
    UUID unentitledOrganizationId = createUnentitledOrganization();
    createDrone(fixture.clientId(), serial()).andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/v1/drones").with(orgAdmin(unentitledOrganizationId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0));
  }

  @Test
  void serviceabilityChangeIsRefusedWithoutAnActiveSubscription() throws Exception {
    UUID droneId = createDroneId(fixture.clientId(), serial());
    UUID unentitledOrganizationId = createUnentitledOrganization();

    mockMvc
        .perform(
            patch("/api/v1/drones/{id}/serviceability", droneId)
                .with(orgAdmin(unentitledOrganizationId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"serviceability\":\"SUSPENDED\"}"))
        .andExpect(status().isNotFound());

    assertThat(serviceabilityOf(droneId)).isEqualTo("ACTIVE");
  }

  private UUID createUnentitledOrganization() {
    UUID organizationId = UUID.randomUUID();
    User admin =
        new User(
            "unentitled-" + organizationId + "@example.test",
            "unentitled-admin",
            null,
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER);
    admin.addRole(UserRole.CUSTOMER);
    return users.saveAndFlush(admin).getId();
  }

  private org.springframework.test.web.servlet.ResultActions createDrone(
      UUID actorId, String serial) throws Exception {
    return mockMvc.perform(
        post("/api/v1/drones")
            .with(orgAdmin(actorId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"serialNumber\":\"%s\",\"model\":\"Mavic 3\",\"manufacturer\":\"DJI\","
                        .formatted(serial)
                    + "\"registrationNumber\":\"REG-01\",\"registrationFileKey\":\"drones/reg-01.pdf\","
                    + "\"registrationExpiresAt\":\"2030-01-01T00:00:00Z\",\"payloadMetadata\":\"{\\\"mtowKg\\\":1.05}\"}"));
  }

  private UUID createDroneId(UUID actorId, String serial) throws Exception {
    String body =
        createDrone(actorId, serial)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String marker = "\"id\":\"";
    int start = body.indexOf(marker) + marker.length();
    return UUID.fromString(body.substring(start, body.indexOf('"', start)));
  }

  private org.springframework.test.web.servlet.ResultActions patchServiceability(
      UUID droneId, String serviceability) throws Exception {
    return mockMvc.perform(
        patch("/api/v1/drones/{id}/serviceability", droneId)
            .with(orgAdmin(fixture.clientId()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"serviceability\":\"%s\"}".formatted(serviceability)));
  }

  private String errorBody(UUID actorId, UUID droneId) throws Exception {
    return mockMvc
        .perform(get("/api/v1/drones/{id}", droneId).with(orgAdmin(actorId)))
        .andExpect(status().isNotFound())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private long droneCount() {
    return jdbcTemplate.queryForObject("SELECT count(*) FROM drones", Long.class);
  }

  private String serviceabilityOf(UUID droneId) {
    return jdbcTemplate.queryForObject(
        "SELECT serviceability FROM drones WHERE id = ?", String.class, droneId);
  }

  private static String serial() {
    return "SN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor orgAdmin(
      UUID id) {
    return jwt()
        .jwt(token -> token.subject(id.toString()))
        .authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                "ROLE_CUSTOMER"));
  }
}
