package com.smartdroneinspection.users.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateInspectorFlightLogRequest(
    @NotBlank @Size(max = 64) String flightCode,
    @NotNull LocalDate flightDate,
    @NotBlank @Size(max = 255) String missionTitle,
    @NotBlank @Size(max = 255) String droneModel,
    @Size(max = 128) String droneSerialNumber,
    @NotBlank @Size(max = 500) String location,
    @NotNull @Min(1) Integer flightDurationMinutes,
    @NotBlank @Size(max = 128) String inspectionType,
    boolean safetyIncident,
    String notes) {}
