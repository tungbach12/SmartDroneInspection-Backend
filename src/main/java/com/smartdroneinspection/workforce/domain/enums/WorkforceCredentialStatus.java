package com.smartdroneinspection.workforce.domain.enums;

/** Status values stored in the V25 workforce credential table. */
public enum WorkforceCredentialStatus {
  DRAFT,
  PENDING_REVIEW,
  ACTIVE,
  EXPIRING_SOON,
  EXPIRED,
  REJECTED,
  SUSPENDED
}
