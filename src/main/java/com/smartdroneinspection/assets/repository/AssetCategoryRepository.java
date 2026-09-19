package com.smartdroneinspection.assets.repository;

import com.smartdroneinspection.assets.domain.AssetCategory;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetCategoryRepository extends JpaRepository<AssetCategory, UUID> {
  boolean existsByCode(String code);
}
