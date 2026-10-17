package com.smartdroneinspection.inspections.domain.enums;

/**
 * MF3-09: what a reviewer return asks the author to revise. A text revision leaves the findings
 * baseline alone; a findings revision reopens Findings, which invalidates the confirmation and
 * forces the Inspector to look at the evidence and findings again before drafting.
 */
public enum ReportReturnReasonType {
  TEXT_REVISION,
  FINDINGS_REVISION
}
