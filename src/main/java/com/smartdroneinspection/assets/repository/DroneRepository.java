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

/** Customer-owned drone access for MF1 inventory and MF2 pairing/readiness. */
public interface DroneRepository extends JpaRepository<Drone, UUID> {

  List<Drone> findAllByInspectorOwnerId(UUID inspectorOwnerId);

  boolean existsBySerialNumberIgnoreCase(String serialNumber);

  Optional<Drone> findBySerialNumberIgnoreCase(String serialNumber);

  boolean existsByInspectorOwnerIdAndSerialNumber(UUID inspectorOwnerId, String serialNumber);

  /** Inspector-owned partner drones eligible for MF2 contract assignment. */
  @Query(
      "select drone from Drone drone where drone.inspectorOwnerId is not null "
          + "and drone.serviceability = com.smartdroneinspection.assets.domain.enums.DroneServiceability.ACTIVE "
          + "order by drone.serialNumber asc")
  List<Drone> findActivePartnerDrones();

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select drone from Drone drone where drone.id = :id")
  Optional<Drone> findWithLockById(@Param("id") UUID id);

  // --- Owner-based access: owner_customer_id is the sole tenant key. ---

  Optional<Drone> findByIdAndOwnerCustomerId(UUID id, UUID ownerCustomerId);

  Optional<Drone> findByOwnerCustomerIdAndSerialNumber(UUID ownerCustomerId, String serialNumber);

  boolean existsByOwnerCustomerIdAndSerialNumber(UUID ownerCustomerId, String serialNumber);

  Page<Drone> findByOwnerCustomerId(UUID ownerCustomerId, Pageable pageable);

  List<Drone> findAllByOwnerCustomerId(UUID ownerCustomerId);

  List<Drone> findByOwnerCustomerIdAndServiceabilityOrderBySerialNumberAsc(
      UUID ownerCustomerId, DroneServiceability serviceability);

  List<Drone> findAllByOwnerCustomerIdAndServiceability(
      UUID ownerCustomerId, DroneServiceability serviceability);

  @Query(
      "select drone from Drone drone where drone.ownerCustomerId = :ownerCustomerId "
          + "and drone.serviceability = com.smartdroneinspection.assets.domain.enums.DroneServiceability.ACTIVE "
          + "order by drone.serialNumber asc")
  List<Drone> findAssignableByOwnerCustomerId(@Param("ownerCustomerId") UUID ownerCustomerId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select drone from Drone drone where drone.id = :id "
          + "and drone.ownerCustomerId = :ownerCustomerId")
  Optional<Drone> findWithLockByIdAndOwnerCustomerId(
      @Param("id") UUID id, @Param("ownerCustomerId") UUID ownerCustomerId);
}
