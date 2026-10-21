package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * The MF2-03 preparation an inspector records on their phone or laptop.
 *
 * <p>Every field is a JSON document string rather than a typed structure. The shot-list shape is
 * agreed with the mobile client and is still moving, and pinning it to a Java type here would make
 * an ordinary client update a backend release. Validation of the content itself belongs to the
 * service, which can refuse it with a reason an inspector can act on.
 */
public record PrepareShotListRequest(
    @Size(max = 20000) String shotList,
    @Size(max = 5000) String evidenceTypes,
    @Size(max = 5000) String accessConstraints,
    @Size(max = 4000) String safetyObservations,
    Instant plannedStartAt,
    Instant plannedEndAt) {

  public PrepareShotListRequest(
      String shotList, String evidenceTypes, String accessConstraints, String safetyObservations) {
    this(shotList, evidenceTypes, accessConstraints, safetyObservations, null, null);
  }
}
