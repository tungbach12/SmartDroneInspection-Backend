package com.smartdroneinspection.shared.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ensures PostgreSQL extensions, base constraints, defaults, and seed data exist on a fresh
 * database before Hibernate {@code ddl-auto: update} synchronizes entity mappings, and drops
 * retired tables on existing databases.
 */
@Component
public class CodeFirstSchemaInitializer implements BeanPostProcessor {

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
    if (bean instanceof DataSource dataSource) {
      initializeDatabase(dataSource);
    }
    return bean;
  }

  public static void initializeDatabase(DataSource dataSource) {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute(
        """
        DROP TABLE IF EXISTS report_version_evidence CASCADE;
        DROP TABLE IF EXISTS report_version_findings CASCADE;
        DROP TABLE IF EXISTS flyway_schema_history CASCADE;
        """);

    Boolean initialized =
        jdbcTemplate.queryForObject(
            """
            SELECT EXISTS (
              SELECT 1
              FROM information_schema.tables
              WHERE table_schema = current_schema()
                AND table_name = 'organizations'
            )
            """,
            Boolean.class);
    if (Boolean.TRUE.equals(initialized)) {
      return;
    }

    ClassPathResource resource = new ClassPathResource("db/schema.sql");
    try (InputStream stream = resource.getInputStream()) {
      String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      jdbcTemplate.execute(sql);
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to read classpath:db/schema.sql", exception);
    }
  }
}
