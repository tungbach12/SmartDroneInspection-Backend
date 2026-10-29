package com.smartdroneinspection.contracts.service;

import com.smartdroneinspection.shared.auth.Roles;
import java.util.Set;
import java.util.UUID;

/** MF2-only compatibility mapping for the legacy account roles. */
public class Mf2ActorPolicy {

  public ActorKind actorKind(Set<String> roles, UUID organizationId) {
    if (roles == null) {
      return ActorKind.NONE;
    }
    if ((roles.contains(Roles.CUSTOMER)
            || roles.contains("CLIENT")
            || roles.contains("ORG_MANAGER"))
        && organizationId != null) {
      return ActorKind.CUSTOMER;
    }
    if ((roles.contains(Roles.MANAGER)
            || roles.contains("SERVICE_MANAGER")
            || roles.contains("PLATFORM_MANAGER"))
        && organizationId == null) {
      return ActorKind.MANAGER;
    }
    return ActorKind.NONE;
  }

  public enum ActorKind {
    NONE,
    CUSTOMER,
    MANAGER
  }
}
