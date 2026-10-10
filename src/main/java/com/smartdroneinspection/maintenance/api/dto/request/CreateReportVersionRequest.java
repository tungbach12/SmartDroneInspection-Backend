package com.smartdroneinspection.maintenance.api.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * MF4-14/16: the report author opens a completion report version.
 *
 * <p>The snapshot hashes bind the report to the approved scope, approved changes, submitted work
 * logs and reconciled actual costs that existed when it was written, so a later approval cannot be
 * compared against a different set of facts.
 */
public record CreateReportVersionRequest(
    @NotBlank String contentSnapshot,
    String approvedScopeHash,
    String changeSnapshotHash,
    String workLogSnapshotHash,
    String actualCostSnapshotHash,
    String llmProvider,
    String llmModel,
    String promptVersion) {}
