package com.smartdroneinspection.inspections.api.dto.request;

import com.smartdroneinspection.inspections.domain.enums.FindingSeverity;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * MF3-06: the Inspector accepts a finding and states what it is.
 *
 * <p>Every field is optional. A field that is not sent keeps the value the finding already had, so
 * accepting a suggestion does not have to restate what the detector already said.
 */
public record ReviewFindingRequest(
    UUID evidenceId,
    @Size(max = 160) String defectLabel,
    FindingSeverity severity,
    @Size(max = 4000) String technicalNotes,
    @Size(max = 4000) String recommendedAction) {}
