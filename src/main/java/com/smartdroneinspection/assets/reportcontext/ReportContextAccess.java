package com.smartdroneinspection.assets.reportcontext;

import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped organization, asset and assigned Drone labels for report composition. */
public interface ReportContextAccess {
  Optional<Identity> find(UUID organizationId, UUID assetId, UUID droneId);

  record Identity(
      String organizationName,
      String assetCode,
      String assetName,
      String assetLocation,
      String assetCategory,
      String droneSerialNumber,
      String droneManufacturer,
      String droneModel) {}
}
