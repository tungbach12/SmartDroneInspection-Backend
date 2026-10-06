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
 * Applies the V12..V20 direct-transfer migrations over BOTH an empty database and a database seeded
 * with V11-era rows, and exercises the fail-closed pre-checks.
 *
 * <p>Every case runs against its own throwaway database created inside the shared Testcontainers
 * PostgreSQL instance, so no case can see another case's rows: the suite is order-independent by
 * construction, and the databases are dropped in {@code @AfterEach}. Nothing is seeded into the
 * application's own schema.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DirectTransferSchemaMigrationTest {

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

  @Test
  void emptyDatabaseAppliesEveryMigrationThroughV21() throws Exception {
    String url = freshDatabase("mig_empty");
    migrate(url, null);

    try (Connection connection = DriverManager.getConnection(url, user(), password());
        Statement statement = connection.createStatement()) {
      List<String> versions =
          queryStrings(
              statement,
              "SELECT version FROM flyway_schema_history WHERE success = TRUE ORDER BY installed_rank");
      assertThat(versions)
          .containsExactly(
              "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16",
              "17", "18", "19", "20", "21");

      List<String> tables =
          queryStrings(
              statement,
              "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'");
      assertThat(tables)
          .contains(
              "provider_organizations",
              "provider_capabilities",
              "provider_vetting_decisions",
              "provider_capability_evidence",
              "platform_configurations",
              "drone_mission_plans",
              "mission_shot_items");

      // The canonical six and nothing else, enforced by the CHECK itself rather than by a comment.
      String roleCheck =
          singleString(
              statement,
              """
          SELECT pg_get_constraintdef(oid)
            FROM pg_constraint
           WHERE conname = 'ck_user_roles_role'
          """);
      assertThat(roleCheck)
          .contains(
              "PLATFORM_ADMIN",
              "PLATFORM_OPERATOR",
              "CLIENT",
              "PROVIDER_MANAGER",
              "INSPECTOR",
              "MAINTENANCE_ENGINEER")
          .doesNotContain("'ADMIN'")
          .doesNotContain("SERVICE_MANAGER")
          .doesNotContain("PLATFORM_ADMINISTRATOR");

      // No escrow, advance-funding, funding, hold or retention column exists anywhere in the
      // schema:
      // the platform is never a custodian of money and warranty retention (H) does not exist.
      List<String> forbiddenColumns =
          queryStrings(
              statement,
              """
          SELECT table_name || '.' || column_name
            FROM information_schema.columns
           WHERE table_schema = 'public'
             AND (column_name ILIKE '%escrow%'
                  OR column_name ILIKE '%advance%fund%'
                  OR column_name ILIKE '%funding%'
                  OR column_name ILIKE '%retention%'
                  OR column_name ILIKE '%holdback%')
          """);
      assertThat(forbiddenColumns).isEmpty();

      // The mission tables exist with their clearance vocabulary.
      String airspaceCheck =
          singleString(
              statement,
              """
          SELECT pg_get_constraintdef(oid)
            FROM pg_constraint
           WHERE conname = 'ck_drone_mission_plans_airspace'
          """);
      assertThat(airspaceCheck)
          .contains("NOT_CHECKED", "CLEARANCE_REQUIRED", "MANUAL_REVIEW", "CLEARED", "BLOCKED")
          .doesNotContain("VERIFIED_CLEAR", "PERMIT_APPROVED");
    }
  }

  @Test
  void populatedV11DatabaseSurvivesTheMigrationWithItsRowsAndValues() throws Exception {
    String url = freshDatabase("mig_populated");
    migrate(url, "11");
    LegacyFixture fixture = seedLegacyV11Rows(url);

    migrate(url, null);

    try (Connection connection = DriverManager.getConnection(url, user(), password());
        Statement statement = connection.createStatement()) {
      // Every seeded row survived, with its own values.
      assertThat(count(statement, "SELECT count(*) FROM organizations")).isEqualTo(1);
      assertThat(count(statement, "SELECT count(*) FROM users")).isEqualTo(4);
      assertThat(count(statement, "SELECT count(*) FROM inspection_service_orders")).isEqualTo(1);
      assertThat(count(statement, "SELECT count(*) FROM maintenance_orders")).isEqualTo(1);
      assertThat(count(statement, "SELECT count(*) FROM maintenance_tickets")).isEqualTo(1);
      assertThat(count(statement, "SELECT count(*) FROM invoices")).isEqualTo(1);

      // ADMIN is migrated to the canonical PLATFORM_ADMIN; the other legacy codes are already
      // canonical and are never rewritten.
      List<String> roles =
          queryStrings(statement, "SELECT DISTINCT role FROM user_roles ORDER BY 1");
      assertThat(roles)
          .containsExactlyInAnyOrder(
              "PLATFORM_ADMIN", "CLIENT", "INSPECTOR", "MAINTENANCE_ENGINEER");

      // The canonical CHECK is live, not decorative: the database itself refuses a legacy code.
      assertThat(
              org.assertj.core.api.Assertions.catchThrowable(
                  () ->
                      statement.execute(
                          "INSERT INTO user_roles (id, user_id, role) "
                              + "SELECT gen_random_uuid(), id, 'SERVICE_MANAGER' FROM users LIMIT 1")))
          .hasMessageContaining("ck_user_roles_role");

      // The legacy order keeps its status and holds NULL for every column V12..V18 added.
      assertThat(
              singleString(
                  statement,
                  "SELECT status FROM inspection_service_orders WHERE id = '"
                      + fixture.orderId()
                      + "'"))
          .isEqualTo("CONFIRMED");
      assertThat(
              count(
                  statement,
                  """
          SELECT count(*) FROM inspection_service_orders
           WHERE id = '%s'
             AND locked_commission_rate IS NULL
             AND locked_review_period_days IS NULL
             AND locked_cancellation_policy IS NULL
             AND payment_invoice_issued_at IS NULL
             AND paid_at IS NULL
             AND provider_bank_account_number IS NULL
             AND client_review_ends_at IS NULL
             AND status = 'CONFIRMED'
          """
                      .formatted(fixture.orderId())))
          .isEqualTo(1);

      // The maintenance-only invoice keeps working and is backfilled into the new discriminator.
      assertThat(
              singleString(
                  statement,
                  "SELECT invoice_type FROM invoices WHERE id = '" + fixture.invoiceId() + "'"))
          .isEqualTo("MAINTENANCE_SERVICE");
      assertThat(
              count(
                  statement,
                  """
          SELECT count(*) FROM invoices
           WHERE id = '%s'
             AND maintenance_order_id IS NOT NULL
             AND maintenance_ticket_id IS NOT NULL
             AND inspection_service_order_id IS NULL
             AND provider_organization_id IS NULL
          """
                      .formatted(fixture.invoiceId())))
          .isEqualTo(1);

      // A pre-V12 maintenance order carries NULL warranty terms and an unchanged status.
      assertThat(
              count(
                  statement,
                  """
          SELECT count(*) FROM maintenance_orders
           WHERE id = '%s'
             AND locked_warranty_days IS NULL
             AND warranty_end_date IS NULL
             AND status = 'CONFIRMED'
          """
                      .formatted(fixture.maintenanceOrderId())))
          .isEqualTo(1);

      // New tables are usable in the same database the legacy rows live in.
      assertThat(
              count(
                  statement,
                  """
          INSERT INTO provider_organizations
              (id, name, legal_name, tax_code, business_license_no, status)
          VALUES (gen_random_uuid(), 'Populated-DB provider', 'Populated DB Provider Ltd',
                  'POP-' || floor(random() * 1000000)::text, 'BL-POP-1', 'PENDING')
          RETURNING 1
          """))
          .isEqualTo(1);
    }
  }

  @Test
  void legacyServiceManagerRoleIsRefusedWithRemediationAndRollsBack() throws Exception {
    String url = freshDatabase("mig_service_manager");
    migrate(url, "11");

    UUID ambiguousUserId;
    try (Connection connection = DriverManager.getConnection(url, user(), password());
        Statement statement = connection.createStatement()) {
      ambiguousUserId =
          insertLegacyUser(statement, "legacy-service-manager", "SERVICE_WORKFORCE", null);
      statement.execute(
          "INSERT INTO user_roles (id, user_id, role) VALUES (gen_random_uuid(), '"
              + ambiguousUserId
              + "', 'SERVICE_MANAGER')");
    }

    assertThatThrownBy(() -> migrate(url, null))
        .isInstanceOf(FlywayException.class)
        .hasMessageContaining("V13 precheck failed")
        .hasMessageContaining("SERVICE_MANAGER")
        .hasMessageContaining("PLATFORM_OPERATOR")
        .hasMessageContaining("PROVIDER_MANAGER");

    try (Connection connection = DriverManager.getConnection(url, user(), password());
        Statement statement = connection.createStatement()) {
      // V12 committed on its own transaction, V13 refused and rolled back: no version past 12 is
      // applied, and the ambiguous row is untouched.
      assertThat(appliedVersions(statement)).last().isEqualTo("12");
      assertThat(
              count(
                  statement,
                  """
          SELECT count(*) FROM user_roles
           WHERE user_id = '%s' AND role = 'SERVICE_MANAGER'
          """
                      .formatted(ambiguousUserId)))
          .isEqualTo(1);
      // V12 (provider organizations) is the migration before the refusal, so it is already applied.
      assertThat(
              count(
                  statement,
                  """
          SELECT count(*) FROM information_schema.tables
           WHERE table_schema = 'public' AND table_name = 'provider_organizations'
          """))
          .isEqualTo(1);
    }
  }

  @Test
  void verifiedWorkLogWithoutAnEvidencePairIsRefusedAndRollsBack() throws Exception {
    String url = freshDatabase("mig_work_log");
    migrate(url, "11");
    LegacyFixture fixture = seedLegacyV11Rows(url);

    UUID workLogId;
    try (Connection connection = DriverManager.getConnection(url, user(), password());
        Statement statement = connection.createStatement()) {
      workLogId = seedVerifiedWorkLog(statement, fixture);
    }

    assertThatThrownBy(() -> migrate(url, null))
        .isInstanceOf(FlywayException.class)
        .hasMessageContaining("V18 precheck failed")
        .hasMessageContaining("before/after evidence pair");

    try (Connection connection = DriverManager.getConnection(url, user(), password());
        Statement statement = connection.createStatement()) {
      assertThat(appliedVersions(statement)).last().isEqualTo("17");
      assertThat(
              count(
                  statement,
                  """
          SELECT count(*) FROM maintenance_work_logs
           WHERE id = '%s' AND status = 'VERIFIED'
           """
                      .formatted(workLogId)))
          .isEqualTo(1);
    }
  }

  // ---------------------------------------------------------------- helpers

  private record LegacyFixture(
      UUID orderId, UUID maintenanceOrderId, UUID invoiceId, UUID ticketId) {}

  private String freshDatabase(String suffix) throws SQLException {
    String database = "mig_" + suffix + "_" + UUID.randomUUID().toString().substring(0, 8);
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
    // Flyway 12 has no close(): it acquires and releases its own connections per operation, and
    // DROP DATABASE ... WITH (FORCE) in the teardown clears anything a failed run left behind.
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

  private static UUID insertLegacyUser(
      Statement statement, String label, String zone, UUID organizationId) throws SQLException {
    UUID id = UUID.randomUUID();
    statement.execute(
        "INSERT INTO users (id, email, normalized_email, full_name, status, actor_zone, "
            + "organization_id) VALUES ('"
            + id
            + "', '"
            + label
            + "@legacy.test', '"
            + label
            + "@legacy.test', 'Legacy "
            + label
            + "', 'ACTIVE', '"
            + zone
            + "', "
            + (organizationId == null ? "NULL" : "'" + organizationId + "'")
            + ")");
    return id;
  }

  /**
   * A complete V11-era chain: identity, catalog, request, quotation, inspection order, assignment,
   * inspection, report, report version, maintenance ticket, assessment, quotation, order, invoice
   * and an execution assignment. Every column the V12..V18 migrations add is therefore absent at
   * seed time, which is exactly the "legacy order with NULL new columns" case.
   */
  private LegacyFixture seedLegacyV11Rows(String url) throws SQLException {
    try (Connection connection = DriverManager.getConnection(url, user(), password());
        Statement statement = connection.createStatement()) {
      return seedLegacyV11Rows(statement);
    }
  }

  private LegacyFixture seedLegacyV11Rows(Statement statement) throws SQLException {
    UUID organizationId = UUID.randomUUID();
    statement.execute(
        "INSERT INTO organizations (id, name, code) VALUES ('"
            + organizationId
            + "', 'Legacy customer', 'LEGACY-ORG')");

    UUID clientId =
        insertLegacyUser(statement, "legacy-client", "CUSTOMER_ORGANIZATION", organizationId);
    UUID inspectorId = insertLegacyUser(statement, "legacy-inspector", "SERVICE_WORKFORCE", null);
    UUID adminId = insertLegacyUser(statement, "legacy-admin", "PLATFORM", null);
    UUID engineerId = insertLegacyUser(statement, "legacy-engineer", "SERVICE_WORKFORCE", null);

    statement.execute(
        "INSERT INTO user_roles (id, user_id, role) VALUES "
            + "(gen_random_uuid(), '"
            + clientId
            + "', 'CLIENT'), "
            + "(gen_random_uuid(), '"
            + inspectorId
            + "', 'INSPECTOR'), "
            + "(gen_random_uuid(), '"
            + adminId
            + "', 'ADMIN'), "
            + "(gen_random_uuid(), '"
            + engineerId
            + "', 'MAINTENANCE_ENGINEER')");

    UUID categoryId = UUID.randomUUID();
    statement.execute(
        "INSERT INTO asset_categories (id, code, name) VALUES ('"
            + categoryId
            + "', 'LEGACY-CAT', 'Legacy bridge')");

    UUID templateId = UUID.randomUUID();
    statement.execute(
        "INSERT INTO checklist_templates (id, template_key, version_number, name, status, "
            + "created_by_user_id, published_at) VALUES ('"
            + templateId
            + "', 'LEGACY-TPL', 1, 'Legacy checklist', 'ACTIVE', '"
            + adminId
            + "', CURRENT_TIMESTAMP)");

    UUID assetId = UUID.randomUUID();
    statement.execute(
        "INSERT INTO assets (id, organization_id, category_id, code, name, location_text, status, "
            + "created_by_user_id) VALUES ('"
            + assetId
            + "', '"
            + organizationId
            + "', '"
            + categoryId
            + "', 'LEGACY-ASSET', 'Legacy bridge asset', 'Legacy site', 'ACTIVE', '"
            + clientId
            + "')");

    UUID requestId = UUID.randomUUID();
    statement.execute(
        "INSERT INTO inspection_requests (id, organization_id, asset_id, checklist_template_id, "
            + "request_type, scope, priority, status) VALUES ('"
            + requestId
            + "', '"
            + organizationId
            + "', '"
            + assetId
            + "', '"
            + templateId
            + "', 'AD_HOC', 'Legacy inspection scope', 'NORMAL', 'ORDER_CONFIRMED')");

    UUID quotationId = UUID.randomUUID();
    statement.execute(
        "INSERT INTO inspection_quotations (id, quotation_series_id, inspection_request_id, "
            + "version_number, prepared_by_user_id, currency, subtotal, tax_amount, total_amount, "
            + "pricing_details, scope_snapshot, payment_terms, status, decided_by_user_id, "
            + "decided_at) VALUES ('"
            + quotationId
            + "', gen_random_uuid(), '"
            + requestId
            + "', 1, '"
            + inspectorId
            + "', 'VND', 1000.00, 100.00, 1100.00, '{}'::jsonb, "
            + "'{}'::jsonb, 'Legacy payment terms', 'APPROVED', '"
            + clientId
            + "', CURRENT_TIMESTAMP)");

    UUID orderId = UUID.randomUUID();
    statement.execute(
        "INSERT INTO inspection_service_orders (id, order_number, approved_quotation_id, "
            + "inspection_request_id, confirmed_by_user_id, confirmed_at, scope_snapshot, "
            + "deliverables, payment_terms, status) VALUES ('"
            + orderId
            + "', 'LEGACY-ORDER-1', '"
            + quotationId
            + "', '"
            + requestId
            + "', '"
            + clientId
            + "', CURRENT_TIMESTAMP, '{}'::jsonb, '{}'::jsonb, 'Legacy payment terms', 'CONFIRMED')");

    UUID assignmentId = UUID.randomUUID();
    statement.execute(
        "INSERT INTO inspection_assignments (id, service_order_id, inspector_user_id, "
            + "assigned_by_user_id, status) VALUES ('"
            + assignmentId
            + "', '"
            + orderId
            + "', '"
            + inspectorId
            + "', '"
            + adminId
            + "', 'PENDING')");

    UUID inspectionId = UUID.randomUUID();
    statement.execute(
        "INSERT INTO inspections (id, service_order_id, accepted_assignment_id, asset_id, "
            + "author_user_id, checklist_template_id, status) VALUES ('"
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
        "INSERT INTO inspection_reports (id, inspection_id, author_user_id, status, "
            + "current_version_number) VALUES ('"
            + reportId
            + "', '"
            + inspectionId
            + "', '"
            + inspectorId
            + "', 'DRAFT', 0)");

    UUID reportVersionId = UUID.randomUUID();
    statement.execute(
        "INSERT INTO report_versions (id, report_id, version_number, created_by_user_id, "
            + "content_snapshot, status) VALUES ('"
            + reportVersionId
            + "', '"
            + reportId
            + "', 1, '"
            + inspectorId
            + "', '{}'::jsonb, 'RELEASED')");

    UUID ticketId = UUID.randomUUID();
    statement.execute(
        "INSERT INTO maintenance_tickets (id, organization_id, asset_id, "
            + "accepted_report_version_id, created_by_user_id, priority, status) VALUES ('"
            + ticketId
            + "', '"
            + organizationId
            + "', '"
            + assetId
            + "', '"
            + reportVersionId
            + "', '"
            + clientId
            + "', 'HIGH', 'ORDER_CONFIRMED')");

    UUID assessmentAssignmentId = UUID.randomUUID();
    statement.execute(
        "INSERT INTO maintenance_assignments (id, maintenance_ticket_id, engineer_user_id, "
            + "assigned_by_user_id, assignment_type, status) VALUES ('"
            + assessmentAssignmentId
            + "', '"
            + ticketId
            + "', '"
            + engineerId
            + "', '"
            + adminId
            + "', 'ASSESSMENT', "
            + "'PENDING')");

    UUID assessmentId = UUID.randomUUID();
    statement.execute(
        "INSERT INTO maintenance_assessments (id, assessment_assignment_id, "
            + "maintenance_ticket_id, engineer_user_id, assessment_mode, required_work, "
            + "materials_estimate, labor_hours_estimate, duration_hours_estimate, "
            + "estimated_cost_min, estimated_cost_max, currency) VALUES ('"
            + assessmentId
            + "', '"
            + assessmentAssignmentId
            + "', '"
            + ticketId
            + "', '"
            + engineerId
            + "', 'ON_SITE', 'Legacy repair scope', '{}'::jsonb, 8.00, 8.00, 100.00, 200.00, "
            + "'VND')");

    UUID maintenanceQuotationId = UUID.randomUUID();
    statement.execute(
        "INSERT INTO maintenance_quotations (id, quotation_series_id, maintenance_ticket_id, "
            + "maintenance_assessment_id, version_number, prepared_by_user_id, currency, subtotal, "
            + "tax_amount, total_amount, pricing_details, scope_snapshot, payment_terms, status, "
            + "decided_by_user_id, decided_at) VALUES ('"
            + maintenanceQuotationId
            + "', gen_random_uuid(), '"
            + ticketId
            + "', '"
            + assessmentId
            + "', 1, '"
            + engineerId
            + "', 'VND', 500.00, 50.00, 550.00, '{}'::jsonb, "
            + "'{}'::jsonb, 'Legacy maintenance terms', 'APPROVED', '"
            + clientId
            + "', CURRENT_TIMESTAMP)");

    UUID maintenanceOrderId = UUID.randomUUID();
    statement.execute(
        "INSERT INTO maintenance_orders (id, order_series_id, order_number, "
            + "maintenance_ticket_id, approved_quotation_id, version_number, scope_snapshot, "
            + "approved_amount, currency, payment_terms, status, approved_by_user_id, approved_at) "
            + "VALUES ('"
            + maintenanceOrderId
            + "', gen_random_uuid(), 'LEGACY-MO-1', '"
            + ticketId
            + "', '"
            + maintenanceQuotationId
            + "', 1, '{}'::jsonb, 500.00, 'VND', "
            + "'Legacy maintenance terms', 'CONFIRMED', '"
            + clientId
            + "', CURRENT_TIMESTAMP)");

    UUID invoiceId = UUID.randomUUID();
    statement.execute(
        "INSERT INTO invoices (id, invoice_number, organization_id, maintenance_order_id, "
            + "maintenance_ticket_id, currency, subtotal, tax_amount, total_amount, status) "
            + "VALUES ('"
            + invoiceId
            + "', 'LEGACY-INV-1', '"
            + organizationId
            + "', '"
            + maintenanceOrderId
            + "', '"
            + ticketId
            + "', 'VND', 500.00, 50.00, 550.00, 'ISSUED')");

    return new LegacyFixture(orderId, maintenanceOrderId, invoiceId, ticketId);
  }

  private static UUID seedVerifiedWorkLog(Statement statement, LegacyFixture fixture)
      throws SQLException {
    UUID engineerId =
        insertLegacyUser(statement, "legacy-verified-engineer", "SERVICE_WORKFORCE", null);
    UUID executionAssignmentId = UUID.randomUUID();
    statement.execute(
        "INSERT INTO maintenance_assignments (id, maintenance_ticket_id, maintenance_order_id, "
            + "engineer_user_id, assigned_by_user_id, assignment_type, status) VALUES ('"
            + executionAssignmentId
            + "', '"
            + fixture.ticketId()
            + "', '"
            + fixture.maintenanceOrderId()
            + "', '"
            + engineerId
            + "', '"
            + engineerId
            + "', 'EXECUTION', 'PENDING')");

    UUID workLogId = UUID.randomUUID();
    statement.execute(
        "INSERT INTO maintenance_work_logs (id, maintenance_ticket_id, execution_assignment_id, "
            + "engineer_user_id, started_at, work_summary, materials_used, labor_hours, status, "
            + "submitted_at, verified_by_user_id, verified_at) VALUES ('"
            + workLogId
            + "', '"
            + fixture.ticketId()
            + "', '"
            + executionAssignmentId
            + "', '"
            + engineerId
            + "', CURRENT_TIMESTAMP, 'Legacy repair finished', '{}'::jsonb, 4.00, 'VERIFIED', "
            + "CURRENT_TIMESTAMP, '"
            + engineerId
            + "', CURRENT_TIMESTAMP)");
    return workLogId;
  }
}
