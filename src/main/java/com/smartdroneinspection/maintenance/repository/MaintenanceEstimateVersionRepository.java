package com.smartdroneinspection.maintenance.repository;

import com.smartdroneinspection.maintenance.domain.MaintenanceEstimateVersion;
import com.smartdroneinspection.maintenance.domain.enums.EstimateStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceEstimateVersionRepository
    extends JpaRepository<MaintenanceEstimateVersion, UUID> {

  List<MaintenanceEstimateVersion> findByWorkOrderIdOrderByVersionNoDesc(UUID workOrderId);

  Optional<MaintenanceEstimateVersion> findByWorkOrderIdAndVersionNo(
      UUID workOrderId, int versionNo);

  Optional<MaintenanceEstimateVersion> findFirstByWorkOrderIdOrderByVersionNoDesc(UUID workOrderId);

  Optional<MaintenanceEstimateVersion> findByWorkOrderIdAndStatus(
      UUID workOrderId, EstimateStatus status);
}
