package com.smartdroneinspection.inspections.domain.enums;

/**
 * Where an inspection stands across MF1 through MF4 (SRS 3.4, MF2-10 and MF2-12).
 *
 * <p>These values are the {@code ck_inspections_status} vocabulary written by V26. The pre-cutover
 * names such as {@code AWAITING_AI_REVIEW} are gone from the schema and must not come back: an
 * entity enum that drifts from its CHECK constraint only fails at the moment a row is written, and
 * fails as a 500 rather than as a rejected workflow step.
 *
 * <p>{@code READY_FOR_FLIGHT} is a decision reached by a named reviewer in MF2-07. It is not a
 * licence, and reaching it says nothing about whether the aircraft may fly.
 */
public enum InspectionStatus {
  DRAFT,
  ASSIGNED,
  PREPARING,
  READY_FOR_FLIGHT,
  IN_PROGRESS,
  FIELD_COMPLETED,
  REPORT_DRAFT,
  REPORT_PUBLISHED,
  REPAIR_PENDING,
  COMPLETED,
  CANCELLED
}
