package com.smartdroneinspection.assets.repository;

import com.smartdroneinspection.assets.domain.CategoryFrequencySuggestion;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryFrequencySuggestionRepository
    extends JpaRepository<CategoryFrequencySuggestion, UUID> {
  List<CategoryFrequencySuggestion> findByAssetCategoryIdOrderBySortOrderAsc(UUID assetCategoryId);
}
