package com.smartdroneinspection.maintenance.repository;

import com.smartdroneinspection.maintenance.domain.MaintenanceReportVersion;
import com.smartdroneinspection.maintenance.domain.enums.ReportVersionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceReportVersionRepository
    extends JpaRepository<MaintenanceReportVersion, UUID> {

  List<MaintenanceReportVersion> findByWorkOrderIdOrderByVersionNoDesc(UUID workOrderId);

  Optional<MaintenanceReportVersion> findByWorkOrderIdAndVersionNo(UUID workOrderId, int versionNo);

  Optional<MaintenanceReportVersion> findFirstByWorkOrderIdOrderByVersionNoDesc(UUID workOrderId);

  Optional<MaintenanceReportVersion> findByWorkOrderIdAndStatus(
      UUID workOrderId, ReportVersionStatus status);
}
