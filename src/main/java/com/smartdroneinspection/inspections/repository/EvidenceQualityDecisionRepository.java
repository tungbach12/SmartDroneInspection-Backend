package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.EvidenceQualityDecision;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceQualityDecisionRepository
    extends JpaRepository<EvidenceQualityDecision, UUID> {

  List<EvidenceQualityDecision> findByInspectionIdOrderByDecidedAtDesc(UUID inspectionId);

  Optional<EvidenceQualityDecision> findFirstByInspectionIdOrderByDecidedAtDesc(UUID inspectionId);
}
