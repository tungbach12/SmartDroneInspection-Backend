package com.smartdroneinspection.maintenance.domain.enums;

/**
 * Execution record lifecycle.
 *
 * <p>{@code SUBMITTED} records an engineer's own timesheet. Verification is the lead's separate
 * act; nothing here closes corrective work, because MF4-18 requires independent acceptance.
 */
public enum WorkLogStatus {
  IN_PROGRESS,
  PAUSED_FOR_CHANGE,
  SUBMITTED,
  VERIFIED
}
