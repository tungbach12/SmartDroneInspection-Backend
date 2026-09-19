package com.smartdroneinspection.maintenance.repository;

import com.smartdroneinspection.maintenance.domain.MaintenanceAssignment;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceAssignmentRepository
    extends JpaRepository<MaintenanceAssignment, UUID> {}
