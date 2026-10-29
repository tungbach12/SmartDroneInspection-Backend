package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface InspectionRepository extends JpaRepository<Inspection, UUID> {

  Optional<Inspection> findByIdAndOrganizationId(UUID id, UUID organizationId);

  Optional<Inspection> findByIdAndOrganizationIdAndInspectorId(
      UUID id, UUID organizationId, UUID inspectorId);

  @Query(
      "select inspection from Inspection inspection where inspection.id = :id "
          + "and inspection.inspectorId = :inspectorId "
          + "and (inspection.organizationId = :organizationId or inspection.partnerAssignment = true)")
  Optional<Inspection> findAssignedInspectorInspection(
      @Param("id") UUID id,
      @Param("organizationId") UUID organizationId,
      @Param("inspectorId") UUID inspectorId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select inspection from Inspection inspection where inspection.id = :id "
          + "and inspection.inspectorId = :inspectorId "
          + "and (inspection.organizationId = :organizationId or inspection.partnerAssignment = true)")
  Optional<Inspection> findAssignedInspectorInspectionWithLock(
      @Param("id") UUID id,
      @Param("organizationId") UUID organizationId,
      @Param("inspectorId") UUID inspectorId);

  List<Inspection> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

  @Query(
      "select i from Inspection i where i.id = :id "
          + "and i.organizationId = :organizationId and i.inspectorId = :inspectorId")
  Optional<Inspection> findAssignedInspection(
      @Param("id") UUID id,
      @Param("organizationId") UUID organizationId,
      @Param("inspectorId") UUID inspectorId);

  Page<Inspection> findByOrganizationId(UUID organizationId, Pageable pageable);

  /** Assigned Inspectors retain their organization-scoped rows plus MF2 partner assignments. */
  @Query(
      value =
          "select i from Inspection i where i.inspectorId = :inspectorId "
              + "and (i.organizationId = :organizationId or i.partnerAssignment = true)",
      countQuery =
          "select count(i) from Inspection i where i.inspectorId = :inspectorId "
              + "and (i.organizationId = :organizationId or i.partnerAssignment = true)")
  Page<Inspection> findByOrganizationIdAndInspectorId(
      @Param("organizationId") UUID organizationId,
      @Param("inspectorId") UUID inspectorId,
      Pageable pageable);

  /**
   * Report review scope. The {@code exists} subquery keeps this one paged query rather than loading
   * every report and filtering in memory, so totalCount describes the filtered set.
   */
  @Query(
      value =
          "select i from Inspection i where i.organizationId = :organizationId "
              + "and exists (select v.id from InspectionReport v where v.inspectionId = i.id)",
      countQuery =
          "select count(i) from Inspection i where i.organizationId = :organizationId "
              + "and exists (select v.id from InspectionReport v where v.inspectionId = i.id)")
  Page<Inspection> findWithReportByOrganizationId(
      @Param("organizationId") UUID organizationId, Pageable pageable);

  @Query(
      value =
          "select i from Inspection i where i.inspectorId = :inspectorId "
              + "and (i.organizationId = :organizationId or i.partnerAssignment = true) "
              + "and exists (select v.id from InspectionReport v where v.inspectionId = i.id)",
      countQuery =
          "select count(i) from Inspection i where i.inspectorId = :inspectorId "
              + "and (i.organizationId = :organizationId or i.partnerAssignment = true) "
              + "and exists (select v.id from InspectionReport v where v.inspectionId = i.id)")
  Page<Inspection> findWithReportByOrganizationIdAndInspectorId(
      @Param("organizationId") UUID organizationId,
      @Param("inspectorId") UUID inspectorId,
      Pageable pageable);

  @Query(
      value =
          "select i from Inspection i where "
              + "exists (select v.id from InspectionReport v where v.inspectionId = i.id)",
      countQuery =
          "select count(i) from Inspection i where "
              + "exists (select v.id from InspectionReport v where v.inspectionId = i.id)")
  Page<Inspection> findWithReport(Pageable pageable);

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

  @Query(
      "select count(i) > 0 from Inspection i where i.organizationId = :organizationId "
          + "and (i.inspectorId = :inspectorId or i.droneId = :droneId) "
          + "and i.status not in (com.smartdroneinspection.inspections.domain.enums.InspectionStatus.CANCELLED, com.smartdroneinspection.inspections.domain.enums.InspectionStatus.COMPLETED) "
          + "and i.plannedStartAt is not null and i.plannedEndAt is not null "
          + "and i.plannedStartAt < :end and i.plannedEndAt > :start")
  boolean hasScheduleConflict(
      @Param("organizationId") UUID organizationId,
      @Param("inspectorId") UUID inspectorId,
      @Param("droneId") UUID droneId,
      @Param("start") java.time.Instant start,
      @Param("end") java.time.Instant end);

  @Query(
      "select count(i) > 0 from Inspection i where "
          + "(i.inspectorId = :inspectorId or i.droneId = :droneId) "
          + "and i.organizationId <> :organizationId "
          + "and i.status not in (com.smartdroneinspection.inspections.domain.enums.InspectionStatus.CANCELLED, com.smartdroneinspection.inspections.domain.enums.InspectionStatus.COMPLETED) "
          + "and i.plannedStartAt is not null and i.plannedEndAt is not null "
          + "and i.plannedStartAt < :end and i.plannedEndAt > :start")
  boolean hasPartnerScheduleConflictOutsideOrganization(
      @Param("organizationId") UUID organizationId,
      @Param("inspectorId") UUID inspectorId,
      @Param("droneId") UUID droneId,
      @Param("start") java.time.Instant start,
      @Param("end") java.time.Instant end);

  @Query(
      "select count(i) > 0 from Inspection i where "
          + "(i.inspectorId = :inspectorId or i.droneId = :droneId) "
          + "and i.status not in (com.smartdroneinspection.inspections.domain.enums.InspectionStatus.CANCELLED, com.smartdroneinspection.inspections.domain.enums.InspectionStatus.COMPLETED) "
          + "and i.plannedStartAt is not null and i.plannedEndAt is not null "
          + "and i.plannedStartAt < :end and i.plannedEndAt > :start")
  boolean hasPlatformPartnerScheduleConflict(
      @Param("inspectorId") UUID inspectorId,
      @Param("droneId") UUID droneId,
      @Param("start") java.time.Instant start,
      @Param("end") java.time.Instant end);

  @Query(
      "select count(i) > 0 from Inspection i where i.id = :inspectionId "
          + "and i.partnerAssignment = true and i.inspectorId = :inspectorId")
  boolean isPartnerInspectionAssignedTo(
      @Param("inspectionId") UUID inspectionId, @Param("inspectorId") UUID inspectorId);
}
