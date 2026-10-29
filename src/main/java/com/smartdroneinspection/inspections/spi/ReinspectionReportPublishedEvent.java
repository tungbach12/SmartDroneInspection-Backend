package com.smartdroneinspection.inspections.spi;

import java.util.Objects;
import java.util.UUID;

public record ReinspectionReportPublishedEvent(
    UUID workOrderId, UUID reinspectionId, UUID reportVersionId) {

  public ReinspectionReportPublishedEvent {
    Objects.requireNonNull(workOrderId, "workOrderId is required");
    Objects.requireNonNull(reinspectionId, "reinspectionId is required");
    Objects.requireNonNull(reportVersionId, "reportVersionId is required");
  }
}
