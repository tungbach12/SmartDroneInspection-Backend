package com.smartdroneinspection.assets.readiness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only readiness facts owned by Assets. */
public interface DroneDocumentReadinessAccess {

  List<DroneDocumentSummary> findForDrone(
      UUID organizationId, UUID droneId, List<UUID> documentIds);

  Optional<AssetPairReadinessSummary> findPairForInspection(
      UUID organizationId, UUID pairId, UUID assetId);
}
