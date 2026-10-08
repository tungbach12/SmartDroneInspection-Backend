package com.smartdroneinspection.maintenance.repository;

import com.smartdroneinspection.maintenance.domain.MaintenanceWorkLog;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceWorkLogRepository extends JpaRepository<MaintenanceWorkLog, UUID> {

  List<MaintenanceWorkLog> findByMaintenanceTicketIdOrderByStartedAtDesc(UUID maintenanceTicketId);

  Optional<MaintenanceWorkLog> findByIdAndEngineerUserId(UUID id, UUID engineerUserId);
}
