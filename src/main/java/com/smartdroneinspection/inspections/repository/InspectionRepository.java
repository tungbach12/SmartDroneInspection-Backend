package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Inspection lookups, every one of them scoped to the owning organization.
 *
 * <p>{@code inspections} does carry an organization column, so unlike the MF1 catalog these finders
 * are straightforward tenant filters rather than joins.
 */
public interface InspectionRepository extends JpaRepository<Inspection, UUID> {

  Optional<Inspection> findByIdAndOrganizationId(UUID id, UUID organizationId);

  List<Inspection> findByOrganizationIdAndInspectorIdOrderByUpdatedAtDesc(
      UUID organizationId, UUID inspectorId);

  List<Inspection> findByOrganizationIdAndStatusOrderByUpdatedAtDesc(
      UUID organizationId, InspectionStatus status);
}
