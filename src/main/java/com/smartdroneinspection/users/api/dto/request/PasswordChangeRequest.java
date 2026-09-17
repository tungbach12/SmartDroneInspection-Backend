package com.smartdroneinspection.users.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
    @NotBlank @Size(max = 128) String currentPassword,
    @NotBlank @Size(min = 15, max = 128) String newPassword) {}
