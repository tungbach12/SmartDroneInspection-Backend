package com.smartdroneinspection.maintenance.repository;

import com.smartdroneinspection.maintenance.domain.MaintenanceCostLine;
import com.smartdroneinspection.maintenance.domain.enums.CostLineState;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MaintenanceCostLineRepository extends JpaRepository<MaintenanceCostLine, UUID> {

  List<MaintenanceCostLine> findByEstimateVersionId(UUID estimateVersionId);

  /**
   * MF4-07: the SYSTEM calculates the version total from its priced lines rather than trusting a
   * typed-in number, so the sum is read back in decimal arithmetic.
   */
  @Query(
      "select coalesce(sum(l.amount), 0) from MaintenanceCostLine l "
          + "where l.workOrderId = :workOrderId and l.state = :state")
  BigDecimal sumAmountByWorkOrderIdAndState(
      @Param("workOrderId") UUID workOrderId, @Param("state") CostLineState state);
}
