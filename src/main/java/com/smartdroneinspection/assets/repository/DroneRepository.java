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

/** Organization-scoped and Inspector-partner-owned Drone access. */
public interface DroneRepository extends JpaRepository<Drone, UUID> {

  Optional<Drone> findByIdAndOrganizationId(UUID id, UUID organizationId);

  Optional<Drone> findByOrganizationIdAndSerialNumber(UUID organizationId, String serialNumber);

  boolean existsByOrganizationIdAndSerialNumber(UUID organizationId, String serialNumber);

  Page<Drone> findByOrganizationId(UUID organizationId, Pageable pageable);

  List<Drone> findAllByOrganizationId(UUID organizationId);

  List<Drone> findAllByInspectorOwnerId(UUID inspectorOwnerId);

  boolean existsBySerialNumberIgnoreCase(String serialNumber);

  boolean existsByInspectorOwnerIdAndSerialNumber(UUID inspectorOwnerId, String serialNumber);

  List<Drone> findByOrganizationIdAndServiceabilityOrderBySerialNumberAsc(
      UUID organizationId, DroneServiceability serviceability);

  List<Drone> findAllByOrganizationIdAndServiceability(
      UUID organizationId, DroneServiceability serviceability);

  /** Drones an organization may pair with an Inspector. */
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

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select drone from Drone drone where drone.id = :id")
  Optional<Drone> findWithLockById(@Param("id") UUID id);
}
