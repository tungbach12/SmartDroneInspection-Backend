package com.smartdroneinspection.users.domain.enums;

import com.smartdroneinspection.shared.auth.Roles;

public enum UserRole {
  PLATFORM_ADMIN(Roles.PLATFORM_ADMIN),
  CLIENT(Roles.CLIENT),
  PROVIDER_MANAGER(Roles.PROVIDER_MANAGER),
  INSPECTOR(Roles.INSPECTOR),
  MAINTENANCE_ENGINEER(Roles.MAINTENANCE_ENGINEER),
  PLATFORM_OPERATOR(Roles.PLATFORM_OPERATOR);

  private final String value;

  UserRole(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean serviceRole() {
    return this == PROVIDER_MANAGER || this == INSPECTOR || this == MAINTENANCE_ENGINEER;
  }
}
