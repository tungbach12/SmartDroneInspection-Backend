package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * MF3-13 forwards selected confirmed findings; an empty list completes a newly published inspection
 * without forwarding items.
 */
public record FindingHandoffRequest(@NotNull @Size(max = 500) List<@NotNull UUID> findingIds) {}
