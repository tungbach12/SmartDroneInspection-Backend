package com.smartdroneinspection.users.domain;

import com.smartdroneinspection.shared.auth.Roles;

public enum UserRole {
  PLATFORM_ADMINISTRATOR(Roles.PLATFORM_ADMINISTRATOR),
  ORGANIZATION_MANAGER(Roles.ORGANIZATION_MANAGER),
  SERVICE_OPERATIONS_MANAGER(Roles.SERVICE_OPERATIONS_MANAGER),
  INSPECTOR(Roles.INSPECTOR),
  MAINTENANCE_ENGINEER(Roles.MAINTENANCE_ENGINEER);

  private final String value;

  UserRole(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean serviceRole() {
    return this == SERVICE_OPERATIONS_MANAGER || this == INSPECTOR || this == MAINTENANCE_ENGINEER;
  }
}
