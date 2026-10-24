package com.smartdroneinspection.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.domain.FlightPermit;
import com.smartdroneinspection.assets.domain.enums.FlightPermitStatus;
import com.smartdroneinspection.assets.domain.enums.PermitStatus;
import com.smartdroneinspection.assets.repository.FlightPermitRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Persistence behaviour of the {@code flight_permits} mapping and its schema constraints. */
@SpringBootTest
@Transactional
@Import(TestcontainersConfiguration.class)
class CompliancePersistenceTest {

  @Autowired FlightPermitRepository permits;
  @Autowired JdbcTemplate jdbcTemplate;
  private final JsonMapper json = JsonMapper.shared();

  @Test
  void roundTripsEveryMappedPermitColumn() {
    UUID organizationId = persistOrganization();
    UUID assetId = persistAsset(organizationId);
    UUID evidenceId = persistEvidence(organizationId, assetId);
    UUID reviewerId = persistUser(organizationId);
    Instant validFrom = Instant.parse("2026-03-01T00:00:00Z");
    Instant validUntil = Instant.parse("2026-09-30T00:00:00Z");
    Instant reviewedAt = Instant.parse("2026-03-02T08:15:00Z");

    // Postgres stores jsonb in its own canonical text form, so the expected values below are the
    // normalised rendering of what was written rather than the submitted bytes.
    FlightPermit saved =
        permits.saveAndFlush(
            new FlightPermit(
                organizationId,
                assetId,
                "North corridor",
                "UAV_OPERATIONS",
                "Regional aviation administration",
                "FP-2026-0001",
                "{\"radiusMeters\":1500}",
                validFrom,
                validUntil,
                "{\"daylightOnly\":true}",
                PermitStatus.ACTIVE,
                evidenceId));

    saved.recordInternalReview(
        PermitStatus.REJECTED, reviewerId, reviewedAt, "Reference does not match the scan.");
    permits.saveAndFlush(saved);

    FlightPermit reloaded = permits.findInOrganization(saved.getId(), organizationId).orElseThrow();

    assertThat(reloaded.getOrganizationId()).isEqualTo(organizationId);
    assertThat(reloaded.getAssetId()).isEqualTo(assetId);
    assertThat(reloaded.getAreaReference()).isEqualTo("North corridor");
    assertThat(reloaded.getPermitType()).isEqualTo("UAV_OPERATIONS");
    assertThat(reloaded.getIssuingAuthority()).isEqualTo("Regional aviation administration");
    assertThat(reloaded.getPermitReference()).isEqualTo("FP-2026-0001");
    assertThat(json.readTree(reloaded.getGeographicScope()))
        .isEqualTo(json.readTree("{\"radiusMeters\":1500}"));
    assertThat(reloaded.getValidFrom()).isEqualTo(validFrom);
    assertThat(reloaded.getValidUntil()).isEqualTo(validUntil);
    assertThat(json.readTree(reloaded.getConditions()))
        .isEqualTo(json.readTree("{\"daylightOnly\":true}"));
    assertThat(reloaded.getStatus()).isEqualTo(FlightPermitStatus.REJECTED);
    assertThat(reloaded.getSourceEvidenceId()).isEqualTo(evidenceId);
    assertThat(reloaded.getReviewedByUserId()).isEqualTo(reviewerId);
    assertThat(reloaded.getReviewedAt()).isEqualTo(reviewedAt);
    assertThat(reloaded.getReviewReason()).isEqualTo("Reference does not match the scan.");
    assertThat(reloaded.getCreatedAt()).isNotNull();
    assertThat(reloaded.getUpdatedAt()).isNotNull();
  }

  @Test
  void returnsAPermitOnlyForItsOwnOrganization() {
    UUID organizationId = persistOrganization();
    UUID otherOrganizationId = persistOrganization();
    FlightPermit saved = permits.saveAndFlush(newPermit(organizationId));

    assertThat(permits.findInOrganization(saved.getId(), organizationId)).isPresent();
    assertThat(permits.findInOrganization(saved.getId(), otherOrganizationId)).isEmpty();
    assertThat(permits.listByOrganization(organizationId)).hasSize(1);
    assertThat(permits.listByOrganization(otherOrganizationId)).isEmpty();
  }

  @Test
  void rejectsAValidityWindowThatEndsBeforeItStarts() {
    UUID organizationId = persistOrganization();

    assertThatThrownBy(
            () ->
                new FlightPermit(
                    organizationId,
                    null,
                    null,
                    "UAV_OPERATIONS",
                    null,
                    null,
                    null,
                    Instant.parse("2026-09-30T00:00:00Z"),
                    Instant.parse("2026-03-01T00:00:00Z"),
                    null,
                    PermitStatus.APPLICATION,
                    null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsABlankPermitType() {
    UUID organizationId = persistOrganization();

    assertThatThrownBy(
            () ->
                new FlightPermit(
                    organizationId,
                    null,
                    null,
                    "  ",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    PermitStatus.APPLICATION,
                    null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void databaseRefusesAReviewerThatIsNotARealUser() {
    UUID organizationId = persistOrganization();
    FlightPermit saved = permits.saveAndFlush(newPermit(organizationId));

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE flight_permits SET reviewed_by_user_id = ? WHERE id = ?",
                    UUID.randomUUID(),
                    saved.getId()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void acceptsEveryRecordedStatusAndRefusesAnythingElse() {
    UUID organizationId = persistOrganization();
    FlightPermit saved = permits.saveAndFlush(newPermit(organizationId));

    for (PermitStatus status : PermitStatus.values()) {
      jdbcTemplate.update(
          "UPDATE flight_permits SET status = ? WHERE id = ?", status.name(), saved.getId());
    }

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE flight_permits SET status = 'AUTHORITY_VERIFIED' WHERE id = ?",
                    saved.getId()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private FlightPermit newPermit(UUID organizationId) {
    return new FlightPermit(
        organizationId,
        null,
        null,
        "UAV_OPERATIONS",
        null,
        null,
        null,
        null,
        null,
        null,
        PermitStatus.APPLICATION,
        null);
  }

  private UUID persistOrganization() {
    UUID organizationId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO organizations (id, legal_name, display_name, registration_code, timezone, status)
        VALUES (?, ?, ?, ?, 'Asia/Ho_Chi_Minh', 'ACTIVE')
        """,
        organizationId,
        "Organization " + organizationId,
        "Organization " + organizationId,
        "ORG-" + organizationId);
    return organizationId;
  }

  private UUID persistUser(UUID organizationId) {
    UUID userId = UUID.randomUUID();
    String email = "permit-user-" + userId + "@example.test";
    jdbcTemplate.update(
        """
        INSERT INTO users (
          id, email, normalized_email, full_name, status, actor_zone,
          organization_id, auth_version, failed_login_count, must_change_password
        ) VALUES (?, ?, ?, ?, 'ACTIVE', 'CUSTOMER_ORGANIZATION', ?, 0, 0, FALSE)
        """,
        userId,
        email,
        email,
        "Permit User",
        organizationId);
    return userId;
  }

  private UUID persistAsset(UUID organizationId) {
    UUID categoryId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO asset_categories (id, code, name, description, active)
        VALUES (?, ?, 'Bridge', 'Compliance persistence category', TRUE)
        """,
        categoryId,
        ("CAT" + categoryId).toUpperCase(java.util.Locale.ROOT));

    UUID assetId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO assets (id, organization_id, category_id, asset_code, name, location, status,
                            created_at, updated_at, row_version)
        VALUES (?, ?, ?, ?, 'Compliance span', '{"label":"District 9"}', 'ACTIVE',
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
        """,
        assetId,
        organizationId,
        categoryId,
        ("AST" + assetId).toUpperCase(java.util.Locale.ROOT));
    return assetId;
  }

  private UUID persistEvidence(UUID organizationId, UUID assetId) {
    UUID uploaderId = persistUser(organizationId);
    UUID inspectionId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO inspections (id, organization_id, asset_id, due_cycle_key, objective, status,
                                 created_at, updated_at, row_version)
        VALUES (?, ?, ?, ?, 'Compliance persistence inspection', 'DRAFT',
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
        """,
        inspectionId,
        organizationId,
        assetId,
        "mf1-" + inspectionId);

    UUID evidenceId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO evidence (id, inspection_id,
                              file_name, content_type, size_bytes, object_key,
                              created_at)
        VALUES (?, ?, 'permit.png', 'image/png', 1, ?,
                CURRENT_TIMESTAMP)
        """,
        evidenceId,
        inspectionId,
        "compliance-persistence/" + evidenceId);
    return evidenceId;
  }
}
