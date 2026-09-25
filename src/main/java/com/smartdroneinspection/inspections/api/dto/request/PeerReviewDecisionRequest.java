package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PeerReviewDecisionRequest(
    @NotNull Decision decision, @Size(max = 4000) String comments) {

  public enum Decision {
    CHANGES_REQUESTED,
    APPROVED
  }
}
