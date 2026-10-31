package com.smartdroneinspection.maintenance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.maintenance.api.dto.response.WorkLogResponse;
import com.smartdroneinspection.maintenance.domain.MaintenanceAcceptanceDecision;
import com.smartdroneinspection.maintenance.domain.MaintenanceTask;
import com.smartdroneinspection.maintenance.domain.MaintenanceWorkLog;
import com.smartdroneinspection.maintenance.domain.MaintenanceWorkOrder;
import com.smartdroneinspection.maintenance.domain.enums.AcceptanceDecisionKind;
import com.smartdroneinspection.maintenance.domain.enums.WorkLogStatus;
import com.smartdroneinspection.maintenance.render.MaintenanceReportRenderer;
import com.smartdroneinspection.maintenance.repository.MaintenanceAcceptanceDecisionRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceChangeOrderRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceCostLineRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceEstimateVersionRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceReportVersionRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceTaskRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceTeamMemberRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceWorkLogRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceWorkOrderRepository;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.UserAccess;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MaintenanceExecutionServiceTest {

  @Mock private MaintenanceWorkOrderRepository workOrders;
  @Mock private MaintenanceTeamMemberRepository teamMembers;
  @Mock private MaintenanceTaskRepository tasks;
  @Mock private MaintenanceWorkLogRepository workLogs;
  @Mock private MaintenanceChangeOrderRepository changeOrders;
  @Mock private MaintenanceEstimateVersionRepository estimates;
  @Mock private MaintenanceCostLineRepository costLines;
  @Mock private MaintenanceReportVersionRepository reportVersions;
  @Mock private MaintenanceAcceptanceDecisionRepository acceptanceDecisions;
  @Mock private WorkOrderService workOrderService;
  @Mock private UserAccess userAccess;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private EvidenceObjectStore objectStore;
  @Mock private MaintenanceReportRenderer reportRenderer;

  private MaintenanceExecutionService service;

  private final UUID leadId = UUID.randomUUID();
  private final UUID authorId = UUID.randomUUID();
  private final UUID reviewerId = UUID.randomUUID();
  private final UUID engineerId = UUID.randomUUID();
  private final UUID workOrderId = UUID.randomUUID();

  private MaintenanceWorkOrder workOrder;

  @BeforeEach
  void setUp() {
    service =
        new MaintenanceExecutionService(
            workOrders,
            teamMembers,
            tasks,
            workLogs,
            changeOrders,
            estimates,
            costLines,
            reportVersions,
            acceptanceDecisions,
            workOrderService,
            userAccess,
            eventPublisher,
            Optional.of(objectStore),
            reportRenderer);

    workOrder =
        new MaintenanceWorkOrder(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID());
    ReflectionTestUtils.setField(workOrder, "id", workOrderId);
    workOrder.assignTeam(leadId, authorId, reviewerId);
  }

  @Test
  @DisplayName("batchVerifyWorkLogs transitions all submitted logs to VERIFIED for lead")
  void batchVerifyWorkLogsSuccess() {
    when(workOrderService.requireWorkOrderInScope(leadId, workOrderId)).thenReturn(workOrder);

    UUID taskId = UUID.randomUUID();
    Instant now = Instant.now();
    MaintenanceWorkLog log1 =
        new MaintenanceWorkLog(
            workOrderId, taskId, engineerId, now.minusSeconds(7200), BigDecimal.valueOf(2));
    ReflectionTestUtils.setField(log1, "id", UUID.randomUUID());
    log1.recordProgress(
        now.minusSeconds(7200),
        now.minusSeconds(3600),
        BigDecimal.valueOf(2),
        "Hoàn thành",
        "Đạt chuẩn");
    log1.submit();

    MaintenanceWorkLog log2 =
        new MaintenanceWorkLog(
            workOrderId, taskId, engineerId, now.minusSeconds(3600), BigDecimal.valueOf(1));
    ReflectionTestUtils.setField(log2, "id", UUID.randomUUID());
    log2.recordProgress(
        now.minusSeconds(3600), now, BigDecimal.valueOf(1), "Hoàn thành", "Đạt chuẩn");
    log2.submit();

    when(workLogs.findByWorkOrderIdAndStatus(workOrderId, WorkLogStatus.SUBMITTED))
        .thenReturn(List.of(log1, log2));

    List<WorkLogResponse> responses = service.batchVerifyWorkLogs(leadId, workOrderId);

    assertThat(responses).hasSize(2);
    assertThat(log1.getStatus()).isEqualTo(WorkLogStatus.VERIFIED);
    assertThat(log2.getStatus()).isEqualTo(WorkLogStatus.VERIFIED);
  }

  @Test
  @DisplayName("batchVerifyWorkLogs throws BusinessException when caller is not team lead")
  void batchVerifyWorkLogsRejectsNonLead() {
    UUID nonLead = UUID.randomUUID();
    when(workOrderService.requireWorkOrderInScope(nonLead, workOrderId)).thenReturn(workOrder);

    assertThatThrownBy(() -> service.batchVerifyWorkLogs(nonLead, workOrderId))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Only the designated team lead");
  }

  @Test
  @DisplayName("generateReportDraft builds structured narrative with tasks and logs")
  void generateReportDraftSuccess() {
    when(workOrderService.requireWorkOrderInScope(authorId, workOrderId)).thenReturn(workOrder);
    workOrder.triage(null, null, "Sửa chữa vết nứt dầm bê tông TCVN 9343", "Đạt cường độ ép dính");

    UUID taskId = UUID.randomUUID();
    MaintenanceTask task1 =
        new MaintenanceTask(
            workOrderId, 1, "Vệ sinh vết nứt", "Bơm khí áp lực cao", engineerId, null, null, null);
    ReflectionTestUtils.setField(task1, "id", taskId);
    when(tasks.findByWorkOrderIdOrderByTaskNumberAsc(workOrderId)).thenReturn(List.of(task1));

    MaintenanceWorkLog log1 =
        new MaintenanceWorkLog(
            workOrderId,
            taskId,
            engineerId,
            Instant.now().minusSeconds(7200),
            BigDecimal.valueOf(2.5));
    log1.recordProgress(
        Instant.now().minusSeconds(7200),
        Instant.now(),
        BigDecimal.valueOf(2.5),
        "Bề mặt sạch, không bám bụi",
        "Áp suất 4.5 bar");
    when(workLogs.findByWorkOrderIdOrderByStartedAtAsc(workOrderId)).thenReturn(List.of(log1));
    when(changeOrders.findByWorkOrderIdOrderByChangeNumberDesc(workOrderId)).thenReturn(List.of());

    Map<String, String> result = service.generateReportDraft(authorId, workOrderId);

    assertThat(result).containsKey("content");
    String content = result.get("content");
    assertThat(content).contains("Sửa chữa vết nứt dầm bê tông");
    assertThat(content).contains("Vệ sinh vết nứt");
    assertThat(content).contains("2.5 giờ");
    assertThat(content).contains("Bề mặt sạch, không bám bụi");
    assertThat(content).contains("Áp suất 4.5 bar");
  }

  @Test
  @DisplayName("checkAcceptanceCompliance calculates compliance score and recommendations")
  void checkAcceptanceComplianceSuccess() {
    when(workOrderService.requireWorkOrderInScope(reviewerId, workOrderId)).thenReturn(workOrder);
    workOrder.triage(null, null, "Sửa chữa dầm nứt", "Cường độ bám dính >= 1.5 MPa");

    MaintenanceTask task1 =
        new MaintenanceTask(
            workOrderId, 1, "Vệ sinh vết nứt", "Bơm khí", engineerId, null, null, null);
    task1.markReady();
    task1.markInProgress();
    task1.markWorkCompleted();
    when(tasks.findByWorkOrderIdOrderByTaskNumberAsc(workOrderId)).thenReturn(List.of(task1));

    UUID taskId = UUID.randomUUID();
    MaintenanceWorkLog log1 =
        new MaintenanceWorkLog(
            workOrderId,
            taskId,
            engineerId,
            Instant.now().minusSeconds(3600),
            BigDecimal.valueOf(1));
    log1.recordProgress(
        Instant.now().minusSeconds(3600),
        Instant.now(),
        BigDecimal.valueOf(1),
        "Bề mặt sạch và khô ráo",
        "Kéo đứt 1.8 MPa");
    log1.submit();
    log1.verify();
    when(workLogs.findByWorkOrderIdOrderByStartedAtAsc(workOrderId)).thenReturn(List.of(log1));
    when(workLogs.countByWorkOrderId(workOrderId)).thenReturn(1L);
    when(workLogs.countByWorkOrderIdAndStatus(workOrderId, WorkLogStatus.VERIFIED)).thenReturn(1L);

    var response = service.checkAcceptanceCompliance(reviewerId, workOrderId);

    assertThat(response).isNotNull();
    assertThat(response.complianceScore()).isGreaterThanOrEqualTo(75);
    assertThat(response.status()).isEqualTo("COMPLIANT");
    assertThat(response.recommendation()).isEqualTo("ACCEPTED");
    assertThat(response.checks()).hasSize(4);
    assertThat(response.checks().get(0).resultStatus()).isEqualTo("PASSED");
    assertThat(response.checks().get(1).resultStatus()).isEqualTo("PASSED");
  }

  @Test
  @DisplayName("markWorkCompleted rejects completion when pending change orders exist")
  void markWorkCompletedRejectsWhenChangeOrdersPending() {
    when(workOrderService.requireWorkOrderInScope(leadId, workOrderId)).thenReturn(workOrder);
    when(changeOrders.existsByWorkOrderIdAndStatusIn(eq(workOrderId), any())).thenReturn(true);

    assertThatThrownBy(() -> service.markWorkCompleted(leadId, workOrderId))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Cannot declare work completed while change orders are pending");
  }

  @Test
  @DisplayName(
      "markWorkCompleted rejects completion if no new verified logs exist after rework request")
  void markWorkCompletedRejectsWhenNoNewLogsAfterRework() {
    when(workOrderService.requireWorkOrderInScope(leadId, workOrderId)).thenReturn(workOrder);
    when(changeOrders.existsByWorkOrderIdAndStatusIn(eq(workOrderId), any())).thenReturn(false);

    Instant reworkTime = Instant.now().minusSeconds(1800);
    MaintenanceAcceptanceDecision decision =
        new MaintenanceAcceptanceDecision(
            workOrderId,
            UUID.randomUUID(),
            reviewerId,
            AcceptanceDecisionKind.REWORK_REQUIRED,
            "Cần trát thêm",
            null,
            null);
    ReflectionTestUtils.setField(decision, "decidedAt", reworkTime);
    when(acceptanceDecisions.findFirstByWorkOrderIdOrderByDecidedAtDesc(workOrderId))
        .thenReturn(Optional.of(decision));

    // Old log before reworkTime
    UUID taskId = UUID.randomUUID();
    MaintenanceWorkLog oldLog =
        new MaintenanceWorkLog(
            workOrderId, taskId, engineerId, reworkTime.minusSeconds(3600), BigDecimal.ONE);
    ReflectionTestUtils.setField(oldLog, "createdAt", reworkTime.minusSeconds(3600));
    oldLog.recordProgress(
        reworkTime.minusSeconds(3600),
        reworkTime.minusSeconds(1800),
        BigDecimal.ONE,
        "Đã làm trước rework",
        "Số đo cũ");
    oldLog.submit();
    oldLog.verify();
    when(workLogs.findByWorkOrderIdOrderByStartedAtAsc(workOrderId)).thenReturn(List.of(oldLog));

    assertThatThrownBy(() -> service.markWorkCompleted(leadId, workOrderId))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("At least one verified work log recorded after the rework request");
  }
}
