package com.smartdroneinspection.maintenance.service;

import com.smartdroneinspection.inspections.spi.ReinspectionCreatedEvent;
import com.smartdroneinspection.inspections.spi.ReinspectionReportPublishedEvent;
import com.smartdroneinspection.maintenance.domain.MaintenanceWorkOrder;
import com.smartdroneinspection.maintenance.domain.enums.ReportVersionStatus;
import com.smartdroneinspection.maintenance.repository.MaintenanceReportVersionRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceWorkOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * MF4-18: Listens to Reinspection events and attaches the re-inspection mission and report to the
 * maintenance work order, advancing it for reviewer acceptance.
 */
@Component
public class WorkOrderReinspectionListener {

  private static final Logger log = LoggerFactory.getLogger(WorkOrderReinspectionListener.class);

  private final MaintenanceWorkOrderRepository workOrders;
  private final MaintenanceReportVersionRepository reportVersions;

  public WorkOrderReinspectionListener(
      MaintenanceWorkOrderRepository workOrders,
      MaintenanceReportVersionRepository reportVersions) {
    this.workOrders = workOrders;
    this.reportVersions = reportVersions;
  }

  @ApplicationModuleListener
  public void onReinspectionCreated(ReinspectionCreatedEvent event) {
    log.info(
        "Linking created re-inspection {} to work order {}",
        event.reinspectionId(),
        event.workOrderId());
    workOrders
        .findById(event.workOrderId())
        .ifPresent(
            workOrder -> {
              workOrder.attachReinspection(event.reinspectionId());
              workOrders.saveAndFlush(workOrder);
            });
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

    workOrder.attachReinspection(event.reinspectionId());
    workOrder.recordReinspectionCompleted(event.reportVersionId());
    workOrders.saveAndFlush(workOrder);

    // Reinstate the returned completion report version so customer can accept
    reportVersions
        .findFirstByWorkOrderIdAndStatusOrderByVersionNoDesc(
            workOrder.getId(), ReportVersionStatus.RETURNED)
        .ifPresent(
            report -> {
              report.resubmitForReinspectionAcceptance();
              reportVersions.saveAndFlush(report);
              log.info(
                  "Reinstated completion report {} to SUBMITTED for acceptance review",
                  report.getId());
            });

    log.info(
        "Attached re-inspection report {} to work order {}, current status: {}",
        event.reportVersionId(),
        workOrder.getId(),
        workOrder.getStatus());
  }
}
