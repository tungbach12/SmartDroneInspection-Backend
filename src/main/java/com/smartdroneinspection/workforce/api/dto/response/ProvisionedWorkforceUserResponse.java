package com.smartdroneinspection.workforce.api.dto.response;

/**
 * Creation-only response carrying the one-time temporary password so the ORG_MANAGER can hand it to
 * the new user. The password is never persisted in plain text, logged, or returned by any other
 * operation.
 */
public record ProvisionedWorkforceUserResponse(
    WorkforceUserResponse user, String temporaryPassword) {}
