package com.smartdroneinspection.contracts.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record EvaluatePartnerCandidatesRequest(
    @NotNull Instant plannedStartAt,
    @NotNull Instant plannedEndAt,
    List<@NotBlank @Size(max = 64) String> requiredDroneFeatureCodes) {

  public EvaluatePartnerCandidatesRequest {
    requiredDroneFeatureCodes =
        requiredDroneFeatureCodes == null
            ? List.of()
            : Collections.unmodifiableList(new ArrayList<>(requiredDroneFeatureCodes));
  }
}
