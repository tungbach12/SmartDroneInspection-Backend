package com.smartdroneinspection.inspections.api.dto.request;

import com.smartdroneinspection.inspections.domain.enums.FindingDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** MF3-09: the qualified reviewer's final decision on a finding, with its human rationale. */
public record FindingDecisionRequest(
    @NotNull FindingDecision decision, @Size(max = 4000) String rationale) {}
