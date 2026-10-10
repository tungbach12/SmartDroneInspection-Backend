package com.smartdroneinspection.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.TestcontainersConfiguration;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RuntimePersistenceInventoryTest {

  private static final Set<String> RUNTIME_ENTITY_TABLES =
      Set.of(
          "event_publication",
          "assets",
          "asset_categories",
          "asset_documents",
          "auth_sessions",
          "checklist_items",
          "checklist_templates",
          "notifications",
          "organizations",
          "refresh_tokens",
          "user_roles",
          "users",
          // MF4 credential read for team assignment checks.
          "workforce_credentials",
          // MF3 inspection slice: evidence, advisory detection, human findings and report versions.
          "inspections",
          "field_sessions",
          "evidence",
          "evidence_quality_decisions",
          "ai_finding_candidates",
          "verified_findings",
          "inspection_reports",
          "inspection_report_versions",
          // MF4 slice: work order aggregate with its team, task, estimate and cost line,
          // then execution, change control, completion report and acceptance.
          "maintenance_work_orders",
          "maintenance_tasks",
          "maintenance_team_members",
          "maintenance_estimate_versions",
          "maintenance_cost_lines",
          "maintenance_change_orders",
          "maintenance_work_logs",
          "maintenance_report_versions",
          "maintenance_acceptance_decisions");

  private static final Set<String> TARGET_APPLICATION_TABLES =
      Set.of(
          "organizations",
          "subscriptions",
          "users",
          "user_roles",
          "auth_sessions",
          "refresh_tokens",
          "workforce_credentials",
          "drones",
          "drone_documents",
          "flight_permits",
          "asset_categories",
          "checklist_templates",
          "checklist_items",
          "assets",
          "asset_documents",
          "asset_pair_assignments",
          "inspection_schedules",
          "inspections",
          "inspection_preparations",
          "inspection_readiness_decisions",
          "field_sessions",
          "checklist_responses",
          "evidence",
          "evidence_quality_decisions",
          "ai_finding_candidates",
          "verified_findings",
          "inspection_reports",
          "inspection_report_versions",
          "report_version_evidence",
          "report_version_findings",
          "maintenance_work_orders",
          "maintenance_tasks",
          "maintenance_team_members",
          "maintenance_estimate_versions",
          "maintenance_cost_lines",
          "maintenance_change_orders",
          "maintenance_work_logs",
          "maintenance_report_versions",
          "maintenance_acceptance_decisions",
          "audit_events",
          "notifications");

  @Autowired EntityManagerFactory entityManagerFactory;
  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  void everyRuntimeEntityMapsOnlyToAnEnterpriseSaasTargetTable() {
    Set<String> mappedTables =
        entityManagerFactory.getMetamodel().getEntities().stream()
            .map(entity -> entity.getJavaType().getAnnotation(Table.class))
            .filter(java.util.Objects::nonNull)
            .map(Table::name)
            .collect(Collectors.toSet());

    assertThat(normalized(mappedTables)).containsExactlyInAnyOrderElementsOf(RUNTIME_ENTITY_TABLES);
  }

  @Test
  void applicationTableInventoryContainsOnlyTheFortyOneTargetTables() {
    Set<String> actualApplicationTables =
        jdbcTemplate
            .queryForList(
                """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = current_schema()
                  AND table_name NOT IN ('event_publication', 'flyway_schema_history')
                """,
                String.class)
            .stream()
            .collect(Collectors.toSet());

    assertThat(TARGET_APPLICATION_TABLES).hasSize(41);
    assertThat(actualApplicationTables)
        .containsExactlyInAnyOrderElementsOf(TARGET_APPLICATION_TABLES);
    assertThat(normalized(mappedTableNames()))
        .containsExactlyInAnyOrderElementsOf(RUNTIME_ENTITY_TABLES);
  }

  private Set<String> mappedTableNames() {
    return entityManagerFactory.getMetamodel().getEntities().stream()
        .map(entity -> entity.getJavaType().getAnnotation(Table.class))
        .filter(java.util.Objects::nonNull)
        .map(Table::name)
        .collect(Collectors.toSet());
  }

  private Set<String> normalized(Set<String> values) {
    return values.stream().map(value -> value.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
  }
}
