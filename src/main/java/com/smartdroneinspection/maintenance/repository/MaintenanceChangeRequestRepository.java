package com.smartdroneinspection.maintenance.repository;

import com.smartdroneinspection.maintenance.domain.MaintenanceChangeRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceChangeRequestRepository
    extends JpaRepository<MaintenanceChangeRequest, UUID> {

  List<MaintenanceChangeRequest> findByMaintenanceTicketIdOrderByCreatedAtDesc(
      UUID maintenanceTicketId);
}
