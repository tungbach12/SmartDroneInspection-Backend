package com.smartdroneinspection.maintenance.repository;

import com.smartdroneinspection.maintenance.domain.MaintenanceAssignment;
import com.smartdroneinspection.maintenance.domain.enums.MaintenanceAssignmentStatus;
import com.smartdroneinspection.maintenance.domain.enums.MaintenanceAssignmentType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceAssignmentRepository
    extends JpaRepository<MaintenanceAssignment, UUID> {

  List<MaintenanceAssignment> findByEngineerUserIdAndStatusOrderByDeadlineAsc(
      UUID engineerUserId, MaintenanceAssignmentStatus status);

  Optional<MaintenanceAssignment> findByIdAndEngineerUserId(UUID id, UUID engineerUserId);

  Optional<MaintenanceAssignment> findByMaintenanceTicketIdAndAssignmentTypeAndStatusIn(
      UUID ticketId,
      MaintenanceAssignmentType type,
      Collection<MaintenanceAssignmentStatus> statuses);
}
