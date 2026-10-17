package com.smartdroneinspection.inspections.api.dto.request;

import com.smartdroneinspection.inspections.domain.enums.FindingSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * MF3-06 correction of an Inspector's own finding while the report is not submitted. The reviewer's
 * decision is not part of this shape: a decided finding is changed through the reviewer, not here.
 */
public record UpdateFindingRequest(
    @NotNull UUID evidenceId,
    @NotBlank @Size(max = 160) String defectLabel,
    @NotNull FindingSeverity severity,
    @Size(max = 1000) String locationDescription,
    @NotBlank @Size(max = 4000) String technicalNotes,
    @Size(max = 4000) String recommendedAction,
    @Size(max = 200) String component,
    @Size(max = 4000) String description,
    @Size(max = 4000) String observedCondition,
    @Size(max = 24) String priority,
    @Size(max = 4000) String measurement,
    Boolean repairRequired) {}
