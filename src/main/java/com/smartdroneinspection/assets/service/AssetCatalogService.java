package com.smartdroneinspection.assets.service;

import com.smartdroneinspection.assets.api.dto.request.CreateCategoryRequest;
import com.smartdroneinspection.assets.api.dto.request.CreateChecklistTemplateRequest;
import com.smartdroneinspection.assets.api.dto.request.UpdateCategoryRequest;
import com.smartdroneinspection.assets.api.dto.request.UpdateChecklistTemplateRequest;
import com.smartdroneinspection.assets.api.dto.response.CategoryResponse;
import com.smartdroneinspection.assets.api.dto.response.ChecklistTemplateResponse;
import com.smartdroneinspection.assets.domain.AssetCategory;
import com.smartdroneinspection.assets.domain.ChecklistTemplate;
import com.smartdroneinspection.assets.domain.InspectionItem;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.assets.repository.InspectionItemRepository;
import com.smartdroneinspection.shared.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetCatalogService {

  private static final Set<String> ALLOWED_TEMPLATE_TYPES =
      Set.of("CATEGORY_INSPECTION_ITEM", "PRE_FLIGHT", "POST_FLIGHT");

  private final AssetCategoryRepository categories;
  private final ChecklistTemplateRepository checklistTemplates;
  private final InspectionItemRepository inspectionItems;

  public AssetCatalogService(
      AssetCategoryRepository categories, ChecklistTemplateRepository checklistTemplates) {
    this(categories, checklistTemplates, null);
  }

  @Autowired
  public AssetCatalogService(
      AssetCategoryRepository categories,
      ChecklistTemplateRepository checklistTemplates,
      InspectionItemRepository inspectionItems) {
    this.categories = categories;
    this.checklistTemplates = checklistTemplates;
    this.inspectionItems = inspectionItems;
  }

  @Transactional(readOnly = true)
  public List<CategoryResponse> listCategories() {
    return categories.findAll().stream().map(this::toResponse).toList();
  }

  @Transactional
  public CategoryResponse createCategory(CreateCategoryRequest request) {
    if (categories.existsByCode(request.code().trim().toUpperCase(Locale.ROOT))) {
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
  public List<ChecklistTemplateResponse> listChecklistTemplates(
      String templateType, UUID categoryId) {
    if (templateType != null && !templateType.isBlank()) {
      String normalizedType = normalizeType(templateType);
      if ("CATEGORY_INSPECTION_ITEM".equals(normalizedType)) {
        if (inspectionItems == null) {
          return List.of();
        }
        List<InspectionItem> items =
            categoryId != null
                ? inspectionItems.findByCategoryIdOrderBySortOrderAscCreatedAtAsc(categoryId)
                : inspectionItems.findAllByOrderByCategoryIdAscSortOrderAscCreatedAtAsc();
        return items.stream().map(this::toInspectionItemResponse).toList();
      }
      return checklistTemplates
          .findByTemplateTypeOrderBySortOrderAscCreatedAtAsc(normalizedType)
          .stream()
          .map(this::toTemplateResponse)
          .toList();
    }

    List<ChecklistTemplateResponse> combined = new ArrayList<>();
    checklistTemplates
        .findAllByOrderByTemplateTypeAscSortOrderAscCreatedAtAsc()
        .forEach(t -> combined.add(toTemplateResponse(t)));
    if (inspectionItems != null) {
      inspectionItems
          .findAllByOrderByCategoryIdAscSortOrderAscCreatedAtAsc()
          .forEach(i -> combined.add(toInspectionItemResponse(i)));
    }
    return combined;
  }

  @Transactional
  public ChecklistTemplateResponse createChecklistTemplate(
      org.springframework.security.oauth2.jwt.Jwt jwt, CreateChecklistTemplateRequest request) {
    String normalizedType = normalizeType(request.templateType());
    if (!ALLOWED_TEMPLATE_TYPES.contains(normalizedType)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "VALIDATION_FAILED",
          "Invalid templateType: " + request.templateType());
    }
    requireTemplatePermission(jwt, normalizedType);
    if ("CATEGORY_INSPECTION_ITEM".equals(normalizedType)) {
      UUID categoryId = request.categoryId();
      if (categoryId == null) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_FAILED",
            "categoryId is required for CATEGORY_INSPECTION_ITEM");
      }
      requireCategory(categoryId);
      int nextOrder =
          request.sortOrder() != null
              ? request.sortOrder()
              : inspectionItems.findByCategoryIdOrderBySortOrderAscCreatedAtAsc(categoryId).size()
                  + 1;
      InspectionItem createdItem = new InspectionItem(categoryId, request.label(), nextOrder, true);
      return toInspectionItemResponse(inspectionItems.saveAndFlush(createdItem));
    }

    int nextOrder =
        request.sortOrder() != null
            ? request.sortOrder()
            : checklistTemplates
                    .findByTemplateTypeOrderBySortOrderAscCreatedAtAsc(normalizedType)
                    .size()
                + 1;

    ChecklistTemplate created =
        new ChecklistTemplate(normalizedType, request.label(), nextOrder, true);
    return toTemplateResponse(checklistTemplates.saveAndFlush(created));
  }

  @Transactional
  public ChecklistTemplateResponse updateChecklistTemplate(
      org.springframework.security.oauth2.jwt.Jwt jwt,
      UUID templateId,
      UpdateChecklistTemplateRequest request) {
    if (inspectionItems != null) {
      Optional<InspectionItem> maybeItem = inspectionItems.findById(templateId);
      if (maybeItem.isPresent()) {
        requireTemplatePermission(jwt, "CATEGORY_INSPECTION_ITEM");
        InspectionItem item = maybeItem.get();
        item.update(request.label(), request.sortOrder(), request.active());
        return toInspectionItemResponse(inspectionItems.saveAndFlush(item));
      }
    }
    ChecklistTemplate template =
        checklistTemplates.findById(templateId).orElseThrow(() -> notFound(templateId));
    requireTemplatePermission(jwt, template.getTemplateType());
    template.update(request.label(), request.sortOrder(), request.active());
    return toTemplateResponse(checklistTemplates.saveAndFlush(template));
  }

  @Transactional
  public void deleteChecklistTemplate(
      org.springframework.security.oauth2.jwt.Jwt jwt, UUID templateId) {
    if (inspectionItems != null) {
      Optional<InspectionItem> maybeItem = inspectionItems.findById(templateId);
      if (maybeItem.isPresent()) {
        requireTemplatePermission(jwt, "CATEGORY_INSPECTION_ITEM");
        inspectionItems.delete(maybeItem.get());
        inspectionItems.flush();
        return;
      }
    }
    ChecklistTemplate template =
        checklistTemplates.findById(templateId).orElseThrow(() -> notFound(templateId));
    requireTemplatePermission(jwt, template.getTemplateType());
    checklistTemplates.delete(template);
    checklistTemplates.flush();
  }

  private void requireTemplatePermission(
      org.springframework.security.oauth2.jwt.Jwt jwt, String templateType) {
    if ("CATEGORY_INSPECTION_ITEM".equals(templateType)) {
      return;
    }
    org.springframework.security.core.Authentication auth =
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .getAuthentication();
    boolean isAdmin =
        auth != null
            && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    if (!isAdmin) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "FORBIDDEN",
          "Only Platform Admin can modify Pre-flight and Post-flight checklists");
    }
  }

  private void requireCategory(UUID categoryId) {
    categories.findById(categoryId).orElseThrow(() -> notFound(categoryId));
  }

  private static String normalizeType(String value) {
    return value.trim().toUpperCase(Locale.ROOT);
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

  private ChecklistTemplateResponse toTemplateResponse(ChecklistTemplate template) {
    return new ChecklistTemplateResponse(
        template.getId(),
        template.getTemplateType(),
        null,
        template.getLabel(),
        template.getSortOrder(),
        template.isActive());
  }

  private ChecklistTemplateResponse toInspectionItemResponse(InspectionItem item) {
    return new ChecklistTemplateResponse(
        item.getId(),
        "CATEGORY_INSPECTION_ITEM",
        item.getCategoryId(),
        item.getLabel(),
        item.getSortOrder(),
        item.isActive());
  }
}
