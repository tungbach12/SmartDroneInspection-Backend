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
          "drone_document_configs",
          "drone_feature_configs",
          "inspections",
          "notifications",
          "prospective_customer_registrations",
          "drone_registration_applications",
          "organizations",
          "users",
          "inspector_flight_logs",
          "field_sessions",
          "evidence",
          "findings",
          "inspection_reports",
          "inspector_profiles",
          "pilot_license_classes",
          "inspector_applications",
          "otp_challenges",
          "service_requests",
          "service_request_messages",
          "service_request_quotes",
          "service_request_occurrences",
          "service_invoices",
          "service_payments",
          "inspector_payouts",
          "inspection_finance_settings");

  private static final Set<String> TARGET_APPLICATION_TABLES =
      Set.of(
          "organizations",
          "users",
          "auth_sessions",
          "drones",
          "drone_document_configs",
          "drone_feature_configs",
          "prospective_customer_registrations",
          "drone_registration_applications",
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
          "audit_events",
          "notifications",
          "platform_ai_settings",
          "service_requests",
          "service_request_messages",
          "service_request_quotes",
          "service_request_occurrences",
          "service_invoices",
          "service_payments",
          "inspector_payouts",
          "inspection_finance_settings",
          "inspector_profiles",
          "pilot_license_classes",
          "inspector_applications",
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

    assertThat(TARGET_APPLICATION_TABLES).hasSize(34);
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
