package com.smartdroneinspection.assets.api.dto.request;

import com.smartdroneinspection.assets.domain.enums.AssignmentResponse;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The assigned inspector's answer to a pairing (MF2-01).
 *
 * <p>{@code rejectionReason} is required when declining; the entity enforces that too, so an empty
 * reason cannot reach the record even if validation is bypassed.
 */
public record RespondToAssignmentRequest(
    @NotNull AssignmentResponse response, @Size(max = 2000) String rejectionReason) {}
