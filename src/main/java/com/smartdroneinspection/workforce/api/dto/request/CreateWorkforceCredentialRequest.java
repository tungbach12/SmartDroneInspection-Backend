package com.smartdroneinspection.workforce.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * ORG_MANAGER credential record request. The organization is always derived from the authenticated
 * principal, so no organization or actor-zone field is accepted here.
 */
public record CreateWorkforceCredentialRequest(
    @NotNull UUID userId,
    @NotBlank @Size(max = 64) String credentialType,
    @Size(max = 200) String issuer,
    @Size(max = 200) String credentialReference,
    Instant issuedAt,
    Instant expiresAt,
    UUID evidenceId) {}
