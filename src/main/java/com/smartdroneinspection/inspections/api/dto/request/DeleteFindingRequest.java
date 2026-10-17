package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * MF3-06 removal of a finding from the record. The reason is required because the finding stays in
 * the database for audit rather than disappearing.
 */
public record DeleteFindingRequest(@NotBlank @Size(max = 2000) String reason) {}
