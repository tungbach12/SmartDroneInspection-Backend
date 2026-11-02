package com.smartdroneinspection.inspections.spi;

import java.util.Objects;
import java.util.UUID;

public record ReinspectionReportPublishedEvent(
    UUID workOrderId, UUID reinspectionId, UUID reportVersionId, boolean allDefectsResolved) {

  public ReinspectionReportPublishedEvent {
    Objects.requireNonNull(workOrderId, "workOrderId is required");
    Objects.requireNonNull(reinspectionId, "reinspectionId is required");
    Objects.requireNonNull(reportVersionId, "reportVersionId is required");
  }

  public ReinspectionReportPublishedEvent(
      UUID workOrderId, UUID reinspectionId, UUID reportVersionId) {
    this(workOrderId, reinspectionId, reportVersionId, true);
  }
}
