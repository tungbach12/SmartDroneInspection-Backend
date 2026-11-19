package com.smartdroneinspection.shared.api;

/** Read boundary exposing Admin-configured operational dispatch policies across modules. */
public interface PlatformOperationsPolicyPort {

  int DEFAULT_ASSIGNMENT_ACCEPTANCE_TIMEOUT_HOURS = 24;

  int getAssignmentAcceptanceTimeoutHours();
}
