package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

/**
 * Inspection lookups, every one of them scoped to the owning organization.
 *
 * <p>{@code inspections} does carry an organization column, so unlike the MF1 catalog these finders
 * are straightforward tenant filters rather than joins.
 */
public interface InspectionRepository extends JpaRepository<Inspection, UUID> {

  Optional<Inspection> findByIdAndOrganizationId(UUID id, UUID organizationId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(
      value = {
        @QueryHint(name = "jakarta.persistence.cache.storeMode", value = "REFRESH"),
        @QueryHint(name = "jakarta.persistence.cache.retrieveMode", value = "BYPASS")
      })
  @Query(
      "select inspection from Inspection inspection "
          + "where inspection.id = :id and inspection.organizationId = :organizationId")
  Optional<Inspection> findWithLockByIdAndOrganizationId(
      @Param("id") UUID id, @Param("organizationId") UUID organizationId);

  List<Inspection> findByOrganizationIdAndInspectorIdOrderByUpdatedAtDesc(
      UUID organizationId, UUID inspectorId);

  List<Inspection> findByOrganizationIdAndStatusOrderByUpdatedAtDesc(
      UUID organizationId, InspectionStatus status);
}
