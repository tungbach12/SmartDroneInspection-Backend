package com.smartdroneinspection.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.TestcontainersConfiguration;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Applies the V19 (drop dead peer_reviews) and V20 (dispute_tickets) cut-list migrations over a
 * database populated with pre-V19 rows, and exercises the fail-closed pre-check and the new CHECK
 * vocabulary.
 *
 * <p>Every case runs against its own throwaway database created inside the shared Testcontainers
 * PostgreSQL instance, so no case can see another case's rows: the suite is order-independent by
 * construction, and the databases are dropped in {@code @AfterEach}. Nothing is seeded into the
 * application's own schema.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CutListMigrationOnPopulatedDatabaseTest {

  @SuppressWarnings("rawtypes")
  @Autowired
  PostgreSQLContainer container;

  private final List<String> createdDatabases = new ArrayList<>();

  @AfterEach
  void dropCreatedDatabases() {
    for (String database : createdDatabases) {
      try (Connection connection = openAdminConnection();
          Statement statement = connection.createStatement()) {
        statement.execute("DROP DATABASE IF EXISTS " + database + " WITH (FORCE)");
      } catch (SQLException ignored) {
        // A leftover scratch database never affects another case: each case uses its own name.
      }
    }
    createdDatabases.clear();
  }

  /**
   * V19 is a guarded DROP: with a peer_reviews row present the migration must abort naming that row
   * (nothing is destroyed), and once the row is archived away the same migration must succeed,
   * removing the table while every other seeded row survives with its own values.
   */
  @Test
  void v19RefusesToDropAPopulatedPeerReviewsTableAndThenDropsAnEmptyOne() throws Exception {
    String url = freshDatabase("peer");
    migrate(url, "18");

    PeerChain chain;
    try (Connection connection = DriverManager.getConnection(url, user(), password());
        Statement statement = connection.createStatement()) {
      chain = seedReportChainWithPeerReview(statement);

      // The pre-check fires and names the offending row instead of dropping it.
      assertThatThrownBy(() -> migrate(url, "19"))
          .isInstanceOf(FlywayException.class)
          .hasMessageContaining("V19 precheck failed")
          .hasMessageContaining(chain.peerReviewId().toString());

      // The failed run left the database exactly where it was: table still there, history at V18,
      // the seeded row untouched.
      List<String> versions = appliedVersions(statement);
      assertThat(lastOf(versions)).isEqualTo("18");
      assertThat(
              count(
                  statement,
                  "SELECT count(*) FROM peer_reviews WHERE id = '" + chain.peerReviewId() + "'"))
          .isEqualTo(1);
    }

    // Remediation as documented in V19: archive the row, then rerun.
    try (Connection connection = DriverManager.getConnection(url, user(), password());
        Statement statement = connection.createStatement()) {
      statement.execute("DELETE FROM peer_reviews");

      migrate(url, null);

      assertThat(lastOf(appliedVersions(statement))).isEqualTo("22");
      List<String> tables =
          queryStrings(
              statement,
              "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'");
      assertThat(tables).doesNotContain("peer_reviews").contains("dispute_tickets");

      // Legacy rows that had nothing to do with the cut survive with their own values.
      assertThat(
              count(
                  statement,
                  "SELECT count(*) FROM report_versions WHERE id = '"
                      + chain.reportVersionId()
                      + "'"))
          .isEqualTo(1);
      assertThat(
              singleString(
                  statement,
                  "SELECT status FROM inspection_service_orders WHERE id = '"
                      + chain.orderId()
                      + "'"))
          .isEqualTo("CONFIRMED");
      assertThat(
              singleString(
                  statement,
                  "SELECT role FROM user_roles WHERE user_id = '" + chain.inspectorId() + "'"))
          .isEqualTo("INSPECTOR");
      assertThat(
              singleString(
                  statement,
                  "SELECT name FROM organizations WHERE id = '" + chain.organizationId() + "'"))
          .isEqualTo("CutList customer");
    }
  }

  /** V20 applies over a populated database, preserving every pre-existing row. */
  @Test
  void v20AppliesOverSeededRowsAndLeavesThemUntouched() throws Exception {
    String url = freshDatabase("dispute_rows");
    migrate(url, "19");

    DisputeParties parties;
    try (Connection connection = DriverManager.getConnection(url, user(), password());
        Statement statement = connection.createStatement()) {
      parties = seedDisputeParties(statement);

      migrate(url, null);

      assertThat(lastOf(appliedVersions(statement))).isEqualTo("22");
      assertThat(
              singleString(
                  statement,
                  "SELECT name FROM organizations WHERE id = '" + parties.organizationId() + "'"))
          .isEqualTo("CutList customer");
      assertThat(
              singleString(
                  statement,
                  "SELECT status FROM provider_organizations WHERE id = '"
                      + parties.providerId()
                      + "'"))
          .isEqualTo("PENDING");
      assertThat(
              singleString(
                  statement, "SELECT email FROM users WHERE id = '" + parties.raiserId() + "'"))
          .isEqualTo("cutlist-raiser@legacy.test");
      // No money-custody vocabulary exists anywhere in the final schema.
      assertThat(
              count(
                  statement,
                  "SELECT count(*) FROM information_schema.columns"
                      + " WHERE table_schema = 'public'"
                      + " AND (column_name ILIKE '%escrow%'"
                      + " OR column_name ILIKE '%advance%fund%'"
                      + " OR column_name ILIKE '%funding%'"
                      + " OR column_name ILIKE '%retention%'"
                      + " OR column_name ILIKE '%holdback%')"))
          .isEqualTo(0);
    }
  }

  /** The full complaint vocabulary of database-design.md section 6.5, refusals included. */
  @Test
  void enforcesTheDisputeTicketContract() throws Exception {
    String url = freshDatabase("dispute_vocabulary");
    migrate(url, "19");

    DisputeParties parties;
    try (Connection connection = DriverManager.getConnection(url, user(), password());
        Statement statement = connection.createStatement()) {
      parties = seedDisputeParties(statement);
      migrate(url, null);

      String validEvidence =
          "[{\"evidence_type\": \"MINIO_IMAGE\", \"object_key\": \"evidence/before-1.jpg\", "
              + "\"checksum_sha256\": \""
              + "a".repeat(64)
              + "\", \"uploaded_by_user_id\": \""
              + parties.raiserId()
              + "\", \"description\": \"Original shot\", \"created_at\": \"2026-10-06T09:00:00Z\"}]";

      // A well-formed open case with folded evidence is accepted.
      DisputeRow openCase = newRow(parties);
      statement.execute(openCase.sql("OPENED", validEvidence));

      // A fully resolved case carries decision, operator, instant and notes together.
      DisputeRow resolvedCase = newRow(parties);
      statement.execute(
          resolvedCase
              .sql("RESOLVED", null)
              .replaceFirst(
                  ", NULL, NULL, NULL, NULL, NULL, NULL\\)",
                  ", 'FREE_RESHOOT', '"
                      + parties.operatorId()
                      + "', CURRENT_TIMESTAMP, 'Reshoot required at no cost', NULL, NULL)"));

      // Exactly one case per dispute number, whatever the id.
      assertRefuses(
          statement,
          newRow(parties).sql("OPENED", null, openCase.disputeNumber),
          "uq_dispute_tickets_number");

      // Vocabulary refusals, each naming the constraint that refuses it.
      assertRefuses(
          statement,
          newRow(parties).sql("OPENED", null).replace("'INSPECTION'", "'ESCROW'"),
          "ck_dispute_tickets_order_type");
      assertRefuses(
          statement, newRow(parties).sql("ARBITRATING", null), "ck_dispute_tickets_status");
      assertRefuses(
          statement,
          newRow(parties).sql("OPENED", null).replace("'QUALITY_DEFECT'", "'WRONG_PLANET'"),
          "ck_dispute_tickets_category");
      assertRefuses(
          statement,
          newRow(parties).sql("OPENED", null).replace("'Legacy defect scope'", "'   '"),
          "ck_dispute_tickets_reason");

      // Resolution substance on a case that is still open: the outcome has not been taken.
      DisputeRow substanceOnOpen = newRow(parties);
      assertRefuses(
          statement,
          substanceOnOpen
              .sql("OPENED", null)
              .replaceFirst(
                  ", NULL, NULL, NULL, NULL, NULL, NULL\\)",
                  ", 'FREE_RESHOOT', NULL, NULL, 'Reshoot required', NULL, NULL)"),
          "ck_dispute_tickets_resolution_state");

      // A half-recorded resolution (instant without operator) is refused even on a closed case.
      DisputeRow halfResolved = newRow(parties);
      assertRefuses(
          statement,
          halfResolved
              .sql("CLOSED", null)
              .replaceFirst(
                  ", NULL, NULL, NULL, NULL, NULL, NULL\\)",
                  ", 'REJECTED_DISPUTE', NULL, CURRENT_TIMESTAMP, 'Closed', NULL, NULL)"),
          "ck_dispute_tickets_resolution_pair");

      // An unknown decision code is refused even when the rest of the resolution is complete.
      DisputeRow badDecision = newRow(parties);
      assertRefuses(
          statement,
          badDecision
              .sql("RESOLVED", null)
              .replaceFirst(
                  ", NULL, NULL, NULL, NULL, NULL, NULL\\)",
                  ", 'FULL_PENALTY', '"
                      + parties.operatorId()
                      + "', CURRENT_TIMESTAMP, 'Notes', NULL, NULL)"),
          "ck_dispute_tickets_resolution_decision");

      // Evidence is an array, never a bare object.
      assertRefuses(statement, newRow(parties).sql("OPENED", "{}"), "ck_dispute_tickets_evidence");

      // The raiser must be a real user.
      DisputeRow badRaiser = newRow(parties);
      assertRefuses(
          statement,
          badRaiser.sql("OPENED", null).replace(badRaiser.raiserId, UUID.randomUUID().toString()),
          "dispute_tickets_raised_by_user_id_fkey");

      // The client organization must be a real organization.
      DisputeRow badClientOrg = newRow(parties);
      assertRefuses(
          statement,
          badClientOrg
              .sql("OPENED", null)
              .replace(badClientOrg.clientOrganizationId, UUID.randomUUID().toString()),
          "dispute_tickets_client_organization_id_fkey");

      // The two accepted rows are exactly as written.
      assertThat(
              count(
                  statement,
                  "SELECT count(*) FROM dispute_tickets WHERE status IN ('OPENED', 'RESOLVED')"))
          .isEqualTo(2);
    }
  }

  /**
   * The final table inventory: database-design.md section 2 declares 44 application tables plus the
   * Spring Modulith event_publication infrastructure table. Anything else - and the removed
   * peer_reviews in particular - fails here loudly rather than drifting silently.
   */
  @Test
  void finalSchemaIsFortyFourApplicationTablesPlusEventPublication() throws Exception {
    String url = freshDatabase("count");
    migrate(url, null);

    try (Connection connection = DriverManager.getConnection(url, user(), password());
        Statement statement = connection.createStatement()) {
      int total =
          count(
              statement,
              "SELECT count(*) FROM information_schema.tables"
                  + " WHERE table_schema = 'public' AND table_type = 'BASE TABLE'"
                  + " AND table_name <> 'flyway_schema_history'");
      int infrastructure =
          count(
              statement,
              "SELECT count(*) FROM information_schema.tables"
                  + " WHERE table_schema = 'public' AND table_type = 'BASE TABLE'"
                  + " AND table_name = 'event_publication'");

      assertThat(infrastructure).isEqualTo(1);
      assertThat(total - infrastructure)
          .as("application tables (database-design.md section 2 declares 44)")
          .isEqualTo(44);
      assertThat(total).isEqualTo(45);
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Seeding
  // ---------------------------------------------------------------------------------------------

  private record PeerChain(
      UUID organizationId,
      UUID inspectorId,
      UUID orderId,
      UUID reportVersionId,
      UUID peerReviewId) {}

  private record DisputeParties(
      UUID organizationId, UUID providerId, UUID raiserId, UUID operatorId) {}

  private record DisputeRow(
      String disputeNumber, String raiserId, String clientOrganizationId, String sql) {
    String sql(String status, String evidenceJson) {
      return sql(status, evidenceJson, disputeNumber);
    }

    String sql(String status, String evidenceJson, String number) {
      return sql.replace("'<NUMBER>'", "'" + number + "'")
          .replace("'<STATUS>'", "'" + status + "'")
          .replace("<EVIDENCE>", evidenceJson == null ? "NULL" : "'" + evidenceJson + "'::jsonb");
    }
  }

  private static DisputeRow newRow(DisputeParties parties) {
    UUID id = UUID.randomUUID();
    String sql =
        "INSERT INTO dispute_tickets (id, dispute_number, order_id, order_type,"
            + " raised_by_user_id, client_organization_id, provider_organization_id, category,"
            + " reason, client_claim, provider_response, status, resolution_decision,"
            + " resolved_by_operator_id, resolved_at, resolution_notes, penalty_amount, evidence)"
            + " VALUES ('"
            + id
            + "', '<NUMBER>', '"
            + UUID.randomUUID()
            + "', 'INSPECTION', '"
            + parties.raiserId()
            + "', '"
            + parties.organizationId()
            + "', '"
            + parties.providerId()
            + "', 'QUALITY_DEFECT', 'Legacy defect scope', NULL, NULL, '<STATUS>',"
            + " NULL, NULL, NULL, NULL, NULL, <EVIDENCE>)";
    return new DisputeRow(
        "DSP-2026-" + id.toString().substring(0, 8),
        parties.raiserId().toString(),
        parties.organizationId().toString(),
        sql);
  }

  /** Minimal but fully foreign-keyed chain from organizations down to one PENDING peer review. */
  private PeerChain seedReportChainWithPeerReview(Statement statement) throws SQLException {
    UUID organizationId = UUID.randomUUID();
    statement.execute(
        "INSERT INTO organizations (id, name, code) VALUES ('"
            + organizationId
            + "', 'CutList customer', 'CUTLIST-ORG')");

    UUID inspectorId = insertUser(statement, "cutlist-inspector", "SERVICE_WORKFORCE", null);
    UUID reviewerId = insertUser(statement, "cutlist-reviewer", "SERVICE_WORKFORCE", null);
    statement.execute(
        "INSERT INTO user_roles (id, user_id, role) VALUES (gen_random_uuid(), '"
            + inspectorId
            + "', 'INSPECTOR')");

    UUID categoryId = UUID.randomUUID();
    statement.execute(
        "INSERT INTO asset_categories (id, code, name) VALUES ('"
            + categoryId
            + "', 'CUTLIST-CAT', 'CutList category')");
    UUID templateId = UUID.randomUUID();
    statement.execute(
        "INSERT INTO checklist_templates (id, template_key, version_number, name, status,"
            + " created_by_user_id, published_at) VALUES ('"
            + templateId
            + "', 'CUTLIST-TPL', 1, 'CutList checklist', 'ACTIVE', '"
            + inspectorId
            + "', CURRENT_TIMESTAMP)");
    UUID assetId = UUID.randomUUID();
    statement.execute(
        "INSERT INTO assets (id, organization_id, category_id, code, name, location_text, status,"
            + " created_by_user_id) VALUES ('"
            + assetId
            + "', '"
            + organizationId
            + "', '"
            + categoryId
            + "', 'CUTLIST-ASSET', 'CutList asset', 'CutList site', 'ACTIVE', '"
            + inspectorId
            + "')");

    UUID requestId = UUID.randomUUID();
    statement.execute(
        "INSERT INTO inspection_requests (id, organization_id, asset_id, checklist_template_id,"
            + " request_type, scope, priority, status) VALUES ('"
            + requestId
            + "', '"
            + organizationId
            + "', '"
            + assetId
            + "', '"
            + templateId
            + "', 'AD_HOC', 'CutList scope', 'NORMAL', 'ORDER_CONFIRMED')");

    UUID quotationId = UUID.randomUUID();
    statement.execute(
        "INSERT INTO inspection_quotations (id, quotation_series_id, inspection_request_id,"
            + " version_number, prepared_by_user_id, currency, subtotal, tax_amount, total_amount,"
            + " pricing_details, scope_snapshot, payment_terms, status, decided_by_user_id,"
            + " decided_at) VALUES ('"
            + quotationId
            + "', gen_random_uuid(), '"
            + requestId
            + "', 1, '"
            + inspectorId
            + "', 'VND', 1000.00, 100.00, 1100.00, '{}'::jsonb, '{}'::jsonb, 'CutList terms',"
            + " 'APPROVED', '"
            + inspectorId
            + "', CURRENT_TIMESTAMP)");

    UUID orderId = UUID.randomUUID();
    statement.execute(
        "INSERT INTO inspection_service_orders (id, order_number, approved_quotation_id,"
            + " inspection_request_id, confirmed_by_user_id, confirmed_at, scope_snapshot,"
            + " deliverables, payment_terms, status) VALUES ('"
            + orderId
            + "', 'CUTLIST-ORDER-1', '"
            + quotationId
            + "', '"
            + requestId
            + "', '"
            + inspectorId
            + "', CURRENT_TIMESTAMP, '{}'::jsonb, '{}'::jsonb, 'CutList terms', 'CONFIRMED')");

    UUID assignmentId = UUID.randomUUID();
    statement.execute(
        "INSERT INTO inspection_assignments (id, service_order_id, inspector_user_id,"
            + " assigned_by_user_id, status) VALUES ('"
            + assignmentId
            + "', '"
            + orderId
            + "', '"
            + inspectorId
            + "', '"
            + inspectorId
            + "', 'PENDING')");

    UUID inspectionId = UUID.randomUUID();
    statement.execute(
        "INSERT INTO inspections (id, service_order_id, accepted_assignment_id, asset_id,"
            + " author_user_id, checklist_template_id, status) VALUES ('"
            + inspectionId
            + "', '"
            + orderId
            + "', '"
            + assignmentId
            + "', '"
            + assetId
            + "', '"
            + inspectorId
            + "', '"
            + templateId
            + "', 'READY_FOR_INSPECTION')");

    UUID reportId = UUID.randomUUID();
    statement.execute(
        "INSERT INTO inspection_reports (id, inspection_id, author_user_id, status,"
            + " current_version_number) VALUES ('"
            + reportId
            + "', '"
            + inspectionId
            + "', '"
            + inspectorId
            + "', 'DRAFT', 0)");

    UUID reportVersionId = UUID.randomUUID();
    statement.execute(
        "INSERT INTO report_versions (id, report_id, version_number, created_by_user_id,"
            + " content_snapshot, status) VALUES ('"
            + reportVersionId
            + "', '"
            + reportId
            + "', 1, '"
            + inspectorId
            + "', '{}'::jsonb, 'DRAFT')");

    UUID peerReviewId = UUID.randomUUID();
    statement.execute(
        "INSERT INTO peer_reviews (id, report_version_id, reviewer_user_id, assigned_by_user_id,"
            + " assigned_at, decision) VALUES ('"
            + peerReviewId
            + "', '"
            + reportVersionId
            + "', '"
            + reviewerId
            + "', '"
            + inspectorId
            + "', CURRENT_TIMESTAMP, 'PENDING')");

    return new PeerChain(organizationId, inspectorId, orderId, reportVersionId, peerReviewId);
  }

  private DisputeParties seedDisputeParties(Statement statement) throws SQLException {
    UUID organizationId = UUID.randomUUID();
    statement.execute(
        "INSERT INTO organizations (id, name, code) VALUES ('"
            + organizationId
            + "', 'CutList customer', 'CUTLIST-ORG-')");

    UUID providerId = UUID.randomUUID();
    statement.execute(
        "INSERT INTO provider_organizations (id, name, legal_name, tax_code,"
            + " business_license_no, status) VALUES ('"
            + providerId
            + "', 'CutList provider', 'CutList provider Ltd', 'TAX-CUTLIST-', 'BL-CUTLIST-',"
            + " 'PENDING')");

    UUID raiserId =
        insertUser(statement, "cutlist-raiser", "CUSTOMER_ORGANIZATION", organizationId);
    UUID operatorId = insertUser(statement, "cutlist-operator", "PLATFORM", null);

    return new DisputeParties(organizationId, providerId, raiserId, operatorId);
  }

  private static void assertRefuses(Statement statement, String sql, String constraintName) {
    assertThatThrownBy(() -> statement.execute(sql))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining(constraintName);
  }

  private static UUID insertUser(
      Statement statement, String label, String zone, UUID organizationId) throws SQLException {
    UUID id = UUID.randomUUID();
    statement.execute(
        "INSERT INTO users (id, email, normalized_email, full_name, status, actor_zone,"
            + " organization_id) VALUES ('"
            + id
            + "', '"
            + label
            + "@legacy.test', '"
            + label
            + "@legacy.test', 'CutList "
            + label
            + "', 'ACTIVE', '"
            + zone
            + "', "
            + (organizationId == null ? "NULL" : "'" + organizationId + "'")
            + ")");
    return id;
  }

  private static String lastOf(List<String> values) {
    return values.get(values.size() - 1);
  }

  // ---------------------------------------------------------------------------------------------
  // Harness (same shape as DirectTransferSchemaMigrationTest: per-case throwaway databases)
  // ---------------------------------------------------------------------------------------------

  private String freshDatabase(String suffix) throws SQLException {
    String database = "cut_" + suffix + "_" + UUID.randomUUID().toString().substring(0, 8);
    try (Connection connection = openAdminConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE DATABASE " + database);
    }
    createdDatabases.add(database);
    return jdbcUrl(database);
  }

  private Connection openAdminConnection() throws SQLException {
    return DriverManager.getConnection(
        container.getJdbcUrl(), container.getUsername(), container.getPassword());
  }

  private String jdbcUrl(String database) {
    return "jdbc:postgresql://"
        + container.getHost()
        + ":"
        + container.getFirstMappedPort()
        + "/"
        + database;
  }

  private String user() {
    return container.getUsername();
  }

  private String password() {
    return container.getPassword();
  }

  private void migrate(String url, String target) {
    Flyway.configure()
        .dataSource(url, user(), password())
        .locations("classpath:db/migration")
        .target(target == null ? null : MigrationVersion.fromVersion(target))
        .load()
        .migrate();
  }

  private static int count(Statement statement, String sql) throws SQLException {
    try (ResultSet resultSet = statement.executeQuery(sql)) {
      resultSet.next();
      return resultSet.getInt(1);
    }
  }

  private static List<String> appliedVersions(Statement statement) throws SQLException {
    return queryStrings(
        statement,
        "SELECT version FROM flyway_schema_history WHERE success = TRUE ORDER BY installed_rank");
  }

  private static List<String> queryStrings(Statement statement, String sql) throws SQLException {
    List<String> values = new ArrayList<>();
    try (ResultSet resultSet = statement.executeQuery(sql)) {
      while (resultSet.next()) {
        values.add(resultSet.getString(1));
      }
    }
    return values;
  }

  private static String singleString(Statement statement, String sql) throws SQLException {
    try (ResultSet resultSet = statement.executeQuery(sql)) {
      return resultSet.next() ? resultSet.getString(1) : null;
    }
  }
}
