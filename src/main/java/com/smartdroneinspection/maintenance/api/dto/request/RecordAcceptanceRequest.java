package com.smartdroneinspection.maintenance.api.dto.request;

import com.smartdroneinspection.maintenance.domain.enums.AcceptanceDecisionKind;
import jakarta.validation.constraints.NotNull;

/**
 * MF4-18/19: the independent reviewer's decision on a submitted completion report.
 *
 * <p>Only the designated independent reviewer may record it. Anything other than an acceptance must
 * carry technical comments.
 */
public record RecordAcceptanceRequest(
    @NotNull AcceptanceDecisionKind decision,
    String technicalComments,
    String acceptanceChecklist,
    String testResult,
    String signatureReference) {}
