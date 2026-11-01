package com.smartdroneinspection.users.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SendLoginOtpRequest(
    @NotBlank @Size(max = 200) String challengeToken,
    @NotBlank @Pattern(regexp = "EMAIL|SMS") String channel) {}
