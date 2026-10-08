package com.smartdroneinspection.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class V24TargetSchemaAlignmentTest {

  @Test
  void v24AlignsRoleAndZoneVocabularyToFourTargetRoles() throws IOException {
    String v24 = readMigration("V24__enterprise_saas_role_and_schema_alignment.sql");

    assertThat(v24)
        .contains("UPDATE user_roles")
        .contains("'PLATFORM_ADMIN'")
        .contains("'ADMIN'")
        .contains("'CLIENT'")
        .contains("'ORG_ADMIN'")
        .contains("'PROVIDER_MANAGER'")
        .contains("'PLATFORM_OPERATOR'")
        .contains(
            "ADD CONSTRAINT ck_user_roles_role CHECK (role IN ('ADMIN', 'ORG_ADMIN', 'INSPECTOR', 'MAINTENANCE_ENGINEER'))")
        .contains("UPDATE users")
        .contains("actor_zone")
        .contains("'SERVICE_WORKFORCE'")
        .contains("'CUSTOMER_ORGANIZATION'")
        .contains(
            "ADD CONSTRAINT ck_users_actor_zone CHECK (actor_zone IN ('PLATFORM', 'CUSTOMER_ORGANIZATION'))");
  }

  @Test
  void v24PrechecksUnmappableRowsAndDropsTheOldRoleCheckBeforeRewrite() throws IOException {
    String v24 = readMigration("V24__enterprise_saas_role_and_schema_alignment.sql");

    assertThat(v24)
        .contains("V24 precheck failed")
        .contains("PROVIDER_MANAGER")
        .contains("PLATFORM_OPERATOR")
        .contains("SERVICE_WORKFORCE")
        .contains("provider_id")
        .contains("r.role IN ('PROVIDER_MANAGER', 'PLATFORM_OPERATOR')");
    assertThat(v24.indexOf("ALTER TABLE user_roles DROP CONSTRAINT IF EXISTS ck_user_roles_role"))
        .isLessThan(v24.indexOf("UPDATE user_roles\nSET role"));
  }

  @Test
  void v24RenamesReportDecisionColumnsAndDropsActivationTokens() throws IOException {
    String v24 = readMigration("V24__enterprise_saas_role_and_schema_alignment.sql");

    assertThat(v24)
        .contains("RENAME COLUMN client_decision_by_user_id TO org_admin_decision_by_user_id")
        .contains("RENAME COLUMN client_decision_reason TO org_admin_decision_reason")
        .contains("DROP COLUMN IF EXISTS activation_token_hash")
        .contains("DROP COLUMN IF EXISTS activation_expires_at")
        .contains("DROP COLUMN IF EXISTS provider_id");
  }

  /**
   * Reads a migration with line endings normalised to {@code \n}.
   *
   * <p>The assertions below include multi-line snippets, and a repository file carries whichever
   * line ending its editor last wrote. Matching raw bytes made the ordering assertion below fail on
   * a correctly ordered migration whenever the file was stored as CRLF, because the search string
   * used {@code \n}. Normalising here keeps the assertions about SQL rather than about encoding.
   */
  private String readMigration(String filename) throws IOException {
    return Files.readString(Path.of("src/main/resources/db/migration", filename))
        .replace("\r\n", "\n");
  }
}
