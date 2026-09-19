package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.TestcontainersConfiguration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class InspectionSchemaMigrationTest {

  private static final Set<String> EXPECTED_TABLES =
      Set.of(
          "inspection_requests",
          "inspection_request_attachments",
          "inspection_quotations",
          "inspection_service_orders",
          "inspection_assignments");

  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  void flywayCreatesTheWf2Tables() {
    List<String> tables =
        jdbcTemplate.queryForList(
            """
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = 'public'
              AND table_name IN (
                'inspection_requests', 'inspection_request_attachments',
                'inspection_quotations', 'inspection_service_orders',
                'inspection_assignments'
              )
            """,
            String.class);

    assertThat(tables).containsExactlyInAnyOrderElementsOf(EXPECTED_TABLES);
  }

  @Test
  void flywayCreatesTheWf2GuardIndexes() {
    List<String> indexes =
        jdbcTemplate.queryForList(
            """
            SELECT indexname
            FROM pg_indexes
            WHERE schemaname = 'public'
              AND indexname IN (
                'uq_inspection_requests_periodic_due_cycle',
                'uq_inspection_assignments_active_order'
              )
            """,
            String.class);

    assertThat(indexes)
        .containsExactlyInAnyOrder(
            "uq_inspection_requests_periodic_due_cycle", "uq_inspection_assignments_active_order");
  }
}
