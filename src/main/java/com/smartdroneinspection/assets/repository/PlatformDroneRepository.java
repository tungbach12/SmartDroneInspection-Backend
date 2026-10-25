package com.smartdroneinspection.assets.repository;

import com.smartdroneinspection.assets.domain.PlatformDrone;
import com.smartdroneinspection.assets.domain.enums.PlatformDroneStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformDroneRepository extends JpaRepository<PlatformDrone, UUID> {

  boolean existsBySerialNumber(String serialNumber);

  List<PlatformDrone> findAllByOrderByCreatedAtDesc();

  List<PlatformDrone> findByStatusOrderByCreatedAtDesc(PlatformDroneStatus status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from PlatformDrone d where d.id = :id")
  Optional<PlatformDrone> findByIdForUpdate(@Param("id") UUID id);
}
