package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ClientReportDecisionRequest(
    @NotNull Decision decision, @Size(max = 2000) String reason) {

  public enum Decision {
    ACCEPT,
    REQUEST_REVISION
  }
}
