package com.smartdroneinspection.inspections.spi;

import java.util.UUID;

public record InspectionReinspectionReportPublishedEvent(
    UUID inspectionId, UUID inspectionReportId, UUID organizationId) {}
