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

  @Query(
      value = "SELECT count(*) FROM verified_findings WHERE inspection_id = :inspectionId",
      nativeQuery = true)
  long countIncludingDeletedByInspectionId(@Param("inspectionId") UUID inspectionId);

  List<VerifiedFinding> findByRepairRequiredTrueAndInspectorConfirmedAtNotNull();

  /** MF3-13 hands only confirmed, repair-required findings to MF4. */
  @Query(
      "select f from VerifiedFinding f where f.inspectionId = :inspectionId "
          + "and f.repairRequired = true and f.correctiveActionReviewedByUserId is not null "
          + "and f.correctiveActionRequired = true and f.inspectorConfirmedAt is not null")
  List<VerifiedFinding> findRepairRequired(@Param("inspectionId") UUID inspectionId);
}
