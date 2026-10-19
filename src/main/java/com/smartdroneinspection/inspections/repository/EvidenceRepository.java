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

  /**
   * Duplicate detection for a retry-safe upload. The unique index on inspection plus checksum is
   * the storage contract; this lookup is how an identical upload becomes an idempotent response.
   */
  Optional<Evidence> findByInspectionIdAndChecksumSha256(UUID inspectionId, String checksumSha256);

  List<Evidence> findByInspectionIdOrderByCreatedAtAsc(UUID inspectionId);

  List<Evidence> findByInspectionIdAndRetiredAtIsNullOrderByCreatedAtAsc(UUID inspectionId);

  Optional<Evidence> findByIdAndInspectionId(UUID id, UUID inspectionId);

  Page<Evidence> findByInspectionId(UUID inspectionId, Pageable pageable);

  Page<Evidence> findByInspectionIdAndRetiredAtIsNull(UUID inspectionId, Pageable pageable);

  Page<Evidence> findByInspectionIdAndRetiredAtIsNotNull(UUID inspectionId, Pageable pageable);

  @Query("select e from Evidence e where e.id = :id and e.organizationId = :organizationId")
  Optional<Evidence> findInOrganization(
      @Param("id") UUID id, @Param("organizationId") UUID organizationId);

  long countByInspectionId(UUID inspectionId);

  @Query(
      "select max(e.createdAt) as latestCreatedAt, max(e.retiredAt) as latestRetiredAt "
          + "from Evidence e where e.inspectionId = :inspectionId")
  EvidenceSummary summarizeInspectionEvidence(@Param("inspectionId") UUID inspectionId);

  /**
   * The newest capture-added and capture-retired instants for one inspection. An Inspector's
   * evidence decision is stale when either is later than the decision, so a paginated read can
   * still answer "did the set change?" without loading every row.
   */
  interface EvidenceSummary {
    Instant getLatestCreatedAt();

    Instant getLatestRetiredAt();
  }

  long countByInspectionIdAndRetiredAtIsNull(UUID inspectionId);

  long countByInspectionIdAndRetiredAtIsNotNull(UUID inspectionId);
}
