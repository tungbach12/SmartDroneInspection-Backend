package com.smartdroneinspection.inspections.domain.enums;

/**
 * The Inspector's substantive evidence decision (MF3-03). Technical validation never sets this:
 * only the assigned Inspector decides whether the set is adequate.
 */
public enum EvidenceQualityDecisionType {
  PENDING,
  ACCEPTED,
  REUPLOAD_REQUIRED,
  ADDITIONAL_SESSION_REQUIRED,
  LIMITED
}
