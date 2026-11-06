package com.smartdroneinspection.assets.repository;

import com.smartdroneinspection.assets.domain.ChecklistTemplate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChecklistTemplateRepository extends JpaRepository<ChecklistTemplate, UUID> {

  List<ChecklistTemplate> findByTemplateTypeOrderBySortOrderAscCreatedAtAsc(String templateType);

  List<ChecklistTemplate> findByTemplateTypeAndCategoryIdOrderBySortOrderAscCreatedAtAsc(
      String templateType, UUID categoryId);

  List<ChecklistTemplate> findAllByOrderByTemplateTypeAscSortOrderAscCreatedAtAsc();
}
