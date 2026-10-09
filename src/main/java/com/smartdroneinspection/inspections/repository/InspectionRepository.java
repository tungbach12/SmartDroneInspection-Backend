package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.Inspection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InspectionRepository extends JpaRepository<Inspection, UUID> {

  Optional<Inspection> findByIdAndOrganizationId(UUID id, UUID organizationId);

  Optional<Inspection> findByIdAndOrganizationIdAndInspectorId(
      UUID id, UUID organizationId, UUID inspectorId);

  List<Inspection> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

  @Query(
      "select i from Inspection i where i.id = :id "
          + "and i.organizationId = :organizationId and i.inspectorId = :inspectorId")
  Optional<Inspection> findAssignedInspection(
      @Param("id") UUID id,
      @Param("organizationId") UUID organizationId,
      @Param("inspectorId") UUID inspectorId);

  Page<Inspection> findByOrganizationId(UUID organizationId, Pageable pageable);

  /**
   * Assignment scope for the Inspector list: their own inspections only, never the whole
   * organization. This mirrors the per-inspection {@code findByIdAndOrganizationIdAndInspectorId}
   * rule so the list cannot widen what the detail endpoint allows.
   */
  Page<Inspection> findByOrganizationIdAndInspectorId(
      UUID organizationId, UUID inspectorId, Pageable pageable);

  /**
   * Report review scope. The {@code exists} subquery keeps this one paged query rather than loading
   * every report and filtering in memory, so totalCount describes the filtered set.
   */
  @Query(
      value =
          "select i from Inspection i where i.organizationId = :organizationId "
              + "and exists (select r.id from InspectionReport r where r.inspectionId = i.id)",
      countQuery =
          "select count(i) from Inspection i where i.organizationId = :organizationId "
              + "and exists (select r.id from InspectionReport r where r.inspectionId = i.id)")
  Page<Inspection> findWithReportByOrganizationId(
      @Param("organizationId") UUID organizationId, Pageable pageable);

  @Query(
      value =
          "select i from Inspection i where i.organizationId = :organizationId "
              + "and i.inspectorId = :inspectorId "
              + "and exists (select r.id from InspectionReport r where r.inspectionId = i.id)",
      countQuery =
          "select count(i) from Inspection i where i.organizationId = :organizationId "
              + "and i.inspectorId = :inspectorId "
              + "and exists (select r.id from InspectionReport r where r.inspectionId = i.id)")
  Page<Inspection> findWithReportByOrganizationIdAndInspectorId(
      @Param("organizationId") UUID organizationId,
      @Param("inspectorId") UUID inspectorId,
      Pageable pageable);

  @Query(
      value =
          "select i from Inspection i where "
              + "exists (select r.id from InspectionReport r where r.inspectionId = i.id)",
      countQuery =
          "select count(i) from Inspection i where "
              + "exists (select r.id from InspectionReport r where r.inspectionId = i.id)")
  Page<Inspection> findWithReport(Pageable pageable);
}
