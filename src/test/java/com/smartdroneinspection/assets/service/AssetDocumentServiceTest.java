package com.smartdroneinspection.assets.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.assets.config.AssetProperties;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.AssetDocument;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.UserAccess;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class AssetDocumentServiceTest {

  private final UUID actorId = UUID.randomUUID();
  private final UUID organizationId = UUID.randomUUID();
  private final UUID assetId = UUID.randomUUID();
  private AssetRepository assets;
  private EvidenceObjectStore objectStore;
  private UserAccess userAccess;
  private Asset asset;
  private AssetDocumentService service;

  @BeforeEach
  void setUp() {
    assets = mock(AssetRepository.class);
    objectStore = mock(EvidenceObjectStore.class);
    userAccess = mock(UserAccess.class);
    asset = mock(Asset.class);
    AssetDocument stored = mock(AssetDocument.class);
    when(stored.getDocumentType()).thenReturn("ASSET_PHOTO");
    when(asset.addDocument(any(), any(), any(), any(), anyLong(), any(), any(), any()))
        .thenReturn(stored);
    service =
        new AssetDocumentService(
            assets, Optional.of(objectStore), userAccess, new AssetProperties());
    when(userAccess.findActiveUser(actorId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(actorId, Set.of(Roles.CUSTOMER), null, "Customer")));
    when(assets.findDetailedByIdAndOwnerCustomerId(assetId, actorId))
        .thenReturn(Optional.of(asset));
    when(asset.getStatus())
        .thenReturn(com.smartdroneinspection.assets.domain.enums.AssetStatus.ACTIVE);
  }

  @Test
  void uploadsPrimaryImageAsAssetPhotoDocument() throws Exception {
    MockMultipartFile image =
        new MockMultipartFile("file", "bridge.png", "image/png", new byte[] {1, 2, 3});

    var result = service.upload(actorId, assetId, image, "OTHER", null, true);

    assertThat(result.documentType()).isEqualTo("ASSET_PHOTO");
    verify(objectStore).put(any(), eq("image/png"), anyLong(), any());
    verify(assets).saveAndFlush(asset);
  }

  @Test
  void rejectsNonImageWhenMarkedAsPrimaryImageBeforeStorageWrite() throws Exception {
    MockMultipartFile pdf =
        new MockMultipartFile("file", "deed.pdf", "application/pdf", new byte[] {1, 2, 3});

    assertThatThrownBy(() -> service.upload(actorId, assetId, pdf, "OWNERSHIP", null, true))
        .isInstanceOf(BusinessException.class)
        .hasMessage("Asset photo must be an image");
    verify(objectStore, never()).put(any(), any(), any(Long.class), any());
  }
}
