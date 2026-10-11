package com.smartdroneinspection.inspections.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What MF2-05 found when it checked a preparation's compliance basis.
 *
 * <p>A {@code blockers} list, not a clearance. SRS 3.4 MF2-05 is explicit that ambiguous authority
 * or geographic conditions require human verification rather than inferred automatic clearance, so
 * the platform reports what it can check and leaves the decision to the named reviewer of MF2-07.
 * An empty list means no blocker was found, never that flight is lawful.
 */
public record ComplianceGateResponse(
    UUID inspectionId,
    Instant plannedStartAt,
    List<Blocker> blockers,
    List<UUID> linkedPermitIds,
    boolean requiresHumanVerification) {

  /**
   * One thing standing between this preparation and a readiness decision.
   *
   * @param code stable machine-readable reason a client can branch on
   * @param detail a sentence an administrator can act on
   */
  public record Blocker(String code, String detail) {}
}
