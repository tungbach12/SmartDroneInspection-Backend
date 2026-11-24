package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateFieldSessionFlightRequest(
    @Size(max = 160) String flightLabel, LocalDate flightDate) {}
