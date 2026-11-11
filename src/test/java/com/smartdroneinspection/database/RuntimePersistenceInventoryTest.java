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
          "checklist_templates",
          "inspection_items",
          "asset_documents",
          "auth_sessions",
          "drones",
          "inspections",
          "notifications",
          "prospective_customer_registrations",
          "drone_registration_applications",
          "reinspection_defect_verifications",
          "organizations",
          "users",
          "inspector_flight_logs",
          "field_sessions",
          "evidence",
          "findings",
          "inspection_reports",
          "maintenance_work_orders",
          "maintenance_tasks",
          "maintenance_team_members",
          "maintenance_estimate_versions",
          "maintenance_cost_lines",
          "maintenance_change_orders",
          "maintenance_work_logs",
          "maintenance_reports",
          "maintenance_acceptance_decisions",
          "repair_knowledge_chunks",
          "inspector_profiles",
          "otp_challenges",
          "service_requests",
          "service_request_messages",
          "service_request_quotes",
          "service_request_occurrences",
          "service_invoices",
          "service_payments",
          "inspector_payouts",
          "service_refunds",
          "inspection_finance_settings");

  private static final Set<String> TARGET_APPLICATION_TABLES =
      Set.of(
          "organizations",
          "users",
          "auth_sessions",
          "drones",
          "prospective_customer_registrations",
          "drone_registration_applications",
          "reinspection_defect_verifications",
          "asset_categories",
          "checklist_templates",
          "inspection_items",
          "assets",
          "asset_documents",
          "inspections",
          "field_sessions",
          "evidence",
          "findings",
          "inspection_reports",
          "maintenance_work_orders",
          "maintenance_tasks",
          "maintenance_team_members",
          "maintenance_estimate_versions",
          "maintenance_cost_lines",
          "maintenance_change_orders",
          "maintenance_work_logs",
          "maintenance_reports",
          "maintenance_acceptance_decisions",
          "audit_events",
          "notifications",
          "repair_knowledge_chunks",
          "service_requests",
          "service_request_messages",
          "service_request_quotes",
          "service_request_occurrences",
          "service_invoices",
          "service_payments",
          "inspector_payouts",
          "service_refunds",
          "inspection_finance_settings",
          "inspector_profiles",
          "otp_challenges",
          "inspector_flight_logs");

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
  void applicationTableInventoryContainsOnlyTheTargetTables() {
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
