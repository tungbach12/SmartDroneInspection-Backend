package com.smartdroneinspection.inspections.spi;

import java.util.UUID;

/** Access port for verifying inspection report publication status across module boundaries. */
public interface InspectionReportAccess {

  /**
   * Returns true if the specified report version belongs to the inspection and is currently
   * published.
   */
  boolean isReportPublished(UUID inspectionId, UUID reportVersionId);
}
