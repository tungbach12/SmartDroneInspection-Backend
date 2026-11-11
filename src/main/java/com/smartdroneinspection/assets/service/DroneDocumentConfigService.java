package com.smartdroneinspection.assets.service;

import com.smartdroneinspection.assets.api.dto.request.CreateDroneDocumentConfigRequest;
import com.smartdroneinspection.assets.api.dto.request.UpdateDroneDocumentConfigRequest;
import com.smartdroneinspection.assets.api.dto.response.DroneDocumentConfigResponse;
import com.smartdroneinspection.assets.domain.DroneDocumentConfig;
import com.smartdroneinspection.assets.repository.DroneDocumentConfigRepository;
import com.smartdroneinspection.shared.exception.BusinessException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DroneDocumentConfigService {

  private final DroneDocumentConfigRepository repository;

  public DroneDocumentConfigService(DroneDocumentConfigRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public List<DroneDocumentConfigResponse> list(boolean includeInactive) {
    List<DroneDocumentConfig> list =
        includeInactive
            ? repository.findAllByOrderByDisplayOrderAscCreatedAtAsc()
            : repository.findAllByIsActiveTrueOrderByDisplayOrderAscCreatedAtAsc();
    return list.stream().map(DroneDocumentConfigResponse::from).toList();
  }

  @Transactional
  public DroneDocumentConfigResponse create(CreateDroneDocumentConfigRequest request) {
    String normalizedCode = DroneDocumentConfig.normalizeCode(request.code());
    if (repository.existsByCodeIgnoreCase(normalizedCode)) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "DUPLICATE_CODE",
          "A document config with this code already exists.");
    }
    DroneDocumentConfig config =
        new DroneDocumentConfig(
            normalizedCode,
            request.titleVi(),
            request.titleEn(),
            request.descriptionVi(),
            request.descriptionEn(),
            Boolean.TRUE.equals(request.isRequired()),
            request.isActive() == null || Boolean.TRUE.equals(request.isActive()),
            request.displayOrder() != null ? request.displayOrder() : 0);
    return DroneDocumentConfigResponse.from(repository.save(config));
  }

  @Transactional
  public DroneDocumentConfigResponse update(UUID id, UpdateDroneDocumentConfigRequest request) {
    DroneDocumentConfig config =
        repository
            .findById(id)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Drone document config not found."));
    config.update(
        request.titleVi(),
        request.titleEn(),
        request.descriptionVi(),
        request.descriptionEn(),
        Boolean.TRUE.equals(request.isRequired()),
        request.isActive() == null || Boolean.TRUE.equals(request.isActive()),
        request.displayOrder() != null ? request.displayOrder() : config.getDisplayOrder());
    return DroneDocumentConfigResponse.from(repository.save(config));
  }

  @Transactional
  public DroneDocumentConfigResponse toggleActive(UUID id) {
    DroneDocumentConfig config =
        repository
            .findById(id)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Drone document config not found."));
    config.toggleActive();
    return DroneDocumentConfigResponse.from(repository.save(config));
  }

  @Transactional
  public void delete(UUID id) {
    DroneDocumentConfig config =
        repository
            .findById(id)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Drone document config not found."));
    repository.delete(config);
  }
}
