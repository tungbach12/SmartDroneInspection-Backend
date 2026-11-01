package com.smartdroneinspection.users.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmPasswordResetRequest(
    @NotBlank @Email String email,
    @NotBlank String challengeToken,
    @NotBlank @Size(min = 6, max = 6) String code,
    @NotBlank @Size(min = 8, max = 128) String newPassword) {}
