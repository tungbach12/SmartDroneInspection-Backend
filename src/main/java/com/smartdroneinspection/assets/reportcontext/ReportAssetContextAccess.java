package com.smartdroneinspection.assets.reportcontext;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.AssetCategory;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.reportcontext.ReportContextAccess.Identity;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.users.UserAccess;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Resolves organization, asset and assigned Drone labels from tenant-scoped records. */
@Service
public class ReportAssetContextAccess implements ReportContextAccess {
  private final AssetRepository assets;
  private final AssetCategoryRepository categories;
  private final DroneRepository drones;
  private final UserAccess users;
  private final ObjectMapper objectMapper;

  public ReportAssetContextAccess(
      AssetRepository assets,
      AssetCategoryRepository categories,
      DroneRepository drones,
      UserAccess users,
      ObjectMapper objectMapper) {
    this.assets = assets;
    this.categories = categories;
    this.drones = drones;
    this.users = users;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Identity> find(UUID organizationId, UUID assetId, UUID droneId) {
    Optional<Asset> asset = assets.findByIdAndOrganizationId(assetId, organizationId);
    if (asset.isEmpty()) return Optional.empty();
    Asset record = asset.get();
    Drone drone =
        droneId == null
            ? null
            : drones.findByIdAndOrganizationId(droneId, organizationId).orElse(null);
    return Optional.of(
        new Identity(
            users.findOrganizationName(organizationId).orElse(null),
            record.getCode(),
            record.getName(),
            locationLabel(record.getLocationText()),
            categories.findById(record.getCategoryId()).map(AssetCategory::getName).orElse(null),
            drone == null ? null : drone.getSerialNumber(),
            drone == null ? null : drone.getManufacturer(),
            drone == null ? null : drone.getModel()));
  }

  private String locationLabel(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      var node = objectMapper.readTree(raw).get("label");
      return node == null || node.isNull() || node.asString().isBlank() ? null : node.asString();
    } catch (JacksonException exception) {
      return null;
    }
  }
}
