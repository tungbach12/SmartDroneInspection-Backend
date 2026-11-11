package com.smartdroneinspection.assets.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.assets.api.dto.request.CreateDroneFeatureConfigRequest;
import com.smartdroneinspection.assets.api.dto.request.UpdateDroneFeatureConfigRequest;
import com.smartdroneinspection.assets.domain.DroneFeatureConfig;
import com.smartdroneinspection.assets.repository.DroneFeatureConfigRepository;
import com.smartdroneinspection.shared.exception.BusinessException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DroneFeatureConfigServiceTest {

  private DroneFeatureConfigRepository repository;
  private DroneFeatureConfigService service;

  @BeforeEach
  void setUp() {
    repository = mock(DroneFeatureConfigRepository.class);
    service = new DroneFeatureConfigService(repository);
  }

  @Test
  void listReturnsActiveOrAllFeatureConfigs() {
    DroneFeatureConfig feature =
        new DroneFeatureConfig(
            "RTK_POSITIONING", "Định vị RTK", "RTK GNSS", "Mô tả", "Desc", "CAPABILITY", true, 1);
    when(repository.findAllByIsActiveTrueOrderByDisplayOrderAscCreatedAtAsc())
        .thenReturn(List.of(feature));
    when(repository.findAllByOrderByDisplayOrderAscCreatedAtAsc()).thenReturn(List.of(feature));

    assertThat(service.list(false)).hasSize(1);
    assertThat(service.list(true)).hasSize(1);
  }

  @Test
  void createSavesWhenCodeIsUnique() {
    CreateDroneFeatureConfigRequest req =
        new CreateDroneFeatureConfigRequest(
            "PARACHUTE", "Dù an toàn", "Parachute", "Dù", "Chute", "SAFETY", true, 8);
    when(repository.existsByCodeIgnoreCase("PARACHUTE")).thenReturn(false);
    when(repository.save(any(DroneFeatureConfig.class))).thenAnswer(inv -> inv.getArgument(0));

    var res = service.create(req);
    assertThat(res.code()).isEqualTo("PARACHUTE");
    assertThat(res.category()).isEqualTo("SAFETY");
  }

  @Test
  void createRejectsDuplicateCode() {
    CreateDroneFeatureConfigRequest req =
        new CreateDroneFeatureConfigRequest(
            "RTK_POSITIONING", "RTK", "RTK", null, null, "CAPABILITY", true, 1);
    when(repository.existsByCodeIgnoreCase("RTK_POSITIONING")).thenReturn(true);

    assertThatThrownBy(() -> service.create(req))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("already exists");
  }

  @Test
  void updateAndToggleAndDeleteWorkAsExpected() {
    UUID id = UUID.randomUUID();
    DroneFeatureConfig feature =
        new DroneFeatureConfig("LIDAR", "LiDAR", "LiDAR", "Quét", "Scan", "CAPABILITY", true, 3);
    when(repository.findById(id)).thenReturn(Optional.of(feature));
    when(repository.save(any(DroneFeatureConfig.class))).thenAnswer(inv -> inv.getArgument(0));

    var updated =
        service.update(
            id,
            new UpdateDroneFeatureConfigRequest(
                "LiDAR 3D", "LiDAR 3D", null, null, "CAPABILITY", true, 4));
    assertThat(updated.nameVi()).isEqualTo("LiDAR 3D");
    assertThat(updated.displayOrder()).isEqualTo(4);

    var toggled = service.toggleActive(id);
    assertThat(toggled.isActive()).isFalse();

    service.delete(id);
    verify(repository).delete(feature);
  }
}
