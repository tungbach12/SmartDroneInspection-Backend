package com.smartdroneinspection.inspections.api.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of the inspection list. The report summary travels with the row so the inspections and
 * reports screens read the same source and never disagree about a report's state.
 *
 * <p>{@code pendingAction} names the one decision the organization administrator is being asked for
 * on this row. The reviewer does not act per evidence and per finding (MF3-09/10), so the queue has
 * to point at a single phase: readiness review, report review, or the post-publication corrective
 * disposition. A client that inferred this from the inspection status would guess wrong, because
 * {@code PREPARING} covers both an open draft and a submitted preparation.
 */
public record InspectionListItemResponse(
    UUID id,
    UUID organizationId,
    UUID assetId,
    UUID inspectorId,
    String objective,
    String status,
    Instant plannedStartAt,
    Instant plannedEndAt,
    Instant createdAt,
    Instant updatedAt,
    UUID reportId,
    String reportStatus,
    Integer reportVersionNo,
    String pendingAction) {}
