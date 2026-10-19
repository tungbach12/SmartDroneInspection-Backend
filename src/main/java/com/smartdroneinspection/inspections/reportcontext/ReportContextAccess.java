package com.smartdroneinspection.inspections.reportcontext;

import java.util.Optional;
import java.util.UUID;

/** Report context assembled from tenant-scoped organization, asset and session records. */
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
