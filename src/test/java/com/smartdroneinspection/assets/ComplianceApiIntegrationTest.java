package com.smartdroneinspection.assets;

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
import com.smartdroneinspection.users.repository.OrganizationRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

/**
 * MF1-07 permit and compliance records.
 *
 * <p>A permit record is what a customer organization states about a permit it holds. Nothing in
 * this slice consults a registry, a government API or an authority: the review an ORG_ADMIN
 * performs is an internal human decision, {@code NOT_APPLICABLE} is the organization's own record,
 * and no field, status or message may imply external verification.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ComplianceApiIntegrationTest {

  /**
   * Free-form JSON documents the organization records; the platform reads no structure into them.
   * Postgres normalises jsonb whitespace, so assertions compare against its canonical form rather
   * than the exact text that was submitted.
   */
  private static final String GEOGRAPHIC_SCOPE = "{\"radiusMeters\": 1500, \"ceilingMeters\": 120}";

  private static final String CONDITIONS = "{\"daylightOnly\": true}";

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired UserRepository users;
  @Autowired OrganizationRepository organizations;
  @Autowired InspectionRepository inspections;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired ObjectMapper json;

  private MockMvc mockMvc;
  private ComplianceTestFixture fixture;
  private ComplianceTestFixture.Data data;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    fixture = new ComplianceTestFixture(users, organizations, inspections, jdbcTemplate);
    data = fixture.create();
  }

  // --- record creation -----------------------------------------------------

  @Test
  void recordsAPermitForTheOwnOrganizationWithItsAssetAndEvidence() throws Exception {
    postPermit(data.adminId(), permitBody())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.organizationId").value(data.organizationId().toString()))
        .andExpect(jsonPath("$.data.assetId").value(data.assetId().toString()))
        .andExpect(jsonPath("$.data.sourceEvidenceId").value(data.evidenceId().toString()))
        .andExpect(jsonPath("$.data.permitType").value("UAV_OPERATIONS"))
        .andExpect(jsonPath("$.data.issuingAuthority").value("Regional aviation administration"))
        .andExpect(jsonPath("$.data.permitReference").value("FP-2026-0001"))
        .andExpect(jsonPath("$.data.status").value("APPLICATION"))
        .andExpect(jsonPath("$.data.reviewedByUserId").doesNotExist())
        .andExpect(jsonPath("$.data.reviewedAt").doesNotExist());

    var row =
        jdbcTemplate.queryForList(
            "SELECT organization_id, asset_id, source_evidence_id, reviewed_by_user_id"
                + " FROM flight_permits WHERE permit_reference = ?",
            "FP-2026-0001");
    assertThat(row).hasSize(1);
    assertThat(row.getFirst().get("organization_id")).isEqualTo(data.organizationId());
    assertThat(row.getFirst().get("asset_id")).isEqualTo(data.assetId());
    assertThat(row.getFirst().get("source_evidence_id")).isEqualTo(data.evidenceId());
    assertThat(row.getFirst().get("reviewed_by_user_id")).isNull();
  }

  @Test
  void keepsGeographicScopeConditionsAndAssetAreaReference() throws Exception {
    postPermit(
            data.adminId(),
            """
            {"permitType":"UAV_OPERATIONS","assetId":"%s","areaReference":"North corridor",
             "permitReference":"FP-SCOPE-0001","geographicScope":"%s","conditions":"%s"}
            """
                // The scope is itself a JSON document, so its quotes are escaped inside the body.
                .formatted(data.assetId(), quoted(GEOGRAPHIC_SCOPE), quoted(CONDITIONS)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.areaReference").value("North corridor"))
        .andExpect(jsonPath("$.data.geographicScope").value(GEOGRAPHIC_SCOPE))
        .andExpect(jsonPath("$.data.conditions").value(CONDITIONS));

    var row =
        jdbcTemplate.queryForList(
            "SELECT geographic_scope, conditions FROM flight_permits WHERE permit_reference = ?",
            "FP-SCOPE-0001");
    assertThat(row).hasSize(1);
    assertThat(String.valueOf(row.getFirst().get("geographic_scope")))
        .contains("radiusMeters")
        .contains("1500");
    assertThat(String.valueOf(row.getFirst().get("conditions"))).contains("daylightOnly");
  }

  @Test
  void ignoresRequestOrganizationAndAlwaysUsesActorOrganization() throws Exception {
    postPermit(
            data.adminId(),
            """
            {"permitType":"UAV_OPERATIONS","organizationId":"%s"}
            """
                .formatted(data.otherOrganizationId()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.organizationId").value(data.organizationId().toString()));

    assertThat(permitCount(data.otherOrganizationId())).isZero();
    assertThat(permitCount(data.organizationId())).isEqualTo(1L);
  }

  @Test
  void permitsOneRecordWithoutAnAssetOrEvidenceReference() throws Exception {
    postPermit(data.adminId(), "{\"permitType\":\"AREA_PERMIT\",\"status\":\"NOT_APPLICABLE\"}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.assetId").doesNotExist())
        .andExpect(jsonPath("$.data.sourceEvidenceId").doesNotExist())
        .andExpect(jsonPath("$.data.status").value("NOT_APPLICABLE"));
  }

  // --- tenant scope --------------------------------------------------------

  @Test
  void returnsNonDisclosingNotFoundForAForeignOrganizationAsset() throws Exception {
    postPermit(
            data.adminId(),
            "{\"permitType\":\"UAV_OPERATIONS\",\"assetId\":\"%s\"}"
                .formatted(data.foreignAssetId()))
        .andExpect(status().isNotFound());
    assertThat(permitCount(data.organizationId())).isZero();
  }

  @Test
  void returnsNonDisclosingNotFoundForAForeignOrganizationEvidenceReference() throws Exception {
    postPermit(
            data.adminId(),
            "{\"permitType\":\"UAV_OPERATIONS\",\"sourceEvidenceId\":\"%s\"}"
                .formatted(data.foreignEvidenceId()))
        .andExpect(status().isNotFound());
    assertThat(permitCount(data.organizationId())).isZero();
  }

  @Test
  void foreignAssetAndForeignEvidenceShareTheSameNotFoundProblem() throws Exception {
    String foreignAssetProblem =
        problemOf(
            "{\"permitType\":\"UAV_OPERATIONS\",\"assetId\":\"%s\"}"
                .formatted(data.foreignAssetId()));
    String foreignEvidenceProblem =
        problemOf(
            "{\"permitType\":\"UAV_OPERATIONS\",\"sourceEvidenceId\":\"%s\"}"
                .formatted(data.foreignEvidenceId()));

    // A caller must not be able to tell "no such asset" from "evidence you may not see".
    assertThat(foreignAssetProblem).isEqualTo(foreignEvidenceProblem);
    assertThat(foreignAssetProblem).contains("\"status\":404");
    assertThat(foreignAssetProblem).contains("PERMIT_REFERENCE_NOT_FOUND");
  }

  @Test
  void listAndReviewNeverRevealAnotherOrganizationsPermits() throws Exception {
    UUID foreignPermitId = insertPermit(data.otherOrganizationId(), "FOREIGN-REF");

    String list =
        mockMvc
            .perform(get("/api/v1/flight-permits").with(orgAdmin(data.adminId())))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(list).doesNotContain(foreignPermitId.toString());

    mockMvc
        .perform(
            put("/api/v1/flight-permits/{id}/review", foreignPermitId)
                .with(orgAdmin(data.adminId()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACTIVE\",\"reason\":\"Cross-tenant attempt.\"}"))
        .andExpect(status().isNotFound());

    assertThat(statusOf(foreignPermitId)).isEqualTo("APPLICATION");
    assertThat(reviewerOf(foreignPermitId)).isNull();
  }

  // --- validation ----------------------------------------------------------

  @Test
  void rejectsAValidityWindowThatEndsBeforeItStarts() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/flight-permits")
                .with(orgAdmin(data.adminId()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"permitType":"UAV_OPERATIONS","validFrom":"%s","validUntil":"%s"}
                    """
                        .formatted(
                            Instant.now().plus(400, ChronoUnit.DAYS),
                            Instant.now().minus(400, ChronoUnit.DAYS))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    assertThat(permitCount(data.organizationId())).isZero();
  }

  @Test
  void rejectsABlankPermitType() throws Exception {
    postPermit(data.adminId(), "{\"permitType\":\"   \"}").andExpect(status().isBadRequest());
    assertThat(permitCount(data.organizationId())).isZero();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"GRANTED", "PENDING", "APPROVED_BY_AUTHORITY", "VERIFIED", "authorityVerified"})
  void rejectsAnyStatusOutsideTheRecordedVocabulary(String status) throws Exception {
    postPermit(
            data.adminId(),
            "{\"permitType\":\"UAV_OPERATIONS\",\"status\":\"%s\"}".formatted(status))
        .andExpect(status().isBadRequest());
    assertThat(permitCount(data.organizationId())).isZero();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"APPLICATION", "ACTIVE", "EXPIRED", "REJECTED", "REVOKED", "NOT_APPLICABLE"})
  void storesEveryRecordedPermitStatus(String status) throws Exception {
    postPermit(
            data.adminId(),
            "{\"permitType\":\"UAV_OPERATIONS\",\"status\":\"%s\"}".formatted(status))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status").value(status));
    assertThat(statusOf(latestPermitId())).isEqualTo(status);
  }

  @Test
  void rejectsGeographicScopeThatIsNotValidJson() throws Exception {
    postPermit(
            data.adminId(), "{\"permitType\":\"UAV_OPERATIONS\",\"geographicScope\":\"not-json\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    assertThat(permitCount(data.organizationId())).isZero();
  }

  // --- internal review -----------------------------------------------------

  @Test
  void recordsTheReviewingAdministratorTheMomentAndTheReason() throws Exception {
    UUID permitId = createPermitId();

    mockMvc
        .perform(
            put("/api/v1/flight-permits/{id}/review", permitId)
                .with(orgAdmin(data.adminId()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"status\":\"ACTIVE\",\"reason\":\"Reference matches the uploaded scan.\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.reviewedByUserId").value(data.adminId().toString()))
        .andExpect(jsonPath("$.data.reviewedAt").isNotEmpty())
        .andExpect(jsonPath("$.data.reviewReason").value("Reference matches the uploaded scan."));

    var row =
        jdbcTemplate.queryForList(
            "SELECT status, reviewed_by_user_id, reviewed_at, review_reason FROM flight_permits"
                + " WHERE id = ?",
            permitId);
    assertThat(row.getFirst().get("status")).isEqualTo("ACTIVE");
    assertThat(row.getFirst().get("reviewed_by_user_id")).isEqualTo(data.adminId());
    assertThat(row.getFirst().get("reviewed_at")).isNotNull();
    assertThat(row.getFirst().get("review_reason"))
        .isEqualTo("Reference matches the uploaded scan.");
  }

  @Test
  void requiresAReasonForAnInternalReview() throws Exception {
    UUID permitId = createPermitId();

    mockMvc
        .perform(
            put("/api/v1/flight-permits/{id}/review", permitId)
                .with(orgAdmin(data.adminId()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"REJECTED\",\"reason\":\"  \"}"))
        .andExpect(status().isBadRequest());

    assertThat(statusOf(permitId)).isEqualTo("APPLICATION");
    assertThat(reviewerOf(permitId)).isNull();
  }

  @Test
  void internalReviewCannotSetAStatusOutsideTheRecordedVocabulary() throws Exception {
    UUID permitId = createPermitId();

    mockMvc
        .perform(
            put("/api/v1/flight-permits/{id}/review", permitId)
                .with(orgAdmin(data.adminId()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"status\":\"AUTHORITY_VERIFIED\",\"reason\":\"Attempted external claim.\"}"))
        .andExpect(status().isBadRequest());

    assertThat(statusOf(permitId)).isEqualTo("APPLICATION");
    assertThat(reviewerOf(permitId)).isNull();
  }

  // --- no external verification claim --------------------------------------

  @Test
  void noPermitFieldClaimsExternalOrGovernmentVerification() throws Exception {
    createPermitId();

    String body = readOne();

    // These columns record what the organization holds and who reviewed it internally. Nothing
    // here may say an authority, registry or regulator confirmed the permit.
    assertThat(body)
        .doesNotContainIgnoringCase("verified")
        .doesNotContainIgnoringCase("authorityConfirmed")
        .doesNotContainIgnoringCase("authorityVerified")
        .doesNotContainIgnoringCase("governmentVerified")
        .doesNotContainIgnoringCase("registryChecked")
        .doesNotContainIgnoringCase("legallyCleared")
        .doesNotContainIgnoringCase("clearedToFly")
        .doesNotContainIgnoringCase("authentic");
  }

  @Test
  void theReviewApiMakesNoExternalVerificationClaimInItsSurface() throws Exception {
    createPermitId();
    String listBody =
        mockMvc
            .perform(get("/api/v1/flight-permits").with(orgAdmin(data.adminId())))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // The recorded review fields are named for the human act, never for an authority's blessing.
    assertThat(listBody).contains("reviewedByUserId").doesNotContainIgnoringCase("verified");

    // The vocabulary itself is fixed by the schema, and none of it asserts an authority agreed.
    String permittedStatuses = statusConstraintDefinition();
    assertThat(permittedStatuses)
        .contains("APPLICATION")
        .contains("ACTIVE")
        .contains("EXPIRED")
        .contains("REJECTED")
        .contains("REVOKED")
        .contains("NOT_APPLICABLE")
        .doesNotContainIgnoringCase("verified")
        .doesNotContainIgnoringCase("confirmed")
        .doesNotContainIgnoringCase("cleared")
        .doesNotContainIgnoringCase("approved");
  }

  // --- entitlement ---------------------------------------------------------

  @Test
  void refusesNewPermitWritesWhenTheTenantIsNotEntitled() throws Exception {
    // A body the unentitled tenant fully owns, so entitlement — not a 404 — is what refuses it.
    postPermit(data.unentitledAdminId(), "{\"permitType\":\"UAV_OPERATIONS\"}")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("SUBSCRIPTION_INACTIVE"));
    assertThat(permitCount(data.unentitledOrganizationId())).isZero();
  }

  @Test
  void refusesReviewWhenTheTenantIsNotEntitled() throws Exception {
    // A permit of the unentitled tenant's own, so entitlement — not a 404 — is what refuses it.
    UUID permitId = insertPermit(data.unentitledOrganizationId(), "UNENTITLED-REVIEW-TARGET");

    mockMvc
        .perform(
            put("/api/v1/flight-permits/{id}/review", permitId)
                .with(orgAdmin(data.unentitledAdminId()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACTIVE\",\"reason\":\"Attempt without entitlement.\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("SUBSCRIPTION_INACTIVE"));

    assertThat(statusOf(permitId)).isEqualTo("APPLICATION");
    assertThat(reviewerOf(permitId)).isNull();
  }

  @Test
  void readsRemainAvailableWithoutAnActiveSubscription() throws Exception {
    insertPermit(data.unentitledOrganizationId(), "UNENTITLED-REF");

    mockMvc
        .perform(get("/api/v1/flight-permits").with(orgAdmin(data.unentitledAdminId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1));
  }

  // --- caller role ---------------------------------------------------------

  @Test
  void rejectsCallersWhoAreNotOrganizationAdministrators() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/flight-permits")
                .with(
                    jwt()
                        .jwt(token -> token.subject(data.inspectorId().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_INSPECTOR")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(permitBody()))
        .andExpect(status().isForbidden());
    assertThat(permitCount(data.organizationId())).isZero();
  }

  // --- helpers -------------------------------------------------------------

  /** Escapes a JSON document so it can be embedded as a string value inside a JSON request. */
  private static String quoted(String jsonDocument) {
    return jsonDocument.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  /** A complete same-organization permit body: own asset, own evidence, an open validity window. */
  private String permitBody() {
    return """
        {"permitType":"UAV_OPERATIONS","assetId":"%s","issuingAuthority":"Regional aviation administration",
         "permitReference":"FP-2026-0001","validFrom":"%s","validUntil":"%s","sourceEvidenceId":"%s"}
        """
        .formatted(
            data.assetId(),
            Instant.now().minus(10, ChronoUnit.DAYS),
            Instant.now().plus(300, ChronoUnit.DAYS),
            data.evidenceId());
  }

  private ResultActions postPermit(UUID actorId, String content) throws Exception {
    return mockMvc.perform(
        post("/api/v1/flight-permits")
            .with(orgAdmin(actorId))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(content));
  }

  private String problemOf(String content) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/flight-permits")
                    .with(orgAdmin(data.adminId()))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(content))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();
    // timestamp and traceId legitimately differ per request, so the disclosure comparison keeps
    // only the fields a caller could actually learn something from.
    return body.replaceAll("\"timestamp\":\"[^\"]*\"", "\"timestamp\":\"?\"")
        .replaceAll("\"traceId\":\"[^\"]*\"", "\"traceId\":\"?\"");
  }

  private UUID createPermitId() {
    UUID permitId = UUID.randomUUID();
    insertPermit(data.organizationId(), "REF-" + UUID.randomUUID(), permitId);
    return permitId;
  }

  private UUID insertPermit(UUID organizationId, String permitReference) {
    UUID permitId = UUID.randomUUID();
    insertPermit(organizationId, permitReference, permitId);
    return permitId;
  }

  private void insertPermit(UUID organizationId, String permitReference, UUID permitId) {
    jdbcTemplate.update(
        """
        INSERT INTO flight_permits (id, organization_id, permit_type, issuing_authority,
                                    permit_reference, status)
        VALUES (?, ?, 'UAV_OPERATIONS', 'Regional aviation administration', ?, 'APPLICATION')
        """,
        permitId,
        organizationId,
        permitReference);
  }

  private UUID latestPermitId() {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM flight_permits ORDER BY created_at DESC, id DESC LIMIT 1", UUID.class);
  }

  private String readOne() throws Exception {
    String body =
        mockMvc
            .perform(get("/api/v1/flight-permits").with(orgAdmin(data.adminId())))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).path("data").get(0).toString();
  }

  /**
   * The status check the database itself enforces on a permit record, read out of the live
   * constraint rather than restated here, so the assertion tracks the schema instead of a copy.
   */
  private String statusConstraintDefinition() {
    return jdbcTemplate.queryForObject(
        "SELECT pg_get_constraintdef(oid) FROM pg_constraint"
            + " WHERE conname = 'ck_flight_permits_status'"
            + " AND connamespace = current_schema()::regnamespace",
        String.class);
  }

  private long permitCount(UUID organizationId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM flight_permits WHERE organization_id = ?",
        Long.class,
        organizationId);
  }

  private String statusOf(UUID permitId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM flight_permits WHERE id = ?", String.class, permitId);
  }

  private Object reviewerOf(UUID permitId) {
    return jdbcTemplate.queryForObject(
        "SELECT reviewed_by_user_id FROM flight_permits WHERE id = ?", Object.class, permitId);
  }

  private RequestPostProcessor orgAdmin(UUID id) {
    return jwt()
        .jwt(token -> token.subject(id.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_ORG_ADMIN"));
  }
}
