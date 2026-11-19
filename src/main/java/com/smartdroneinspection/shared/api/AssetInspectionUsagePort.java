package com.smartdroneinspection.shared.api;

import java.util.UUID;

/**
 * Read-only platform check that lets the assets module refuse to mutate inspection checkpoints once
 * an inspection exists for the asset. Implemented by the inspections module.
 */
public interface AssetInspectionUsagePort {

  boolean hasInspection(UUID assetId);
}
