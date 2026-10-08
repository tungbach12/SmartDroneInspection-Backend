package com.smartdroneinspection.assets.api;

import com.smartdroneinspection.assets.api.dto.request.CreateCategoryRequest;
import com.smartdroneinspection.assets.api.dto.request.UpdateCategoryRequest;
import com.smartdroneinspection.assets.api.dto.response.CategoryResponse;
import com.smartdroneinspection.assets.service.AssetCatalogService;
import com.smartdroneinspection.shared.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/asset-categories")
public class AssetCatalogController {

  private final AssetCatalogService catalog;

  public AssetCatalogController(AssetCatalogService catalog) {
    this.catalog = catalog;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<List<CategoryResponse>> list() {
    return ApiResponse.success(catalog.listCategories());
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<CategoryResponse> create(@Valid @RequestBody CreateCategoryRequest request) {
    return ApiResponse.success(catalog.createCategory(request));
  }

  @PutMapping("/{categoryId}")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<CategoryResponse> update(
      @PathVariable UUID categoryId, @Valid @RequestBody UpdateCategoryRequest request) {
    return ApiResponse.success(catalog.updateCategory(categoryId, request));
  }
}
