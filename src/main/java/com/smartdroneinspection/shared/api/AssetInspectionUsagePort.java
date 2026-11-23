package com.smartdroneinspection.shared.api;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only platform check that lets the assets module refuse to mutate inspection checkpoints once
 * an inspection exists for the asset and expose that lock state to clients. Implemented by the
 * inspections module.
 */
public interface AssetInspectionUsagePort {

  boolean hasInspection(UUID assetId);

  /** Returns the subset of asset ids that already have at least one inspection. */
  Set<UUID> inspectedAssetIds(Collection<UUID> assetIds);
}
