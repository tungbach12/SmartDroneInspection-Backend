package com.smartdroneinspection.users.service;

import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.api.dto.request.SavePilotLicenseClassRequest;
import com.smartdroneinspection.users.api.dto.response.PilotLicenseClassResponse;
import com.smartdroneinspection.users.domain.PilotLicenseClass;
import com.smartdroneinspection.users.repository.InspectorProfileRepository;
import com.smartdroneinspection.users.repository.PilotLicenseClassRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PilotLicenseClassService {

  private final PilotLicenseClassRepository classes;
  private final InspectorProfileRepository inspectorProfiles;

  public PilotLicenseClassService(
      PilotLicenseClassRepository classes, InspectorProfileRepository inspectorProfiles) {
    this.classes = classes;
    this.inspectorProfiles = inspectorProfiles;
  }

  @Transactional(readOnly = true)
  public List<PilotLicenseClassResponse> listActive() {
    return classes.findAllByActiveTrueOrderByNameAsc().stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public List<PilotLicenseClassResponse> listAll() {
    return classes.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
  }

  public PilotLicenseClassResponse create(SavePilotLicenseClassRequest request) {
    String name = normalizeName(request.name());
    if (classes.existsByNameIgnoreCase(name)) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "DUPLICATE_LICENSE_CLASS", "Hạng bằng lái đã tồn tại");
    }
    PilotLicenseClass created =
        new PilotLicenseClass(name, normalizeDescription(request.description()));
    return toResponse(classes.saveAndFlush(created));
  }

  public PilotLicenseClassResponse update(UUID id, SavePilotLicenseClassRequest request) {
    PilotLicenseClass licenseClass = find(id);
    String name = normalizeName(request.name());
    if (classes.existsByNameIgnoreCaseAndIdNot(name, id)) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "DUPLICATE_LICENSE_CLASS", "Hạng bằng lái đã tồn tại");
    }
    licenseClass.update(name, normalizeDescription(request.description()));
    licenseClass.setActive(Boolean.TRUE.equals(request.active()));
    return toResponse(classes.saveAndFlush(licenseClass));
  }

  public void deactivate(UUID id) {
    PilotLicenseClass licenseClass = find(id);
    licenseClass.setActive(false);
    classes.saveAndFlush(licenseClass);
  }

  @Transactional(readOnly = true)
  public String requireActiveName(String name) {
    if (name == null || name.isBlank()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "PILOT_LICENSE_CLASS_REQUIRED",
          "Vui lòng chọn hạng bằng lái UAV");
    }
    String normalized = normalizeName(name);
    return classes
        .findByNameIgnoreCaseAndActiveTrue(normalized)
        .map(PilotLicenseClass::getName)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PILOT_LICENSE_CLASS",
                    "Hạng bằng lái không tồn tại hoặc đã ngừng hoạt động"));
  }

  private PilotLicenseClass find(UUID id) {
    return classes
        .findById(id)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND,
                    "PILOT_LICENSE_CLASS_NOT_FOUND",
                    "Không tìm thấy hạng bằng lái"));
  }

  private PilotLicenseClassResponse toResponse(PilotLicenseClass entity) {
    return new PilotLicenseClassResponse(
        entity.getId(),
        entity.getName(),
        entity.getDescription(),
        entity.isActive(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }

  private static String normalizeName(String name) {
    return name.trim();
  }

  private static String normalizeDescription(String description) {
    return description == null || description.isBlank() ? null : description.trim();
  }
}
