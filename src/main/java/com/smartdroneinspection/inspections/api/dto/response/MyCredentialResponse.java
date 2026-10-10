package com.smartdroneinspection.inspections.api.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * One credential held by the caller.
 *
 * <p>MF2-07 approval requires the reviewer to name their own credential, and they cannot know its
 * id without reading it from somewhere. This returns their own records only: there is no id
 * parameter and no organization parameter, so a caller cannot ask about anyone else.
 *
 * <p>This DTO lives in the inspections module rather than beside the workforce credential it
 * describes. {@code workforce} owns the credential; {@code inspections} owns the MF2-07 review that
 * consumes it, and the review's HTTP contract is transport code of this module. A workforce
 * transport type would be imported across a module boundary, which the Modulith boundary check
 * rejects.
 *
 * <p>{@code verificationReason} and {@code verifiedAt} travel because a reviewer choosing which
 * credential to present is entitled to know whether the stored record says it was verified and
 * when. The evidence document itself does not travel here.
 */
public record MyCredentialResponse(
    UUID id,
    String credentialType,
    String issuer,
    String credentialReference,
    Instant issuedAt,
    Instant expiresAt,
    String status,
    UUID verifiedByUserId,
    Instant verifiedAt,
    String verificationReason) {}
