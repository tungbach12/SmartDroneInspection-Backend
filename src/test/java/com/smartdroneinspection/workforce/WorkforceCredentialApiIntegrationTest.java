package com.smartdroneinspection.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.users.domain.AuthSession;
import com.smartdroneinspection.users.domain.enums.ClientType;
import com.smartdroneinspection.users.repository.AuthSessionRepository;
import com.smartdroneinspection.users.repository.OrganizationRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

/**
 * MF1-04/05 credential records. A credential is a tenant record of a document the organization
 * holds; reviewing it is an attributable internal human decision and never a government or
 * professional verification.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class WorkforceCredentialApiIntegrationTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired UserRepository users;
  @Autowired OrganizationRepository organizations;
  @Autowired InspectionRepository inspections;
  @Autowired AuthSessionRepository sessions;
  @Autowired EntityManager entityManager;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired ObjectMapper json;

  private MockMvc mockMvc;
  private WorkforceCredentialTestFixture fixture;
  private WorkforceCredentialTestFixture.Data data;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    fixture = new WorkforceCredentialTestFixture(users, organizations, inspections, jdbcTemplate);
    data = fixture.create();
  }

  @Test
  void createsCredentialForSameOrganizationSubjectAndAttachesEvidence() throws Exception {
    UUID evidenceId = fixture.createEvidence(data.organizationId(), "owned");

    postCredential(data.subjectId(), "QUALIFICATION", evidenceId, issuedIn(400), expiresIn(400))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.credentialType").value("QUALIFICATION"))
        .andExpect(jsonPath("$.data.userId").value(data.subjectId().toString()))
        .andExpect(jsonPath("$.data.organizationId").value(data.organizationId().toString()))
        .andExpect(jsonPath("$.data.evidenceId").value(evidenceId.toString()))
        .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
        .andExpect(jsonPath("$.data.verifiedByUserId").doesNotExist())
        .andExpect(jsonPath("$.data.governmentVerified").doesNotExist());

    var rows =
        jdbcTemplate.queryForList(
            "SELECT * FROM workforce_credentials WHERE user_id = ?", data.subjectId());
    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().get("evidence_id")).isEqualTo(evidenceId);
    assertThat(rows.getFirst().get("verified_by_user_id")).isNull();
  }

  @Test
  void ignoresRequestOrganizationAndAlwaysUsesActorOrganization() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/workforce/credentials")
                .with(orgAdmin(data.reviewerId()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"userId":"%s","credentialType":"TRAINING","issuer":"Authority",
                     "organizationId":"%s"}
                    """
                        .formatted(data.subjectId(), data.otherOrganizationId())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.organizationId").value(data.organizationId().toString()));

    assertThat(credentialOrganizationOf(data.subjectId())).isEqualTo(data.organizationId());
  }

  @Test
  void returnsNonDisclosingNotFoundForForeignOrganizationSubject() throws Exception {
    postCredential(data.foreignSubjectId(), "QUALIFICATION", null, issuedIn(400), expiresIn(400))
        .andExpect(status().isNotFound());
    assertThat(credentialCount(data.foreignSubjectId())).isZero();
  }

  @Test
  void returnsNonDisclosingNotFoundForForeignOrganizationEvidence() throws Exception {
    UUID foreignEvidenceId = fixture.createEvidence(data.otherOrganizationId(), "foreign");

    postCredential(
            data.subjectId(), "QUALIFICATION", foreignEvidenceId, issuedIn(400), expiresIn(400))
        .andExpect(status().isNotFound());
    assertThat(credentialCount(data.subjectId())).isZero();
  }

  @Test
  void foreignSubjectAndForeignEvidenceShareTheSameNotFoundMessageShape() throws Exception {
    UUID foreignEvidenceId = fixture.createEvidence(data.otherOrganizationId(), "foreign");

    String foreignSubjectProblem = problemOf(data.foreignSubjectId(), null);
    String foreignEvidenceProblem = problemOf(data.subjectId(), foreignEvidenceId);

    // A caller must not be able to tell "no such subject" from "evidence you may not see".
    assertThat(foreignSubjectProblem).isEqualTo(foreignEvidenceProblem);
  }

  @Test
  void rejectsExpiryBeforeIssue() throws Exception {
    UUID evidenceId = fixture.createEvidence(data.organizationId(), "inverted");

    // Issued 300 days ago but expiring 400 days ago, so the window runs backwards.
    postCredential(data.subjectId(), "QUALIFICATION", evidenceId, issuedIn(300), issuedIn(400))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    assertThat(credentialCount(data.subjectId())).isZero();
  }

  @Test
  void rejectsBlankCredentialType() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/workforce/credentials")
                .with(orgAdmin(data.reviewerId()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"userId":"%s","credentialType":"  ","issuer":"Authority"}
                    """
                        .formatted(data.subjectId())))
        .andExpect(status().isBadRequest());
    assertThat(credentialCount(data.subjectId())).isZero();
  }

  @Test
  void recordsReviewerAttributionAndTimestampOnApproval() throws Exception {
    UUID credentialId = createCredentialId();

    mockMvc
        .perform(
            put("/api/v1/workforce/credentials/{id}/review", credentialId)
                .with(orgAdmin(data.reviewerId()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"decision":"APPROVE","reason":"Certificate matches the uploaded scan."}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.verifiedByUserId").value(data.reviewerId().toString()))
        .andExpect(jsonPath("$.data.verifiedAt").isNotEmpty())
        .andExpect(
            jsonPath("$.data.verificationReason").value("Certificate matches the uploaded scan."));

    var row =
        jdbcTemplate.queryForList(
            "SELECT verified_by_user_id, verified_at, verification_reason, status"
                + " FROM workforce_credentials WHERE id = ?",
            credentialId);
    assertThat(row).hasSize(1);
    assertThat(row.getFirst().get("verified_by_user_id")).isEqualTo(data.reviewerId());
    assertThat(row.getFirst().get("verified_at")).isNotNull();
    assertThat(row.getFirst().get("status")).isEqualTo("ACTIVE");
  }

  @Test
  void requiresAReasonWhenRejectingACredential() throws Exception {
    UUID credentialId = createCredentialId();

    mockMvc
        .perform(
            put("/api/v1/workforce/credentials/{id}/review", credentialId)
                .with(orgAdmin(data.reviewerId()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"decision":"REJECT","reason":"   "}
                    """))
        .andExpect(status().isBadRequest());

    assertThat(statusOf(credentialId)).isEqualTo("PENDING_REVIEW");
    assertThat(reviewerOf(credentialId)).isNull();
  }

  @Test
  void reviewIsScopedToTheReviewersOwnOrganization() throws Exception {
    UUID credentialId = createCredentialId();

    mockMvc
        .perform(
            put("/api/v1/workforce/credentials/{id}/review", credentialId)
                .with(orgAdmin(data.foreignReviewerId()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"decision":"APPROVE","reason":"Attempted cross-tenant approval."}
                    """))
        .andExpect(status().isNotFound());

    assertThat(statusOf(credentialId)).isEqualTo("PENDING_REVIEW");
    assertThat(reviewerOf(credentialId)).isNull();
  }

  @Test
  void listReturnsOnlyTheReviewersOrganizationCredentials() throws Exception {
    createCredentialId();
    UUID foreignCredentialId =
        createCredentialIdFor(data.foreignSubjectId(), data.otherOrganizationId());

    String body =
        mockMvc
            .perform(get("/api/v1/workforce/credentials").with(orgAdmin(data.reviewerId())))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body).contains(data.subjectId().toString());
    assertThat(body).doesNotContain(foreignCredentialId.toString());
    assertThat(body).doesNotContain(data.foreignSubjectId().toString());
  }

  @Test
  void reportsExpiryDerivedStatusWithoutClaimingExternalVerification() throws Exception {
    Instant now = Instant.now();
    UUID expiringSoon = createCredentialId("EXPIRING_SOON", now.plus(5, ChronoUnit.DAYS));
    UUID expired = createCredentialId("EXPIRED", now.minus(1, ChronoUnit.DAYS));
    UUID stillValid = createCredentialId("ACTIVE", now.plus(400, ChronoUnit.DAYS));

    assertThat(reportedStatus(expiringSoon)).isEqualTo("EXPIRING_SOON");
    assertThat(reportedStatus(expired)).isEqualTo("EXPIRED");
    assertThat(reportedStatus(stillValid)).isEqualTo("ACTIVE");
  }

  @Test
  void keepsTheCredentialRecordAfterTheSubjectIsSuspended() throws Exception {
    UUID credentialId = createCredentialId();
    UUID sessionId = insertSession(data.subjectId());

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                    "/api/v1/workforce/users/{userId}/status", data.subjectId())
                .with(orgAdmin(data.reviewerId()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SUSPENDED\"}"))
        .andExpect(status().isOk());

    // History is preserved: the credential row survives the subject's suspension.
    assertThat(statusOf(credentialId)).isNotNull();
    assertThat(reviewerOf(credentialId)).isNull();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM workforce_credentials WHERE id = ?",
                Long.class,
                credentialId))
        .isEqualTo(1L);
    // Suspension revoked the subject's live session, confirming the user really was suspended.
    entityManager.flush();
    entityManager.clear();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT revoked_at FROM auth_sessions WHERE id = ?",
                java.sql.Timestamp.class,
                sessionId))
        .isNotNull();
  }

  @Test
  void refusesNewCredentialWritesWhenTheTenantIsNotEntitled() throws Exception {
    UUID evidenceId = fixture.createEvidence(data.unentitledOrganizationId(), "unentitled");

    mockMvc
        .perform(
            post("/api/v1/workforce/credentials")
                .with(orgAdmin(data.unentitledReviewerId()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"userId":"%s","credentialType":"TRAINING","evidenceId":"%s"}
                    """
                        .formatted(data.unentitledReviewerId(), evidenceId)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("SUBSCRIPTION_INACTIVE"));
  }

  @Test
  void rejectsNonOrganizationAdministratorCallers() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/workforce/credentials")
                .with(
                    jwt()
                        .jwt(token -> token.subject(data.subjectId().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_INSPECTOR")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"userId":"%s","credentialType":"TRAINING"}
                    """
                        .formatted(data.subjectId())))
        .andExpect(status().isForbidden());
    assertThat(credentialCount(data.subjectId())).isZero();
  }

  // --- helpers -------------------------------------------------------------

  private UUID createCredentialId() {
    return createCredentialId("PENDING_REVIEW", expiresIn(400));
  }

  private UUID createCredentialId(String persistedStatus, Instant expiresAt) {
    UUID credentialId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO workforce_credentials (id, organization_id, user_id, credential_type, issuer,
                                          credential_reference, issued_at, expires_at, status)
        VALUES (?, ?, ?, 'QUALIFICATION', 'Authority', ?, ?, ?, ?)
        """,
        credentialId,
        data.organizationId(),
        data.subjectId(),
        "REF-" + UUID.randomUUID(),
        java.sql.Timestamp.from(issuedIn(400)),
        java.sql.Timestamp.from(expiresAt),
        persistedStatus);
    return credentialId;
  }

  private UUID createCredentialIdFor(UUID subjectId, UUID organizationId) {
    UUID credentialId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO workforce_credentials (id, organization_id, user_id, credential_type, issuer,
                                          issued_at, expires_at, status)
        VALUES (?, ?, ?, 'TRAINING', 'Foreign Authority', ?, ?, 'PENDING_REVIEW')
        """,
        credentialId,
        organizationId,
        subjectId,
        java.sql.Timestamp.from(issuedIn(400)),
        java.sql.Timestamp.from(expiresIn(400)));
    return credentialId;
  }

  private String reportedStatus(UUID credentialId) throws Exception {
    String body =
        mockMvc
            .perform(get("/api/v1/workforce/credentials").with(orgAdmin(data.reviewerId())))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var node = json.readTree(body).path("data");
    for (var credential : node) {
      if (credentialId.toString().equals(credential.path("id").asText())) {
        return credential.path("status").asText();
      }
    }
    throw new AssertionError("Credential " + credentialId + " was not listed.");
  }

  private String problemOf(UUID subjectId, UUID evidenceId) throws Exception {
    String evidenceField = evidenceId == null ? "" : ",\"evidenceId\":\"" + evidenceId + "\"";
    String body =
        mockMvc
            .perform(
                post("/api/v1/workforce/credentials")
                    .with(orgAdmin(data.reviewerId()))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"userId":"%s","credentialType":"QUALIFICATION"%s}
                        """
                            .formatted(subjectId, evidenceField)))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();
    // timestamp and traceId legitimately differ per request, so the disclosure comparison keeps
    // only the fields a caller could actually learn something from.
    return body.replaceAll("\"timestamp\":\"[^\"]*\"", "\"timestamp\":\"?\"")
        .replaceAll("\"traceId\":\"[^\"]*\"", "\"traceId\":\"?\"");
  }

  private org.springframework.test.web.servlet.ResultActions postCredential(
      UUID subjectId, String credentialType, UUID evidenceId, Instant issuedAt, Instant expiresAt)
      throws Exception {
    String evidenceField = evidenceId == null ? "" : ",\"evidenceId\":\"" + evidenceId + "\"";
    return mockMvc.perform(
        post("/api/v1/workforce/credentials")
            .with(orgAdmin(data.reviewerId()))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"userId":"%s","credentialType":"%s","issuer":"Authority",
                 "issuedAt":"%s","expiresAt":"%s"%s}
                """
                    .formatted(subjectId, credentialType, issuedAt, expiresAt, evidenceField)));
  }

  private Instant issuedIn(long days) {
    return Instant.now().minus(days, ChronoUnit.DAYS);
  }

  private Instant expiresIn(long days) {
    return Instant.now().plus(days, ChronoUnit.DAYS);
  }

  private long credentialCount(UUID userId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM workforce_credentials WHERE user_id = ?", Long.class, userId);
  }

  private UUID credentialOrganizationOf(UUID userId) {
    return jdbcTemplate.queryForObject(
        "SELECT organization_id FROM workforce_credentials WHERE user_id = ?", UUID.class, userId);
  }

  private String statusOf(UUID credentialId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM workforce_credentials WHERE id = ?", String.class, credentialId);
  }

  private Object reviewerOf(UUID credentialId) {
    return jdbcTemplate.queryForObject(
        "SELECT verified_by_user_id FROM workforce_credentials WHERE id = ?",
        Object.class,
        credentialId);
  }

  private UUID insertSession(UUID userId) {
    var user = users.findDetailedById(userId).orElseThrow();
    return sessions
        .saveAndFlush(new AuthSession(user, ClientType.WEB, Instant.now().plusSeconds(3600)))
        .getId();
  }

  private RequestPostProcessor orgAdmin(UUID id) {
    return jwt()
        .jwt(token -> token.subject(id.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_ORG_MANAGER"));
  }
}
