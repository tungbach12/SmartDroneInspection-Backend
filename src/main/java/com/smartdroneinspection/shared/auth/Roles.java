package com.smartdroneinspection.shared.auth;

public final class Roles {

  public static final String ADMIN = "ADMIN";
  public static final String MANAGER = "MANAGER";
  public static final String CUSTOMER = "CUSTOMER";
  public static final String INSPECTOR = "INSPECTOR";
  public static final String MAINTENANCE_ENGINEER = "MAINTENANCE_ENGINEER";

  public static final String PLATFORM_MANAGER = MANAGER;
  public static final String ORG_MANAGER = CUSTOMER;
  public static final String SERVICE_MANAGER = MANAGER;
  public static final String CLIENT = CUSTOMER;

  private Roles() {}
}
