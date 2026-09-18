package com.smartdroneinspection.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.repository.AssetRepository;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AssetPersistenceTest {

  private static final String CHECKSUM = "a".repeat(64);

  @Autowired AssetRepository assetRepository;
  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  void returnsAssetOnlyForItsOrganization() {
    Fixture fixture = persistFixture();
    UUID otherOrganizationId = persistOrganization();
    Asset asset = assetRepository.saveAndFlush(newAsset(fixture, "bridge-01"));

    assertThat(
            assetRepository
                .findByIdAndOrganizationId(asset.getId(), fixture.organizationId())
                .map(Asset::getId))
        .contains(asset.getId());
    assertThat(assetRepository.findByIdAndOrganizationId(asset.getId(), otherOrganizationId))
        .isEmpty();
  }

  @Test
  void allowsSameNormalizedCodeInDifferentOrganizations() {
    Fixture firstFixture = persistFixture();
    Fixture secondFixture = persistFixture();

    Asset first = assetRepository.saveAndFlush(newAsset(firstFixture, "bridge-01"));
    Asset second = assetRepository.saveAndFlush(newAsset(secondFixture, " BRIDGE-01 "));

    assertThat(first.getCode()).isEqualTo("BRIDGE-01");
    assertThat(second.getCode()).isEqualTo("BRIDGE-01");
    assertThat(
            assetRepository.existsByOrganizationIdAndCode(
                firstFixture.organizationId(), "BRIDGE-01"))
        .isTrue();
    assertThat(
            assetRepository.existsByOrganizationIdAndCode(
                secondFixture.organizationId(), "BRIDGE-01"))
        .isTrue();
  }

  @Test
  void rejectsDuplicateCodeWithinOrganization() {
    Fixture fixture = persistFixture();
    assetRepository.saveAndFlush(newAsset(fixture, "bridge-01"));

    assertThatThrownBy(() -> assetRepository.saveAndFlush(newAsset(fixture, " BRIDGE-01 ")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void rejectsDuplicateDocumentChecksumWithinAsset() {
    Fixture fixture = persistFixture();
    Asset asset = newAsset(fixture, "bridge-01");
    asset.addDocument(
        fixture.userId(),
        "INSPECTION_REPORT",
        "first.pdf",
        "application/pdf",
        10,
        CHECKSUM,
        "assets/first.pdf",
        LocalDate.of(2026, 9, 18));
    asset.addDocument(
        fixture.userId(),
        "INSPECTION_REPORT",
        "second.pdf",
        "application/pdf",
        20,
        CHECKSUM,
        "assets/second.pdf",
        LocalDate.of(2026, 9, 18));

    assertThatThrownBy(() -> assetRepository.saveAndFlush(asset))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void allowsSameDocumentChecksumForDifferentAssets() {
    Fixture fixture = persistFixture();
    Asset first = newAsset(fixture, "bridge-01");
    Asset second = newAsset(fixture, "bridge-02");
    first.addDocument(
        fixture.userId(),
        "INSPECTION_REPORT",
        "first.pdf",
        "application/pdf",
        10,
        CHECKSUM,
        "assets/first.pdf",
        null);
    second.addDocument(
        fixture.userId(),
        "INSPECTION_REPORT",
        "second.pdf",
        "application/pdf",
        20,
        CHECKSUM,
        "assets/second.pdf",
        null);

    assetRepository.saveAndFlush(first);
    assetRepository.saveAndFlush(second);

    assertThat(
            assetRepository.findDetailedByIdAndOrganizationId(
                first.getId(), fixture.organizationId()))
        .hasValueSatisfying(asset -> assertThat(asset.getDocuments()).hasSize(1));
    assertThat(
            assetRepository.findDetailedByIdAndOrganizationId(
                second.getId(), fixture.organizationId()))
        .hasValueSatisfying(asset -> assertThat(asset.getDocuments()).hasSize(1));
  }

  private Asset newAsset(Fixture fixture, String code) {
    return new Asset(
        fixture.organizationId(),
        fixture.categoryId(),
        code,
        "River Bridge",
        null,
        "District 1",
        null,
        null,
        null,
        fixture.userId());
  }

  private Fixture persistFixture() {
    UUID organizationId = persistOrganization();
    UUID userId = persistUser(organizationId);
    UUID categoryId = persistCategory();
    return new Fixture(organizationId, userId, categoryId);
  }

  private UUID persistOrganization() {
    UUID organizationId = UUID.randomUUID();
    String suffix = organizationId.toString();
    jdbcTemplate.update(
        "INSERT INTO organizations (id, name, code) VALUES (?, ?, ?)",
        organizationId,
        "Organization " + suffix,
        "ORG-" + suffix);
    return organizationId;
  }

  private UUID persistUser(UUID organizationId) {
    UUID userId = UUID.randomUUID();
    String email = "asset-user-" + userId + "@example.test";
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
        "Asset User",
        organizationId);
    return userId;
  }

  private UUID persistCategory() {
    UUID categoryId = UUID.randomUUID();
    String suffix = categoryId.toString().toUpperCase(Locale.ROOT);
    jdbcTemplate.update(
        "INSERT INTO asset_categories (id, code, name) VALUES (?, ?, ?)",
        categoryId,
        "CATEGORY-" + suffix,
        "Category " + suffix);
    return categoryId;
  }

  private record Fixture(UUID organizationId, UUID userId, UUID categoryId) {}
}
