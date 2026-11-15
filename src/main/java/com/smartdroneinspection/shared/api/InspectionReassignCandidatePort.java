package com.smartdroneinspection.shared.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Shared boundary port allowing {@code inspections} to request partner candidate evaluations for
 * reassignment using the MF2 eligibility &amp; reliability evaluator in {@code contracts}.
 */
public interface InspectionReassignCandidatePort {

  List<ReassignCandidate> evaluateReassignCandidates(
      UUID managerId,
      UUID inspectionId,
      UUID assetId,
      UUID ownerCustomerId,
      Instant plannedStartAt,
      Instant plannedEndAt);

  record CandidateCheck(String code, String status, String explanation) {}

  record ReassignCandidate(
      UUID inspectorId,
      UUID droneId,
      String inspectorName,
      String inspectorAvatarUrl,
      String droneSerialNumber,
      String droneModel,
      boolean droneHasImage,
      List<String> droneFeatureCodes,
      String eligibility,
      String capabilityFit,
      BigDecimal droneMaxAltitudeMeters,
      BigDecimal structureHeightMeters,
      BigDecimal footprintAreaSquareMeters,
      List<CandidateCheck> hardGates,
      List<CandidateCheck> capabilityChecks,
      String disclaimer,
      long declineCount,
      long noShowCount,
      long abortCount) {}
}
