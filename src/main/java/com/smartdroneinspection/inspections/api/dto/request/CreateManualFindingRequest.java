package com.smartdroneinspection.inspections.api.dto.request;

import com.smartdroneinspection.inspections.domain.enums.FindingSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateManualFindingRequest(
    @NotNull UUID evidenceId,
    @NotBlank @Size(max = 160) String defectLabel,
    @NotNull FindingSeverity severity,
    @NotBlank @Size(max = 1000) String locationDescription,
    @NotBlank @Size(max = 4000) String technicalNotes,
    @Size(max = 4000) String recommendedAction) {}
