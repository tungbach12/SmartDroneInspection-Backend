package com.smartdroneinspection.assets.reportcontext;

import java.util.List;
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
      String droneModel,
      List<String> inspectionItems) {

    public Identity {
      inspectionItems = inspectionItems == null ? List.of() : List.copyOf(inspectionItems);
    }

    public Identity(
        String organizationName,
        String assetCode,
        String assetName,
        String assetLocation,
        String assetCategory,
        String droneSerialNumber,
        String droneManufacturer,
        String droneModel) {
      this(
          organizationName,
          assetCode,
          assetName,
          assetLocation,
          assetCategory,
          droneSerialNumber,
          droneManufacturer,
          droneModel,
          List.of());
    }
  }
}
