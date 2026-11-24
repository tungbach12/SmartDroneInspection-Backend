package com.smartdroneinspection.contracts.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.contracts.api.dto.request.AssignServiceRequestManagerRequest;
import com.smartdroneinspection.contracts.api.dto.request.CreateServiceRequest;
import com.smartdroneinspection.contracts.api.dto.request.EvaluatePartnerCandidatesRequest;
import com.smartdroneinspection.contracts.api.dto.response.FinanceHistoryPageResponse;
import com.smartdroneinspection.contracts.api.dto.response.ManagerFinanceHistoryResponse;
import com.smartdroneinspection.contracts.api.dto.response.OccurrenceFinanceResponse;
import com.smartdroneinspection.contracts.api.dto.response.PartnerCandidateResponse;
import com.smartdroneinspection.contracts.api.dto.response.ServiceRequestResponse;
import com.smartdroneinspection.contracts.service.ContractWorkflowService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class ContractWorkflowControllerDelegationTest {

  private static final UUID ACTOR_ID = UUID.randomUUID();
  private static final UUID REQUEST_ID = UUID.randomUUID();
  private static final UUID OCCURRENCE_ID = UUID.randomUUID();

  private ContractWorkflowService workflow;
  private ContractWorkflowController controller;
  private Jwt jwt;

  @BeforeEach
  void setUp() {
    workflow = mock(ContractWorkflowService.class);
    controller = new ContractWorkflowController(workflow);
    jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn(ACTOR_ID.toString());
  }

  @Test
  void delegatesRequestListCreateAndLookup() {
    ServiceRequestResponse response = mock(ServiceRequestResponse.class);
    CreateServiceRequest create = mock(CreateServiceRequest.class);
    when(workflow.list(ACTOR_ID)).thenReturn(List.of(response));
    when(workflow.create(ACTOR_ID, create)).thenReturn(response);
    when(workflow.claimRequest(ACTOR_ID, REQUEST_ID)).thenReturn(response);
    when(workflow.unassignRequest(ACTOR_ID, REQUEST_ID)).thenReturn(response);
    when(workflow.get(ACTOR_ID, REQUEST_ID)).thenReturn(response);

    assertThat(controller.list(jwt).data()).hasSize(1);
    assertThat(controller.create(jwt, create).data()).isSameAs(response);
    assertThat(controller.claimRequest(jwt, REQUEST_ID).data()).isSameAs(response);
    assertThat(controller.unassignRequest(jwt, REQUEST_ID).data()).isSameAs(response);
    assertThat(controller.get(jwt, REQUEST_ID).data()).isSameAs(response);
  }

  @Test
  void delegatesManagerAssignmentAndMessages() {
    ServiceRequestResponse response = mock(ServiceRequestResponse.class);
    AssignServiceRequestManagerRequest assign = mock(AssignServiceRequestManagerRequest.class);
    UUID managerId = UUID.randomUUID();
    when(assign.managerId()).thenReturn(managerId);
    when(workflow.reassignRequest(ACTOR_ID, REQUEST_ID, managerId)).thenReturn(response);
    when(workflow.postMessage(ACTOR_ID, REQUEST_ID, "hello", List.of())).thenReturn(response);
    when(workflow.markMessagesRead(ACTOR_ID, REQUEST_ID)).thenReturn(response);

    assertThat(controller.reassignRequest(jwt, REQUEST_ID, assign).data()).isSameAs(response);
    assertThat(controller.postMessage(jwt, REQUEST_ID, "hello", List.of()).data())
        .isSameAs(response);
    assertThat(controller.markMessagesRead(jwt, REQUEST_ID).data()).isSameAs(response);
  }

  @Test
  void delegatesPartnerCandidatesAndFinanceHistories() {
    PartnerCandidateResponse candidate = mock(PartnerCandidateResponse.class);
    FinanceHistoryPageResponse customerPage = mock(FinanceHistoryPageResponse.class);
    FinanceHistoryPageResponse inspectorPage = mock(FinanceHistoryPageResponse.class);
    ManagerFinanceHistoryResponse managerPage = mock(ManagerFinanceHistoryResponse.class);
    OccurrenceFinanceResponse occurrence = mock(OccurrenceFinanceResponse.class);
    when(workflow.evaluatePartnerCandidates(eq(ACTOR_ID), eq(REQUEST_ID), any()))
        .thenReturn(List.of(candidate));
    when(workflow.customerFinanceHistory(ACTOR_ID, 0, 20)).thenReturn(customerPage);
    when(workflow.inspectorFinanceHistory(ACTOR_ID, 0, 20)).thenReturn(inspectorPage);
    when(workflow.managerFinanceHistory(ACTOR_ID, 0, 20)).thenReturn(managerPage);
    when(workflow.managerFinanceHistory(ACTOR_ID, 0, 20, "PAYOUTS")).thenReturn(managerPage);
    when(workflow.customerFinanceHistoryDetail(ACTOR_ID, OCCURRENCE_ID)).thenReturn(occurrence);
    when(workflow.inspectorFinanceHistoryDetail(ACTOR_ID, OCCURRENCE_ID)).thenReturn(occurrence);
    when(workflow.managerFinanceHistoryDetail(ACTOR_ID, OCCURRENCE_ID)).thenReturn(occurrence);
    when(workflow.getOccurrenceFinance(ACTOR_ID, REQUEST_ID, OCCURRENCE_ID)).thenReturn(occurrence);

    assertThat(
            controller
                .evaluatePartnerCandidates(
                    jwt, REQUEST_ID, mock(EvaluatePartnerCandidatesRequest.class))
                .data())
        .hasSize(1);
    assertThat(controller.customerFinanceHistory(jwt, 0, 20).data()).isSameAs(customerPage);
    assertThat(controller.inspectorFinanceHistory(jwt, 0, 20).data()).isSameAs(inspectorPage);
    assertThat(controller.managerFinanceHistory(jwt, 0, 20, "ALL").data()).isSameAs(managerPage);
    assertThat(controller.managerFinanceHistory(jwt, 0, 20, "PAYOUTS").data())
        .isSameAs(managerPage);
    assertThat(controller.customerFinanceHistoryDetail(jwt, OCCURRENCE_ID).data())
        .isSameAs(occurrence);
    assertThat(controller.inspectorFinanceHistoryDetail(jwt, OCCURRENCE_ID).data())
        .isSameAs(occurrence);
    assertThat(controller.managerFinanceHistoryDetail(jwt, OCCURRENCE_ID).data())
        .isSameAs(occurrence);
    assertThat(controller.getOccurrenceFinance(jwt, REQUEST_ID, OCCURRENCE_ID).data())
        .isSameAs(occurrence);
  }

  @Test
  void delegatesQuoteAndContractEndpoints() {
    ServiceRequestResponse response = mock(ServiceRequestResponse.class);
    com.smartdroneinspection.contracts.api.dto.request.IssueServiceQuoteRequest quote =
        mock(com.smartdroneinspection.contracts.api.dto.request.IssueServiceQuoteRequest.class);
    com.smartdroneinspection.contracts.api.dto.request.AcceptServiceQuoteRequest accept =
        mock(com.smartdroneinspection.contracts.api.dto.request.AcceptServiceQuoteRequest.class);
    when(workflow.saveQuote(ACTOR_ID, REQUEST_ID, quote)).thenReturn(response);
    when(workflow.acceptQuote(ACTOR_ID, REQUEST_ID, accept)).thenReturn(response);
    when(workflow.uploadSignedContract(eq(ACTOR_ID), eq(REQUEST_ID), any())).thenReturn(response);

    assertThat(controller.saveQuote(jwt, REQUEST_ID, quote).data()).isSameAs(response);
    assertThat(controller.issueQuote(jwt, REQUEST_ID, quote).data()).isSameAs(response);
    assertThat(controller.acceptQuote(jwt, REQUEST_ID, accept).data()).isSameAs(response);
    assertThat(
            controller
                .uploadSignedContract(
                    jwt,
                    REQUEST_ID,
                    new org.springframework.mock.web.MockMultipartFile(
                        "file", "contract.pdf", "application/pdf", new byte[] {1}))
                .data())
        .isSameAs(response);
  }
}
