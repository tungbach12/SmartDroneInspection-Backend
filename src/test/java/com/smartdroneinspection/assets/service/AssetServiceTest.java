package com.smartdroneinspection.assets.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.assets.api.dto.request.UpdateAssetRequest;
import com.smartdroneinspection.assets.api.dto.response.AirspaceEvaluationResponse;
import com.smartdroneinspection.assets.api.dto.response.AirspaceZoneResponse;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AssetServiceTest {

  private AssetRepository assets;
  private AssetCategoryRepository categories;
  private UserAccess userAccess;
  private AirspaceAdvisoryService airspaceAdvisory;
  private AssetService service;

  private final UUID actorId = UUID.randomUUID();
  private final UUID assetId = UUID.randomUUID();
  private final UUID categoryId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    assets = mock(AssetRepository.class);
    categories = mock(AssetCategoryRepository.class);
    userAccess = mock(UserAccess.class);
    airspaceAdvisory = mock(AirspaceAdvisoryService.class);
    service = new AssetService(assets, categories, userAccess, airspaceAdvisory);

    when(userAccess.findActiveUser(actorId))
        .thenReturn(
            Optional.of(new UserAccess.ActiveUser(actorId, Set.of(Roles.CUSTOMER), "Customer")));
  }

  @Test
  void updateSingleCoordinateStillValidatesNoFlyZoneAndPreservesCoordinatesInLocationJson() {
    Asset asset =
        new Asset(
            actorId,
            categoryId,
            "BR-01",
            "Bridge",
            "Main",
            "District 1",
            BigDecimal.valueOf(10.5500),
            BigDecimal.valueOf(106.6519),
            null,
            actorId);
    ReflectionTestUtils.setField(asset, "id", assetId);
    when(assets.findByIdAndOwnerCustomerId(assetId, actorId)).thenReturn(Optional.of(asset));
    when(assets.saveAndFlush(any(Asset.class))).thenAnswer(inv -> inv.getArgument(0));

    // Updating only latitude into a No-Fly Zone while keeping existing longitude is blocked
    BigDecimal noFlyLat = BigDecimal.valueOf(10.8188);
    when(airspaceAdvisory.evaluate(noFlyLat, BigDecimal.valueOf(106.6519)))
        .thenReturn(
            new AirspaceEvaluationResponse(
                noFlyLat,
                BigDecimal.valueOf(106.6519),
                "NO_FLY_ZONE",
                "NO_FLY",
                "Blocked",
                null,
                List.of(
                    new AirspaceZoneResponse(
                        "TSN",
                        "Tan Son Nhat",
                        "HCMC",
                        "NO_FLY_ZONE",
                        noFlyLat,
                        BigDecimal.valueOf(106.6519),
                        5000,
                        0,
                        "BQP",
                        "QĐ 18/2020",
                        "Blocked",
                        0L)),
                List.of()));

    UpdateAssetRequest moveIntoNoFly =
        new UpdateAssetRequest(null, null, null, noFlyLat, null, null);

    assertThatThrownBy(() -> service.update(actorId, assetId, moveIntoNoFly))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex -> assertThat(((BusinessException) ex).code()).isEqualTo("NO_FLY_ZONE_RESTRICTION"));

    // Updating only locationText keeps existing latitude & longitude in location JSON
    when(airspaceAdvisory.evaluate(BigDecimal.valueOf(10.5500), BigDecimal.valueOf(106.6519)))
        .thenReturn(
            new AirspaceEvaluationResponse(
                BigDecimal.valueOf(10.5500),
                BigDecimal.valueOf(106.6519),
                "CLEAR_ZONE",
                "CLEAR",
                "Clear",
                null,
                List.of(),
                List.of()));
    UpdateAssetRequest updateLabelOnly =
        new UpdateAssetRequest(null, null, "Updated District", null, null, null);
    var updated = service.update(actorId, assetId, updateLabelOnly);
    assertThat(updated.locationText()).contains("10.55").contains("106.6519");
  }
}
