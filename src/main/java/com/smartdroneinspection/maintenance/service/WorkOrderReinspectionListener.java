package com.smartdroneinspection.maintenance.service;

import com.smartdroneinspection.inspections.spi.ReinspectionReportPublishedEvent;
import com.smartdroneinspection.maintenance.domain.MaintenanceWorkOrder;
import com.smartdroneinspection.maintenance.repository.MaintenanceWorkOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * MF4-18: Listens to ReinspectionReportPublishedEvent and attaches the re-inspection report to the
 * maintenance work order, advancing it for reviewer acceptance.
 */
@Component
public class WorkOrderReinspectionListener {

  private static final Logger log = LoggerFactory.getLogger(WorkOrderReinspectionListener.class);

  private final MaintenanceWorkOrderRepository workOrders;

  public WorkOrderReinspectionListener(MaintenanceWorkOrderRepository workOrders) {
    this.workOrders = workOrders;
  }

  @ApplicationModuleListener
  public void onReinspectionReportPublished(ReinspectionReportPublishedEvent event) {
    log.info(
        "Handling published re-inspection report {} for work order {}",
        event.reportVersionId(),
        event.workOrderId());
    MaintenanceWorkOrder workOrder = workOrders.findById(event.workOrderId()).orElse(null);
    if (workOrder == null) {
      log.warn(
          "Work order {} not found for re-inspection report {}",
          event.workOrderId(),
          event.reportVersionId());
      return;
    }

    workOrder.recordReinspectionCompleted(event.reportVersionId());
    workOrders.saveAndFlush(workOrder);
    log.info(
        "Attached re-inspection report {} to work order {}, current status: {}",
        event.reportVersionId(),
        workOrder.getId(),
        workOrder.getStatus());
  }
}
