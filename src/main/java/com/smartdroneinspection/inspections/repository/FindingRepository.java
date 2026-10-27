package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.Finding;
import com.smartdroneinspection.inspections.domain.enums.FindingStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FindingRepository extends JpaRepository<Finding, UUID> {

  List<Finding> findByInspectionIdOrderByCreatedAtAsc(UUID inspectionId);

  List<Finding> findByEvidenceIdOrderByCreatedAtAsc(UUID evidenceId);

  void deleteByIdAndInspectionId(UUID id, UUID inspectionId);

  Optional<Finding> findByIdAndInspectionId(UUID id, UUID inspectionId);

  long countByInspectionId(UUID inspectionId);

  long countByInspectionIdAndStatus(UUID inspectionId, FindingStatus status);

  /**
   * The removed findings of an inspection, for the caller that asks to see them.
   *
   * <p>The entity carries a soft-delete restriction, so this query asks for the removed rows
   * explicitly rather than relying on a finder that would filter them out.
   */
  @Query(
      "select f from Finding f where f.inspectionId = :inspectionId and f.isDeleted = true "
          + "order by f.createdAt desc")
  List<Finding> findRemovedByInspectionId(@Param("inspectionId") UUID inspectionId);

  /** The next free code for this inspection, counting removed rows so a code is never reused. */
  @Query(
      value = "SELECT count(*) FROM findings WHERE inspection_id = :inspectionId",
      nativeQuery = true)
  long countIncludingRemovedByInspectionId(@Param("inspectionId") UUID inspectionId);

  @Query(
      value =
          "SELECT COALESCE(MAX(NULLIF(regexp_replace(finding_code, '\\D', '', 'g'), '')::bigint), 0) "
              + "FROM findings WHERE inspection_id = :inspectionId",
      nativeQuery = true)
  long maxFindingCodeSequence(@Param("inspectionId") UUID inspectionId);

  @Query(
      value = "SELECT * FROM findings WHERE inspection_id = :inspectionId ORDER BY created_at ASC",
      nativeQuery = true)
  List<Finding> findAllByInspectionIdIncludingRemoved(@Param("inspectionId") UUID inspectionId);

  List<Finding> findByForwardedToMaintenanceAtIsNotNull();

  List<Finding> findByInspectionIdAndForwardedToMaintenanceAtIsNotNull(UUID inspectionId);
}
