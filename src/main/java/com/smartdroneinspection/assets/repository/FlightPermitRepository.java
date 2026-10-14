package com.smartdroneinspection.assets.repository;

import com.smartdroneinspection.assets.domain.FlightPermit;
import com.smartdroneinspection.assets.domain.enums.FlightPermitStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tenant-scoped permit queries for MF1 management and MF2 readiness. */
public interface FlightPermitRepository extends JpaRepository<FlightPermit, UUID> {

  Optional<FlightPermit> findByIdAndOrganizationId(UUID id, UUID organizationId);

  /** MF1: tenant-safe lookup for internal record review. */
  @Query("select p from FlightPermit p where p.id = :id and p.organizationId = :organizationId")
  Optional<FlightPermit> findInOrganization(
      @Param("id") UUID id, @Param("organizationId") UUID organizationId);

  /** MF1: list only records owned by the caller's organization. */
  @Query(
      "select p from FlightPermit p where p.organizationId = :organizationId"
          + " order by p.createdAt desc")
  List<FlightPermit> listByOrganization(@Param("organizationId") UUID organizationId);

  /** MF2: permits with a recorded status for one tenant. */
  List<FlightPermit> findByOrganizationIdAndStatusOrderByCreatedAtDesc(
      UUID organizationId, FlightPermitStatus status);

  /** MF2: permits attached to a particular asset. */
  List<FlightPermit> findByOrganizationIdAndAssetIdOrderByCreatedAtDesc(
      UUID organizationId, UUID assetId);

  /** Permits that may support readiness for this tenant's asset. */
  List<FlightPermit> findByOrganizationIdAndAssetIdAndStatusIn(
      UUID organizationId, UUID assetId, List<FlightPermitStatus> statuses);
}
