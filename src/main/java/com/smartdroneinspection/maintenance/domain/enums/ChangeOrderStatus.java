package com.smartdroneinspection.maintenance.domain.enums;

/**
 * Change control lifecycle.
 *
 * <p>MF4-12 holds additional work unauthorized until a decision is recorded, so only {@code
 * APPROVED} may widen the authorized amount. {@code RETURNED} sends the proposal back to the team
 * for clarification without rejecting it outright.
 */
public enum ChangeOrderStatus {
  DRAFT,
  AWAITING_APPROVAL,
  APPROVED,
  REJECTED,
  RETURNED,
  SUPERSEDED
}
