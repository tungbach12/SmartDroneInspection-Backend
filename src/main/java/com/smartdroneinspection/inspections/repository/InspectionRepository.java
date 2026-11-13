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

  Optional<Inspection> findByIdAndOwnerCustomerId(UUID id, UUID ownerCustomerId);

  Optional<Inspection> findByIdAndOwnerCustomerIdAndInspectorId(
      UUID id, UUID ownerCustomerId, UUID inspectorId);

  /**
   * The inspector assigned to this inspection, whether a partner dispatch or an owned assignment.
   */
  @Query(
      "select inspection from Inspection inspection where inspection.id = :id "
          + "and inspection.inspectorId = :inspectorId")
  Optional<Inspection> findAssignedByInspector(
      @Param("id") UUID id, @Param("inspectorId") UUID inspectorId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select inspection from Inspection inspection where inspection.id = :id "
          + "and inspection.inspectorId = :inspectorId")
  Optional<Inspection> findAssignedByInspectorWithLock(
      @Param("id") UUID id, @Param("inspectorId") UUID inspectorId);

  @Query("select i from Inspection i where i.inspectorId = :inspectorId")
  Page<Inspection> findByInspectorId(@Param("inspectorId") UUID inspectorId, Pageable pageable);

  @Query(
      value =
          "select i from Inspection i where i.inspectorId = :inspectorId "
              + "and exists (select v.id from InspectionReport v where v.inspectionId = i.id)",
      countQuery =
          "select count(i) from Inspection i where i.inspectorId = :inspectorId "
              + "and exists (select v.id from InspectionReport v where v.inspectionId = i.id)")
  Page<Inspection> findWithReportByInspectorId(
      @Param("inspectorId") UUID inspectorId, Pageable pageable);

  List<Inspection> findByOwnerCustomerIdOrderByCreatedAtDesc(UUID ownerCustomerId);

  Page<Inspection> findByOwnerCustomerId(UUID ownerCustomerId, Pageable pageable);

  /** Assigned Inspectors retain their owned rows plus MF2 partner assignments. */
  @Query(
      value =
          "select i from Inspection i where i.inspectorId = :inspectorId "
              + "and (i.ownerCustomerId = :ownerCustomerId or i.partnerAssignment = true)",
      countQuery =
          "select count(i) from Inspection i where i.inspectorId = :inspectorId "
              + "and (i.ownerCustomerId = :ownerCustomerId or i.partnerAssignment = true)")
  Page<Inspection> findByOwnerCustomerIdAndInspectorId(
      @Param("ownerCustomerId") UUID ownerCustomerId,
      @Param("inspectorId") UUID inspectorId,
      Pageable pageable);

  Page<Inspection> findByInspectorIdAndPartnerAssignmentTrue(UUID inspectorId, Pageable pageable);

  @Query(
      value =
          "select i from Inspection i where i.inspectorId = :inspectorId "
              + "and i.partnerAssignment = true "
              + "and exists (select v.id from InspectionReport v where v.inspectionId = i.id)",
      countQuery =
          "select count(i) from Inspection i where i.inspectorId = :inspectorId "
              + "and i.partnerAssignment = true "
              + "and exists (select v.id from InspectionReport v where v.inspectionId = i.id)")
  Page<Inspection> findWithReportByInspectorIdAndPartnerAssignmentTrue(
      @Param("inspectorId") UUID inspectorId, Pageable pageable);

  /**
   * Report review scope. The {@code exists} subquery keeps this one paged query rather than loading
   * every report and filtering in memory, so totalCount describes the filtered set.
   */
  @Query(
      value =
          "select i from Inspection i where i.ownerCustomerId = :ownerCustomerId "
              + "and exists (select v.id from InspectionReport v where v.inspectionId = i.id)",
      countQuery =
          "select count(i) from Inspection i where i.ownerCustomerId = :ownerCustomerId "
              + "and exists (select v.id from InspectionReport v where v.inspectionId = i.id)")
  Page<Inspection> findWithReportByOwnerCustomerId(
      @Param("ownerCustomerId") UUID ownerCustomerId, Pageable pageable);

  @Query(
      value =
          "select i from Inspection i where i.inspectorId = :inspectorId "
              + "and (i.ownerCustomerId = :ownerCustomerId or i.partnerAssignment = true) "
              + "and exists (select v.id from InspectionReport v where v.inspectionId = i.id)",
      countQuery =
          "select count(i) from Inspection i where i.inspectorId = :inspectorId "
              + "and (i.ownerCustomerId = :ownerCustomerId or i.partnerAssignment = true) "
              + "and exists (select v.id from InspectionReport v where v.inspectionId = i.id)")
  Page<Inspection> findWithReportByOwnerCustomerIdAndInspectorId(
      @Param("ownerCustomerId") UUID ownerCustomerId,
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
          + "where inspection.id = :id and inspection.ownerCustomerId = :ownerCustomerId")
  Optional<Inspection> findWithLockByIdAndOwnerCustomerId(
      @Param("id") UUID id, @Param("ownerCustomerId") UUID ownerCustomerId);

  List<Inspection> findByOwnerCustomerIdAndInspectorIdOrderByUpdatedAtDesc(
      UUID ownerCustomerId, UUID inspectorId);

  List<Inspection> findByOwnerCustomerIdAndStatusOrderByUpdatedAtDesc(
      UUID ownerCustomerId, InspectionStatus status);

  @Query(
      "select count(i) > 0 from Inspection i where i.ownerCustomerId = :ownerCustomerId "
          + "and (i.inspectorId = :inspectorId or i.droneId = :droneId) "
          + "and i.status not in (com.smartdroneinspection.inspections.domain.enums.InspectionStatus.CANCELLED, com.smartdroneinspection.inspections.domain.enums.InspectionStatus.COMPLETED) "
          + "and i.plannedStartAt is not null and i.plannedEndAt is not null "
          + "and i.plannedStartAt < :end and i.plannedEndAt > :start")
  boolean hasScheduleConflictByOwner(
      @Param("ownerCustomerId") UUID ownerCustomerId,
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
