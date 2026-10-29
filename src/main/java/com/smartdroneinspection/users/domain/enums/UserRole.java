package com.smartdroneinspection.users.domain.enums;

import com.smartdroneinspection.shared.auth.Roles;

public enum UserRole {
  ADMIN(Roles.ADMIN),
  MANAGER(Roles.MANAGER),
  CUSTOMER(Roles.CUSTOMER),
  INSPECTOR(Roles.INSPECTOR),
  MAINTENANCE_ENGINEER(Roles.MAINTENANCE_ENGINEER);

  public static final UserRole PLATFORM_MANAGER = MANAGER;
  public static final UserRole ORG_MANAGER = CUSTOMER;
  public static final UserRole ORG_ADMIN = CUSTOMER;
  public static final UserRole SERVICE_MANAGER = MANAGER;
  public static final UserRole CLIENT = CUSTOMER;

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
    return switch (raw.trim()) {
      case "PLATFORM_MANAGER", "SERVICE_MANAGER" -> MANAGER;
      case "ORG_MANAGER", "ORG_ADMIN", "CLIENT" -> CUSTOMER;
      default -> UserRole.valueOf(raw.trim());
    };
  }
}
