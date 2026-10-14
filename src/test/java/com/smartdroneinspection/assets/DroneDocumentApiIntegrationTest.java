package com.smartdroneinspection.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** Drone document upload: server-derived checksum, tenant scope, expiry, and revocation. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class DroneDocumentApiIntegrationTest {

  private static final byte[] PNG =
      Base64.getDecoder()
          .decode(
              "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/o2cAAAAASUVORK5CYII=");

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired AssetCategoryRepository categories;
  @Autowired ChecklistTemplateRepository templates;
  @Autowired AssetRepository assets;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired EntityManager entityManager;
  @MockitoBean EvidenceObjectStore objectStore;

  private final Map<String, byte[]> storedObjects = new ConcurrentHashMap<>();
  private AssetTestFixture.Data fixture;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() throws IOException {
    mockMvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    storedObjects.clear();
    org.mockito.Mockito.doAnswer(
            invocation -> {
              storedObjects.put(
                  invocation.getArgument(0),
                  invocation.getArgument(3, InputStream.class).readAllBytes());
              return null;
            })
        .when(objectStore)
        .put(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any(InputStream.class));
    org.mockito.Mockito.when(objectStore.open(org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            invocation -> new ByteArrayInputStream(storedObjects.get(invocation.getArgument(0))));
    fixture = new AssetTestFixture(categories, templates, assets, users, jdbcTemplate).create();
    grantEntitlement(fixture.organizationId());
    grantEntitlement(fixture.otherOrganizationId());
  }

  private void grantEntitlement(UUID organizationId) {
    jdbcTemplate.update(
        """
        INSERT INTO subscriptions (organization_id, plan_code, billing_period_months, status, starts_at)
        VALUES (?, 'ENTERPRISE', 1, 'ACTIVE', CURRENT_TIMESTAMP - INTERVAL '1 hour')
        """,
        organizationId);
  }

  @Test
  void uploadStoresTheServerDerivedChecksumNotAClientSuppliedOne() throws Exception {
    UUID droneId = seedDrone(fixture.organizationId());
    String forgedChecksum = "0".repeat(64);

    String body =
        upload(droneId, "AIRWORTHINESS_CERTIFICATE")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String expectedChecksum =
        HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(PNG));

    // The caller asserted an all-zero checksum; the server must have recorded its own digest of the
    // bytes it actually stored, so the persisted value can never be a client claim.
    assertThat(body).contains(expectedChecksum).doesNotContain(forgedChecksum);
    assertThat(checksumInDatabase(droneId)).isEqualTo(expectedChecksum);
    assertThat(storedObjects.values())
        .singleElement()
        .satisfies(bytes -> assertThat(bytes).isEqualTo(PNG));
  }

  @Test
  void listsOnlyDocumentsOfTheOwnOrganization() throws Exception {
    UUID droneId = seedDrone(fixture.organizationId());
    UUID foreignDroneId = seedDrone(fixture.otherOrganizationId());
    upload(droneId, "FLIGHT_PERMIT").andExpect(status().isCreated());
    upload(foreignDroneId, fixture.otherClientId(), "FLIGHT_PERMIT", "2099-01-01T00:00:00Z")
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/v1/drones/{id}/documents", droneId).with(orgAdmin(fixture.clientId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1));

    // The foreign drone is reported exactly like a drone that does not exist.
    mockMvc
        .perform(
            get("/api/v1/drones/{id}/documents", foreignDroneId).with(orgAdmin(fixture.clientId())))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("DRONE_NOT_FOUND"));
  }

  @Test
  void refusesToUploadOntoAnotherOrganizationsDrone() throws Exception {
    UUID foreignDroneId = seedDrone(fixture.otherOrganizationId());
    long documentsBefore = droneDocumentCount();

    // The caller owns the Drone in the previous test; this one is the Drone owned by somebody
    // else, which is the case the write path has to refuse.
    upload(foreignDroneId, "FLIGHT_PERMIT")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("DRONE_NOT_FOUND"));

    assertThat(droneDocumentCount()).isEqualTo(documentsBefore);
    assertThat(storedObjects).isEmpty();
  }

  @Test
  void repeatedUploadsNeverReuseAnObjectKeyAndTheDatabaseRefusesDuplicates() throws Exception {
    UUID droneId = seedDrone(fixture.organizationId());
    upload(droneId, "FLIGHT_PERMIT").andExpect(status().isCreated());
    upload(droneId, "FLIGHT_PERMIT").andExpect(status().isCreated());

    List<String> keys =
        jdbcTemplate.queryForList("SELECT object_key FROM drone_documents", String.class);
    assertThat(keys).hasSize(2).doesNotHaveDuplicates();
    assertThat(keys)
        .allSatisfy(key -> assertThat(key).startsWith("drones/" + droneId + "/documents/"));

    // The unique constraint is the backstop: even a forced duplicate key must be refused.
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO drone_documents (
                      id, drone_id, document_type, status, object_key, checksum_sha256, uploaded_by_user_id
                    ) VALUES (?, ?, 'FLIGHT_PERMIT', 'PENDING_REVIEW', ?, ?, ?)
                    """,
                    UUID.randomUUID(),
                    droneId,
                    keys.get(0),
                    "e".repeat(64),
                    fixture.clientId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void approvedDocumentPastItsValidityIsReportedAsExpired() throws Exception {
    UUID droneId = seedDrone(fixture.organizationId());
    UUID documentId =
        idFrom(
            upload(droneId, "AIRWORTHINESS_CERTIFICATE")
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());

    approve(documentId);

    // The document is ACTIVE but its validity window has already closed, so a reader must see
    // EXPIRED: a stale certificate cannot be read as a current one.
    expireDocument(documentId);

    mockMvc
        .perform(get("/api/v1/drones/{id}/documents", droneId).with(orgAdmin(fixture.clientId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(documentId.toString()))
        .andExpect(jsonPath("$.data[0].status").value("EXPIRED"));
  }

  @Test
  void revokedDocumentCannotReturnToActive() throws Exception {
    UUID droneId = seedDrone(fixture.organizationId());
    UUID documentId =
        idFrom(
            upload(droneId, "FLIGHT_PERMIT")
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());

    mockMvc
        .perform(
            patch("/api/v1/drones/{droneId}/documents/{documentId}/status", droneId, documentId)
                .with(orgAdmin(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"REVOKED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("REVOKED"));

    mockMvc
        .perform(
            patch("/api/v1/drones/{droneId}/documents/{documentId}/status", droneId, documentId)
                .with(orgAdmin(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACTIVE\"}"))
        .andExpect(status().isBadRequest());

    assertThat(statusInDatabase(documentId)).isEqualTo("REVOKED");
  }

  @Test
  void invalidValidityWindowIsRejectedAndNothingIsStored() throws Exception {
    UUID droneId = seedDrone(fixture.organizationId());
    long before = documentCount();

    mockMvc
        .perform(
            multipart("/api/v1/drones/{id}/documents", droneId)
                .file(new MockMultipartFile("file", "permit.png", "image/png", PNG))
                .param("documentType", "FLIGHT_PERMIT")
                .param("validFrom", "2027-01-01T00:00:00Z")
                .param("validUntil", "2026-01-01T00:00:00Z")
                .with(orgAdmin(fixture.clientId())))
        .andExpect(status().isBadRequest());

    assertThat(documentCount()).isEqualTo(before);
    assertThat(storedObjects).isEmpty();
  }

  @Test
  void uploadRequiresAnActiveSubscription() throws Exception {
    UUID droneId = seedDrone(fixture.organizationId());
    revokeEntitlement(fixture.organizationId());

    mockMvc
        .perform(
            multipart("/api/v1/drones/{id}/documents", droneId)
                .file(new MockMultipartFile("file", "permit.png", "image/png", PNG))
                .param("documentType", "FLIGHT_PERMIT")
                .with(orgAdmin(fixture.clientId())))
        .andExpect(status().isForbidden());

    assertThat(documentCount()).isZero();
    assertThat(storedObjects).isEmpty();
  }

  private void revokeEntitlement(UUID organizationId) {
    jdbcTemplate.update(
        "UPDATE subscriptions SET status = 'SUSPENDED' WHERE organization_id = ?", organizationId);
  }

  private void approve(UUID documentId) throws Exception {
    mockMvc
        .perform(
            patch(
                    "/api/v1/drones/{droneId}/documents/{documentId}/status",
                    droneOf(documentId),
                    documentId)
                .with(orgAdmin(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACTIVE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ACTIVE"));
  }

  /** Backdates the validity window without touching the stored status. */
  private void expireDocument(UUID documentId) {
    jdbcTemplate.update(
        "UPDATE drone_documents SET valid_until = CURRENT_TIMESTAMP - INTERVAL '1 day' WHERE id = ?",
        documentId);
    // The test runs in one transaction, so the JDBC write must evict the stale entity from the
    // persistence context or the read would still return the cached, unexpired row.
    entityManager.clear();
  }

  private UUID droneOf(UUID documentId) {
    return jdbcTemplate.queryForObject(
        "SELECT drone_id FROM drone_documents WHERE id = ?", UUID.class, documentId);
  }

  private org.springframework.test.web.servlet.ResultActions upload(
      UUID droneId, String documentType) throws Exception {
    return mockMvc.perform(
        multipart("/api/v1/drones/{id}/documents", droneId)
            .file(new MockMultipartFile("file", "document.png", "image/png", PNG))
            .param("documentType", documentType)
            .param("issuer", "Civil Aviation Authority")
            .param("validUntil", "2099-01-01T00:00:00Z")
            // A client-supplied checksum must be ignored: the server derives its own.
            .param("checksumSha256", "0".repeat(64))
            .with(orgAdmin(fixture.clientId())));
  }

  private org.springframework.test.web.servlet.ResultActions upload(
      UUID droneId, UUID actorId, String documentType, String validUntil) throws Exception {
    return mockMvc.perform(
        multipart("/api/v1/drones/{id}/documents", droneId)
            .file(new MockMultipartFile("file", "document.png", "image/png", PNG))
            .param("documentType", documentType)
            .param("validUntil", validUntil)
            .with(orgAdmin(actorId)));
  }

  private UUID seedDrone(UUID organizationId) throws Exception {
    String serial = "SN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    return UUID.fromString(
        jdbcTemplate.queryForObject(
            "INSERT INTO drones (organization_id, serial_number, serviceability)"
                + " VALUES (?, ?, 'ACTIVE') RETURNING id",
            String.class,
            organizationId,
            serial));
  }

  private long droneDocumentCount() {
    return jdbcTemplate.queryForObject("SELECT count(*) FROM drone_documents", Long.class);
  }

  private String checksumInDatabase(UUID droneId) {
    return jdbcTemplate.queryForObject(
        "SELECT checksum_sha256 FROM drone_documents WHERE drone_id = ?", String.class, droneId);
  }

  private String statusInDatabase(UUID documentId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM drone_documents WHERE id = ?", String.class, documentId);
  }

  private long documentCount() {
    return jdbcTemplate.queryForObject("SELECT count(*) FROM drone_documents", Long.class);
  }

  private static UUID idFrom(String body) {
    String marker = "\"id\":\"";
    int start = body.indexOf(marker) + marker.length();
    return UUID.fromString(body.substring(start, body.indexOf('"', start)));
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor orgAdmin(
      UUID id) {
    return jwt()
        .jwt(token -> token.subject(id.toString()))
        .authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                "ROLE_ORG_ADMIN"));
  }
}
