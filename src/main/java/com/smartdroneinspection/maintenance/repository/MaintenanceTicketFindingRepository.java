package com.smartdroneinspection.maintenance.repository;

import com.smartdroneinspection.maintenance.domain.MaintenanceTicketFinding;
import com.smartdroneinspection.maintenance.domain.MaintenanceTicketFindingId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceTicketFindingRepository
    extends JpaRepository<MaintenanceTicketFinding, MaintenanceTicketFindingId> {}
