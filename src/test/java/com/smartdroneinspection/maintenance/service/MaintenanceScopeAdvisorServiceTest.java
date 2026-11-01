package com.smartdroneinspection.maintenance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.inspections.spi.RepairCandidateAccess;
import com.smartdroneinspection.maintenance.api.dto.request.SuggestScopeRequest;
import com.smartdroneinspection.maintenance.api.dto.response.SuggestScopeResponse;
import com.smartdroneinspection.maintenance.domain.MaintenanceTask;
import com.smartdroneinspection.maintenance.domain.MaintenanceWorkOrder;
import com.smartdroneinspection.maintenance.domain.RepairKnowledgeChunk;
import com.smartdroneinspection.maintenance.repository.MaintenanceTaskRepository;
import com.smartdroneinspection.maintenance.repository.RepairKnowledgeChunkRepository;
import com.smartdroneinspection.maintenance.spi.MaintenanceScopeAdvisorPort;
import com.smartdroneinspection.maintenance.spi.MaintenanceScopeAdvisorPort.SuggestedScope;
import com.smartdroneinspection.maintenance.spi.MaintenanceScopeAdvisorPort.SuggestedTask;
import com.smartdroneinspection.users.UserAccess;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MaintenanceScopeAdvisorServiceTest {

  private MaintenanceScopeAdvisorPort advisorPort;
  private RepairKnowledgeChunkRepository knowledgeRepository;
  private RepairCandidateAccess repairCandidateAccess;
  private DeterministicScopeFallback deterministicFallback;
  private UserAccess userAccess;
  private WorkOrderService workOrderService;
  private MaintenanceTaskRepository tasks;
  private MaintenanceScopeAdvisorService service;

  private final UUID actorId = UUID.randomUUID();
  private final UUID organizationId = UUID.randomUUID();
  private final UUID reportVersionId = UUID.randomUUID();
  private final UUID findingId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    advisorPort = mock(MaintenanceScopeAdvisorPort.class);
    knowledgeRepository = mock(RepairKnowledgeChunkRepository.class);
    repairCandidateAccess = mock(RepairCandidateAccess.class);
    deterministicFallback = new DeterministicScopeFallback();
    userAccess = mock(UserAccess.class);
    workOrderService = mock(WorkOrderService.class);
    tasks = mock(MaintenanceTaskRepository.class);

    service =
        new MaintenanceScopeAdvisorService(
            Optional.of(advisorPort),
            knowledgeRepository,
            repairCandidateAccess,
            deterministicFallback,
            userAccess,
            workOrderService,
            tasks);

    UserAccess.ActiveUser actor =
        new UserAccess.ActiveUser(actorId, Set.of("CUSTOMER"), organizationId, "Org Admin");
    when(userAccess.findActiveUser(actorId)).thenReturn(Optional.of(actor));

    RepairKnowledgeChunk chunk =
        new RepairKnowledgeChunk(
            "TCVN 9343:2012",
            "Bơm keo epoxy",
            "CONCRETE_CRACK",
            "crack",
            "Hướng dẫn bơm keo epoxy",
            "http://example.com");
    when(knowledgeRepository.findRelevantChunks(anyString(), anyString(), anyInt()))
        .thenReturn(List.of(chunk));
  }

  @Test
  void returnsAiSuggestedScopeWhenPortSucceeds() throws Exception {
    when(advisorPort.modelName()).thenReturn("gpt-4o-mini");
    when(advisorPort.suggestScope(any()))
        .thenReturn(
            new SuggestedScope(
                "Đục rãnh và bơm keo epoxy Sikadur-752",
                "Độ bám dính >= 1.5 MPa theo TCVN 9349",
                List.of("TCVN 9343:2012"),
                List.of(new SuggestedTask(1, "Bơm keo", "Epoxy injection", "Kín khít"))));

    SuggestScopeRequest request =
        new SuggestScopeRequest(
            reportVersionId,
            findingId,
            "FND-01",
            "Nứt dầm bê tông",
            "Vết nứt bề rộng 0.3mm",
            "HIGH",
            "Trám keo",
            "Dầm T",
            "Nhịp 2",
            "0.3mm");

    SuggestScopeResponse response = service.suggestScope(actorId, request);

    assertThat(response.fallbackApplied()).isFalse();
    assertThat(response.modelName()).isEqualTo("gpt-4o-mini");
    assertThat(response.correctiveScope()).contains("Sikadur-752");
    assertThat(response.acceptanceCriteria()).contains("1.5 MPa");
    assertThat(response.referenceStandards()).contains("TCVN 9343:2012");
  }

  @Test
  void fallsBackGracefullyWhenAiPortThrowsIOException() throws Exception {
    when(advisorPort.suggestScope(any())).thenThrow(new IOException("Connection timed out"));

    SuggestScopeRequest request =
        new SuggestScopeRequest(
            reportVersionId,
            findingId,
            "FND-01",
            "Nứt dầm bê tông",
            "Vết nứt bề rộng 0.3mm",
            "HIGH",
            "Trám keo",
            "Dầm T",
            "Nhịp 2",
            "0.3mm");

    SuggestScopeResponse response = service.suggestScope(actorId, request);

    assertThat(response.fallbackApplied()).isTrue();
    assertThat(response.modelName()).isEqualTo("engineering-standards-fallback");
    assertThat(response.correctiveScope()).isNotBlank();
    assertThat(response.acceptanceCriteria()).contains("TCVN 9349");
  }

  @Test
  void fallsBackWhenPortIsEmpty() {
    service =
        new MaintenanceScopeAdvisorService(
            Optional.empty(),
            knowledgeRepository,
            repairCandidateAccess,
            deterministicFallback,
            userAccess);

    SuggestScopeRequest request =
        new SuggestScopeRequest(
            reportVersionId,
            findingId,
            "FND-02",
            "Bong tróc bê tông",
            "Bê tông phong hóa lộ thép",
            "CRITICAL",
            "Đục tẩy trám vữa",
            "Trụ cầu",
            "Đáy trụ",
            "50x40cm");

    SuggestScopeResponse response = service.suggestScope(actorId, request);

    assertThat(response.fallbackApplied()).isTrue();
    assertThat(response.correctiveScope()).contains("vữa sửa chữa");
    assertThat(response.referenceStandards()).contains("EN 1504-9");
  }

  @Test
  void suggestEstimateCalculatesNormsAndLines() {
    UUID workOrderId = UUID.randomUUID();
    MaintenanceWorkOrder order =
        new MaintenanceWorkOrder(
            organizationId,
            UUID.randomUUID(),
            reportVersionId,
            findingId,
            actorId,
            UUID.randomUUID());
    order.triage(null, null, "Sửa chữa nứt dầm bê tông cốt thép", "TCVN 9343:2012");
    when(workOrderService.requireWorkOrderInScope(actorId, workOrderId)).thenReturn(order);

    MaintenanceTask task1 =
        new MaintenanceTask(
            workOrderId, 1, "Vệ sinh khe nứt", "Bơm khí", actorId, null, null, null);
    when(tasks.findByWorkOrderIdOrderByTaskNumberAsc(order.getId())).thenReturn(List.of(task1));

    var estimate = service.suggestEstimate(actorId, workOrderId);

    assertThat(estimate).isNotNull();
    assertThat(estimate.currency()).isEqualTo("VND");
    assertThat(estimate.lines()).isNotEmpty();
    assertThat(estimate.estimatedTotal()).isGreaterThan(java.math.BigDecimal.ZERO);
    assertThat(estimate.lines().get(0).description()).contains("Nhân công");
    assertThat(estimate.lines().get(1).description()).contains("Sikadur");
  }
}
