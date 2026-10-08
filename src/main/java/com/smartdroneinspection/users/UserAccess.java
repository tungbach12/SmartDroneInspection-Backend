package com.smartdroneinspection.users;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Public users-module boundary for feature authorization checks. */
public interface UserAccess {

  Optional<ActiveUser> findActiveUser(UUID userId);

  record ActiveUser(UUID id, Set<String> roles, UUID organizationId) {

    public ActiveUser(UUID id, Set<String> roles) {
      this(id, roles, null);
    }

    public ActiveUser {
      roles = Set.copyOf(roles);
    }

    public boolean hasRole(String role) {
      return roles.contains(role);
    }
  }
}
