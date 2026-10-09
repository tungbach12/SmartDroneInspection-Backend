package com.smartdroneinspection.inspections.api.dto.request;

import com.smartdroneinspection.inspections.domain.enums.AiFindingCandidateStatus;
import com.smartdroneinspection.inspections.domain.enums.FindingSeverity;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * MF3-06 candidate review. A REJECT decision needs a reason; a CONFIRM or MODIFY decision needs the
 * human classification that becomes the official finding.
 */
public record ReviewCandidateRequest(
    @NotNull AiFindingCandidateStatus decision,
    @Size(max = 160) String defectLabel,
    FindingSeverity severity,
    @Size(max = 1000) String locationDescription,
    @Size(max = 4000) String technicalNotes,
    @Size(max = 4000) String recommendedAction,
    @Size(max = 200) String component,
    @Size(max = 4000) String description,
    @Size(max = 4000) String observedCondition,
    @Size(max = 24) String priority,
    @Size(max = 4000) String measurement,
    @Size(max = 2000) String reason,
    Boolean repairRequired) {}
