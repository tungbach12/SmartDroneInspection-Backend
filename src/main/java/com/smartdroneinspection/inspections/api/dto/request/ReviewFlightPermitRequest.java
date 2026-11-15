package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Manager verification decision for a submitted flight permit. */
public record ReviewFlightPermitRequest(
    @NotNull Decision decision, @Size(max = 1000) String reason, @Size(max = 1000) String notes) {

  public enum Decision {
    APPROVED,
    REJECTED
  }
}
