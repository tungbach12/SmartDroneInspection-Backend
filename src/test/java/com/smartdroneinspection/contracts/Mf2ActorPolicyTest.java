package com.smartdroneinspection.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.contracts.service.Mf2ActorPolicy;
import com.smartdroneinspection.shared.auth.Roles;
import java.util.Set;
import org.junit.jupiter.api.Test;

class Mf2ActorPolicyTest {

  private final Mf2ActorPolicy policy = new Mf2ActorPolicy();

  @Test
  void classifiesCustomerForMf2() {
    assertThat(policy.actorKind(Set.of(Roles.CUSTOMER)))
        .isEqualTo(Mf2ActorPolicy.ActorKind.CUSTOMER);
    assertThat(policy.actorKind(Set.of("CLIENT"))).isEqualTo(Mf2ActorPolicy.ActorKind.CUSTOMER);
  }

  @Test
  void classifiesManagerByRoleOnly() {
    assertThat(policy.actorKind(Set.of(Roles.MANAGER))).isEqualTo(Mf2ActorPolicy.ActorKind.MANAGER);
    assertThat(policy.actorKind(Set.of("SERVICE_MANAGER")))
        .isEqualTo(Mf2ActorPolicy.ActorKind.MANAGER);
  }

  @Test
  void adminHasNoMf2ActorKind() {
    assertThat(policy.actorKind(Set.of(Roles.ADMIN))).isEqualTo(Mf2ActorPolicy.ActorKind.NONE);
  }

  @Test
  void inspectorHasInspectorActorKindForPayoutHistory() {
    assertThat(policy.actorKind(Set.of(Roles.INSPECTOR)))
        .isEqualTo(Mf2ActorPolicy.ActorKind.INSPECTOR);
  }
}
