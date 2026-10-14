package com.smartdroneinspection.workforce.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * ORG_ADMIN workforce provisioning request. The organization is always derived from the
 * authenticated principal, so no organization or actor-zone field is accepted here.
 */
public record ProvisionWorkforceUserRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(max = 200) String fullName,
    @NotEmpty Set<String> roles) {}
