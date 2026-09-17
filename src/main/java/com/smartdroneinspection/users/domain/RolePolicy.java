package com.smartdroneinspection.users.domain;

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
            || !roles.contains(UserRole.PLATFORM_ADMINISTRATOR)) {
          throw new IllegalArgumentException(
              "Platform users must have only the Platform Administrator role.");
        }
      }
      case CUSTOMER_ORGANIZATION -> {
        if (organizationId == null
            || roles.size() != 1
            || !roles.contains(UserRole.ORGANIZATION_MANAGER)) {
          throw new IllegalArgumentException(
              "Customer users must belong to an organization and be Organization Managers.");
        }
      }
      case SERVICE_WORKFORCE -> {
        if (organizationId != null || roles.stream().anyMatch(role -> !role.serviceRole())) {
          throw new IllegalArgumentException(
              "Service workforce users may only combine service roles.");
        }
      }
    }
  }
}
