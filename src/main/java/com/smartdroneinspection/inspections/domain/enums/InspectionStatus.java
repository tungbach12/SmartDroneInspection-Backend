package com.smartdroneinspection.inspections.domain.enums;

/**
 * Target inspection lifecycle from Report 3 business-flows section VI.
 *
 * <p>READY_FOR_FLIGHT records an internal human readiness decision, not external flight authority.
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
