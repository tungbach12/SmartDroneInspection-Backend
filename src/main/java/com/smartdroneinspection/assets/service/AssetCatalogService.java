package com.smartdroneinspection.assets.service;

import com.smartdroneinspection.assets.api.dto.request.CreateCategoryRequest;
import com.smartdroneinspection.assets.api.dto.request.UpdateCategoryRequest;
import com.smartdroneinspection.assets.api.dto.response.CategoryResponse;
import com.smartdroneinspection.assets.domain.AssetCategory;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.shared.exception.BusinessException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetCatalogService {

  private final AssetCategoryRepository categories;

  public AssetCatalogService(AssetCategoryRepository categories) {
    this.categories = categories;
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
