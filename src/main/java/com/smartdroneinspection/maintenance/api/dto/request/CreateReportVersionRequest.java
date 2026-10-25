package com.smartdroneinspection.maintenance.api.dto.request;

import jakarta.validation.constraints.NotBlank;

/** MF4-14/16: the report author opens a completion report version. */
public record CreateReportVersionRequest(@NotBlank String contentSnapshot) {}
