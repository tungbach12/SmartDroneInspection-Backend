package com.smartdroneinspection.contracts.service;

import com.smartdroneinspection.shared.auth.Roles;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Maps account roles to MF2 actor kinds. */
@Component
public class Mf2ActorPolicy {

  public ActorKind actorKind(Set<String> roles) {
    if (roles == null) {
      return ActorKind.NONE;
    }
    if (roles.contains(Roles.CUSTOMER)) {
      return ActorKind.CUSTOMER;
    }
    if (roles.contains(Roles.MANAGER)) {
      return ActorKind.MANAGER;
    }
    if (roles.contains(Roles.INSPECTOR)) {
      return ActorKind.INSPECTOR;
    }
    return ActorKind.NONE;
  }

  public enum ActorKind {
    NONE,
    CUSTOMER,
    MANAGER,
    INSPECTOR
  }
}
