package com.smartdroneinspection.contracts.api.dto.request;

import com.smartdroneinspection.contracts.domain.ServiceRequest.RequestType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record CreateServiceRequest(
    @NotNull UUID assetId,
    @NotBlank @Size(max = 4000) String description,
    @NotNull LocalDate requestedFrom,
    @NotNull LocalDate requestedUntil,
    @NotBlank @Size(max = 64) String timezone,
    LocalTime windowStart,
    LocalTime windowEnd,
    @NotNull RequestType requestType,
    String recurrenceUnit,
    Integer recurrenceInterval,
    Integer occurrenceCount,
    LocalDate recurrenceEndDate) {}
