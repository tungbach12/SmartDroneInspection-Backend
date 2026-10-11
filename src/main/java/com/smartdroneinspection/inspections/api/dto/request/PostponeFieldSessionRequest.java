package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The MF2-09 postponement reason.
 *
 * <p>Required on the record rather than derived: a postponed session without a stated reason cannot
 * be told apart from one the Inspector abandoned, and the distinction matters when the inspection
 * is started again.
 */
public record PostponeFieldSessionRequest(@NotBlank @Size(max = 2000) String reason) {}
