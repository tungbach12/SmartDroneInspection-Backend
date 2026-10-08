package com.smartdroneinspection.maintenance.repository;

import com.smartdroneinspection.maintenance.domain.MaintenanceOrder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceOrderRepository extends JpaRepository<MaintenanceOrder, UUID> {

  Optional<MaintenanceOrder> findByApprovedQuotationId(UUID approvedQuotationId);

  Optional<MaintenanceOrder> findFirstByMaintenanceTicketIdOrderByVersionNumberDesc(
      UUID maintenanceTicketId);

  List<MaintenanceOrder> findByMaintenanceTicketIdOrderByVersionNumberDesc(
      UUID maintenanceTicketId);
}
