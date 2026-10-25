package com.smartdroneinspection.users.domain.enums;

import com.smartdroneinspection.shared.auth.Roles;

public enum UserRole {
  ADMIN(Roles.ADMIN),
  PLATFORM_MANAGER(Roles.PLATFORM_MANAGER),
  ORG_MANAGER(Roles.ORG_MANAGER),
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
