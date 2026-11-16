package com.smartdroneinspection.contracts.api.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignServiceRequestManager(@NotNull UUID managerId) {}
