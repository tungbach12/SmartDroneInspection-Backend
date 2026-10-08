package com.smartdroneinspection.users;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.users.domain.RolePolicy;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RolePolicyTest {

  private final RolePolicy policy = new RolePolicy();

  @Test
  void permitsMultipleServiceRolesWithinAnOrganization() {
    assertThatCode(
            () ->
                policy.validate(
                    ActorZone.CUSTOMER_ORGANIZATION,
                    UUID.randomUUID(),
                    Set.of(UserRole.INSPECTOR, UserRole.MAINTENANCE_ENGINEER)))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsAdminCombinedWithAnotherRole() {
    assertThatThrownBy(
            () ->
                policy.validate(
                    ActorZone.PLATFORM, null, Set.of(UserRole.ADMIN, UserRole.INSPECTOR)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void requiresOrganizationForClient() {
    assertThatThrownBy(
            () ->
                policy.validate(ActorZone.CUSTOMER_ORGANIZATION, null, Set.of(UserRole.ORG_ADMIN)))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatCode(
            () ->
                policy.validate(
                    ActorZone.CUSTOMER_ORGANIZATION, UUID.randomUUID(), Set.of(UserRole.ORG_ADMIN)))
        .doesNotThrowAnyException();
  }
}
