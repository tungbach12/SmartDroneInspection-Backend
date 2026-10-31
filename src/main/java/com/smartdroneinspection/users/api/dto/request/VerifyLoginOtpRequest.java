package com.smartdroneinspection.users.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifyLoginOtpRequest(
    @NotBlank @Size(max = 200) String challengeToken,
    @NotBlank @Pattern(regexp = "\\d{6}") String code) {}
