package com.smartdroneinspection.inspections.api.dto.request;

import com.smartdroneinspection.inspections.domain.enums.FindingSeverity;
import jakarta.validation.constraints.Size;

public record ReviewFindingCandidateRequest(
    Decision decision,
    @Size(max = 160) String defectLabel,
    FindingSeverity severity,
    @Size(max = 1000) String locationDescription,
    @Size(max = 4000) String technicalNotes,
    @Size(max = 4000) String recommendedAction,
    @Size(max = 2000) String rejectionReason) {

  public enum Decision {
    CONFIRM,
    MODIFY,
    REJECT
  }
}
