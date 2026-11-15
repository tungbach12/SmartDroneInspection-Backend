package com.smartdroneinspection.users.domain;

import com.smartdroneinspection.users.domain.enums.UserRole;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class RolePolicy {

  public void validate(Set<UserRole> roles) {
    if (roles == null || roles.isEmpty()) {
      throw new IllegalArgumentException("At least one role is required.");
    }
    if (roles.size() != 1) {
      throw new IllegalArgumentException("Exactly one role is required.");
    }
  }
}
