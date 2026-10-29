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
  void customerOrganizationCannotCarryInspectorOrMultipleRoles() {
    assertThatThrownBy(
            () ->
                policy.validate(
                    ActorZone.CUSTOMER_ORGANIZATION, UUID.randomUUID(), Set.of(UserRole.INSPECTOR)))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(
            () ->
                policy.validate(
                    ActorZone.CUSTOMER_ORGANIZATION,
                    UUID.randomUUID(),
                    Set.of(UserRole.CUSTOMER, UserRole.MAINTENANCE_ENGINEER)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void permitsMaintenanceEngineerAsAnIndividualOrganizationRole() {
    assertThatCode(
            () ->
                policy.validate(
                    ActorZone.CUSTOMER_ORGANIZATION,
                    UUID.randomUUID(),
                    Set.of(UserRole.MAINTENANCE_ENGINEER)))
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
  void requiresOrganizationForCustomer() {
    assertThatThrownBy(
            () -> policy.validate(ActorZone.CUSTOMER_ORGANIZATION, null, Set.of(UserRole.CUSTOMER)))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatCode(
            () ->
                policy.validate(
                    ActorZone.CUSTOMER_ORGANIZATION, UUID.randomUUID(), Set.of(UserRole.CUSTOMER)))
        .doesNotThrowAnyException();
  }

  @Test
  void permitsOnlyManagerOrAdminAsPlatformRoles() {
    assertThatCode(() -> policy.validate(ActorZone.PLATFORM, null, Set.of(UserRole.MANAGER)))
        .doesNotThrowAnyException();

    assertThatThrownBy(
            () ->
                policy.validate(ActorZone.PLATFORM, null, Set.of(UserRole.MANAGER, UserRole.ADMIN)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void inspectorPartnerMustBeOrganizationIndependentInspector() {
    assertThatCode(
            () -> policy.validate(ActorZone.INSPECTOR_PARTNER, null, Set.of(UserRole.INSPECTOR)))
        .doesNotThrowAnyException();

    assertThatThrownBy(
            () ->
                policy.validate(
                    ActorZone.INSPECTOR_PARTNER, UUID.randomUUID(), Set.of(UserRole.INSPECTOR)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void prospectiveCustomerMustHaveNoOrganization() {
    assertThatCode(
            () -> policy.validate(ActorZone.PROSPECT_CUSTOMER, null, Set.of(UserRole.CUSTOMER)))
        .doesNotThrowAnyException();

    assertThatThrownBy(
            () ->
                policy.validate(
                    ActorZone.PROSPECT_CUSTOMER, UUID.randomUUID(), Set.of(UserRole.CUSTOMER)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
