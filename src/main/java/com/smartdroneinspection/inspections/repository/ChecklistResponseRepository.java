package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.ChecklistResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChecklistResponseRepository extends JpaRepository<ChecklistResponse, UUID> {

  List<ChecklistResponse> findByInspectionId(UUID inspectionId);

  Optional<ChecklistResponse> findByInspectionIdAndChecklistItemId(
      UUID inspectionId, UUID checklistItemId);
}
