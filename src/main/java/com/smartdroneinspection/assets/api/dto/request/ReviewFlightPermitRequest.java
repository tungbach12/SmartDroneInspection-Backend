package com.smartdroneinspection.assets.api.dto.request;

import com.smartdroneinspection.assets.domain.enums.PermitStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * An internal review decision on a permit record, made by an administrator of the same customer
 * organization.
 *
 * <p>The decision is a human act inside the organization: it records who read the document, when,
 * and why. It is not a government, registry or authority verification of the permit, and it does
 * not declare the permit a legal basis for flight.
 */
public record ReviewFlightPermitRequest(
    @NotNull PermitStatus status, @NotBlank @Size(max = 2000) String reason) {}
