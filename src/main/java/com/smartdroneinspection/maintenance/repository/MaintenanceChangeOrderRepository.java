package com.smartdroneinspection.maintenance.repository;

import com.smartdroneinspection.maintenance.domain.MaintenanceChangeOrder;
import com.smartdroneinspection.maintenance.domain.enums.ChangeOrderStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MaintenanceChangeOrderRepository
    extends JpaRepository<MaintenanceChangeOrder, UUID> {

  List<MaintenanceChangeOrder> findByWorkOrderIdOrderByChangeNumberDesc(UUID workOrderId);

  Optional<MaintenanceChangeOrder> findByWorkOrderIdAndChangeNumber(
      UUID workOrderId, int changeNumber);

  Optional<MaintenanceChangeOrder> findFirstByWorkOrderIdOrderByChangeNumberDesc(UUID workOrderId);

  List<MaintenanceChangeOrder> findByWorkOrderIdAndStatus(
      UUID workOrderId, ChangeOrderStatus status);

  /**
   * MF4-13: only approved changes widen the authorized amount, so the delta is summed over approved
   * rows alone.
   */
  @Query(
      "select coalesce(sum(c.proposedDelta), 0) from MaintenanceChangeOrder c "
          + "where c.workOrderId = :workOrderId and c.status = :status")
  BigDecimal sumDeltaByWorkOrderIdAndStatus(
      @Param("workOrderId") UUID workOrderId, @Param("status") ChangeOrderStatus status);
}
