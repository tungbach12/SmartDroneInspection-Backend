package com.smartdroneinspection.users.domain.enums;

import com.smartdroneinspection.shared.auth.Roles;

public enum UserRole {
  ADMIN(Roles.ADMIN),
  ORG_ADMIN(Roles.ORG_ADMIN),
  INSPECTOR(Roles.INSPECTOR),
  MAINTENANCE_ENGINEER(Roles.MAINTENANCE_ENGINEER);

  private final String value;

  UserRole(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
