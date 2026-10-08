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
        if (organizationId != null || roles.size() != 1 || !roles.contains(UserRole.ADMIN)) {
          throw new IllegalArgumentException("Platform users must have only the Admin role.");
        }
      }
      case CUSTOMER_ORGANIZATION -> {
        if (organizationId == null
            || roles.isEmpty()
            || roles.stream()
                .anyMatch(
                    role ->
                        role != UserRole.ORG_ADMIN
                            && role != UserRole.INSPECTOR
                            && role != UserRole.MAINTENANCE_ENGINEER)) {
          throw new IllegalArgumentException(
              "Customer users must belong to an organization and carry an org-scoped role.");
        }
      }
    }
  }
}
