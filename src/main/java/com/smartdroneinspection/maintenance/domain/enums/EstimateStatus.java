package com.smartdroneinspection.maintenance.domain.enums;

/**
 * Estimate version lifecycle.
 *
 * <p>Mirrors {@code ck_maintenance_estimate_versions_status}. An {@code APPROVED} version is an
 * immutable snapshot: corrections create a new version rather than editing the approved one.
 */
public enum EstimateStatus {
  DRAFT,
  SUBMITTED,
  APPROVED,
  REJECTED,
  SUPERSEDED
}
