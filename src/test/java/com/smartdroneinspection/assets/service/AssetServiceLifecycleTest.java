package com.smartdroneinspection.assets.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

class AssetServiceLifecycleTest {

  private final UUID actorId = UUID.randomUUID();
  private final UUID assetId = UUID.randomUUID();
  private final UUID categoryId = UUID.randomUUID();

  private AssetRepository assets;
  private UserAccess userAccess;
  private AssetService service;

  @BeforeEach
  void setUp() {
    assets = mock(AssetRepository.class);
    userAccess = mock(UserAccess.class);
    service =
        new AssetService(
            assets,
            mock(AssetCategoryRepository.class),
            userAccess,
            mock(AirspaceAdvisoryService.class));
    when(userAccess.findActiveUser(actorId))
        .thenReturn(
            Optional.of(new UserAccess.ActiveUser(actorId, Set.of(Roles.CUSTOMER), "Customer")));
  }

  @Test
  void readsOwnedAsset() {
    Asset asset = asset();
    when(assets.findByIdAndOwnerCustomerId(assetId, actorId)).thenReturn(Optional.of(asset));

    var response = service.get(actorId, assetId);

    assertThat(response.id()).isEqualTo(assetId);
    assertThat(response.code()).isEqualTo("BR-01");
    assertThat(response.inspectionItems()).containsExactly("Roof");
  }

  @Test
  void retiresOwnedAssetOnlyOnce() {
    Asset asset = asset();
    when(assets.findByIdAndOwnerCustomerId(assetId, actorId)).thenReturn(Optional.of(asset));
    when(assets.saveAndFlush(any(Asset.class))).thenAnswer(invocation -> invocation.getArgument(0));

    var retired = service.retire(actorId, assetId);
    assertThat(retired.status()).isEqualTo("RETIRED");

    assertBusinessCode(() -> service.retire(actorId, assetId), "INVALID_STATE");
  }

  @Test
  void updatesInspectionItemsWithSanitizedUniqueValues() {
    Asset asset = asset();
    when(assets.findByIdAndOwnerCustomerId(assetId, actorId)).thenReturn(Optional.of(asset));
    when(assets.saveAndFlush(any(Asset.class))).thenAnswer(invocation -> invocation.getArgument(0));

    var response =
        service.updateInspectionItems(
            actorId, assetId, java.util.Arrays.asList(" Deck ", null, "Deck", "Roof", "  "));

    assertThat(response.inspectionItems()).containsExactly("Deck", "Roof");
  }

  @Test
  void retiredAssetCannotChangeInspectionItems() {
    Asset asset = asset();
    asset.retire();
    when(assets.findByIdAndOwnerCustomerId(assetId, actorId)).thenReturn(Optional.of(asset));

    assertBusinessCode(
        () -> service.updateInspectionItems(actorId, assetId, List.of("Roof")), "INVALID_STATE");
  }

  @Test
  void missingAssetIsNotFound() {
    when(assets.findByIdAndOwnerCustomerId(assetId, actorId)).thenReturn(Optional.empty());

    assertBusinessCode(() -> service.get(actorId, assetId), "NOT_FOUND");
  }

  private Asset asset() {
    Asset asset =
        new Asset(
            actorId,
            categoryId,
            "BR-01",
            "Bridge",
            "Main",
            "District 1",
            new BigDecimal("10.55"),
            new BigDecimal("106.65"),
            null,
            actorId);
    asset.setInspectionItems(List.of("Roof"));
    ReflectionTestUtils.setField(asset, "id", assetId);
    return asset;
  }

  private static void assertBusinessCode(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, String code) {
    assertThatThrownBy(callable)
        .isInstanceOfSatisfying(
            BusinessException.class, exception -> assertThat(exception.code()).isEqualTo(code));
  }
}
