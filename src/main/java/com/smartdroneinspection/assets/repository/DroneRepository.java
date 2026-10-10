package com.smartdroneinspection.assets.repository;

import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.enums.DroneServiceability;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Drone lookups scoped to the owning organization.
 *
 * <p>Every finder that accepts an organization id exists so a caller can never reach another
 * tenant's fleet by guessing an id.
 */
public interface DroneRepository extends JpaRepository<Drone, UUID> {

  Optional<Drone> findByIdAndOrganizationId(UUID id, UUID organizationId);

  Optional<Drone> findByOrganizationIdAndSerialNumber(UUID organizationId, String serialNumber);

  List<Drone> findByOrganizationIdAndServiceabilityOrderBySerialNumberAsc(
      UUID organizationId, DroneServiceability serviceability);

  Page<Drone> findByOrganizationId(UUID organizationId, Pageable pageable);

  /** Drones an organization may still pair with an inspector to. */
  @Query(
      "select drone from Drone drone where drone.organizationId = :organizationId "
          + "and drone.serviceability = com.smartdroneinspection.assets.domain.enums.DroneServiceability.ACTIVE "
          + "order by drone.serialNumber asc")
  List<Drone> findAssignableByOrganizationId(@Param("organizationId") UUID organizationId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select drone from Drone drone where drone.id = :id and drone.organizationId = :organizationId")
  Optional<Drone> findWithLockByIdAndOrganizationId(
      @Param("id") UUID id, @Param("organizationId") UUID organizationId);

  boolean existsByOrganizationIdAndSerialNumber(UUID organizationId, String serialNumber);
}
