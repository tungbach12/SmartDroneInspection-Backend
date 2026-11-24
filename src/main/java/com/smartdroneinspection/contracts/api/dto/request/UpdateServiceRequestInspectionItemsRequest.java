package com.smartdroneinspection.contracts.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Replaces the per-request inspection checkpoints configured by the assigned Manager. */
public record UpdateServiceRequestInspectionItemsRequest(
    @NotNull @Size(max = 50) List<@NotBlank @Size(max = 200) String> inspectionItems) {}
