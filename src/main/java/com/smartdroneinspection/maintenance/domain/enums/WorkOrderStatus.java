package com.smartdroneinspection.maintenance.domain.enums;

/**
 * Work order lifecycle from the MF4 target schema.
 *
 * <p>Mirrors the {@code ck_maintenance_work_orders_status} check constraint. The values beyond
 * {@code APPROVED} belong to later slices; they exist so the constraint holds and so the enum
 * describes the whole target lifecycle rather than only what is reachable today.
 */
public enum WorkOrderStatus {
  DRAFT,
  AWAITING_APPROVAL,
  APPROVED,
  READY,
  IN_PROGRESS,
  WORK_COMPLETED,
  SUBMITTED_FOR_ACCEPTANCE,
  ACCEPTED,
  COST_RECONCILED,
  CLOSED,
  REWORK_REQUIRED,
  REINSPECTION_REQUIRED
}
