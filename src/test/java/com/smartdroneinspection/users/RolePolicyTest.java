package com.smartdroneinspection.users;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.users.domain.RolePolicy;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RolePolicyTest {

  private final RolePolicy policy = new RolePolicy();

  @Test
  void customerCannotCarryInspectorOrMultipleRoles() {
    assertThatThrownBy(() -> policy.validate(ActorZone.CUSTOMER, Set.of(UserRole.INSPECTOR)))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(
            () -> policy.validate(ActorZone.CUSTOMER, Set.of(UserRole.CUSTOMER, UserRole.ADMIN)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsAdminCombinedWithAnotherRole() {
    assertThatThrownBy(
            () -> policy.validate(ActorZone.PLATFORM, Set.of(UserRole.ADMIN, UserRole.INSPECTOR)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void requiresExactlyOneCustomerRoleForCustomerZone() {
    assertThatCode(() -> policy.validate(ActorZone.CUSTOMER, Set.of(UserRole.CUSTOMER)))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> policy.validate(ActorZone.CUSTOMER, Set.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> policy.validate(ActorZone.CUSTOMER, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void permitsOnlyManagerOrAdminAsPlatformRoles() {
    assertThatCode(() -> policy.validate(ActorZone.PLATFORM, Set.of(UserRole.MANAGER)))
        .doesNotThrowAnyException();

    assertThatThrownBy(
            () -> policy.validate(ActorZone.PLATFORM, Set.of(UserRole.MANAGER, UserRole.ADMIN)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void inspectorMustCarryOnlyInspectorRole() {
    assertThatCode(() -> policy.validate(ActorZone.INSPECTOR, Set.of(UserRole.INSPECTOR)))
        .doesNotThrowAnyException();

    assertThatThrownBy(() -> policy.validate(ActorZone.INSPECTOR, Set.of(UserRole.CUSTOMER)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
