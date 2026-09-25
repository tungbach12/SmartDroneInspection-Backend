package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.inspectionrequests.domain.InspectionRequest;
import com.smartdroneinspection.inspectionrequests.domain.InspectionServiceOrder;
import com.smartdroneinspection.inspectionrequests.repository.InspectionAssignmentRepository;
import com.smartdroneinspection.inspectionrequests.repository.InspectionRequestRepository;
import com.smartdroneinspection.inspectionrequests.repository.InspectionServiceOrderRepository;
import com.smartdroneinspection.inspections.api.dto.request.ClientReportDecisionRequest;
import com.smartdroneinspection.inspections.api.dto.request.PeerReviewDecisionRequest;
import com.smartdroneinspection.inspections.api.dto.response.ReportSnapshot;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.InspectionReport;
import com.smartdroneinspection.inspections.domain.ReportVersion;
import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
import com.smartdroneinspection.inspections.events.ReportAcceptedEvent;
import com.smartdroneinspection.inspections.repository.ChecklistResponseRepository;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.repository.InspectionReportRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.repository.PeerReviewRepository;
import com.smartdroneinspection.inspections.repository.ReportVersionRepository;
import com.smartdroneinspection.inspections.repository.VerifiedFindingRepository;
import com.smartdroneinspection.inspections.service.InspectionReportService;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InspectionReportServiceTest {

  @Mock InspectionRepository inspections;
  @Mock InspectionAssignmentRepository assignments;
  @Mock InspectionReportRepository reports;
  @Mock ReportVersionRepository versions;
  @Mock PeerReviewRepository reviews;
  @Mock com.smartdroneinspection.assets.repository.ChecklistTemplateRepository templates;
  @Mock ChecklistResponseRepository checklistResponses;
  @Mock EvidenceRepository evidence;
  @Mock VerifiedFindingRepository findings;
  @Mock InspectionServiceOrderRepository serviceOrders;
  @Mock InspectionRequestRepository inspectionRequests;
  @Mock UserAccess users;
  @Mock ObjectMapper objectMapper;
  @Mock ApplicationEventPublisher events;

  private final UUID organizationId = UUID.randomUUID();
  private final UUID clientId = UUID.randomUUID();
  private final UUID inspectorId = UUID.randomUUID();
  private final UUID inspectionId = UUID.randomUUID();
  private final UUID serviceOrderId = UUID.randomUUID();
  private final UUID requestId = UUID.randomUUID();
  private final UUID reportId = UUID.randomUUID();
  private final UUID versionId = UUID.randomUUID();

  private InspectionReport report;
  private ReportVersion version;
  private ReportSnapshot snapshot;
  private InspectionReportService service;

  @BeforeEach
  void setUp() throws Exception {
    service =
        new InspectionReportService(
            inspections,
            assignments,
            reports,
            versions,
            reviews,
            templates,
            checklistResponses,
            evidence,
            findings,
            serviceOrders,
            inspectionRequests,
            users,
            Optional.empty(),
            objectMapper,
            events);
    snapshot =
        new ReportSnapshot(
            inspectionId,
            serviceOrderId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Bridge checklist",
            Instant.now(),
            List.of(),
            List.of(),
            List.of(),
            null);
    report = Mockito.spy(new InspectionReport(inspectionId, inspectorId));
    report.startVersion(1);
    report.changeStatus(ReportStatus.RELEASED);
    Mockito.doReturn(reportId).when(report).getId();
    version = new ReportVersion(reportId, 1, null, inspectorId, "{}");
    version.submitForReview();
    version.approve();
    version.release();
    version = Mockito.spy(version);
    Mockito.doReturn(versionId).when(version).getId();

    Inspection inspection =
        new Inspection(
            serviceOrderId,
            UUID.randomUUID(),
            snapshot.assetId(),
            inspectorId,
            snapshot.checklistTemplateId());
    inspection.start();
    InspectionServiceOrder order = Mockito.mock(InspectionServiceOrder.class);
    InspectionRequest request = Mockito.mock(InspectionRequest.class);
    when(inspections.findById(inspectionId)).thenReturn(Optional.of(inspection));
    when(serviceOrders.findById(serviceOrderId)).thenReturn(Optional.of(order));
    when(order.getInspectionRequestId()).thenReturn(requestId);
    when(inspectionRequests.findById(requestId)).thenReturn(Optional.of(request));
    when(request.getOrganizationId()).thenReturn(organizationId);
    when(reports.findForUpdateById(reportId)).thenReturn(Optional.of(report));
    when(versions.findForUpdateById(versionId)).thenReturn(Optional.of(version));
    when(reviews.findByReportVersionId(versionId)).thenReturn(Optional.empty());
    when(objectMapper.readValue(anyString(), eq(ReportSnapshot.class))).thenReturn(snapshot);
    when(versions.saveAndFlush(any(ReportVersion.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(reports.saveAndFlush(any(InspectionReport.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void clientAcceptanceIsIdempotentAndPublishesOneHandoff() {
    when(users.findActiveUser(clientId))
        .thenReturn(
            Optional.of(new UserAccess.ActiveUser(clientId, Set.of("CLIENT"), organizationId)));
    var accept = new ClientReportDecisionRequest(ClientReportDecisionRequest.Decision.ACCEPT, null);

    var first = service.clientDecision(clientId, reportId, versionId, accept);
    var retry = service.clientDecision(clientId, reportId, versionId, accept);

    assertThat(first.versionStatus()).isEqualTo(ReportStatus.ACCEPTED);
    assertThat(retry.versionStatus()).isEqualTo(ReportStatus.ACCEPTED);
    assertThat(version.isImmutable()).isTrue();
    assertThat(version.getClientDecisionByUserId()).isEqualTo(clientId);
    verify(events).publishEvent(any(ReportAcceptedEvent.class));
  }

  @Test
  void clientRevisionRequestIsPersistedOnThatReleasedVersionWithoutHandoff() {
    when(users.findActiveUser(clientId))
        .thenReturn(
            Optional.of(new UserAccess.ActiveUser(clientId, Set.of("CLIENT"), organizationId)));

    var result =
        service.clientDecision(
            clientId,
            reportId,
            versionId,
            new ClientReportDecisionRequest(
                ClientReportDecisionRequest.Decision.REQUEST_REVISION,
                "Please clarify the corrosion rating."));

    assertThat(result.versionStatus()).isEqualTo(ReportStatus.REVISION_REQUESTED);
    assertThat(version.getClientDecisionByUserId()).isEqualTo(clientId);
    assertThat(version.getClientDecisionReason()).isEqualTo("Please clarify the corrosion rating.");
    verify(events, never()).publishEvent(any(ReportAcceptedEvent.class));
  }

  @Test
  void reportAuthorCannotReviewTheirOwnVersion() {
    when(users.findActiveUser(inspectorId))
        .thenReturn(Optional.of(new UserAccess.ActiveUser(inspectorId, Set.of("INSPECTOR"))));

    assertThatThrownBy(
            () ->
                service.review(
                    inspectorId,
                    reportId,
                    versionId,
                    new PeerReviewDecisionRequest(
                        PeerReviewDecisionRequest.Decision.APPROVED, null)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code()).isEqualTo("REPORT_SCOPE_DENIED"));
    verify(reviews, never()).findForUpdateByReportVersionId(versionId);
  }
}
