package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * The permits and credentials an administrator links to a preparation (MF2-04).
 *
 * <p>Ids only. The organization records what it holds, and MF2-05 reads the status and validity
 * back off those records rather than trusting anything asserted here.
 */
public record LinkPermitReferencesRequest(@NotNull @NotEmpty List<UUID> permitIds) {}
