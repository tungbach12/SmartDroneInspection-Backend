package com.smartdroneinspection.contracts.api.dto.response;

import com.smartdroneinspection.contracts.service.PartnerCandidateEvaluation;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PartnerCandidateResponse(
    UUID inspectorId,
    UUID droneId,
    String inspectorName,
    String inspectorAvatarUrl,
    String droneSerialNumber,
    String droneModel,
    boolean droneHasImage,
    List<String> droneFeatureCodes,
    PartnerCandidateEvaluation.Status eligibility,
    PartnerCandidateEvaluation.Status capabilityFit,
    BigDecimal droneMaxAltitudeMeters,
    BigDecimal structureHeightMeters,
    BigDecimal footprintAreaSquareMeters,
    List<PartnerCandidateEvaluation.Check> hardGates,
    List<PartnerCandidateEvaluation.Check> capabilityChecks,
    String disclaimer) {

  public PartnerCandidateResponse {
    droneFeatureCodes = List.copyOf(droneFeatureCodes);
    hardGates = List.copyOf(hardGates);
    capabilityChecks = List.copyOf(capabilityChecks);
  }

  public static PartnerCandidateResponse from(PartnerCandidateEvaluation.Result result) {
    return new PartnerCandidateResponse(
        result.inspectorId(),
        result.droneId(),
        result.inspectorName(),
        result.inspectorAvatarUrl(),
        result.droneSerialNumber(),
        result.droneModel(),
        result.droneHasImage(),
        result.droneFeatureCodes(),
        result.eligibility(),
        result.capabilityFit(),
        result.droneMaxAltitudeMeters(),
        result.structureHeightMeters(),
        result.footprintAreaSquareMeters(),
        result.hardGates(),
        result.capabilityChecks(),
        result.disclaimer());
  }
}
