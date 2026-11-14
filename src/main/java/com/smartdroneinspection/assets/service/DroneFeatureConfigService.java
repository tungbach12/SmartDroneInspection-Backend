package com.smartdroneinspection.assets.service;

import com.smartdroneinspection.assets.api.dto.request.CreateDroneFeatureConfigRequest;
import com.smartdroneinspection.assets.api.dto.request.UpdateDroneFeatureConfigRequest;
import com.smartdroneinspection.assets.api.dto.response.DroneFeatureConfigResponse;
import com.smartdroneinspection.assets.domain.DroneFeatureConfig;
import com.smartdroneinspection.assets.repository.DroneFeatureConfigRepository;
import com.smartdroneinspection.shared.exception.BusinessException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DroneFeatureConfigService {

  private final DroneFeatureConfigRepository repository;

  public DroneFeatureConfigService(DroneFeatureConfigRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public List<DroneFeatureConfigResponse> list(boolean includeInactive) {
    List<DroneFeatureConfig> list =
        includeInactive
            ? repository.findAllByOrderByDisplayOrderAscCreatedAtAsc()
            : repository.findAllByIsActiveTrueOrderByDisplayOrderAscCreatedAtAsc();
    return list.stream().map(DroneFeatureConfigResponse::from).toList();
  }

  @Transactional
  public DroneFeatureConfigResponse create(CreateDroneFeatureConfigRequest request) {
    String normalizedCode = DroneFeatureConfig.normalizeCode(request.code());
    if (repository.existsByCodeIgnoreCase(normalizedCode)) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "DUPLICATE_CODE", "A feature config with this code already exists.");
    }
    DroneFeatureConfig config =
        new DroneFeatureConfig(
            normalizedCode,
            request.nameVi(),
            request.nameEn(),
            request.descriptionVi(),
            request.descriptionEn(),
            request.category(),
            request.isActive() == null || Boolean.TRUE.equals(request.isActive()),
            request.displayOrder() != null ? request.displayOrder() : 0);
    return DroneFeatureConfigResponse.from(repository.save(config));
  }

  @Transactional
  public DroneFeatureConfigResponse update(UUID id, UpdateDroneFeatureConfigRequest request) {
    DroneFeatureConfig config =
        repository
            .findById(id)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Drone feature config not found."));
    config.update(
        request.nameVi(),
        request.nameEn(),
        request.descriptionVi(),
        request.descriptionEn(),
        request.category(),
        request.isActive() == null || Boolean.TRUE.equals(request.isActive()),
        request.displayOrder() != null ? request.displayOrder() : config.getDisplayOrder());
    return DroneFeatureConfigResponse.from(repository.save(config));
  }

  @Transactional
  public DroneFeatureConfigResponse toggleActive(UUID id) {
    DroneFeatureConfig config =
        repository
            .findById(id)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Drone feature config not found."));
    config.toggleActive();
    return DroneFeatureConfigResponse.from(repository.save(config));
  }

  @Transactional
  public void delete(UUID id) {
    DroneFeatureConfig config =
        repository
            .findById(id)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Drone feature config not found."));
    repository.delete(config);
  }
}
