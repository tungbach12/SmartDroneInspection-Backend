package com.smartdroneinspection.inspections.domain.enums;

/** Verification lifecycle state for an inspection's regulatory flight permit. */
public enum FlightPermitVerificationStatus {
  NOT_UPLOADED,
  PENDING_REVIEW,
  APPROVED,
  REJECTED
}
