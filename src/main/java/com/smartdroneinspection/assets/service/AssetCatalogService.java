package com.smartdroneinspection.assets.service;

import com.smartdroneinspection.assets.api.dto.request.CreateCategoryRequest;
import com.smartdroneinspection.assets.api.dto.request.SuggestedFrequencyRequest;
import com.smartdroneinspection.assets.api.dto.request.UpdateCategoryRequest;
import com.smartdroneinspection.assets.api.dto.response.CategoryResponse;
import com.smartdroneinspection.assets.api.dto.response.SuggestedFrequencyResponse;
import com.smartdroneinspection.assets.domain.AssetCategory;
import com.smartdroneinspection.assets.domain.CategoryFrequencySuggestion;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.CategoryFrequencySuggestionRepository;
import com.smartdroneinspection.shared.exception.BusinessException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetCatalogService {

  private final AssetCategoryRepository categories;
  private final CategoryFrequencySuggestionRepository frequencies;

  public AssetCatalogService(
      AssetCategoryRepository categories, CategoryFrequencySuggestionRepository frequencies) {
    this.categories = categories;
    this.frequencies = frequencies;
  }

  @Transactional(readOnly = true)
  public List<CategoryResponse> listCategories() {
    return categories.findAll().stream().map(this::toResponse).toList();
  }

  @Transactional
  public CategoryResponse createCategory(CreateCategoryRequest request) {
    if (categories.existsByCode(request.code().trim().toUpperCase(java.util.Locale.ROOT))) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "DUPLICATE_CODE", "Category code already exists");
    }
    AssetCategory category =
        new AssetCategory(request.code(), request.name(), request.description(), true);
    return toResponse(categories.saveAndFlush(category));
  }

  @Transactional
  public CategoryResponse updateCategory(UUID categoryId, UpdateCategoryRequest request) {
    AssetCategory category =
        categories.findById(categoryId).orElseThrow(() -> notFound(categoryId));
    category.update(request.name(), request.description());
    return toResponse(categories.saveAndFlush(category));
  }

  @Transactional(readOnly = true)
  public List<SuggestedFrequencyResponse> listFrequencies(UUID categoryId) {
    requireCategory(categoryId);
    return frequencies.findByAssetCategoryIdOrderBySortOrderAsc(categoryId).stream()
        .map(
            f ->
                new SuggestedFrequencyResponse(
                    f.getId(), f.getFrequencyUnit(), f.getFrequencyInterval(), f.getSortOrder()))
        .toList();
  }

  @Transactional
  public SuggestedFrequencyResponse addFrequency(
      UUID categoryId, SuggestedFrequencyRequest request) {
    requireCategory(categoryId);
    CategoryFrequencySuggestion suggestion =
        new CategoryFrequencySuggestion(
            categoryId,
            request.frequencyUnit(),
            request.frequencyInterval(),
            frequencies.findByAssetCategoryIdOrderBySortOrderAsc(categoryId).size());
    try {
      suggestion = frequencies.saveAndFlush(suggestion);
    } catch (org.springframework.dao.DataIntegrityViolationException ex) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "DUPLICATE_FREQUENCY", "Suggested frequency already exists");
    }
    return new SuggestedFrequencyResponse(
        suggestion.getId(),
        suggestion.getFrequencyUnit(),
        suggestion.getFrequencyInterval(),
        suggestion.getSortOrder());
  }

  @Transactional
  public void deleteFrequency(UUID categoryId, UUID frequencyId) {
    requireCategory(categoryId);
    CategoryFrequencySuggestion suggestion =
        frequencies
            .findById(frequencyId)
            .filter(f -> f.getAssetCategoryId().equals(categoryId))
            .orElseThrow(() -> notFound(frequencyId));
    frequencies.delete(suggestion);
  }

  private void requireCategory(UUID categoryId) {
    categories.findById(categoryId).orElseThrow(() -> notFound(categoryId));
  }

  private BusinessException notFound(UUID id) {
    return new BusinessException(
        HttpStatus.NOT_FOUND, "NOT_FOUND", "Catalog entry not found: " + id);
  }

  private CategoryResponse toResponse(AssetCategory category) {
    return new CategoryResponse(
        category.getId(),
        category.getCode(),
        category.getName(),
        category.getDescription(),
        category.isActive());
  }
}
