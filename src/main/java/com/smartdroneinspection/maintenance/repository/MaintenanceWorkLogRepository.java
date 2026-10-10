package com.smartdroneinspection.maintenance.repository;

import com.smartdroneinspection.maintenance.domain.MaintenanceWorkLog;
import com.smartdroneinspection.maintenance.domain.enums.WorkLogStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MaintenanceWorkLogRepository extends JpaRepository<MaintenanceWorkLog, UUID> {

  List<MaintenanceWorkLog> findByWorkOrderIdOrderByStartedAtAsc(UUID workOrderId);

  List<MaintenanceWorkLog> findByWorkOrderIdAndEngineerUserIdOrderByStartedAtDesc(
      UUID workOrderId, UUID engineerUserId);

  List<MaintenanceWorkLog> findByTaskIdOrderByStartedAtAsc(UUID taskId);

  /** MF4-11: the lead verifies each submitted timesheet; verification is a separate act. */
  List<MaintenanceWorkLog> findByWorkOrderIdAndStatus(UUID workOrderId, WorkLogStatus status);

  /**
   * MF4-20: the actual total is summed from reconciled cost lines in decimal arithmetic rather than
   * read from a typed-in figure.
   */
  @Query(
      "select coalesce(sum(l.hours), 0) from MaintenanceWorkLog l "
          + "where l.workOrderId = :workOrderId and l.status = :status")
  BigDecimal sumHoursByWorkOrderIdAndStatus(
      @Param("workOrderId") UUID workOrderId, @Param("status") WorkLogStatus status);
}
