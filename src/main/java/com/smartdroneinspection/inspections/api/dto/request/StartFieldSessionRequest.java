package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * The MF2-10 start of a field session.
 *
 * <p>{@code preFlightChecklistNote} is the Inspector's own record that MF2-09's pre-flight
 * checklist was completed on site. It is required and it is prose rather than a checkbox flag,
 * because a boolean a client sets automatically would attest to nothing.
 */
public record StartFieldSessionRequest(
    UUID checklistTemplateId, @NotBlank @Size(max = 2000) String preFlightChecklistNote) {}
