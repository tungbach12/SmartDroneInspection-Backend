package com.smartdroneinspection.users.domain.enums;

import com.smartdroneinspection.shared.auth.Roles;

public enum UserRole {
  ADMIN(Roles.ADMIN),
  MANAGER(Roles.MANAGER),
  CUSTOMER(Roles.CUSTOMER),
  INSPECTOR(Roles.INSPECTOR);

  private final String value;

  UserRole(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public static UserRole fromName(String raw) {
    if (raw == null) {
      throw new IllegalArgumentException("Role name is required");
    }
    return UserRole.valueOf(raw.trim());
  }
}
