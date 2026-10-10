package com.smartdroneinspection.maintenance.api.dto.response;

import java.time.Instant;
import java.util.UUID;

/** MF4-01: a published repair-required finding available to the organization's administrator. */
public record RepairCandidateResponse(
    UUID assetId,
    UUID inspectionId,
    UUID reportId,
    UUID reportVersionId,
    UUID findingId,
    String findingCode,
    String defectLabel,
    String description,
    String severity,
    String recommendedAction,
    Instant reportPublishedAt) {}
