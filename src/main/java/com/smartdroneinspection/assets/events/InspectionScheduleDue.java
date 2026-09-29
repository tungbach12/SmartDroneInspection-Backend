package com.smartdroneinspection.assets.events;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Frozen WF1 to WF2 handoff contract. Identity: unique (assetId, scheduleId, dueCycle).
 *
 * <p>See development/plans/bach/2026-09-22-four-week-mainflow-delivery/flow-handoffs.md.
 */
public record InspectionScheduleDue(
    UUID organizationId,
    UUID assetId,
    UUID scheduleId,
    UUID checklistTemplateVersionId,
    LocalDate dueCycle) {}
