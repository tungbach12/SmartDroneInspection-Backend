package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.Inspection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
}
