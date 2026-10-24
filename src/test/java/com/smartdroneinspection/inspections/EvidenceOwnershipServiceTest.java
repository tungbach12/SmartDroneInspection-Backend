package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.shared.EvidenceOwnership;
import com.smartdroneinspection.users.repository.UserRepository;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * The tenant-ownership facade other modules use to validate an evidence reference. It answers only
 * "is this evidence in this organization", never anything about the evidence itself, so a foreign
 * and an unknown identifier are indistinguishable to the caller.
 */
@SpringBootTest
@Transactional
@Import(TestcontainersConfiguration.class)
class EvidenceOwnershipServiceTest {

  @Autowired EvidenceOwnership evidenceOwnership;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired UserRepository users;
  @Autowired InspectionRepository inspections;

  private InspectionTestFixture.Data fixture;

  @BeforeEach
  void setUp() {
    fixture = new InspectionTestFixture(users, inspections, jdbcTemplate).create();
  }

  @Test
  void recognisesEvidenceOwnedByTheCallingOrganization() {
    UUID evidenceId = insertEvidence(fixture.organizationId(), fixture.inspectionId(), "own.png");

    assertThat(evidenceOwnership.existsInOrganization(evidenceId, fixture.organizationId()))
        .isTrue();
  }

  @Test
  void deniesEvidenceOwnedByAnotherOrganization() {
    // Fully coherent foreign lineage: the evidence, its inspection and its asset all belong to the
    // other tenant, so a true result here could only come from a check that ignores organization.
    UUID foreignInspectionId = createInspection(fixture.otherOrganizationId());
    UUID foreignEvidenceId =
        insertEvidence(fixture.otherOrganizationId(), foreignInspectionId, "foreign.png");

    assertThat(evidenceOwnership.existsInOrganization(foreignEvidenceId, fixture.organizationId()))
        .isFalse();
    assertThat(
            evidenceOwnership.existsInOrganization(
                foreignEvidenceId, fixture.otherOrganizationId()))
        .isTrue();
  }

  @Test
  void deniesAnEvidenceIdentifierThatDoesNotExist() {
    assertThat(evidenceOwnership.existsInOrganization(UUID.randomUUID(), fixture.organizationId()))
        .isFalse();
  }

  private UUID createInspection(UUID organizationId) {
    UUID categoryId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO asset_categories (id, code, name, description, active)
        VALUES (?, ?, 'Bridge', 'Evidence ownership fixture category', TRUE)
        """,
        categoryId,
        upperCode("CAT"));

    UUID assetId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO assets (id, organization_id, category_id, asset_code, name, location, status,
                            created_at, updated_at, row_version)
        VALUES (?, ?, ?, ?, 'Foreign span', '{"label":"District 9"}', 'ACTIVE',
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
        """,
        assetId,
        organizationId,
        categoryId,
        upperCode("AST"));

    return inspections
        .saveAndFlush(
            new Inspection(
                organizationId,
                assetId,
                null,
                null,
                null,
                null,
                "mf3-" + UUID.randomUUID(),
                "Foreign inspection",
                null,
                null,
                null,
                null,
                null))
        .getId();
  }

  private UUID insertEvidence(UUID organizationId, UUID inspectionId, String fileName) {
    UUID evidenceId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO evidence (id, inspection_id,
                              file_name, content_type, size_bytes, object_key,
                              created_at)
        VALUES (?, ?, ?, 'image/png', 1, ?,
                CURRENT_TIMESTAMP)
        """,
        evidenceId,
        inspectionId,
        fileName,
        "credentials-test/" + evidenceId);
    return evidenceId;
  }

  /** Codes are stored normalized: the schema rejects anything that is not upper case. */
  private static String upperCode(String prefix) {
    return (prefix + UUID.randomUUID()).toUpperCase(Locale.ROOT);
  }
}
