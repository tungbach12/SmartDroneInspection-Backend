package com.smartdroneinspection.assets.api.dto.request;

import com.smartdroneinspection.assets.domain.enums.PermitStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * A permit/compliance record as the organization states it. The organization is always derived from
 * the authenticated principal, so no organization field is accepted here.
 *
 * <p>{@code status} is the organization's own record of where its application stands. Recording
 * {@code ACTIVE} is a transcription of what the organization holds; it is not a claim that an
 * authority granted or confirmed anything. Any value outside {@link PermitStatus} is refused by the
 * deserializer, so no status can smuggle in a stronger claim than the schema allows.
 */
public record CreateFlightPermitRequest(
    @NotBlank @Size(max = 64) String permitType,
    UUID assetId,
    @Size(max = 200) String areaReference,
    @Size(max = 200) String issuingAuthority,
    @Size(max = 200) String permitReference,
    @Size(max = 4000) String geographicScope,
    Instant validFrom,
    Instant validUntil,
    @Size(max = 4000) String conditions,
    PermitStatus status,
    UUID sourceEvidenceId) {}
