package com.smartdroneinspection.inspectionrequests.domain;

public enum InspectionRequestStatus {
  DRAFT,
  SUBMITTED,
  UNDER_REVIEW,
  REVISION_REQUIRED,
  QUOTED,
  AWAITING_CLIENT_APPROVAL,
  ORDER_CONFIRMED,
  ASSIGNMENT_PENDING,
  READY_FOR_INSPECTION,
  MANUAL_REVIEW,
  CANCELLED
}
