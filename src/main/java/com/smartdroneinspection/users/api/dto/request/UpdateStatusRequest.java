package com.smartdroneinspection.users.api.dto.request;

import com.smartdroneinspection.users.domain.enums.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(@NotNull UserStatus status) {}
