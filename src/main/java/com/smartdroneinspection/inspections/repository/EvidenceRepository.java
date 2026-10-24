package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.Evidence;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EvidenceRepository
    extends JpaRepository<Evidence, UUID>, JpaSpecificationExecutor<Evidence> {

  Optional<Evidence> findFirstByInspectionIdAndFileNameOrderByCreatedAtAsc(
      UUID inspectionId, String fileName);

  List<Evidence> findByInspectionIdOrderByCreatedAtAsc(UUID inspectionId);

  List<Evidence> findByInspectionIdAndIsDeletedFalseOrderByCreatedAtAsc(UUID inspectionId);

  boolean existsByInspectionIdAndIsDeletedFalse(UUID inspectionId);

  List<Evidence> findByInspectionIdAndFieldSessionIdAndIsDeletedFalseOrderByCreatedAtAsc(
      UUID inspectionId, UUID fieldSessionId);

  boolean existsByInspectionIdAndFieldSessionIdAndIsDeletedFalse(
      UUID inspectionId, UUID fieldSessionId);

  Optional<Evidence> findByIdAndInspectionId(UUID id, UUID inspectionId);

  Page<Evidence> findByInspectionId(UUID inspectionId, Pageable pageable);

  Page<Evidence> findByInspectionIdAndIsDeletedFalse(UUID inspectionId, Pageable pageable);

  Page<Evidence> findByInspectionIdAndIsDeletedTrue(UUID inspectionId, Pageable pageable);

  @Query(
      "select e from Evidence e join Inspection i on i.id = e.inspectionId where e.id = :id and"
          + " i.organizationId = :organizationId")
  Optional<Evidence> findInOrganization(
      @Param("id") UUID id, @Param("organizationId") UUID organizationId);

  long countByInspectionId(UUID inspectionId);

  @Query(
      "select max(e.createdAt) as latestCreatedAt "
          + "from Evidence e where e.inspectionId = :inspectionId")
  EvidenceSummary summarizeInspectionEvidence(@Param("inspectionId") UUID inspectionId);

  interface EvidenceSummary {
    Instant getLatestCreatedAt();

    default Instant getLatestRetiredAt() {
      return null;
    }
  }

  long countByInspectionIdAndIsDeletedFalse(UUID inspectionId);

  long countByInspectionIdAndIsDeletedTrue(UUID inspectionId);
}
