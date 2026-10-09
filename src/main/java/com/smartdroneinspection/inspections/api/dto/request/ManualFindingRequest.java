package com.smartdroneinspection.inspections.api.dto.request;

import com.smartdroneinspection.inspections.domain.enums.FindingSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * MF3-06 manual finding. Available whenever automated detection is unavailable, incompatible, or
 * simply missed a defect, so an AI outage never blocks the inspection record.
 */
public record ManualFindingRequest(
    UUID evidenceId,
    @NotBlank @Size(max = 160) String defectLabel,
    @NotNull FindingSeverity severity,
    @NotBlank @Size(max = 1000) String locationDescription,
    @NotBlank @Size(max = 4000) String technicalNotes,
    @Size(max = 4000) String recommendedAction,
    @Size(max = 200) String component,
    @Size(max = 4000) String description,
    @Size(max = 4000) String observedCondition,
    @Size(max = 24) String priority,
    @Size(max = 4000) String measurement,
    Boolean repairRequired) {}
