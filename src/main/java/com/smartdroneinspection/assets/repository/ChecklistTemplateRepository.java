package com.smartdroneinspection.assets.repository;

import com.smartdroneinspection.assets.domain.ChecklistTemplate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChecklistTemplateRepository extends JpaRepository<ChecklistTemplate, UUID> {
  @EntityGraph(attributePaths = "items")
  Optional<ChecklistTemplate> findDetailedById(UUID id);

  Optional<ChecklistTemplate> findByTemplateKeyAndVersionNumber(
      String templateKey, int versionNumber);
}
