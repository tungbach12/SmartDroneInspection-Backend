package com.smartdroneinspection.inspections.spi;

import java.util.Objects;
import java.util.UUID;

public record ReinspectionCreatedEvent(UUID workOrderId, UUID reinspectionId) {

  public ReinspectionCreatedEvent {
    Objects.requireNonNull(workOrderId, "workOrderId is required");
    Objects.requireNonNull(reinspectionId, "reinspectionId is required");
  }
}
