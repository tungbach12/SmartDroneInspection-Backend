package com.smartdroneinspection.inspections.domain.enums;

/** Operational lifecycle of an engineering drone mission plan (GAP-F-08, BR-07, BR-16). */
public enum MissionPlanStatus {
  DRAFT,
  SUBMITTED,
  REVISION_REQUIRED,
  APPROVED,
  CANCELLED,
  SUPERSEDED
}
