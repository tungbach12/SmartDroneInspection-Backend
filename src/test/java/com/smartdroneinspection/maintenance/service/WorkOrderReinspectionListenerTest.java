package com.smartdroneinspection.maintenance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.inspections.spi.ReinspectionReportPublishedEvent;
import com.smartdroneinspection.maintenance.domain.MaintenanceWorkOrder;
import com.smartdroneinspection.maintenance.domain.enums.WorkOrderStatus;
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

  private WorkOrderReinspectionListener listener;

  @BeforeEach
  void setUp() {
    listener = new WorkOrderReinspectionListener(workOrders);
  }

  @Test
  @DisplayName(
      "Attaches published reinspection report and advances status to SUBMITTED_FOR_ACCEPTANCE")
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

    when(workOrders.findById(workOrderId)).thenReturn(Optional.of(workOrder));

    listener.onReinspectionReportPublished(
        new ReinspectionReportPublishedEvent(workOrderId, reinspectionId, reportVersionId));

    ArgumentCaptor<MaintenanceWorkOrder> captor =
        ArgumentCaptor.forClass(MaintenanceWorkOrder.class);
    verify(workOrders).saveAndFlush(captor.capture());

    MaintenanceWorkOrder saved = captor.getValue();
    assertThat(saved.getReinspectionReportVersionId()).isEqualTo(reportVersionId);
    assertThat(saved.getStatus()).isEqualTo(WorkOrderStatus.SUBMITTED_FOR_ACCEPTANCE);
  }
}
