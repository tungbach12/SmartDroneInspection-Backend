package com.smartdroneinspection.maintenance.repository;

import com.smartdroneinspection.maintenance.domain.MaintenanceQuotation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceQuotationRepository extends JpaRepository<MaintenanceQuotation, UUID> {

  List<MaintenanceQuotation> findByMaintenanceTicketIdOrderByVersionNumberDesc(
      UUID maintenanceTicketId);

  Optional<MaintenanceQuotation> findFirstByMaintenanceTicketIdOrderByVersionNumberDesc(
      UUID maintenanceTicketId);
}
