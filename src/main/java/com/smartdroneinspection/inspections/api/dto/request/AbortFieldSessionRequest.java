package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The MF2-11 abort reason.
 *
 * <p>Kept separate from {@link PostponeFieldSessionRequest} rather than sharing one "reason" field:
 * a postponement and an abort leave the inspection in different states, and reusing one payload
 * would let a client close a session as postponed when the field outcome was actually an abort.
 */
public record AbortFieldSessionRequest(@NotBlank @Size(max = 2000) String reason) {}
