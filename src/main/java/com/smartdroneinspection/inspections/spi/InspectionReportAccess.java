package com.smartdroneinspection.inspections.spi;

import java.util.UUID;

/** Access port for verifying inspection report publication status across module boundaries. */
public interface InspectionReportAccess {

  /**
   * Returns true if the specified report version belongs to the inspection and is currently
   * published.
   */
  boolean isReportPublished(UUID inspectionId, UUID reportVersionId);

  /** Returns true if the inspection has at least one currently published report version. */
  boolean hasPublishedReport(UUID inspectionId);

  /** Returns true if the inspection has a currently approved or published report version. */
  boolean hasApprovedOrPublishedReport(UUID inspectionId);
}
