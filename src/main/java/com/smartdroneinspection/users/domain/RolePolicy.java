package com.smartdroneinspection.users.domain;

import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class RolePolicy {

  public void validate(ActorZone zone, Set<UserRole> roles) {
    if (roles == null || roles.isEmpty()) {
      throw new IllegalArgumentException("At least one role is required.");
    }
    if (roles.size() != 1) {
      throw new IllegalArgumentException("Exactly one role is required.");
    }
    UserRole role = roles.iterator().next();

    switch (zone) {
      case PLATFORM -> {
        if (role != UserRole.ADMIN && role != UserRole.MANAGER) {
          throw new IllegalArgumentException("Platform users must have exactly one platform role.");
        }
      }
      case CUSTOMER -> {
        if (role != UserRole.CUSTOMER) {
          throw new IllegalArgumentException("Customer users must carry only the Customer role.");
        }
      }
      case INSPECTOR -> {
        if (role != UserRole.INSPECTOR) {
          throw new IllegalArgumentException("Inspectors must carry only the Inspector role.");
        }
      }
    }
  }
}
