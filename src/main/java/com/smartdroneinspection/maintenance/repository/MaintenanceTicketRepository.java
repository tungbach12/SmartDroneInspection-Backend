package com.smartdroneinspection.maintenance.repository;

import com.smartdroneinspection.maintenance.domain.MaintenanceTicket;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceTicketRepository extends JpaRepository<MaintenanceTicket, UUID> {

  Page<MaintenanceTicket> findByOrganizationIdOrderByCreatedAtDesc(
      UUID organizationId, Pageable pageable);

  Optional<MaintenanceTicket> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
