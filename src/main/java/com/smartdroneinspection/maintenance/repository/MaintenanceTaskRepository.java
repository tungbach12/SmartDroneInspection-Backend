package com.smartdroneinspection.maintenance.repository;

import com.smartdroneinspection.maintenance.domain.MaintenanceTask;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceTaskRepository extends JpaRepository<MaintenanceTask, UUID> {

  List<MaintenanceTask> findByWorkOrderIdOrderByTaskNumberAsc(UUID workOrderId);

  Optional<MaintenanceTask> findByIdAndWorkOrderId(UUID id, UUID workOrderId);

  Optional<MaintenanceTask> findByWorkOrderIdAndTaskNumber(UUID workOrderId, int taskNumber);

  /** MF4-05: the lead allocates the next task number, so the current maximum is needed. */
  Optional<MaintenanceTask> findFirstByWorkOrderIdOrderByTaskNumberDesc(UUID workOrderId);
}
