package com.smartdroneinspection.assets.repository;

import com.smartdroneinspection.assets.domain.DroneLease;
import com.smartdroneinspection.assets.domain.enums.DroneLeaseStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DroneLeaseRepository extends JpaRepository<DroneLease, UUID> {

  List<DroneLease> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

  List<DroneLease> findAllByOrderByCreatedAtDesc();

  List<DroneLease> findByStatusOrderByCreatedAtDesc(DroneLeaseStatus status);

  List<DroneLease> findByPlatformDroneIdAndStatus(UUID platformDroneId, DroneLeaseStatus status);

  Optional<DroneLease> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
