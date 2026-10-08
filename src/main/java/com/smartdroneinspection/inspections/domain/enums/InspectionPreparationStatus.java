package com.smartdroneinspection.inspections.domain.enums;

/**
 * Where a preparation version stands (MF2-03, MF2-06, MF2-07).
 *
 * <p>{@code RETURNED} is the state that keeps the workflow honest: a reviewer who rejects a
 * submission does not edit it, so the inspector must reopen the draft, produce a new submission,
 * and let the reviewer judge that one. Nothing here implies a licence - only a named reviewer can
 * move a submission to {@code READY}, and readiness itself is recorded separately by MF2-07.
 */
public enum InspectionPreparationStatus {
  DRAFT,
  SUBMITTED,
  RETURNED,
  READY
}
