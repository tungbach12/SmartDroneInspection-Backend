package com.smartdroneinspection.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Validates the V40 forward migration contract as well as the complete SQL schema definition across
 * Flyway scripts.
 */
class V40SchemaMigrationContractTest {

  @Test
  void v40ContainsGuardedBackfillAndRequiredStructuralDrops() throws IOException {
    String v40 =
        Files.readString(Path.of("src/main/resources/db/migration/V40__mf3_entity_field_audit.sql"))
            .replace("\r\n", "\n");

    assertThat(v40)
        .contains("V40 cannot merge finding notes")
        .contains("V40 cannot move finding location")
        .contains("SET forwarded_to_maintenance_at = i.corrective_handoff_at")
        .contains("UPDATE findings")
        .contains("DROP TABLE evidence_quality_decisions")
        .contains("ADD COLUMN forwarded_to_maintenance_at TIMESTAMPTZ")
        .contains("RENAME COLUMN review_reason TO return_reason")
        .contains("ADD COLUMN submitted_at TIMESTAMPTZ")
        .contains("DROP COLUMN checklist_version")
        .contains("DROP COLUMN permit_snapshot_ids")
        .contains("DROP COLUMN created_at");

    assertThat(v40)
        .contains("DROP COLUMN uploaded_by_user_id")
        .contains("DROP COLUMN checksum_sha256")
        .contains("DROP COLUMN source")
        .contains("DROP COLUMN external_reference")
        .contains("DROP COLUMN capture_metadata")
        .contains("DROP COLUMN immutable_original")
        .contains("DROP COLUMN retired_by_user_id")
        .contains("DROP COLUMN retirement_reason")
        .contains("DROP COLUMN analysis_completed_at")
        .contains("DROP COLUMN analysis_detection_count")
        .contains("DROP COLUMN kind");

    assertThat(v40)
        .contains("DROP COLUMN created_by_user_id")
        .contains("DROP COLUMN location_description")
        .contains("DROP COLUMN confirmed_by_user_id")
        .contains("DROP COLUMN component")
        .contains("DROP COLUMN description")
        .contains("DROP COLUMN observed_condition")
        .contains("DROP COLUMN priority")
        .contains("DROP COLUMN measurement")
        .contains("DROP COLUMN corrective_action_required")
        .contains("DROP COLUMN corrective_action_priority")
        .contains("DROP COLUMN corrective_action_due_at")
        .contains("DROP COLUMN corrective_action_reviewed_by_user_id")
        .contains("DROP COLUMN corrective_action_reviewed_at")
        .contains("DROP COLUMN corrective_action_rationale")
        .contains("DROP COLUMN repair_required")
        .contains("DROP COLUMN deleted_by_user_id")
        .contains("DROP COLUMN deletion_reason")
        .contains("DROP COLUMN row_version");

    assertThat(v40)
        .contains("DROP COLUMN author_user_id")
        .contains("DROP COLUMN author_verified_by_user_id")
        .contains("DROP COLUMN author_verified_at")
        .contains("DROP COLUMN llm_provider")
        .contains("DROP COLUMN llm_model")
        .contains("DROP COLUMN prompt_version")
        .contains("DROP COLUMN generated_at")
        .contains("DROP COLUMN evidence_snapshot_hash")
        .contains("DROP COLUMN findings_snapshot_hash")
        .contains("DROP COLUMN withdrawn_by_user_id")
        .contains("DROP COLUMN return_reason_type");

    assertThat(v40)
        .contains("DROP COLUMN findings_confirmed_by_user_id")
        .contains("DROP COLUMN corrective_handoff_by_user_id")
        .contains("DROP COLUMN corrective_handoff_at")
        .contains("DROP COLUMN no_repair_rationale");
  }

  @Test
  void baselineStillCreatesPgcryptoExtension() throws IOException {
    String v1 = Files.readString(Path.of("src/main/resources/db/migration/V1__baseline.sql"));
    assertThat(v1).contains("CREATE EXTENSION IF NOT EXISTS \"pgcrypto\"");
  }
}
