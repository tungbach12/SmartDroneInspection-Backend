package com.smartdroneinspection.database;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PersistenceEntityOwnershipTest {

  @ParameterizedTest(name = "{0}.{1} is owned by {2}")
  @MethodSource("entityMappings")
  void mapsDatabaseTableToOwningFeature(String feature, String tableName, String entityClassName)
      throws ClassNotFoundException {
    Class<?> entityType = Class.forName(entityClassName);

    assertThat(entityType.getPackageName())
        .isEqualTo("com.smartdroneinspection." + feature + ".domain");
    assertThat(entityType.isAnnotationPresent(Entity.class)).isTrue();
    assertThat(entityType.getAnnotation(Table.class).name()).isEqualTo(tableName);
  }

  private static Stream<Arguments> entityMappings() {
    return Stream.of(
        Arguments.of("assets", "assets", "com.smartdroneinspection.assets.domain.Asset"),
        Arguments.of(
            "assets", "asset_categories", "com.smartdroneinspection.assets.domain.AssetCategory"),
        Arguments.of(
            "assets", "asset_documents", "com.smartdroneinspection.assets.domain.AssetDocument"),
        Arguments.of(
            "assets", "checklist_items", "com.smartdroneinspection.assets.domain.ChecklistItem"),
        Arguments.of(
            "assets",
            "checklist_templates",
            "com.smartdroneinspection.assets.domain.ChecklistTemplate"),
        Arguments.of(
            "notifications",
            "notifications",
            "com.smartdroneinspection.notifications.domain.Notification"),
        Arguments.of("users", "auth_sessions", "com.smartdroneinspection.users.domain.AuthSession"),
        Arguments.of(
            "users", "organizations", "com.smartdroneinspection.users.domain.Organization"),
        Arguments.of(
            "users", "refresh_tokens", "com.smartdroneinspection.users.domain.RefreshToken"),
        Arguments.of("users", "users", "com.smartdroneinspection.users.domain.User"),
        Arguments.of(
            "users", "user_roles", "com.smartdroneinspection.users.domain.UserRoleAssignment"));
  }
}
