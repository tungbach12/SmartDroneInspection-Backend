package com.smartdroneinspection.assets.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record InspectionScheduleResponse(
    UUID id,
    UUID assetId,
    UUID checklistTemplateId,
    String frequencyUnit,
    int frequencyInterval,
    Instant nextDueAt,
    String status) {}
