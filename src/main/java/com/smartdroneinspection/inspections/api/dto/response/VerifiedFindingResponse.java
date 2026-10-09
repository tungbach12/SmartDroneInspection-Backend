package com.smartdroneinspection.inspections.api.dto.response;

import com.smartdroneinspection.inspections.domain.enums.FindingDecision;
import com.smartdroneinspection.inspections.domain.enums.FindingSeverity;
import com.smartdroneinspection.inspections.domain.enums.VerifiedFindingSource;
import com.smartdroneinspection.inspections.domain.enums.VerifiedFindingStatus;
import java.util.UUID;

/**
 * A human-confirmed finding. {@code decision} records the reviewer's final call; only a confirmed
 * finding is official and eligible for corrective work.
 */
public record VerifiedFindingResponse(
    UUID id,
    UUID inspectionId,
    UUID evidenceId,
    UUID aiCandidateId,
    VerifiedFindingSource source,
    String findingCode,
    String defectLabel,
    FindingSeverity severity,
    String locationDescription,
    String technicalNotes,
    String recommendedAction,
    String component,
    String description,
    String observedCondition,
    String priority,
    String measurement,
    FindingDecision decision,
    boolean repairRequired,
    VerifiedFindingStatus status) {}
