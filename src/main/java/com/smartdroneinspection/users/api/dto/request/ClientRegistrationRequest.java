package com.smartdroneinspection.users.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ClientRegistrationRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(max = 200) String fullName,
    @NotBlank @Size(max = 200) String organizationName,
    @NotBlank
        @Pattern(
            regexp = "[A-Za-z0-9][A-Za-z0-9_-]{2,63}",
            message =
                "Organization code must contain 3 to 64 letters, numbers, hyphens, or underscores.")
        String organizationCode,
    @NotBlank String password) {}
