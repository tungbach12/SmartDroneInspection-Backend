package com.smartdroneinspection.maintenance.domain.enums;

/**
 * The independent reviewer's decision on a completed work order.
 *
 * <p>Mirrors {@code ck_maintenance_acceptance_decision}. {@code REINSPECTION_REQUIRED} records the
 * decision and its reason; dispatching the linked inspection is MF1's responsibility and is not
 * performed by MF4.
 */
public enum AcceptanceDecisionKind {
  ACCEPTED,
  REWORK_REQUIRED,
  REINSPECTION_REQUIRED,
  REJECTED
}
