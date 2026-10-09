package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.VerifiedFinding;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VerifiedFindingRepository extends JpaRepository<VerifiedFinding, UUID> {

  List<VerifiedFinding> findByInspectionIdOrderByCreatedAtAsc(UUID inspectionId);

  Optional<VerifiedFinding> findByIdAndInspectionId(UUID id, UUID inspectionId);

  Optional<VerifiedFinding> findByInspectionIdAndAiCandidateId(
      UUID inspectionId, UUID aiCandidateId);

  long countByInspectionId(UUID inspectionId);

  /** MF3-13 hands only confirmed, repair-required findings to MF4. */
  @Query(
      "select f from VerifiedFinding f where f.inspectionId = :inspectionId "
          + "and f.repairRequired = true and f.decision in "
          + "(com.smartdroneinspection.inspections.domain.enums.FindingDecision.CONFIRMED, "
          + "com.smartdroneinspection.inspections.domain.enums.FindingDecision.MODIFIED)")
  List<VerifiedFinding> findRepairRequired(@Param("inspectionId") UUID inspectionId);
}
