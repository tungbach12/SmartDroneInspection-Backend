package com.smartdroneinspection.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.contracts.service.Mf2ActorPolicy;
import com.smartdroneinspection.shared.auth.Roles;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class Mf2ActorPolicyTest {

  private final Mf2ActorPolicy policy = new Mf2ActorPolicy();

  @Test
  void classifiesCustomerForMf2() {
    assertThat(policy.actorKind(Set.of(Roles.CUSTOMER), UUID.randomUUID()))
        .isEqualTo(Mf2ActorPolicy.ActorKind.CUSTOMER);
    assertThat(policy.actorKind(Set.of("CLIENT"), UUID.randomUUID()))
        .isEqualTo(Mf2ActorPolicy.ActorKind.CUSTOMER);
  }

  @Test
  void classifiesManagerWithoutOrganizationScope() {
    assertThat(policy.actorKind(Set.of(Roles.MANAGER), null))
        .isEqualTo(Mf2ActorPolicy.ActorKind.MANAGER);
    assertThat(policy.actorKind(Set.of("SERVICE_MANAGER"), null))
        .isEqualTo(Mf2ActorPolicy.ActorKind.MANAGER);
  }

  @Test
  void rejectsCustomerRoleWithoutOrganizationScope() {
    assertThat(policy.actorKind(Set.of(Roles.CUSTOMER), null))
        .isEqualTo(Mf2ActorPolicy.ActorKind.NONE);
  }

  @Test
  void doesNotGrantAdminCustomerOrManagerCapabilities() {
    assertThat(policy.actorKind(Set.of(Roles.ADMIN), null))
        .isEqualTo(Mf2ActorPolicy.ActorKind.NONE);
  }
}
