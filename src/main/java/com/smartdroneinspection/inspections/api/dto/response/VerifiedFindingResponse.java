package com.smartdroneinspection.inspections.api.dto.response;

import com.smartdroneinspection.inspections.domain.enums.FindingSeverity;
import com.smartdroneinspection.inspections.domain.enums.VerifiedFindingSource;
import java.util.UUID;

public record VerifiedFindingResponse(
    UUID id,
    UUID evidenceId,
    UUID aiCandidateId,
    String findingCode,
    VerifiedFindingSource source,
    String defectLabel,
    FindingSeverity severity,
    String locationDescription,
    String technicalNotes,
    String recommendedAction) {}
