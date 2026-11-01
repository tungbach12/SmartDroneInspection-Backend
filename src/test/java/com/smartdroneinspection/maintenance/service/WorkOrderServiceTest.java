package com.smartdroneinspection.maintenance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.inspections.spi.RepairCandidate;
import com.smartdroneinspection.inspections.spi.RepairCandidateAccess;
import com.smartdroneinspection.maintenance.api.dto.request.CreateWorkOrderRequest;
import com.smartdroneinspection.maintenance.api.dto.response.WorkOrderResponse;
import com.smartdroneinspection.maintenance.domain.MaintenanceTask;
import com.smartdroneinspection.maintenance.domain.MaintenanceWorkOrder;
import com.smartdroneinspection.maintenance.domain.enums.MaintenancePriority;
import com.smartdroneinspection.maintenance.repository.MaintenanceCostLineRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceEstimateVersionRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceTaskRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceTeamMemberRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceWorkOrderRepository;
import com.smartdroneinspection.users.UserAccess;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WorkOrderServiceTest {

  @Mock private MaintenanceWorkOrderRepository workOrders;
  @Mock private MaintenanceTeamMemberRepository teamMembers;
  @Mock private MaintenanceTaskRepository tasks;
  @Mock private MaintenanceEstimateVersionRepository estimates;
  @Mock private MaintenanceCostLineRepository costLines;
  @Mock private RepairCandidateAccess repairCandidateAccess;
  @Mock private UserAccess userAccess;
  @Mock private ApplicationEventPublisher eventPublisher;

  private WorkOrderService service;

  private final UUID orgId = UUID.randomUUID();
  private final UUID actorId = UUID.randomUUID();
  private final UUID budgetApproverId = UUID.randomUUID();
  private final UUID assetId = UUID.randomUUID();
  private final UUID reportVersionId = UUID.randomUUID();
  private final UUID findingId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service =
        new WorkOrderService(
            workOrders,
            teamMembers,
            tasks,
            estimates,
            costLines,
            repairCandidateAccess,
            userAccess,
            eventPublisher);
  }

  @Test
  @DisplayName("create automatically creates initialTasks when provided in request")
  void createWithInitialTasks() {
    UserAccess.ActiveUser actor =
        new UserAccess.ActiveUser(
            actorId,
            Set.of("CUSTOMER"),
            orgId,
            "Admin User",
            "admin@test.com",
            null,
            null,
            null,
            null);
    when(userAccess.findActiveUser(actorId)).thenReturn(Optional.of(actor));

    RepairCandidate candidate =
        new RepairCandidate(
            orgId,
            assetId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            reportVersionId,
            findingId,
            "FND-01",
            "Cracked Beam",
            "Crack width 0.4mm",
            "HIGH",
            "Inject epoxy resin",
            Instant.now());
    when(repairCandidateAccess.findRepairCandidate(orgId, reportVersionId, findingId))
        .thenReturn(Optional.of(candidate));
    when(workOrders.existsBySourceFindingIdAndStatusIn(any(), any())).thenReturn(false);

    UserAccess.ActiveUser approver =
        new UserAccess.ActiveUser(
            budgetApproverId,
            Set.of("CUSTOMER"),
            orgId,
            "Budget Approver",
            "approver@test.com",
            null,
            null,
            null,
            null);
    when(userAccess.findActiveUser(budgetApproverId)).thenReturn(Optional.of(approver));

    when(workOrders.saveAndFlush(any(MaintenanceWorkOrder.class)))
        .thenAnswer(
            invocation -> {
              MaintenanceWorkOrder order = invocation.getArgument(0);
              ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
              return order;
            });

    CreateWorkOrderRequest.InitialTaskRequest task1 =
        new CreateWorkOrderRequest.InitialTaskRequest("Vệ sinh bề mặt", "Bơm khí nén", "Sạch bụi");
    CreateWorkOrderRequest.InitialTaskRequest task2 =
        new CreateWorkOrderRequest.InitialTaskRequest(
            "Bơm keo epoxy", "Áp lực thấp", "Điền đầy vết nứt");

    CreateWorkOrderRequest request =
        new CreateWorkOrderRequest(
            assetId,
            reportVersionId,
            findingId,
            budgetApproverId,
            MaintenancePriority.HIGH,
            Instant.now().plusSeconds(86400),
            "Sửa chữa dầm nứt",
            "Đạt chuẩn TCVN 9343",
            List.of(task1, task2));

    WorkOrderResponse response = service.create(actorId, request);

    assertThat(response).isNotNull();
    ArgumentCaptor<MaintenanceTask> taskCaptor = ArgumentCaptor.forClass(MaintenanceTask.class);
    verify(tasks, times(2)).save(taskCaptor.capture());
    verify(tasks).flush();

    List<MaintenanceTask> savedTasks = taskCaptor.getAllValues();
    assertThat(savedTasks).hasSize(2);
    assertThat(savedTasks.get(0).getTaskNumber()).isEqualTo(1);
    assertThat(savedTasks.get(0).getName()).isEqualTo("Vệ sinh bề mặt");
    assertThat(savedTasks.get(0).getMethod()).isEqualTo("Bơm khí nén");
    assertThat(savedTasks.get(1).getTaskNumber()).isEqualTo(2);
    assertThat(savedTasks.get(1).getName()).isEqualTo("Bơm keo epoxy");
  }
}
