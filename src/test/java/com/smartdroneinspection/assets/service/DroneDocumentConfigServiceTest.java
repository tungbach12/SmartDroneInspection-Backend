package com.smartdroneinspection.assets.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.assets.api.dto.request.CreateDroneDocumentConfigRequest;
import com.smartdroneinspection.assets.api.dto.request.UpdateDroneDocumentConfigRequest;
import com.smartdroneinspection.assets.domain.DroneDocumentConfig;
import com.smartdroneinspection.assets.repository.DroneDocumentConfigRepository;
import com.smartdroneinspection.shared.exception.BusinessException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DroneDocumentConfigServiceTest {

  private DroneDocumentConfigRepository repository;
  private DroneDocumentConfigService service;

  @BeforeEach
  void setUp() {
    repository = mock(DroneDocumentConfigRepository.class);
    service = new DroneDocumentConfigService(repository);
  }

  @Test
  void listActiveOrAllReturnsConfigResponses() {
    DroneDocumentConfig config =
        new DroneDocumentConfig(
            "INSURANCE", "Bảo hiểm", "Insurance", "Mô tả", "Desc", true, true, 4);
    when(repository.findAllByIsActiveTrueOrderByDisplayOrderAscCreatedAtAsc())
        .thenReturn(List.of(config));
    when(repository.findAllByOrderByDisplayOrderAscCreatedAtAsc()).thenReturn(List.of(config));

    assertThat(service.list(false)).hasSize(1);
    assertThat(service.list(true)).hasSize(1);
  }

  @Test
  void createNewConfigSavesWhenCodeIsUnique() {
    CreateDroneDocumentConfigRequest req =
        new CreateDroneDocumentConfigRequest(
            "INSURANCE", "Bảo hiểm", "Insurance", "Mô tả", "Desc", true, true, 4);
    when(repository.existsByCodeIgnoreCase("INSURANCE")).thenReturn(false);
    when(repository.save(any(DroneDocumentConfig.class))).thenAnswer(inv -> inv.getArgument(0));

    var response = service.create(req);
    assertThat(response.code()).isEqualTo("INSURANCE");
    assertThat(response.titleVi()).isEqualTo("Bảo hiểm");
    assertThat(response.isRequired()).isTrue();
  }

  @Test
  void createRejectsDuplicateCode() {
    CreateDroneDocumentConfigRequest req =
        new CreateDroneDocumentConfigRequest(
            "REGISTRATION", "Đăng ký", "Registration", null, null, true, true, 1);
    when(repository.existsByCodeIgnoreCase("REGISTRATION")).thenReturn(true);

    assertThatThrownBy(() -> service.create(req))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("already exists");
  }

  @Test
  void updateModifiesExistingConfig() {
    UUID id = UUID.randomUUID();
    DroneDocumentConfig config =
        new DroneDocumentConfig(
            "INSURANCE", "Bảo hiểm", "Insurance", "Mô tả", "Desc", true, true, 4);
    when(repository.findById(id)).thenReturn(Optional.of(config));
    when(repository.save(any(DroneDocumentConfig.class))).thenAnswer(inv -> inv.getArgument(0));

    UpdateDroneDocumentConfigRequest updateReq =
        new UpdateDroneDocumentConfigRequest(
            "Bảo hiểm sửa đổi", "Insurance Updated", "Mới", "New", false, true, 5);
    var updated = service.update(id, updateReq);
    assertThat(updated.titleVi()).isEqualTo("Bảo hiểm sửa đổi");
    assertThat(updated.isRequired()).isFalse();
    assertThat(updated.displayOrder()).isEqualTo(5);
  }

  @Test
  void toggleActiveFlipsStatus() {
    UUID id = UUID.randomUUID();
    DroneDocumentConfig config =
        new DroneDocumentConfig("INSURANCE", "Bảo hiểm", "Insurance", null, null, true, true, 4);
    when(repository.findById(id)).thenReturn(Optional.of(config));
    when(repository.save(any(DroneDocumentConfig.class))).thenAnswer(inv -> inv.getArgument(0));

    var toggled = service.toggleActive(id);
    assertThat(toggled.isActive()).isFalse();
  }

  @Test
  void deleteRemovesConfig() {
    UUID id = UUID.randomUUID();
    DroneDocumentConfig config =
        new DroneDocumentConfig("INSURANCE", "Bảo hiểm", "Insurance", null, null, true, true, 4);
    when(repository.findById(id)).thenReturn(Optional.of(config));

    service.delete(id);
    verify(repository).delete(config);
  }
}
