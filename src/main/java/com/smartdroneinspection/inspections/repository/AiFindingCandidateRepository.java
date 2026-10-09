package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.AiFindingCandidate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiFindingCandidateRepository extends JpaRepository<AiFindingCandidate, UUID> {

  List<AiFindingCandidate> findByInspectionIdOrderByCreatedAtDesc(UUID inspectionId);

  Optional<AiFindingCandidate> findByIdAndInspectionId(UUID id, UUID inspectionId);

  long countByEvidenceId(UUID evidenceId);
}
