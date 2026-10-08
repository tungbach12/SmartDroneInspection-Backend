package com.smartdroneinspection.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.AssetCategory;
import com.smartdroneinspection.assets.domain.AssetDocument;
import com.smartdroneinspection.assets.domain.ChecklistItem;
import com.smartdroneinspection.assets.domain.ChecklistTemplate;
import com.smartdroneinspection.notifications.domain.Notification;
import com.smartdroneinspection.users.domain.AuthSession;
import com.smartdroneinspection.users.domain.Organization;
import com.smartdroneinspection.users.domain.RefreshToken;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.UserRoleAssignment;
import java.util.List;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.dialect.PostgreSQLDialect;
import org.junit.jupiter.api.Test;

class PersistenceEntityMappingTest {

  private static final List<Class<?>> FEATURE_ENTITIES =
      List.of(
          Asset.class,
          AssetCategory.class,
          AssetDocument.class,
          ChecklistItem.class,
          ChecklistTemplate.class,
          Notification.class,
          AuthSession.class,
          Organization.class,
          RefreshToken.class,
          User.class,
          UserRoleAssignment.class);

  @Test
  void allFeatureEntitiesBuildValidHibernateMetadataWithoutDatabaseAccess() {
    StandardServiceRegistry registry =
        new StandardServiceRegistryBuilder()
            .applySetting("hibernate.dialect", PostgreSQLDialect.class.getName())
            .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
            .build();
    try {
      MetadataSources sources = new MetadataSources(registry);
      FEATURE_ENTITIES.forEach(sources::addAnnotatedClass);
      Metadata metadata = sources.buildMetadata();

      assertThat(metadata.getEntityBindings()).hasSize(FEATURE_ENTITIES.size());
      assertThat(metadata.getEntityBindings())
          .allSatisfy(binding -> assertThat(binding.getTable()).isNotNull());
    } finally {
      StandardServiceRegistryBuilder.destroy(registry);
    }
  }
}
