package com.smartdroneinspection.users;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.users.service.PasswordPolicy;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

  private final PasswordPolicy policy = new PasswordPolicy();

  @Test
  void acceptsLongPassphraseWithoutCompositionRules() {
    assertThatCode(
            () ->
                policy.validate("four calm drones inspect safely", "pilot@example.com", "A Person"))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsShortAndCommonPasswords() {
    assertThatThrownBy(() -> policy.validate("short", "pilot@example.com", "A Person"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> policy.validate("passwordpassword", "pilot@example.com", "A Person"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsPasswordContainingEmailIdentity() {
    assertThatThrownBy(
            () -> policy.validate("pilot-has-a-long-password", "pilot@example.com", "A Person"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
