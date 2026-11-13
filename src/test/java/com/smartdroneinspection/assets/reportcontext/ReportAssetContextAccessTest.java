package com.smartdroneinspection.assets.reportcontext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.users.UserAccess;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Covers owner scoping in {@link ReportAssetContextAccess}. The critical case is a partner-assigned
 * (inspector-owned) Drone: its {@code owner_customer_id} is null, so the report must still resolve
 * it via the by-id fallback. Without that fallback, published reports for cross-organization
 * partner dispatch would silently lose the Drone identity.
 */
class ReportAssetContextAccessTest {

  private AssetRepository assets;
  private AssetCategoryRepository categories;
  private DroneRepository drones;
  private UserAccess users;
  private ReportAssetContextAccess access;

  private UUID ownerCustomerId;
  private UUID assetId;
  private UUID droneId;

  @BeforeEach
  void setUp() {
    assets = mock(AssetRepository.class);
    categories = mock(AssetCategoryRepository.class);
    drones = mock(DroneRepository.class);
    users = mock(UserAccess.class);
    access = new ReportAssetContextAccess(assets, categories, drones, users, new ObjectMapper());

    ownerCustomerId = UUID.randomUUID();
    assetId = UUID.randomUUID();
    droneId = UUID.randomUUID();
  }

  @Test
  @DisplayName("an asset outside the caller's owner scope yields no identity")
  void assetOutsideOwnerScopeYieldsEmpty() {
    when(assets.findByIdAndOwnerCustomerId(assetId, ownerCustomerId)).thenReturn(Optional.empty());

    assertThat(access.find(ownerCustomerId, assetId, droneId)).isEmpty();
    verify(drones, never()).findById(any());
  }

  @Test
  @DisplayName("a customer-owned drone resolves within the owner scope")
  void customerOwnedDroneResolvesInScope() {
    Asset asset = asset();
    Drone drone = customerOwnedDrone();
    when(assets.findByIdAndOwnerCustomerId(assetId, ownerCustomerId))
        .thenReturn(Optional.of(asset));
    when(drones.findByIdAndOwnerCustomerId(droneId, ownerCustomerId))
        .thenReturn(Optional.of(drone));

    ReportContextAccess.Identity identity =
        access.find(ownerCustomerId, assetId, droneId).orElseThrow();

    assertThat(identity.droneSerialNumber()).isEqualTo(drone.getSerialNumber());
    verify(drones, never()).findById(any());
  }

  @Test
  @DisplayName("a partner-assigned inspector-owned drone falls back to lookup by id")
  void partnerDroneFallsBackToLookupById() {
    Asset asset = asset();
    Drone partnerDrone = inspectorOwnedDrone();
    when(assets.findByIdAndOwnerCustomerId(assetId, ownerCustomerId))
        .thenReturn(Optional.of(asset));
    // Inspector-owned drones have a null owner_customer_id, so the scoped query misses.
    when(drones.findByIdAndOwnerCustomerId(droneId, ownerCustomerId)).thenReturn(Optional.empty());
    when(drones.findById(droneId)).thenReturn(Optional.of(partnerDrone));

    ReportContextAccess.Identity identity =
        access.find(ownerCustomerId, assetId, droneId).orElseThrow();

    assertThat(identity.droneSerialNumber()).isEqualTo(partnerDrone.getSerialNumber());
    verify(drones).findById(droneId);
  }

  @Test
  @DisplayName("a missing drone leaves the identity without drone labels")
  void missingDroneLeavesIdentityWithoutDroneLabels() {
    Asset asset = asset();
    when(assets.findByIdAndOwnerCustomerId(assetId, ownerCustomerId))
        .thenReturn(Optional.of(asset));
    when(drones.findByIdAndOwnerCustomerId(droneId, ownerCustomerId)).thenReturn(Optional.empty());
    when(drones.findById(droneId)).thenReturn(Optional.empty());

    ReportContextAccess.Identity identity =
        access.find(ownerCustomerId, assetId, droneId).orElseThrow();

    assertThat(identity.droneSerialNumber()).isNull();
    assertThat(identity.droneManufacturer()).isNull();
  }

  private Asset asset() {
    Asset asset = mock(Asset.class);
    when(asset.getCode()).thenReturn("AST-1");
    when(asset.getName()).thenReturn("Bridge");
    when(asset.getLocationText()).thenReturn(null);
    when(asset.getCategoryId()).thenReturn(UUID.randomUUID());
    when(asset.getInspectionItems()).thenReturn(List.of());
    when(categories.findById(any())).thenReturn(Optional.empty());
    return asset;
  }

  private Drone customerOwnedDrone() {
    Drone drone = mock(Drone.class);
    when(drone.getSerialNumber()).thenReturn("SN-CUSTOMER");
    when(drone.getManufacturer()).thenReturn("DJI");
    when(drone.getModel()).thenReturn("M300");
    return drone;
  }

  private Drone inspectorOwnedDrone() {
    Drone drone = mock(Drone.class);
    when(drone.getSerialNumber()).thenReturn("SN-PARTNER");
    when(drone.getManufacturer()).thenReturn("Autel");
    when(drone.getModel()).thenReturn("EVO II");
    return drone;
  }
}
