package com.smartdroneinspection.assets;

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
class AssetSchemaMigrationTest {

  private static final Set<String> EXPECTED_TABLES =
      Set.of(
          "asset_categories",
          "checklist_templates",
          "checklist_items",
          "assets",
          "asset_documents",
          "inspection_schedules");

  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  void flywayCreatesTheWf1Tables() {
    List<String> tables =
        jdbcTemplate.queryForList(
            """
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = 'public'
              AND table_name IN (
                'asset_categories', 'checklist_templates', 'checklist_items',
                'assets', 'asset_documents', 'inspection_schedules'
              )
            """,
            String.class);

    assertThat(tables).containsExactlyInAnyOrderElementsOf(EXPECTED_TABLES);
  }
}
