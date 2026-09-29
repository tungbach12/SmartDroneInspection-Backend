package com.smartdroneinspection.assets.api.dto.response;

import java.util.UUID;

public record ScheduleProposalResponse(
    UUID id,
    UUID assetId,
    UUID checklistTemplateId,
    String frequencyUnit,
    int frequencyInterval,
    String status,
    String managerNote) {}
