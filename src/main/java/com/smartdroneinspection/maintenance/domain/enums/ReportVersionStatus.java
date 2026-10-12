package com.smartdroneinspection.maintenance.domain.enums;

/**
 * Maintenance completion report lifecycle.
 *
 * <p>Mirrors {@code ck_maintenance_report_versions_status}. The sequence deliberately mirrors the
 * MF3 inspection report: the author verifies what was drafted, and a different qualified person
 * approves it. Acceptance is a further, separate decision recorded against the approved version.
 */
public enum ReportVersionStatus {
  DRAFT,
  AUTHOR_VERIFIED,
  SUBMITTED,
  RETURNED,
  APPROVED,
  SUPERSEDED
}
