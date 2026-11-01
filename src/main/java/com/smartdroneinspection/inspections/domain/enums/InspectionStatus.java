package com.smartdroneinspection.inspections.domain.enums;

/**
 * Streamlined inspection lifecycle (6 canonical states).
 *
 * <p>Flight readiness is governed by regulatory permit on file and active pilot licensing rather
 * than a separate readiness state. Report drafting takes place in FIELD_COMPLETED with domain
 * timestamps and snapshot hashes. Completed inspections conclude upon report publication.
 */
public enum InspectionStatus {
  SCHEDULED,
  IN_PROGRESS,
  FIELD_COMPLETED,
  UNDER_REVIEW,
  COMPLETED,
  CANCELLED
}
