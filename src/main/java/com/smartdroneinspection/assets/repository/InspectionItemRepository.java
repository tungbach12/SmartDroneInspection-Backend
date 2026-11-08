package com.smartdroneinspection.assets.repository;

import com.smartdroneinspection.assets.domain.InspectionItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionItemRepository extends JpaRepository<InspectionItem, UUID> {

  List<InspectionItem> findByCategoryIdOrderBySortOrderAscCreatedAtAsc(UUID categoryId);

  List<InspectionItem> findAllByOrderByCategoryIdAscSortOrderAscCreatedAtAsc();
}
