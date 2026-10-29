package com.smartdroneinspection.contracts.api.dto.request;

import jakarta.validation.constraints.NotNull;

public record SetPartnerResourceStatusRequest(@NotNull Boolean active) {}
