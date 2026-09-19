package com.smartdroneinspection.assets;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.AssetDocument;
import java.sql.Types;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.mapping.Column;
import org.junit.jupiter.api.Test;

class AssetDocumentMappingTest {

  @Test
  void mapsChecksumToTheFixedLengthCharColumnRequiredByTheMigration() {
    StandardServiceRegistry registry =
        new StandardServiceRegistryBuilder()
            .applySetting("hibernate.dialect", PostgreSQLDialect.class.getName())
            .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
            .build();
    try {
      Metadata metadata =
          new MetadataSources(registry)
              .addAnnotatedClass(Asset.class)
              .addAnnotatedClass(AssetDocument.class)
              .buildMetadata();
      Column checksum =
          metadata
              .getEntityBinding(AssetDocument.class.getName())
              .getProperty("checksumSha256")
              .getValue()
              .getColumns()
              .getFirst();

      assertThat(checksum.getSqlTypeCode(metadata)).isEqualTo(Types.CHAR);
      assertThat(checksum.getSqlType(metadata)).isEqualTo("char(64)");
      assertThat(checksum.isNullable()).isFalse();
    } finally {
      StandardServiceRegistryBuilder.destroy(registry);
    }
  }
}
