package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.InspectionReadinessDecision;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionReadinessDecisionRepository
    extends JpaRepository<InspectionReadinessDecision, UUID> {

  List<InspectionReadinessDecision> findByInspectionIdOrderByDecidedAtDesc(UUID inspectionId);

  Optional<InspectionReadinessDecision> findFirstByInspectionIdOrderByDecidedAtDesc(
      UUID inspectionId);
}
