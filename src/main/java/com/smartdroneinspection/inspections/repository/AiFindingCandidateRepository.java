package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.AiFindingCandidate;
import com.smartdroneinspection.inspections.domain.enums.AiFindingCandidateStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiFindingCandidateRepository extends JpaRepository<AiFindingCandidate, UUID> {

  List<AiFindingCandidate> findByInspectionIdOrderByCreatedAtDesc(UUID inspectionId);

  long countByInspectionIdAndStatus(UUID inspectionId, AiFindingCandidateStatus status);

  Optional<AiFindingCandidate> findByIdAndInspectionId(UUID id, UUID inspectionId);

  List<AiFindingCandidate> findByEvidenceIdOrderByCreatedAtAsc(UUID evidenceId);

  long countByEvidenceId(UUID evidenceId);
}
