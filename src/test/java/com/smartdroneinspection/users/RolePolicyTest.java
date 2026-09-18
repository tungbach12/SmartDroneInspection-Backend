package com.smartdroneinspection.users;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.users.domain.ActorZone;
import com.smartdroneinspection.users.domain.RolePolicy;
import com.smartdroneinspection.users.domain.UserRole;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RolePolicyTest {

  private final RolePolicy policy = new RolePolicy();

  @Test
  void permitsMultipleServiceRoles() {
    assertThatCode(
            () ->
                policy.validate(
                    ActorZone.SERVICE_WORKFORCE,
                    null,
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
            () -> policy.validate(ActorZone.CUSTOMER_ORGANIZATION, null, Set.of(UserRole.CLIENT)))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatCode(
            () ->
                policy.validate(
                    ActorZone.CUSTOMER_ORGANIZATION, UUID.randomUUID(), Set.of(UserRole.CLIENT)))
        .doesNotThrowAnyException();
  }
}
