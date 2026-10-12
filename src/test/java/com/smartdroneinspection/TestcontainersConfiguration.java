package com.smartdroneinspection;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared infrastructure for the integration suites.
 *
 * <p>PostgreSQL is declared here with {@code @ServiceConnection}. Object storage is started
 * separately by {@link MinioTestInfrastructure}, which registers itself as an {@code
 * ApplicationContextInitializer} so the MinIO endpoint is bound for every suite in this JVM without
 * each one opting in.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    return new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg17"));
  }
}
