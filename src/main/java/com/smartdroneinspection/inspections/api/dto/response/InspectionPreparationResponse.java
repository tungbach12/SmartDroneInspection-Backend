package com.smartdroneinspection.inspections.api.dto.response;

import com.smartdroneinspection.inspections.domain.enums.InspectionPreparationStatus;
import java.time.Instant;
import java.util.UUID;

/** What MF2-03 and MF2-06 show about one preparation version. */
public record InspectionPreparationResponse(
    UUID id,
    UUID inspectionId,
    int preparationVersion,
    Instant plannedStartAt,
    Instant plannedEndAt,
    String shotList,
    String evidenceTypes,
    String accessConstraints,
    String safetyObservations,
    String permitDocumentReferences,
    InspectionPreparationStatus status,
    Instant submittedAt,
    UUID reviewedByUserId,
    Instant reviewedAt,
    String reviewReason,
    Instant createdAt,
    Instant updatedAt) {}
