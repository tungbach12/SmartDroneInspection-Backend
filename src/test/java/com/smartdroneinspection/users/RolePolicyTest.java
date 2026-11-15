package com.smartdroneinspection.users;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.users.domain.RolePolicy;
import com.smartdroneinspection.users.domain.enums.UserRole;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RolePolicyTest {

  private final RolePolicy policy = new RolePolicy();

  @Test
  void rejectsMultipleRoles() {
    assertThatThrownBy(() -> policy.validate(Set.of(UserRole.CUSTOMER, UserRole.ADMIN)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> policy.validate(Set.of(UserRole.ADMIN, UserRole.INSPECTOR)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> policy.validate(Set.of(UserRole.MANAGER, UserRole.ADMIN)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsEmptyOrNullRoles() {
    assertThatThrownBy(() -> policy.validate(Set.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> policy.validate(null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void acceptsAnySingleCanonicalRole() {
    for (UserRole role : UserRole.values()) {
      assertThatCode(() -> policy.validate(Set.of(role))).doesNotThrowAnyException();
    }
  }
}
