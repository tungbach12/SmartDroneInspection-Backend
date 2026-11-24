package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.api.dto.request.CreateRatingCriterionRequest;
import com.smartdroneinspection.inspections.api.dto.request.UpdateRatingCriterionRequest;
import com.smartdroneinspection.inspections.api.dto.response.RatingCriterionConfigResponse;
import com.smartdroneinspection.inspections.domain.RatingCriterionConfig;
import com.smartdroneinspection.inspections.domain.enums.RaterRole;
import com.smartdroneinspection.inspections.repository.InspectorRatingRepository;
import com.smartdroneinspection.inspections.repository.RatingCriterionConfigRepository;
import com.smartdroneinspection.shared.exception.BusinessException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RatingCriteriaConfigService {

  private final RatingCriterionConfigRepository repository;
  private final InspectorRatingRepository ratings;

  public RatingCriteriaConfigService(
      RatingCriterionConfigRepository repository, InspectorRatingRepository ratings) {
    this.repository = repository;
    this.ratings = ratings;
  }

  @Transactional(readOnly = true)
  public List<RatingCriterionConfigResponse> list(RaterRole role, boolean includeInactive) {
    List<RatingCriterionConfig> items;
    if (role != null) {
      items =
          includeInactive
              ? repository.findAllByRaterRoleOrderByDisplayOrderAscCreatedAtAsc(role)
              : repository.findAllByRaterRoleAndIsActiveTrueOrderByDisplayOrderAscCreatedAtAsc(
                  role);
    } else {
      items =
          includeInactive
              ? repository.findAllByOrderByRaterRoleAscDisplayOrderAscCreatedAtAsc()
              : repository.findAllByIsActiveTrueOrderByRaterRoleAscDisplayOrderAscCreatedAtAsc();
    }
    return items.stream().map(RatingCriterionConfigResponse::from).toList();
  }

  @Transactional
  public RatingCriterionConfigResponse create(CreateRatingCriterionRequest request) {
    String normalizedCode;
    try {
      normalizedCode = RatingCriterionConfig.normalizeCode(request.code());
    } catch (IllegalArgumentException ex) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "INVALID_CRITERION_CODE", ex.getMessage());
    }
    if (repository.existsByRaterRoleAndCodeIgnoreCase(request.raterRole(), normalizedCode)) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "DUPLICATE_CRITERION_CODE",
          "A criterion with this code already exists for " + request.raterRole().name() + ".");
    }
    int order =
        request.displayOrder() != null
            ? request.displayOrder()
            : repository
                    .findAllByRaterRoleOrderByDisplayOrderAscCreatedAtAsc(request.raterRole())
                    .stream()
                    .mapToInt(RatingCriterionConfig::getDisplayOrder)
                    .max()
                    .orElse(0)
                + 1;
    boolean active = request.active() == null || Boolean.TRUE.equals(request.active());
    try {
      RatingCriterionConfig entity =
          new RatingCriterionConfig(
              request.raterRole(),
              normalizedCode,
              request.labelEn(),
              request.labelVi(),
              request.descriptionEn(),
              request.descriptionVi(),
              order,
              active);
      return RatingCriterionConfigResponse.from(repository.saveAndFlush(entity));
    } catch (IllegalArgumentException ex) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_CRITERION", ex.getMessage());
    }
  }

  @Transactional
  public RatingCriterionConfigResponse update(UUID id, UpdateRatingCriterionRequest request) {
    RatingCriterionConfig config = findOrThrow(id);
    boolean nextActive = request.active() == null ? config.isActive() : request.active();
    if (config.isActive() && !nextActive) {
      ensureNotLastActive(config.getRaterRole());
    }
    int order = request.displayOrder() != null ? request.displayOrder() : config.getDisplayOrder();
    try {
      config.update(
          request.labelEn(),
          request.labelVi(),
          request.descriptionEn(),
          request.descriptionVi(),
          order,
          nextActive);
      return RatingCriterionConfigResponse.from(repository.saveAndFlush(config));
    } catch (IllegalArgumentException ex) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_CRITERION", ex.getMessage());
    }
  }

  @Transactional
  public RatingCriterionConfigResponse toggleActive(UUID id) {
    RatingCriterionConfig config = findOrThrow(id);
    if (config.isActive()) {
      ensureNotLastActive(config.getRaterRole());
    }
    config.setActive(!config.isActive());
    return RatingCriterionConfigResponse.from(repository.saveAndFlush(config));
  }

  @Transactional
  public void delete(UUID id) {
    RatingCriterionConfig config = findOrThrow(id);
    if (config.isActive()) {
      ensureNotLastActive(config.getRaterRole());
    }
    if (ratings != null
        && ratings.existsByRaterRoleAndCriterionCode(
            config.getRaterRole().name(), config.getCode())) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "CRITERION_IN_USE",
          "Cannot delete a criterion already used in submitted ratings; deactivate it instead.");
    }
    repository.delete(config);
    repository.flush();
  }

  private void ensureNotLastActive(RaterRole role) {
    if (repository.countByRaterRoleAndIsActiveTrue(role) <= 1) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "LAST_ACTIVE_CRITERION",
          "At least one active rating criterion is required for " + role.name() + ".");
    }
  }

  private RatingCriterionConfig findOrThrow(UUID id) {
    return repository
        .findById(id)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND,
                    "RATING_CRITERION_NOT_FOUND",
                    "Rating criterion config not found."));
  }
}
