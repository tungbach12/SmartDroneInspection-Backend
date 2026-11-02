package com.smartdroneinspection.maintenance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.inspections.spi.ReinspectionCreatedEvent;
import com.smartdroneinspection.inspections.spi.ReinspectionReportPublishedEvent;
import com.smartdroneinspection.maintenance.domain.MaintenanceReportVersion;
import com.smartdroneinspection.maintenance.domain.MaintenanceWorkOrder;
import com.smartdroneinspection.maintenance.domain.enums.ReportVersionStatus;
import com.smartdroneinspection.maintenance.domain.enums.WorkOrderStatus;
import com.smartdroneinspection.maintenance.repository.MaintenanceReportVersionRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceWorkOrderRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WorkOrderReinspectionListenerTest {

  @Mock private MaintenanceWorkOrderRepository workOrders;
  @Mock private MaintenanceReportVersionRepository reportVersions;

  private WorkOrderReinspectionListener listener;

  @BeforeEach
  void setUp() {
    listener = new WorkOrderReinspectionListener(workOrders, reportVersions);
  }

  @Test
  @DisplayName("Attaches reinspectionId when ReinspectionCreatedEvent is received")
  void attachesReinspectionIdOnCreated() {
    UUID workOrderId = UUID.randomUUID();
    UUID reinspectionId = UUID.randomUUID();

    MaintenanceWorkOrder workOrder =
        new MaintenanceWorkOrder(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID());
    ReflectionTestUtils.setField(workOrder, "id", workOrderId);

    when(workOrders.findById(workOrderId)).thenReturn(Optional.of(workOrder));

    listener.onReinspectionCreated(new ReinspectionCreatedEvent(workOrderId, reinspectionId));

    ArgumentCaptor<MaintenanceWorkOrder> captor =
        ArgumentCaptor.forClass(MaintenanceWorkOrder.class);
    verify(workOrders).saveAndFlush(captor.capture());

    assertThat(captor.getValue().getReinspectionId()).isEqualTo(reinspectionId);
  }

  @Test
  @DisplayName(
      "Attaches published reinspection report, reinstates report version and advances status to SUBMITTED_FOR_ACCEPTANCE")
  void attachesPublishedReinspectionReport() {
    UUID workOrderId = UUID.randomUUID();
    UUID reinspectionId = UUID.randomUUID();
    UUID reportVersionId = UUID.randomUUID();

    MaintenanceWorkOrder workOrder =
        new MaintenanceWorkOrder(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID());
    ReflectionTestUtils.setField(workOrder, "id", workOrderId);
    ReflectionTestUtils.setField(workOrder, "status", WorkOrderStatus.REINSPECTION_REQUIRED);

    MaintenanceReportVersion report = new MaintenanceReportVersion(workOrderId, 1, "Work summary");
    ReflectionTestUtils.setField(report, "status", ReportVersionStatus.RETURNED);

    when(workOrders.findById(workOrderId)).thenReturn(Optional.of(workOrder));
    when(reportVersions.findFirstByWorkOrderIdAndStatusOrderByVersionNoDesc(
            workOrderId, ReportVersionStatus.RETURNED))
        .thenReturn(Optional.of(report));

    listener.onReinspectionReportPublished(
        new ReinspectionReportPublishedEvent(workOrderId, reinspectionId, reportVersionId));

    ArgumentCaptor<MaintenanceWorkOrder> captor =
        ArgumentCaptor.forClass(MaintenanceWorkOrder.class);
    verify(workOrders).saveAndFlush(captor.capture());

    MaintenanceWorkOrder saved = captor.getValue();
    assertThat(saved.getReinspectionId()).isEqualTo(reinspectionId);
    assertThat(saved.getReinspectionReportVersionId()).isEqualTo(reportVersionId);
    assertThat(saved.getStatus()).isEqualTo(WorkOrderStatus.SUBMITTED_FOR_ACCEPTANCE);

    verify(reportVersions).saveAndFlush(report);
    assertThat(report.getStatus()).isEqualTo(ReportVersionStatus.SUBMITTED);
  }

  @Test
  @DisplayName(
      "Multi-round reinspection: unresolved defects move work order to REWORK_REQUIRED, subsequent resolved round moves to SUBMITTED_FOR_ACCEPTANCE")
  void handlesMultiRoundReinspectionWhenFirstRoundHasUnresolvedDefects() {
    UUID workOrderId = UUID.randomUUID();
    UUID reinspectionRound1Id = UUID.randomUUID();
    UUID reportRound1Id = UUID.randomUUID();
    UUID reinspectionRound2Id = UUID.randomUUID();
    UUID reportRound2Id = UUID.randomUUID();

    MaintenanceWorkOrder workOrder =
        new MaintenanceWorkOrder(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID());
    ReflectionTestUtils.setField(workOrder, "id", workOrderId);
    ReflectionTestUtils.setField(workOrder, "status", WorkOrderStatus.REINSPECTION_REQUIRED);

    MaintenanceReportVersion report = new MaintenanceReportVersion(workOrderId, 1, "Work summary");
    ReflectionTestUtils.setField(report, "status", ReportVersionStatus.RETURNED);

    when(workOrders.findById(workOrderId)).thenReturn(Optional.of(workOrder));

    // Round 1: Inspector marks defect UNRESOLVED (allDefectsResolved = false)
    listener.onReinspectionReportPublished(
        new ReinspectionReportPublishedEvent(
            workOrderId, reinspectionRound1Id, reportRound1Id, false));

    assertThat(workOrder.getReinspectionId()).isEqualTo(reinspectionRound1Id);
    assertThat(workOrder.getReinspectionReportVersionId()).isEqualTo(reportRound1Id);
    assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.REWORK_REQUIRED);
    assertThat(report.getStatus()).isEqualTo(ReportVersionStatus.RETURNED);

    // Maintenance repairs again and requests Round 2 reinspection
    ReflectionTestUtils.setField(workOrder, "status", WorkOrderStatus.REINSPECTION_REQUIRED);
    when(reportVersions.findFirstByWorkOrderIdAndStatusOrderByVersionNoDesc(
            workOrderId, ReportVersionStatus.RETURNED))
        .thenReturn(Optional.of(report));

    // Round 2: Inspector marks defect RESOLVED (allDefectsResolved = true)
    listener.onReinspectionReportPublished(
        new ReinspectionReportPublishedEvent(
            workOrderId, reinspectionRound2Id, reportRound2Id, true));

    assertThat(workOrder.getReinspectionId()).isEqualTo(reinspectionRound2Id);
    assertThat(workOrder.getReinspectionReportVersionId()).isEqualTo(reportRound2Id);
    assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.SUBMITTED_FOR_ACCEPTANCE);
    assertThat(report.getStatus()).isEqualTo(ReportVersionStatus.SUBMITTED);
  }
}
