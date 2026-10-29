package com.smartdroneinspection.users.domain;

import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RolePolicy {

  public void validate(ActorZone zone, UUID organizationId, Set<UserRole> roles) {
    if (roles == null || roles.isEmpty()) {
      throw new IllegalArgumentException("At least one role is required.");
    }

    switch (zone) {
      case PLATFORM -> {
        if (organizationId != null
            || roles.size() != 1
            || (roles.iterator().next() != UserRole.ADMIN
                && roles.iterator().next() != UserRole.MANAGER)) {
          throw new IllegalArgumentException("Platform users must have exactly one platform role.");
        }
      }
      case CUSTOMER_ORGANIZATION -> {
        if (organizationId == null
            || roles.size() != 1
            || roles.iterator().next() == UserRole.ADMIN
            || roles.iterator().next() == UserRole.MANAGER
            || roles.iterator().next() == UserRole.INSPECTOR) {
          throw new IllegalArgumentException(
              "Customer users must belong to an organization and carry an org-scoped role.");
        }
      }
      case INSPECTOR_PARTNER -> {
        if (organizationId != null || !roles.equals(Set.of(UserRole.INSPECTOR))) {
          throw new IllegalArgumentException(
              "Inspector partners must be organization-independent Inspectors.");
        }
      }
      case PROSPECT_CUSTOMER -> {
        if (organizationId != null || !roles.equals(Set.of(UserRole.CUSTOMER))) {
          throw new IllegalArgumentException(
              "Prospective customers must have only the Customer role and no organization.");
        }
      }
    }
  }
}
