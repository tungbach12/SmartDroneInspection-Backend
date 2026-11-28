package com.smartdroneinspection.shared.api;

/** Read boundary exposing Admin-configured operational dispatch policies across modules. */
public interface PlatformOperationsPolicyPort {

  int DEFAULT_ASSIGNMENT_ACCEPTANCE_TIMEOUT_HOURS = 24;
  int DEFAULT_SERVICE_REQUEST_LEAD_DAYS = 20;
  int DEFAULT_API_REQUEST_TIMEOUT_SECONDS = 120;

  int getAssignmentAcceptanceTimeoutHours();

  int getServiceRequestLeadDays();

  int getApiRequestTimeoutSeconds();
}
