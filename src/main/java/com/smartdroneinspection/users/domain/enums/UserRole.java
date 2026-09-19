package com.smartdroneinspection.users.domain.enums;

import com.smartdroneinspection.shared.auth.Roles;

public enum UserRole {
  ADMIN(Roles.ADMIN),
  CLIENT(Roles.CLIENT),
  SERVICE_MANAGER(Roles.SERVICE_MANAGER),
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
    return this == SERVICE_MANAGER || this == INSPECTOR || this == MAINTENANCE_ENGINEER;
  }
}
