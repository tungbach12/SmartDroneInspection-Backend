package com.smartdroneinspection.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.tuple;

import com.smartdroneinspection.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.core.api.output.ValidateResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class V3Wf1ColumnDropUpgradeMigrationTest {

  private static final String MIGRATION_LOCATIONS = "classpath:db/migration";
  private static final String FIRST_DOCUMENT_CHECKSUM = "a".repeat(64);
  private static final String SECOND_DOCUMENT_CHECKSUM = "b".repeat(64);

  @Autowired PostgreSQLContainer postgresContainer;
  @Autowired JdbcTemplate publicSchemaJdbcTemplate;

  @Test
  void upgradesPopulatedV2SchemaThroughV3WhileRetainingWf1RowsFilesAndFlywayChecksums() {
    String upgradeSchema =
        "wf1_v3_upgrade_" + UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.ROOT);
    DriverManagerDataSource upgradeDataSource =
        new DriverManagerDataSource(
            postgresUrlForSchema(upgradeSchema),
            postgresContainer.getUsername(),
            postgresContainer.getPassword());
    JdbcTemplate upgradeJdbcTemplate = new JdbcTemplate(upgradeDataSource);

    try {
      Flyway targetV2Flyway =
          Flyway.configure()
              .dataSource(upgradeDataSource)
              .locations(MIGRATION_LOCATIONS)
              .defaultSchema(upgradeSchema)
              .schemas(upgradeSchema)
              .createSchemas(true)
              .target(MigrationVersion.fromVersion("2"))
              .load();

      MigrateResult v2Migration = targetV2Flyway.migrate();
      assertThat(v2Migration.migrationsExecuted).isEqualTo(2);
      assertThat(v2Migration.targetSchemaVersion).isEqualTo("2");
      assertThatCode(targetV2Flyway::validate).doesNotThrowAnyException();

      ValidateResult v2Validation = targetV2Flyway.validateWithResult();
      assertThat(v2Validation.validationSuccessful)
          .as("Flyway validation at target 2: %s", v2Validation.invalidMigrations)
          .isTrue();
      assertThat(v2Validation.invalidMigrations).isEmpty();

      List<HistoryRecord> historyAtV2 = flywayHistory(upgradeJdbcTemplate);
      List<HistoryRecord> publicBaselineHistory =
          flywayHistory(publicSchemaJdbcTemplate).stream()
              .filter(record -> record.version().equals("1") || record.version().equals("2"))
              .toList();
      assertThat(historyAtV2)
          .extracting(
              HistoryRecord::version,
              HistoryRecord::script,
              HistoryRecord::checksum,
              HistoryRecord::success)
          .containsExactly(
              tuple("1", "V1__baseline.sql", publicBaselineHistory.get(0).checksum(), true),
              tuple(
                  "2",
                  "V2__role_restructure_and_drone_leasing.sql",
                  publicBaselineHistory.get(1).checksum(),
                  true));

      assertThat(columnsOf(upgradeJdbcTemplate, upgradeSchema, "asset_categories"))
          .contains("organization_id");
      assertThat(columnsOf(upgradeJdbcTemplate, upgradeSchema, "assets")).contains("asset_type");
      assertThat(columnsOf(upgradeJdbcTemplate, upgradeSchema, "asset_documents"))
          .contains("document_version", "status", "updated_at");
      assertThat(constraintsOf(upgradeJdbcTemplate, upgradeSchema))
          .contains("asset_categories_organization_id_fkey");

      SeededWf1Data seeded = seedV2Wf1FixtureData(upgradeJdbcTemplate);

      Flyway v3UpgradeFlyway =
          Flyway.configure()
              .dataSource(upgradeDataSource)
              .locations(MIGRATION_LOCATIONS)
              .defaultSchema(upgradeSchema)
              .schemas(upgradeSchema)
              .createSchemas(false)
              .load();

      MigrateResult v3Migration = v3UpgradeFlyway.migrate();
      assertThat(v3Migration.migrationsExecuted).isEqualTo(1);
      assertThat(v3Migration.targetSchemaVersion).isEqualTo("3");
      assertThat(v3UpgradeFlyway.info().current().getVersion())
          .isEqualTo(MigrationVersion.fromVersion("3"));
      assertThat(v3UpgradeFlyway.info().pending()).isEmpty();
      assertThat(v3UpgradeFlyway.info().applied())
          .filteredOn(info -> info.getVersion() != null)
          .extracting(
              info -> info.getVersion().getVersion(),
              MigrationInfo::getScript,
              MigrationInfo::getState)
          .containsExactly(
              tuple("1", "V1__baseline.sql", MigrationState.SUCCESS),
              tuple("2", "V2__role_restructure_and_drone_leasing.sql", MigrationState.SUCCESS),
              tuple("3", "V3__drop_unused_wf1_columns.sql", MigrationState.SUCCESS));

      assertThatCode(v3UpgradeFlyway::validate).doesNotThrowAnyException();
      ValidateResult v3Validation = v3UpgradeFlyway.validateWithResult();
      assertThat(v3Validation.validationSuccessful)
          .as("Flyway validation after V3 upgrade: %s", v3Validation.invalidMigrations)
          .isTrue();
      assertThat(v3Validation.invalidMigrations).isEmpty();

      List<HistoryRecord> historyAtV3 = flywayHistory(upgradeJdbcTemplate);
      assertThat(historyAtV3)
          .hasSize(3)
          .allSatisfy(
              record -> {
                assertThat(record.checksum()).isNotNull();
                assertThat(record.success()).isTrue();
              });
      assertThat(historyAtV3.subList(0, 2)).containsExactlyElementsOf(historyAtV2);
      assertThat(historyAtV3.get(2))
          .extracting(HistoryRecord::version, HistoryRecord::script, HistoryRecord::success)
          .containsExactly("3", "V3__drop_unused_wf1_columns.sql", true);

      Set<String> categoryColumns =
          columnsOf(upgradeJdbcTemplate, upgradeSchema, "asset_categories");
      Set<String> assetColumns = columnsOf(upgradeJdbcTemplate, upgradeSchema, "assets");
      Set<String> documentColumns =
          columnsOf(upgradeJdbcTemplate, upgradeSchema, "asset_documents");
      Set<String> constraints = constraintsOf(upgradeJdbcTemplate, upgradeSchema);

      assertThat(categoryColumns)
          .containsExactlyInAnyOrder(
              "id",
              "code",
              "name",
              "description",
              "active",
              "created_at",
              "updated_at",
              "row_version")
          .doesNotContain("organization_id");
      assertThat(assetColumns)
          .containsExactlyInAnyOrder(
              "id",
              "organization_id",
              "category_id",
              "asset_code",
              "name",
              "description",
              "location",
              "latitude",
              "longitude",
              "technical_profile",
              "status",
              "created_at",
              "updated_at",
              "row_version")
          .doesNotContain("asset_type");
      assertThat(documentColumns)
          .containsExactlyInAnyOrder(
              "id",
              "asset_id",
              "uploaded_by_user_id",
              "document_type",
              "file_name",
              "content_type",
              "size_bytes",
              "checksum_sha256",
              "object_key",
              "document_date",
              "created_at")
          .doesNotContain("document_version", "status", "updated_at");
      assertThat(constraints)
          .doesNotContain("asset_categories_organization_id_fkey")
          .contains(
              "assets_category_id_fkey",
              "assets_organization_id_fkey",
              "asset_documents_asset_id_fkey",
              "asset_documents_uploaded_by_user_id_fkey");

      assertRetainedData(upgradeJdbcTemplate, seeded);
    } finally {
      publicSchemaJdbcTemplate.execute("DROP SCHEMA IF EXISTS " + upgradeSchema + " CASCADE");
    }
  }

  private SeededWf1Data seedV2Wf1FixtureData(JdbcTemplate jdbcTemplate) {
    UUID organizationId = UUID.randomUUID();
    String orgSuffix = organizationId.toString();
    jdbcTemplate.update(
        """
        INSERT INTO organizations (id, legal_name, display_name, registration_code, timezone, status)
        VALUES (?, ?, ?, ?, 'Asia/Ho_Chi_Minh', 'ACTIVE')
        """,
        organizationId,
        "Organization " + orgSuffix,
        "Organization " + orgSuffix,
        "ORG-" + orgSuffix);

    UUID userId = UUID.randomUUID();
    String email = "asset-user-" + userId + "@example.test";
    jdbcTemplate.update(
        """
        INSERT INTO users (
          id, email, normalized_email, full_name, status, actor_zone,
          organization_id, auth_version, failed_login_count, must_change_password, roles
        ) VALUES (?, ?, ?, ?, 'ACTIVE', 'CUSTOMER_ORGANIZATION', ?, 0, 0, FALSE, 'ORG_MANAGER')
        """,
        userId,
        email,
        email,
        "Asset User",
        organizationId);

    Instant timestamp = Instant.parse("2026-09-18T08:15:30Z");
    UUID scopedCategoryId = UUID.randomUUID();
    String scopedCategoryCode = "CATEGORY-" + scopedCategoryId.toString().toUpperCase(Locale.ROOT);
    jdbcTemplate.update(
        """
        INSERT INTO asset_categories (
          id, code, name, description, active, created_at, updated_at, row_version, organization_id
        ) VALUES (?, ?, 'Bridge', 'Organization-scoped V1 category', TRUE, ?, ?, 2, ?)
        """,
        scopedCategoryId,
        scopedCategoryCode,
        Timestamp.from(timestamp),
        Timestamp.from(timestamp),
        organizationId);

    UUID globalCategoryId = UUID.randomUUID();
    String globalCategoryCode = "CATEGORY-" + globalCategoryId.toString().toUpperCase(Locale.ROOT);
    jdbcTemplate.update(
        """
        INSERT INTO asset_categories (
          id, code, name, description, active, created_at, updated_at, row_version, organization_id
        ) VALUES (?, ?, 'Tower', 'Platform catalog category', FALSE, ?, ?, 5, NULL)
        """,
        globalCategoryId,
        globalCategoryCode,
        Timestamp.from(timestamp),
        Timestamp.from(timestamp));

    UUID assetId = UUID.randomUUID();
    String assetCode = "BRIDGE-01";
    jdbcTemplate.update(
        """
        INSERT INTO assets (
          id, organization_id, category_id, asset_code, asset_type, name, description,
          location, latitude, longitude, technical_profile, status,
          created_at, updated_at, row_version
        ) VALUES (
          ?, ?, ?, ?, 'SUSPENSION_BRIDGE', 'River Bridge', 'Main river crossing',
          '{"label":"District 1"}'::jsonb, 10.776889, 106.700806,
          '{"owner":"HCMC Infrastructure"}'::jsonb, 'ACTIVE',
          ?, ?, 3
        )
        """,
        assetId,
        organizationId,
        scopedCategoryId,
        assetCode,
        Timestamp.from(timestamp),
        Timestamp.from(timestamp));

    UUID firstDocumentId = UUID.randomUUID();
    LocalDate firstDocumentDate = LocalDate.of(2026, 9, 18);
    jdbcTemplate.update(
        """
        INSERT INTO asset_documents (
          id, asset_id, uploaded_by_user_id, document_type, file_name, content_type,
          size_bytes, checksum_sha256, object_key, document_date, created_at,
          document_version, status, updated_at
        ) VALUES (
          ?, ?, ?, 'INSPECTION_REPORT', 'first.pdf', 'application/pdf',
          1024, ?, 'assets/first.pdf', ?, ?,
          'v1.0', 'ACTIVE', ?
        )
        """,
        firstDocumentId,
        assetId,
        userId,
        FIRST_DOCUMENT_CHECKSUM,
        Date.valueOf(firstDocumentDate),
        Timestamp.from(timestamp),
        Timestamp.from(timestamp));

    UUID secondDocumentId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO asset_documents (
          id, asset_id, uploaded_by_user_id, document_type, file_name, content_type,
          size_bytes, checksum_sha256, object_key, document_date, created_at,
          document_version, status, updated_at
        ) VALUES (
          ?, ?, ?, 'BLUEPRINT', 'second.png', 'image/png',
          2048, ?, 'assets/second.png', NULL, ?,
          NULL, NULL, NULL
        )
        """,
        secondDocumentId,
        assetId,
        userId,
        SECOND_DOCUMENT_CHECKSUM,
        Timestamp.from(timestamp));

    return new SeededWf1Data(
        organizationId,
        userId,
        email,
        scopedCategoryId,
        scopedCategoryCode,
        globalCategoryId,
        globalCategoryCode,
        assetId,
        assetCode,
        firstDocumentId,
        firstDocumentDate,
        secondDocumentId,
        timestamp);
  }

  private void assertRetainedData(JdbcTemplate jdbcTemplate, SeededWf1Data seeded) {
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM organizations WHERE id = ? AND registration_code = ?",
                Integer.class,
                seeded.organizationId(),
                "ORG-" + seeded.organizationId()))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ? AND organization_id = ? AND email = ? AND roles = 'ORG_MANAGER'",
                Integer.class,
                seeded.userId(),
                seeded.organizationId(),
                seeded.email()))
        .isEqualTo(1);

    List<CategoryRow> categories =
        jdbcTemplate.query(
            """
            SELECT id, code, name, description, active, created_at, updated_at, row_version
            FROM asset_categories
            ORDER BY name
            """,
            (rs, rowNum) ->
                new CategoryRow(
                    rs.getObject("id", UUID.class),
                    rs.getString("code"),
                    rs.getString("name"),
                    rs.getString("description"),
                    rs.getBoolean("active"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant(),
                    rs.getLong("row_version")));
    assertThat(categories)
        .containsExactly(
            new CategoryRow(
                seeded.scopedCategoryId(),
                seeded.scopedCategoryCode(),
                "Bridge",
                "Organization-scoped V1 category",
                true,
                seeded.timestamp(),
                seeded.timestamp(),
                2L),
            new CategoryRow(
                seeded.globalCategoryId(),
                seeded.globalCategoryCode(),
                "Tower",
                "Platform catalog category",
                false,
                seeded.timestamp(),
                seeded.timestamp(),
                5L));

    List<AssetRow> assets =
        jdbcTemplate.query(
            """
            SELECT id, organization_id, category_id, asset_code, name, description,
                   location->>'label' AS location_label, latitude, longitude,
                   technical_profile->>'owner' AS technical_owner, status,
                   created_at, updated_at, row_version
            FROM assets
            """,
            (rs, rowNum) ->
                new AssetRow(
                    rs.getObject("id", UUID.class),
                    rs.getObject("organization_id", UUID.class),
                    rs.getObject("category_id", UUID.class),
                    rs.getString("asset_code"),
                    rs.getString("name"),
                    rs.getString("description"),
                    rs.getString("location_label"),
                    rs.getBigDecimal("latitude"),
                    rs.getBigDecimal("longitude"),
                    rs.getString("technical_owner"),
                    rs.getString("status"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant(),
                    rs.getLong("row_version")));
    assertThat(assets)
        .containsExactly(
            new AssetRow(
                seeded.assetId(),
                seeded.organizationId(),
                seeded.scopedCategoryId(),
                seeded.assetCode(),
                "River Bridge",
                "Main river crossing",
                "District 1",
                new BigDecimal("10.776889"),
                new BigDecimal("106.700806"),
                "HCMC Infrastructure",
                "ACTIVE",
                seeded.timestamp(),
                seeded.timestamp(),
                3L));

    List<DocumentRow> documents =
        jdbcTemplate.query(
            """
            SELECT id, asset_id, uploaded_by_user_id, document_type, file_name,
                   content_type, size_bytes, checksum_sha256, object_key,
                   document_date, created_at
            FROM asset_documents
            ORDER BY file_name
            """,
            (rs, rowNum) -> {
              Date documentDate = rs.getDate("document_date");
              return new DocumentRow(
                  rs.getObject("id", UUID.class),
                  rs.getObject("asset_id", UUID.class),
                  rs.getObject("uploaded_by_user_id", UUID.class),
                  rs.getString("document_type"),
                  rs.getString("file_name"),
                  rs.getString("content_type"),
                  rs.getLong("size_bytes"),
                  rs.getString("checksum_sha256"),
                  rs.getString("object_key"),
                  documentDate == null ? null : documentDate.toLocalDate(),
                  rs.getTimestamp("created_at").toInstant());
            });
    assertThat(documents)
        .containsExactly(
            new DocumentRow(
                seeded.firstDocumentId(),
                seeded.assetId(),
                seeded.userId(),
                "INSPECTION_REPORT",
                "first.pdf",
                "application/pdf",
                1024L,
                FIRST_DOCUMENT_CHECKSUM,
                "assets/first.pdf",
                seeded.firstDocumentDate(),
                seeded.timestamp()),
            new DocumentRow(
                seeded.secondDocumentId(),
                seeded.assetId(),
                seeded.userId(),
                "BLUEPRINT",
                "second.png",
                "image/png",
                2048L,
                SECOND_DOCUMENT_CHECKSUM,
                "assets/second.png",
                null,
                seeded.timestamp()));
  }

  private String postgresUrlForSchema(String schemaName) {
    String baseUrl = postgresContainer.getJdbcUrl();
    String separator = baseUrl.contains("?") ? "&" : "?";
    return baseUrl + separator + "currentSchema=" + schemaName + ",public";
  }

  private List<HistoryRecord> flywayHistory(JdbcTemplate jdbcTemplate) {
    return jdbcTemplate.query(
        """
        SELECT installed_rank, version, script, checksum, success
        FROM flyway_schema_history
        WHERE version IS NOT NULL
        ORDER BY installed_rank
        """,
        (rs, rowNum) ->
            new HistoryRecord(
                rs.getInt("installed_rank"),
                rs.getString("version"),
                rs.getString("script"),
                (Integer) rs.getObject("checksum"),
                rs.getBoolean("success")));
  }

  private Set<String> columnsOf(JdbcTemplate jdbcTemplate, String schema, String tableName) {
    return jdbcTemplate
        .queryForList(
            """
            SELECT column_name
            FROM information_schema.columns
            WHERE table_schema = ? AND table_name = ?
            """,
            String.class,
            schema,
            tableName)
        .stream()
        .collect(Collectors.toSet());
  }

  private Set<String> constraintsOf(JdbcTemplate jdbcTemplate, String schema) {
    return jdbcTemplate
        .queryForList(
            """
            SELECT c.conname
            FROM pg_constraint c
            JOIN pg_namespace n ON n.oid = c.connamespace
            WHERE n.nspname = ?
            """,
            String.class,
            schema)
        .stream()
        .collect(Collectors.toSet());
  }

  private record HistoryRecord(
      int installedRank, String version, String script, Integer checksum, boolean success) {}

  private record SeededWf1Data(
      UUID organizationId,
      UUID userId,
      String email,
      UUID scopedCategoryId,
      String scopedCategoryCode,
      UUID globalCategoryId,
      String globalCategoryCode,
      UUID assetId,
      String assetCode,
      UUID firstDocumentId,
      LocalDate firstDocumentDate,
      UUID secondDocumentId,
      Instant timestamp) {}

  private record CategoryRow(
      UUID id,
      String code,
      String name,
      String description,
      boolean active,
      Instant createdAt,
      Instant updatedAt,
      long rowVersion) {}

  private record AssetRow(
      UUID id,
      UUID organizationId,
      UUID categoryId,
      String assetCode,
      String name,
      String description,
      String locationLabel,
      BigDecimal latitude,
      BigDecimal longitude,
      String technicalOwner,
      String status,
      Instant createdAt,
      Instant updatedAt,
      long rowVersion) {}

  private record DocumentRow(
      UUID id,
      UUID assetId,
      UUID uploadedByUserId,
      String documentType,
      String fileName,
      String contentType,
      long sizeBytes,
      String checksumSha256,
      String objectKey,
      LocalDate documentDate,
      Instant createdAt) {}
}
