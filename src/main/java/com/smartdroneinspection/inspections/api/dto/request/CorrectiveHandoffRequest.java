package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * MF3-12/13: closes the corrective-action handoff for a published report.
 *
 * <p>{@code noRepairRationale} is required only when no confirmed finding requires corrective work,
 * so a no-repair outcome is a recorded human statement rather than an absent list.
 */
public record CorrectiveHandoffRequest(@NotBlank @Size(max = 2000) String rationale) {}
