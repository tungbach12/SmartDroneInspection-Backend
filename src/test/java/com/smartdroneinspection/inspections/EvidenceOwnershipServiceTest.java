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
  void recognisesEvidenceOwnedByTheCallingCustomer() {
    UUID evidenceId = insertEvidence(fixture.inspectionId(), "own.png");

    assertThat(evidenceOwnership.existsOwnedByCustomer(evidenceId, fixture.reviewerId())).isTrue();
  }

  @Test
  void deniesEvidenceOwnedByAnotherCustomer() {
    // Fully coherent foreign lineage: the evidence, its inspection and its asset all belong to the
    // other customer, so a true result here could only come from a check that ignores ownership.
    UUID foreignInspectionId = createInspection(fixture.outsiderId());
    UUID foreignEvidenceId = insertEvidence(foreignInspectionId, "foreign.png");

    assertThat(evidenceOwnership.existsOwnedByCustomer(foreignEvidenceId, fixture.reviewerId()))
        .isFalse();
    assertThat(evidenceOwnership.existsOwnedByCustomer(foreignEvidenceId, fixture.outsiderId()))
        .isTrue();
  }

  @Test
  void deniesAnEvidenceIdentifierThatDoesNotExist() {
    assertThat(evidenceOwnership.existsOwnedByCustomer(UUID.randomUUID(), fixture.reviewerId()))
        .isFalse();
  }

  private UUID createInspection(UUID ownerCustomerId) {
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
        INSERT INTO assets (id, owner_customer_id, category_id, asset_code, name, location, status,
                            created_at, updated_at, row_version)
        VALUES (?, ?, ?, ?, 'Foreign span', '{"label":"District 9"}', 'ACTIVE',
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
        """,
        assetId,
        ownerCustomerId,
        categoryId,
        upperCode("AST"));

    return inspections
        .saveAndFlush(new Inspection(ownerCustomerId, assetId, null, null, null, null))
        .getId();
  }

  private UUID insertEvidence(UUID inspectionId, String fileName) {
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
