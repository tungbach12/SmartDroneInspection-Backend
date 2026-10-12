package com.smartdroneinspection.maintenance.repository;

import com.smartdroneinspection.maintenance.domain.MaintenanceWorkOrder;
import com.smartdroneinspection.maintenance.domain.enums.WorkOrderStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MaintenanceWorkOrderRepository extends JpaRepository<MaintenanceWorkOrder, UUID> {

  /** Organization-scoped read: a work order outside the caller's tenant must not be reachable. */
  Optional<MaintenanceWorkOrder> findByIdAndOrganizationId(UUID id, UUID organizationId);

  List<MaintenanceWorkOrder> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

  Page<MaintenanceWorkOrder> findByOrganizationId(UUID organizationId, Pageable pageable);

  /** Team members see only orders with an active assignment row. */
  @Query(
      "select w from MaintenanceWorkOrder w where w.organizationId = :organizationId "
          + "and exists (select tm.id from MaintenanceTeamMember tm "
          + "where tm.workOrderId = w.id and tm.engineerUserId = :engineerId "
          + "and tm.active = true)")
  Page<MaintenanceWorkOrder> findAssignedToEngineer(
      @Param("organizationId") UUID organizationId,
      @Param("engineerId") UUID engineerId,
      Pageable pageable);

  /** Team members receive not-found semantics for unassigned orders. */
  @Query(
      "select w from MaintenanceWorkOrder w where w.id = :id and w.organizationId = :organizationId "
          + "and exists (select tm.id from MaintenanceTeamMember tm "
          + "where tm.workOrderId = w.id and tm.engineerUserId = :engineerId "
          + "and tm.active = true)")
  Optional<MaintenanceWorkOrder> findAssignedOrder(
      @Param("id") UUID id,
      @Param("organizationId") UUID organizationId,
      @Param("engineerId") UUID engineerId);

  Page<MaintenanceWorkOrder> findByOrganizationIdAndStatus(
      UUID organizationId, WorkOrderStatus status, Pageable pageable);

  /**
   * MF4-01 duplicate check: one finding must not have two active work orders. Only the statuses
   * that represent live corrective work count as active; a closed or cancelled order does not block
   * a new one.
   */
  List<MaintenanceWorkOrder> findBySourceFindingIdAndStatusIn(
      UUID sourceFindingId, List<WorkOrderStatus> statuses);

  boolean existsBySourceFindingIdAndStatusIn(UUID sourceFindingId, List<WorkOrderStatus> statuses);
}
