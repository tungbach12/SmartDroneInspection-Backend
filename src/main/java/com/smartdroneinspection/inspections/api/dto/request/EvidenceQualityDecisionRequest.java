package com.smartdroneinspection.inspections.api.dto.request;

import com.smartdroneinspection.inspections.domain.enums.EvidenceQualityDecisionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * MF3-03: the Inspector's substantive evidence decision. Reupload, additional-session and limited
 * outcomes require a stated reason or limitation so the report can disclose what was not observed.
 */
public record EvidenceQualityDecisionRequest(
    UUID fieldSessionId,
    @NotNull EvidenceQualityDecisionType decision,
    @Size(max = 8000) String shotListComparison,
    @Size(max = 2000) String limitationReason) {}
