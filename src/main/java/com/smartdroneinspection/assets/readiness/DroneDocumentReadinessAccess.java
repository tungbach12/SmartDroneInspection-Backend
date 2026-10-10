package com.smartdroneinspection.assets.readiness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only readiness facts owned by Assets. */
public interface DroneDocumentReadinessAccess {

  List<DroneDocumentSummary> findForDrone(
      UUID organizationId, UUID droneId, List<UUID> documentIds);

  /**
   * Every document held by one Drone.
   *
   * <p>MF2-07 cannot be performed through the API without this: the reviewer chooses which Drone
   * documents they actually inspected, and a client cannot guess their ids. Scoped to the owning
   * organization and reached through the Drone, so another tenant's documents cannot appear.
   */
  List<DroneDocumentSummary> listForDrone(UUID organizationId, UUID droneId);

  Optional<AssetPairReadinessSummary> findPairForInspection(
      UUID organizationId, UUID pairId, UUID assetId);
}
