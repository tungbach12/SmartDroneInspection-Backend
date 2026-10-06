package com.smartdroneinspection.users;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Public users-module boundary for feature authorization checks. */
public interface UserAccess {

  Optional<ActiveUser> findActiveUser(UUID userId);

  record ActiveUser(UUID id, Set<String> roles, UUID organizationId, UUID providerId) {

    public ActiveUser(UUID id, Set<String> roles, UUID organizationId) {
      this(id, roles, organizationId, null);
    }

    public ActiveUser(UUID id, Set<String> roles) {
      this(id, roles, null, null);
    }

    public ActiveUser {
      roles = Set.copyOf(roles);
    }

    public boolean hasRole(String role) {
      return roles.contains(role);
    }

    /** The provider this identity speaks for, or null. Never derived from the roles. */
    public boolean hasProviderScope() {
      return providerId != null;
    }
  }
}
