package com.smartdroneinspection.maintenance.repository;

import com.smartdroneinspection.maintenance.domain.MaintenanceChangeRequest;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceChangeRequestRepository
    extends JpaRepository<MaintenanceChangeRequest, UUID> {}
