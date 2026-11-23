package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.shared.api.AssetInspectionUsagePort;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Assets-owned port implementation backed by the inspections repository. */
@Component
class InspectionAssetUsageAdapter implements AssetInspectionUsagePort {

  private final InspectionRepository inspections;

  InspectionAssetUsageAdapter(InspectionRepository inspections) {
    this.inspections = inspections;
  }

  @Override
  public boolean hasInspection(UUID assetId) {
    return inspections.existsByAssetId(assetId);
  }

  @Override
  public Set<UUID> inspectedAssetIds(Collection<UUID> assetIds) {
    if (assetIds == null || assetIds.isEmpty()) {
      return Set.of();
    }
    return Set.copyOf(inspections.findDistinctAssetIdsByAssetIdIn(assetIds));
  }
}
