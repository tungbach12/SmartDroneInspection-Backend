package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.inspections.domain.InspectionQuotation;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.dialect.PostgreSQLDialect;
import org.junit.jupiter.api.Test;

class InspectionQuotationMappingTest {

  @Test
  void mapsQuotationAsAnOptimisticallyLockedAggregate() {
    StandardServiceRegistry registry =
        new StandardServiceRegistryBuilder()
            .applySetting("hibernate.dialect", PostgreSQLDialect.class.getName())
            .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
            .build();
    try {
      Metadata metadata =
          new MetadataSources(registry)
              .addAnnotatedClass(InspectionQuotation.class)
              .buildMetadata();

      assertThat(metadata.getEntityBinding(InspectionQuotation.class.getName()).isVersioned())
          .isTrue();
      assertThat(
              metadata
                  .getEntityBinding(InspectionQuotation.class.getName())
                  .getVersion()
                  .getColumns()
                  .getFirst()
                  .getName())
          .isEqualTo("row_version");
    } finally {
      StandardServiceRegistryBuilder.destroy(registry);
    }
  }
}
