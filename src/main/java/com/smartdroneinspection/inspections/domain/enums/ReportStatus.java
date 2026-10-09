package com.smartdroneinspection.inspections.domain.enums;

/**
 * Report version lifecycle. The human gates are explicit: the author verifies, the reviewer
 * decides, and publication is a separate act. There is no timer-based default and no autonomous
 * approval.
 */
public enum ReportStatus {
  DRAFT,
  AUTHOR_VERIFIED,
  SUBMITTED,
  RETURNED,
  APPROVED,
  PUBLISHED,
  SUPERSEDED
}
