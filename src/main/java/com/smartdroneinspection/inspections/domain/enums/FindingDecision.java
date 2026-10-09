package com.smartdroneinspection.inspections.domain.enums;

/**
 * The reviewer's final decision on a finding (MF3-09). Only a confirmed finding enters official
 * statistics and corrective-work scope; a rejected candidate never becomes official by appearing in
 * a generated draft.
 */
public enum FindingDecision {
  CONFIRMED,
  MODIFIED,
  REJECTED
}
