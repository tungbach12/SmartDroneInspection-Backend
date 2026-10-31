package com.smartdroneinspection.users.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendRegistrationOtpRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @Size(max = 32) String phoneNumber,
    String channel) {}
