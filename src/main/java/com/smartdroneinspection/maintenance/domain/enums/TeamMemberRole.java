package com.smartdroneinspection.maintenance.domain.enums;

/**
 * A team member's responsibility on a work order.
 *
 * <p>{@code LEAD} and {@code REPORT_AUTHOR} are unique per work order while active; the database
 * enforces that with partial unique indexes. {@code MEMBER} is unbounded. The final accepting
 * reviewer is deliberately not one of these roles: they must sit outside the executing team.
 */
public enum TeamMemberRole {
  LEAD,
  REPORT_AUTHOR,
  MEMBER
}
