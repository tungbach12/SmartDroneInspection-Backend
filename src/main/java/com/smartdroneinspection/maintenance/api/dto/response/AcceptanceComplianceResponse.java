package com.smartdroneinspection.maintenance.api.dto.response;

import java.util.List;

/**
 * MF4: RAG-assisted technical compliance check comparing verified work logs and test measurements
 * against the work order's acceptance criteria and engineering standards.
 */
public record AcceptanceComplianceResponse(
    int complianceScore,
    String status,
    String recommendation,
    String technicalSummary,
    List<ComplianceCheckItem> checks) {

  public record ComplianceCheckItem(
      String criteriaName, String requiredStandard, String actualMeasured, String resultStatus) {}
}
