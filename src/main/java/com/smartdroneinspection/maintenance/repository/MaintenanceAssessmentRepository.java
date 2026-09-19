package com.smartdroneinspection.maintenance.repository;

import com.smartdroneinspection.maintenance.domain.MaintenanceAssessment;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceAssessmentRepository
    extends JpaRepository<MaintenanceAssessment, UUID> {}
