package com.smartdroneinspection.inspections.domain.enums;

import java.util.List;

/** Role of the human actor submitting a post-inspection rating for an Inspector. */
public enum RaterRole {
  CUSTOMER(List.of("evidenceQuality", "communication", "professionalism")),
  MANAGER(List.of("sopAndSafety", "technicalAccuracy", "timeliness"));

  private final List<String> expectedCriteriaKeys;

  RaterRole(List<String> expectedCriteriaKeys) {
    this.expectedCriteriaKeys = expectedCriteriaKeys;
  }

  public List<String> expectedCriteriaKeys() {
    return expectedCriteriaKeys;
  }
}
