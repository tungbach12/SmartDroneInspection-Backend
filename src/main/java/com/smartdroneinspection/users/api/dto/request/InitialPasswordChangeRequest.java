package com.smartdroneinspection.users.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InitialPasswordChangeRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(max = 128) String currentPassword,
    @NotBlank @Size(min = 15, max = 128) String password) {}
