package com.smartdroneinspection.assets.readiness;

/** Drone-document lifecycle values exposed by the readiness contract. */
public enum DroneDocumentReadinessStatus {
  PENDING_REVIEW,
  ACTIVE,
  EXPIRED,
  REJECTED,
  REVOKED
}
