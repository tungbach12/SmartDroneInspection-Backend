package com.smartdroneinspection.inspections.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.inspections.api.dto.request.BulkRetireEvidenceRequest;
import com.smartdroneinspection.inspections.api.dto.request.ConfirmFindingsRequest;
import com.smartdroneinspection.inspections.api.dto.request.FindingSourceFilter;
import com.smartdroneinspection.inspections.api.dto.request.FindingStatusFilter;
import com.smartdroneinspection.inspections.api.dto.request.ManualFindingRequest;
import com.smartdroneinspection.inspections.api.dto.request.ReviewFindingRequest;
import com.smartdroneinspection.inspections.api.dto.request.UpdateEvidenceLocationRequest;
import com.smartdroneinspection.inspections.api.dto.request.UpdateFindingRequest;
import com.smartdroneinspection.inspections.api.dto.request.WithdrawVersionRequest;
import com.smartdroneinspection.inspections.api.dto.response.EvidencePageResponse;
import com.smartdroneinspection.inspections.api.dto.response.EvidenceResponse;
import com.smartdroneinspection.inspections.api.dto.response.FindingPageResponse;
import com.smartdroneinspection.inspections.api.dto.response.FindingResponse;
import com.smartdroneinspection.inspections.api.dto.response.InspectionReportResponse;
import com.smartdroneinspection.inspections.api.dto.response.InspectionWorkflowStateResponse;
import com.smartdroneinspection.inspections.api.dto.response.ReportPublishedResponse;
import com.smartdroneinspection.inspections.domain.enums.FindingSeverity;
import com.smartdroneinspection.inspections.service.EvidenceService;
import com.smartdroneinspection.inspections.service.FindingService;
import com.smartdroneinspection.inspections.service.InspectionCreationService;
import com.smartdroneinspection.inspections.service.InspectionWorkflowService;
import com.smartdroneinspection.inspections.service.ReportService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;

class InspectionControllerDelegationTest {

  private static final UUID ACTOR_ID = UUID.randomUUID();
  private static final UUID INSPECTION_ID = UUID.randomUUID();
  private static final UUID EVIDENCE_ID = UUID.randomUUID();
  private static final UUID FINDING_ID = UUID.randomUUID();
  private static final UUID VERSION_ID = UUID.randomUUID();

  private EvidenceService evidenceService;
  private FindingService findingService;
  private ReportService reportService;
  private InspectionWorkflowService workflowService;
  private InspectionCreationService creationService;
  private InspectionController controller;
  private Jwt jwt;

  @BeforeEach
  void setUp() {
    evidenceService = mock(EvidenceService.class);
    findingService = mock(FindingService.class);
    reportService = mock(ReportService.class);
    workflowService = mock(InspectionWorkflowService.class);
    creationService = mock(InspectionCreationService.class);
    controller =
        new InspectionController(
            evidenceService, findingService, reportService, workflowService, creationService);
    jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn(ACTOR_ID.toString());
  }

  @Test
  void delegatesEvidenceEndpoints() {
    EvidenceResponse response = mock(EvidenceResponse.class);
    EvidencePageResponse page = mock(EvidencePageResponse.class);
    UpdateEvidenceLocationRequest location = mock(UpdateEvidenceLocationRequest.class);
    BulkRetireEvidenceRequest bulk = mock(BulkRetireEvidenceRequest.class);
    when(evidenceService.list(ACTOR_ID, INSPECTION_ID)).thenReturn(List.of(response));
    when(evidenceService.query(
            eq(ACTOR_ID), eq(INSPECTION_ID), any(), any(), eq("search"), eq(1), eq(20)))
        .thenReturn(page);
    when(evidenceService.updateLocation(ACTOR_ID, INSPECTION_ID, EVIDENCE_ID, "Hanoi"))
        .thenReturn(response);
    when(evidenceService.retire(ACTOR_ID, INSPECTION_ID, EVIDENCE_ID)).thenReturn(response);
    when(evidenceService.bulkRetire(ACTOR_ID, INSPECTION_ID, List.of(EVIDENCE_ID)))
        .thenReturn(List.of(response));
    when(evidenceService.retryProcessing(ACTOR_ID, INSPECTION_ID, EVIDENCE_ID))
        .thenReturn(response);
    when(location.locationDescription()).thenReturn("Hanoi");
    when(bulk.evidenceIds()).thenReturn(List.of(EVIDENCE_ID));

    assertThat(controller.listEvidence(jwt, INSPECTION_ID).data()).hasSize(1);
    assertThat(
            controller
                .queryEvidence(
                    jwt,
                    INSPECTION_ID,
                    com.smartdroneinspection.inspections.api.dto.EvidenceStatusFilter.ALL,
                    com.smartdroneinspection.inspections.api.dto.EvidenceMediaTypeFilter.ALL,
                    "search",
                    1,
                    20)
                .data())
        .isSameAs(page);
    assertThat(controller.updateEvidenceLocation(jwt, INSPECTION_ID, EVIDENCE_ID, location).data())
        .isSameAs(response);
    assertThat(controller.retireEvidence(jwt, INSPECTION_ID, EVIDENCE_ID).data())
        .isSameAs(response);
    assertThat(controller.bulkRetireEvidence(jwt, INSPECTION_ID, bulk).data()).hasSize(1);
    assertThat(controller.retryEvidenceProcessing(jwt, INSPECTION_ID, EVIDENCE_ID).data())
        .isSameAs(response);
  }

  @Test
  void delegatesFindingEndpoints() {
    FindingResponse response = mock(FindingResponse.class);
    FindingPageResponse page = mock(FindingPageResponse.class);
    ReviewFindingRequest review = mock(ReviewFindingRequest.class);
    ManualFindingRequest manual = mock(ManualFindingRequest.class);
    UpdateFindingRequest update = mock(UpdateFindingRequest.class);
    ConfirmFindingsRequest confirm = mock(ConfirmFindingsRequest.class);
    when(findingService.confirmFinding(ACTOR_ID, INSPECTION_ID, FINDING_ID, review))
        .thenReturn(response);
    when(findingService.createManualFinding(ACTOR_ID, INSPECTION_ID, manual)).thenReturn(response);
    when(findingService.updateFinding(ACTOR_ID, INSPECTION_ID, FINDING_ID, update))
        .thenReturn(response);
    when(findingService.restoreFinding(ACTOR_ID, INSPECTION_ID, FINDING_ID)).thenReturn(response);
    when(findingService.listRemovedFindings(ACTOR_ID, INSPECTION_ID)).thenReturn(List.of(response));
    when(findingService.listFindings(ACTOR_ID, INSPECTION_ID)).thenReturn(List.of(response));
    when(findingService.query(
            eq(ACTOR_ID),
            eq(INSPECTION_ID),
            any(),
            eq(FindingSeverity.HIGH),
            any(),
            eq("crack"),
            eq("pier"),
            eq(1),
            eq(50)))
        .thenReturn(page);
    when(confirm.noFindingsAcknowledged()).thenReturn(true);

    assertThat(controller.confirmFinding(jwt, INSPECTION_ID, FINDING_ID, review).data())
        .isSameAs(response);
    assertThat(controller.createManualFinding(jwt, INSPECTION_ID, manual).data())
        .isSameAs(response);
    assertThat(controller.updateFinding(jwt, INSPECTION_ID, FINDING_ID, update).data())
        .isSameAs(response);
    assertThat(controller.restoreFinding(jwt, INSPECTION_ID, FINDING_ID).data()).isSameAs(response);
    assertThat(controller.listRemovedFindings(jwt, INSPECTION_ID).data()).hasSize(1);
    assertThat(controller.listFindings(jwt, INSPECTION_ID).data()).hasSize(1);
    assertThat(
            controller
                .queryFindings(
                    jwt,
                    INSPECTION_ID,
                    FindingStatusFilter.ACTIVE,
                    FindingSeverity.HIGH,
                    FindingSourceFilter.ALL,
                    "crack",
                    "pier",
                    1,
                    50)
                .data())
        .isSameAs(page);

    controller.deleteFinding(jwt, INSPECTION_ID, FINDING_ID);
    verify(findingService).deleteFinding(ACTOR_ID, INSPECTION_ID, FINDING_ID);
    controller.reopenFindings(jwt, INSPECTION_ID);
    verify(findingService).reopenFindings(ACTOR_ID, INSPECTION_ID);
    controller.confirmFindings(jwt, INSPECTION_ID, confirm);
    verify(findingService).confirmFindings(ACTOR_ID, INSPECTION_ID, true);
  }

  @Test
  void delegatesWorkflowAndReportEndpoints() {
    InspectionWorkflowStateResponse state = mock(InspectionWorkflowStateResponse.class);
    InspectionReportResponse report = mock(InspectionReportResponse.class);
    ReportPublishedResponse published = mock(ReportPublishedResponse.class);
    WithdrawVersionRequest withdraw = mock(WithdrawVersionRequest.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(workflowService.state(ACTOR_ID, INSPECTION_ID)).thenReturn(state);
    when(reportService.generateDraft(eq(ACTOR_ID), eq(INSPECTION_ID), any(), eq("Summary")))
        .thenReturn(report);
    when(reportService.authorManualDraft(ACTOR_ID, INSPECTION_ID)).thenReturn(report);
    when(reportService.verifyVersion(ACTOR_ID, INSPECTION_ID, VERSION_ID)).thenReturn(report);
    when(reportService.submitVersion(ACTOR_ID, INSPECTION_ID, VERSION_ID)).thenReturn(report);
    when(reportService.withdrawVersion(ACTOR_ID, INSPECTION_ID, VERSION_ID, "reason"))
        .thenReturn(report);
    when(reportService.reviewVersion(
            eq(ACTOR_ID), eq(INSPECTION_ID), eq(VERSION_ID), eq(true), eq("reason"), any()))
        .thenReturn(published);
    when(reportService.publishVersion(eq(ACTOR_ID), eq(INSPECTION_ID), eq(VERSION_ID), any()))
        .thenReturn(published);
    when(reportService.requestCustomerClarification(ACTOR_ID, INSPECTION_ID, VERSION_ID, "reason"))
        .thenReturn(published);
    when(withdraw.reason()).thenReturn("reason");

    assertThat(controller.workflowState(jwt, INSPECTION_ID).data()).isSameAs(state);
    assertThat(
            controller
                .generateDraft(jwt, INSPECTION_ID, Map.of("summary", "Summary"), request)
                .data())
        .isSameAs(report);
    assertThat(controller.authorManualDraft(jwt, INSPECTION_ID).data()).isSameAs(report);
    assertThat(controller.verifyVersion(jwt, INSPECTION_ID, VERSION_ID).data()).isSameAs(report);
    assertThat(controller.submitVersion(jwt, INSPECTION_ID, VERSION_ID).data()).isSameAs(report);
    assertThat(controller.withdrawVersion(jwt, INSPECTION_ID, VERSION_ID, withdraw).data())
        .isSameAs(report);
    assertThat(
            controller
                .reviewVersion(jwt, INSPECTION_ID, VERSION_ID, true, "reason", request)
                .data())
        .isSameAs(published);
    assertThat(controller.publishVersion(jwt, INSPECTION_ID, VERSION_ID, request).data())
        .isSameAs(published);
    assertThat(
            controller.requestReportClarification(jwt, INSPECTION_ID, VERSION_ID, "reason").data())
        .isSameAs(published);
  }

  @Test
  void delegatesEvidenceUploadEndpoint() {
    EvidenceResponse response = mock(EvidenceResponse.class);
    when(evidenceService.upload(
            eq(ACTOR_ID), eq(INSPECTION_ID), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(response);
    MockMultipartFile file = new MockMultipartFile("file", "e.jpg", "image/jpeg", new byte[] {1});

    assertThat(
            controller
                .uploadEvidence(
                    jwt, INSPECTION_ID, file, null, Instant.now(), null, null, null, null)
                .data())
        .isSameAs(response);
  }

  @Test
  void delegatesReinspectionEndpoints() {
    var candidate =
        mock(
            com.smartdroneinspection.shared.api.InspectionReassignCandidatePort.ReassignCandidate
                .class);
    when(creationService.evaluateReassignCandidates(ACTOR_ID, INSPECTION_ID))
        .thenReturn(List.of(candidate));

    assertThat(controller.reassignCandidates(jwt, INSPECTION_ID).data()).hasSize(1);
  }
}
