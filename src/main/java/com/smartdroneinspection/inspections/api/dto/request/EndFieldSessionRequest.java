package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The Inspector's recorded field outcome before MF2-12 closes the session. */
public record EndFieldSessionRequest(@NotBlank @Size(max = 2000) String outcomeNotes) {}
