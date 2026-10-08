package com.smartdroneinspection.inspections;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Public inspections-module boundary for feature authorization and cross-workflow queries. */
public interface InspectionAccess {

  Optional<AcceptedReportSnapshot> findAcceptedReportVersion(UUID reportVersionId);

  record AcceptedReportSnapshot(
      UUID reportVersionId,
      UUID reportId,
      UUID inspectionId,
      UUID assetId,
      Set<UUID> verifiedFindingIds) {

    public AcceptedReportSnapshot {
      verifiedFindingIds = Set.copyOf(verifiedFindingIds);
    }

    public boolean containsFinding(UUID findingId) {
      return verifiedFindingIds.contains(findingId);
    }
  }

  UUID createMaintenanceEvidence(
      UUID uploadedByUserId,
      String evidenceKind,
      String fileName,
      String contentType,
      long sizeBytes,
      String checksumSha256,
      String objectKey);

  void linkEvidenceToWorkLog(UUID evidenceId, UUID workLogId);

  boolean existsEvidence(UUID evidenceId);
}
