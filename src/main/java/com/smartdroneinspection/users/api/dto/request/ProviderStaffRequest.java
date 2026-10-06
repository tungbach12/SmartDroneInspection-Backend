package com.smartdroneinspection.users.api.dto.request;

import com.smartdroneinspection.users.domain.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProviderStaffRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(max = 200) String fullName,
    @NotNull UserRole role) {}
