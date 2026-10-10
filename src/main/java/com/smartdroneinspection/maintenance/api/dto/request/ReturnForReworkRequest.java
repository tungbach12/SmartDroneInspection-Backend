package com.smartdroneinspection.maintenance.api.dto.request;

import jakarta.validation.constraints.NotBlank;

/** MF4-13/18: a return to the team always carries the reason that must be acted on. */
public record ReturnForReworkRequest(@NotBlank String reason) {}
