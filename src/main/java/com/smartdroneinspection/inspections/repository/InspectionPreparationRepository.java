package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.InspectionPreparation;
import com.smartdroneinspection.inspections.domain.enums.InspectionPreparationStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Preparation lookups for MF2-03 through MF2-07.
 *
 * <p>Every finder takes the organization, even though {@code inspection_preparations} has no
 * organization column: the table reaches tenant scope only through {@code inspection_id}, so a
 * finder that omitted it could hand one organization's preparation to another.
 *
 * <p>The submission finder takes a pessimistic lock because MF2-06 and MF2-07 both act on the same
 * row, and two concurrent submissions would otherwise both pass the "not already submitted" check
 * and record two submission timestamps.
 */
public interface InspectionPreparationRepository
    extends JpaRepository<InspectionPreparation, UUID> {

  Optional<InspectionPreparation> findByIdAndInspectionId(UUID id, UUID inspectionId);

  List<InspectionPreparation> findByInspectionIdOrderByPreparationVersionDesc(UUID inspectionId);

  List<InspectionPreparation> findByInspectionIdAndStatus(
      UUID inspectionId, InspectionPreparationStatus status);

  /** The most recent version of a preparation for an inspection, newest first. */
  Optional<InspectionPreparation> findFirstByInspectionIdOrderByPreparationVersionDesc(
      UUID inspectionId);

  /**
   * A preparation locked for update, reached through an inspection the caller already scoped.
   *
   * <p>The organization is matched through the owning inspection rather than a column of its own.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select preparation from InspectionPreparation preparation "
          + "where preparation.id = :id and preparation.inspectionId = :inspectionId")
  Optional<InspectionPreparation> findWithLockByIdAndInspectionId(
      @Param("id") UUID id, @Param("inspectionId") UUID inspectionId);

  /**
   * Preparations of an inspection, but only those whose owning inspection belongs to the given
   * organization. This is the scope check in one query rather than two round trips.
   */
  @Query(
      "select preparation from InspectionPreparation preparation "
          + "join Inspection inspection on inspection.id = preparation.inspectionId "
          + "where preparation.id = :id and inspection.organizationId = :organizationId")
  Optional<InspectionPreparation> findByIdAndOrganizationId(
      @Param("id") UUID id, @Param("organizationId") UUID organizationId);
}
