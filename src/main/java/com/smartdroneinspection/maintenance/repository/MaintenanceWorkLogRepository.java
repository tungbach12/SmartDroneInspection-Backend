package com.smartdroneinspection.maintenance.repository;

import com.smartdroneinspection.maintenance.domain.MaintenanceWorkLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceWorkLogRepository extends JpaRepository<MaintenanceWorkLog, UUID> {}
