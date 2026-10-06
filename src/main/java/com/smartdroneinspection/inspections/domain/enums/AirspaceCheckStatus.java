package com.smartdroneinspection.inspections.domain.enums;

/**
 * Status of airspace lookup, restrictions and authoritative flight permits (BR-08, GAP-F-02,
 * GAP-F-09).
 *
 * <p>Five values per V17 CHECK {@code ck_drone_mission_plans_airspace}. A public map lookup
 * (cambay.mod.gov.vn per QD 18/2020/QD-TTg) is an advisory pre-check, not a flight permit. Mission
 * approval is blocked until flight clearance is verified.
 */
public enum AirspaceCheckStatus {
  NOT_CHECKED,
  CLEARANCE_REQUIRED,
  MANUAL_REVIEW,
  CLEARED,
  BLOCKED;

  /**
   * Whether this status counts as a recorded, non-blocked airspace determination. Mirrors the V17
   * clearance gate: approval requires a completed determination that is not BLOCKED; a
   * CLEARANCE_REQUIRED determination additionally demands a permit reference (enforced at approval
   * and by the DB CHECK). {@code BLOCKED} and {@code NOT_CHECKED} never clear.
   */
  public boolean isFlightCleared() {
    return this == CLEARED || this == MANUAL_REVIEW || this == CLEARANCE_REQUIRED;
  }

  /** Whether an authority-issued flight permit reference is required before approval. */
  public boolean requiresPermit() {
    return this == CLEARANCE_REQUIRED;
  }
}
