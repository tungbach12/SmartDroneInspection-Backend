package com.smartdroneinspection.assets.repository;

import com.smartdroneinspection.assets.domain.DroneRegistrationApplication;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DroneRegistrationApplicationRepository
    extends JpaRepository<DroneRegistrationApplication, UUID> {
  List<DroneRegistrationApplication> findAllByInspectorUserIdOrderByCreatedAtDesc(UUID inspectorId);

  List<DroneRegistrationApplication> findAllByStatusOrderByCreatedAtAsc(String status);

  Optional<DroneRegistrationApplication> findByIdAndStatus(UUID id, String status);

  boolean existsBySerialNumberIgnoreCaseAndStatus(String serialNumber, String status);
}
