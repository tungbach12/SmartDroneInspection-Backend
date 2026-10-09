package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.Evidence;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EvidenceRepository extends JpaRepository<Evidence, UUID> {

  /**
   * Duplicate detection for a retry-safe upload. The unique index on inspection plus checksum is
   * the storage contract; this lookup is how an identical upload becomes an idempotent response.
   */
  Optional<Evidence> findByInspectionIdAndChecksumSha256(UUID inspectionId, String checksumSha256);

  List<Evidence> findByInspectionIdOrderByCreatedAtAsc(UUID inspectionId);

  Optional<Evidence> findByIdAndInspectionId(UUID id, UUID inspectionId);

  @Query("select e from Evidence e where e.id = :id and e.organizationId = :organizationId")
  Optional<Evidence> findInOrganization(
      @Param("id") UUID id, @Param("organizationId") UUID organizationId);

  long countByInspectionId(UUID inspectionId);
}
