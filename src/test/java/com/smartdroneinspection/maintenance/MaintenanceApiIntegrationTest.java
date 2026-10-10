package com.smartdroneinspection.maintenance;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.users.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

/** MF4-01/02 HTTP boundary, duplicate protection and organization scope. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MaintenanceApiIntegrationTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired ObjectMapper objectMapper;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbcTemplate;

  private MaintenanceTestFixture helper;
  private MaintenanceTestFixture.Data fixture;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(
                webApplicationContext)
            .apply(
                org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                    .springSecurity())
            .build();
    helper = new MaintenanceTestFixture(users, jdbcTemplate);
    fixture = helper.create();
  }

  @Test
  void orgAdminCreatesAndReadsWorkOrderFromOwnOrganization() throws Exception {
    UUID workOrderId = createWorkOrder(fixture.ownerId());

    mockMvc
        .perform(
            get("/api/v1/maintenance/work-orders/{id}", workOrderId)
                .with(principal(fixture.ownerId(), "ORG_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(workOrderId.toString()))
        .andExpect(jsonPath("$.data.status").value("DRAFT"))
        .andExpect(jsonPath("$.data.sourceFindingId").value(fixture.findingId().toString()));
  }

  @Test
  void anotherOrganizationCannotReadWorkOrderEvenWhenItKnowsTheId() throws Exception {
    UUID workOrderId = createWorkOrder(fixture.ownerId());

    mockMvc
        .perform(
            get("/api/v1/maintenance/work-orders/{id}", workOrderId)
                .with(principal(fixture.outsiderId(), "ORG_ADMIN")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("WORK_ORDER_NOT_FOUND"));
  }

  @Test
  void duplicateActiveFindingCannotOpenSecondWorkOrder() throws Exception {
    createWorkOrder(fixture.ownerId());

    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders")
                .with(principal(fixture.ownerId(), "ORG_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createWorkOrderBody()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("WORK_ORDER_DUPLICATE_SCOPE"));
  }

  @Test
  void repairCandidatesShowPublishedFindingsUntilAnActiveWorkOrderExists() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/maintenance/repair-candidates")
                .with(principal(fixture.ownerId(), "ORG_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].findingId").value(fixture.findingId().toString()));

    createWorkOrder(fixture.ownerId());

    mockMvc
        .perform(
            get("/api/v1/maintenance/repair-candidates")
                .with(principal(fixture.ownerId(), "ORG_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0));
  }

  @Test
  void maintenanceEngineerCannotOpenWorkOrder() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders")
                .with(principal(fixture.engineerId(), "MAINTENANCE_ENGINEER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createWorkOrderBody()))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminAssignsIndependentTeamAndEngineerReadsAssignments() throws Exception {
    UUID workOrderId = createWorkOrder(fixture.ownerId());

    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/team", workOrderId)
                .with(principal(fixture.ownerId(), "ORG_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "leadUserId", fixture.leadId(),
                            "reportAuthorUserId", fixture.reportAuthorId(),
                            "acceptingReviewerUserId", fixture.reviewerId(),
                            "reason", "Qualified internal team"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.teamLeadUserId").value(fixture.leadId().toString()))
        .andExpect(
            jsonPath("$.data.acceptingReviewerUserId").value(fixture.reviewerId().toString()));

    mockMvc
        .perform(
            get("/api/v1/maintenance/work-orders/{id}/team", workOrderId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2));
  }

  @Test
  void teamCannotIncludeReviewerWhoIsAlsoTheLead() throws Exception {
    UUID workOrderId = createWorkOrder(fixture.ownerId());

    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/team", workOrderId)
                .with(principal(fixture.ownerId(), "ORG_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "leadUserId", fixture.leadId(),
                            "reportAuthorUserId", fixture.reportAuthorId(),
                            "acceptingReviewerUserId", fixture.leadId()))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("TEAM_REVIEWER_INVALID"));
  }

  @Test
  void expiredCredentialBlocksTeamAssignment() throws Exception {
    helper.grantCredential(
        fixture.organizationId(),
        fixture.engineerId(),
        "ACTIVE",
        java.time.Instant.now().minusSeconds(30));
    UUID workOrderId = createWorkOrder(fixture.ownerId());

    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/team", workOrderId)
                .with(principal(fixture.ownerId(), "ORG_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "leadUserId", fixture.engineerId(),
                            "reportAuthorUserId", fixture.reportAuthorId(),
                            "acceptingReviewerUserId", fixture.reviewerId()))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("WORK_ORDER_SCOPE_DENIED"));
  }

  @Test
  void unrecordedCredentialDoesNotBlockAssignmentPerApprovedInterimRule() throws Exception {
    UUID workOrderId = createWorkOrder(fixture.ownerId());

    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/team", workOrderId)
                .with(principal(fixture.ownerId(), "ORG_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "leadUserId", fixture.engineerId(),
                            "reportAuthorUserId", fixture.reportAuthorId(),
                            "acceptingReviewerUserId", fixture.reviewerId()))))
        .andExpect(status().isOk());
  }

  @Test
  void leadCreatesEstimateAndSystemCalculatesItsTotal() throws Exception {
    UUID workOrderId = createWorkOrder(fixture.ownerId());
    assignDefaultTeam(workOrderId);
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/tasks", workOrderId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("name", "Replace support", "method", "Install new support"))))
        .andExpect(status().isOk());

    String taskResponse =
        mockMvc
            .perform(
                get("/api/v1/maintenance/work-orders/{id}/tasks", workOrderId)
                    .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].taskNumber").value(1))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID taskId =
        UUID.fromString(
            objectMapper.readTree(taskResponse).path("data").get(0).path("id").asString());

    Map<String, Object> line =
        Map.of(
            "taskId",
            taskId,
            "lineKind",
            "MATERIAL",
            "description",
            "Steel support",
            "quantity",
            2,
            "unit",
            "piece",
            "unitRate",
            125000);
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/estimate-versions", workOrderId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "currency", "VND",
                            "snapshot", "{\"source\":\"line items\"}",
                            "lines", List.of(line)))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.versionNo").value(1))
        .andExpect(jsonPath("$.data.baselineTotal").value(250000.0));
  }

  @Test
  void unrelatedOrgAdminCannotApproveEstimate() throws Exception {
    UUID workOrderId = createWorkOrder(fixture.ownerId());
    assignDefaultTeam(workOrderId);
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/tasks", workOrderId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Replace support"))))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/estimate-versions", workOrderId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "currency", "VND",
                            "snapshot", "{}",
                            "lines",
                                List.of(
                                    Map.of(
                                        "lineKind",
                                        "MATERIAL",
                                        "description",
                                        "Support",
                                        "quantity",
                                        1,
                                        "unitRate",
                                        100000))))))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/estimate-versions/1/submit", workOrderId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/estimate-versions/1/approve", workOrderId)
                .with(principal(fixture.reviewerId(), "ORG_ADMIN")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("WORK_ORDER_SCOPE_DENIED"));
  }

  @Test
  void designatedApproverCanReturnSubmittedEstimateForRework() throws Exception {
    UUID workOrderId = createWorkOrder(fixture.ownerId());
    assignDefaultTeam(workOrderId);
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/tasks", workOrderId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Replace support"))))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/estimate-versions", workOrderId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "currency", "VND",
                            "snapshot", "{}",
                            "lines",
                                List.of(
                                    Map.of(
                                        "lineKind",
                                        "MATERIAL",
                                        "description",
                                        "Support",
                                        "quantity",
                                        1,
                                        "unitRate",
                                        100000))))))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/estimate-versions/1/submit", workOrderId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/estimate-versions/1/reject", workOrderId)
                .with(principal(fixture.approverId(), "ORG_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", "Add labor estimate"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("REJECTED"));

    mockMvc
        .perform(
            get("/api/v1/maintenance/work-orders/{id}", workOrderId)
                .with(principal(fixture.ownerId(), "ORG_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("REWORK_REQUIRED"));
  }

  @Test
  void reconciliationRejectsActualLineInDifferentCurrencyFromApprovedBaseline() throws Exception {
    UUID workOrderId = createWorkOrder(fixture.ownerId());
    assignDefaultTeam(workOrderId);
    UUID taskId = createTask(workOrderId);
    createAndApproveEstimate(workOrderId);
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/release", workOrderId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/start", workOrderId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/declare-complete", workOrderId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER")))
        .andExpect(status().isConflict());
    createAndVerifyWorkLog(workOrderId, taskId);
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/declare-complete", workOrderId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER")))
        .andExpect(status().isOk());
    submitCompletionReport(workOrderId);
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/acceptance-decisions", workOrderId)
                .with(principal(fixture.reviewerId(), "ORG_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("decision", "ACCEPTED", "technicalComments", "Passed"))))
        .andExpect(status().isOk());

    Map<String, Object> mismatchedCurrencyLine =
        Map.of(
            "taskId",
            taskId,
            "lineKind",
            "MATERIAL",
            "description",
            "Actual material",
            "quantity",
            1,
            "unitRate",
            1,
            "currency",
            "USD");
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/cost-reconciliation", workOrderId)
                .with(principal(fixture.reviewerId(), "ORG_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("actualLines", List.of(mismatchedCurrencyLine)))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COST_LINE_CURRENCY_MISMATCH"));
  }

  @Test
  void leadCanResumeExecutionAfterReviewerRequiresRework() throws Exception {
    UUID workOrderId = createWorkOrder(fixture.ownerId());
    assignDefaultTeam(workOrderId);
    createAndApproveEstimate(workOrderId);
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/release", workOrderId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/start", workOrderId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER")))
        .andExpect(status().isOk());
    UUID taskId = createTask(workOrderId);
    createAndVerifyWorkLog(workOrderId, taskId);
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/declare-complete", workOrderId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER")))
        .andExpect(status().isOk());
    submitCompletionReport(workOrderId);
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/acceptance-decisions", workOrderId)
                .with(principal(fixture.reviewerId(), "ORG_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "decision", "REWORK_REQUIRED",
                            "technicalComments", "Load test evidence missing"))))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/resume-after-rework", workOrderId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
  }

  @Test
  void teamEngineerCanRequestChangeAndOnlyBudgetApproverCanApproveIt() throws Exception {
    UUID workOrderId = createWorkOrder(fixture.ownerId());
    assignDefaultTeam(workOrderId);
    createAndApproveEstimate(workOrderId);

    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/release", workOrderId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/start", workOrderId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/changes", workOrderId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("reason", "Hidden damage", "proposedDelta", 50000))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("AWAITING_APPROVAL"));

    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/changes/1/approve", workOrderId)
                .with(principal(fixture.ownerId(), "ORG_ADMIN")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("WORK_ORDER_SCOPE_DENIED"));

    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/changes/1/approve", workOrderId)
                .with(principal(fixture.approverId(), "ORG_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("APPROVED"));
  }

  @Test
  void rejectedChangeDoesNotIncreaseAuthorizedBaseline() throws Exception {
    UUID workOrderId = createWorkOrder(fixture.ownerId());
    assignDefaultTeam(workOrderId);
    createAndApproveEstimate(workOrderId);
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/release", workOrderId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/start", workOrderId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/changes", workOrderId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("reason", "Extra materials", "proposedDelta", 50000))))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/changes/1/reject", workOrderId)
                .with(principal(fixture.approverId(), "ORG_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", "Not authorized"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("REJECTED"));
    mockMvc
        .perform(
            get("/api/v1/maintenance/work-orders/{id}/changes", workOrderId)
                .with(principal(fixture.ownerId(), "ORG_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].proposedDelta").value(50000.0))
        .andExpect(jsonPath("$.data[0].status").value("REJECTED"));
  }

  @Test
  void nonTeamEngineerCannotRecordWorkLog() throws Exception {
    UUID workOrderId = createWorkOrder(fixture.ownerId());
    assignDefaultTeam(workOrderId);
    UUID taskId = createTask(workOrderId);
    createAndApproveEstimate(workOrderId);
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/release", workOrderId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/start", workOrderId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/work-logs", workOrderId)
                .with(principal(fixture.engineerId(), "MAINTENANCE_ENGINEER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("taskId", taskId, "hours", 1))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("WORK_ORDER_SCOPE_DENIED"));
  }

  @Test
  void leadCannotAssignTaskToEngineerOutsideWorkOrderTeam() throws Exception {
    UUID workOrderId = createWorkOrder(fixture.ownerId());
    assignDefaultTeam(workOrderId);

    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/tasks", workOrderId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "name",
                            "Replace support",
                            "assignedEngineerUserId",
                            fixture.engineerId()))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("TASK_ASSIGNEE_NOT_IN_TEAM"));
  }

  @Test
  void estimateRejectsMissingRateRatherThanTreatingItAsZero() throws Exception {
    UUID workOrderId = createWorkOrder(fixture.ownerId());
    assignDefaultTeam(workOrderId);
    Map<String, Object> line =
        Map.of(
            "lineKind", "MATERIAL",
            "description", "Unpriced part",
            "quantity", 1);

    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/estimate-versions", workOrderId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "currency", "VND",
                            "snapshot", "{}",
                            "lines", List.of(line)))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COST_LINE_INVALID"));
  }

  @Test
  void completesWorkOrderThroughIndependentAcceptanceAndCostReconciliation() throws Exception {
    UUID workOrderId = createWorkOrder(fixture.ownerId());
    assignDefaultTeam(workOrderId);

    String taskResponse =
        mockMvc
            .perform(
                post("/api/v1/maintenance/work-orders/{id}/tasks", workOrderId)
                    .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("name", "Replace support"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID taskId =
        UUID.fromString(objectMapper.readTree(taskResponse).path("data").path("id").asString());

    Map<String, Object> estimateLine =
        Map.of(
            "taskId",
            taskId,
            "lineKind",
            "MATERIAL",
            "description",
            "Steel support",
            "quantity",
            2,
            "unit",
            "piece",
            "unitRate",
            125000);
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/estimate-versions", workOrderId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "currency", "VND",
                            "snapshot", "{}",
                            "lines", List.of(estimateLine)))))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/estimate-versions/1/submit", workOrderId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/estimate-versions/1/approve", workOrderId)
                .with(principal(fixture.approverId(), "ORG_ADMIN")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/release", workOrderId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/start", workOrderId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER")))
        .andExpect(status().isOk());
    String logResponse =
        mockMvc
            .perform(
                post("/api/v1/maintenance/work-orders/{id}/work-logs", workOrderId)
                    .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "taskId",
                                taskId,
                                "startedAt",
                                java.time.Instant.now().minusSeconds(3600).toString(),
                                "endedAt",
                                java.time.Instant.now().toString(),
                                "hours",
                                1,
                                "asLeftCondition",
                                "Installed",
                                "testReadings",
                                "{\"narrative\":\"Replaced support\"}"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID workLogId =
        UUID.fromString(objectMapper.readTree(logResponse).path("data").path("id").asString());
    mockMvc
        .perform(
            post(
                    "/api/v1/maintenance/work-orders/{id}/work-logs/{logId}/submit",
                    workOrderId,
                    workLogId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post(
                    "/api/v1/maintenance/work-orders/{id}/work-logs/{logId}/verify",
                    workOrderId,
                    workLogId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("VERIFIED"));
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/declare-complete", workOrderId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/reports", workOrderId)
                .with(principal(fixture.reportAuthorId(), "MAINTENANCE_ENGINEER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "contentSnapshot", "{\"summary\":\"Support replaced\"}",
                            "approvedScopeHash", "scope-hash",
                            "workLogSnapshotHash", "work-log-hash",
                            "actualCostSnapshotHash", "actual-cost-hash"))))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/reports/1/verify", workOrderId)
                .with(principal(fixture.reportAuthorId(), "MAINTENANCE_ENGINEER")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/reports/1/submit", workOrderId)
                .with(principal(fixture.reportAuthorId(), "MAINTENANCE_ENGINEER")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/acceptance-decisions", workOrderId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("decision", "ACCEPTED"))))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/acceptance-decisions", workOrderId)
                .with(principal(fixture.reviewerId(), "ORG_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "decision", "ACCEPTED",
                            "technicalComments", "Acceptance checks passed",
                            "acceptanceChecklist", "{\"checks\":[\"load test\"]}",
                            "testResult", "{\"pass\":true}"))))
        .andExpect(status().isOk());

    Map<String, Object> actualLine =
        Map.of(
            "taskId", taskId,
            "lineKind", "MATERIAL",
            "description", "Actual steel support",
            "quantity", 2,
            "unit", "piece",
            "unitRate", 120000,
            "currency", "VND");
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/cost-reconciliation", workOrderId)
                .with(principal(fixture.reviewerId(), "ORG_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("actualLines", List.of(actualLine)))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.approvedBaselineTotal").value(250000.0))
        .andExpect(jsonPath("$.data.actualTotal").value(240000.0))
        .andExpect(jsonPath("$.data.variance").value(-10000.0))
        .andExpect(jsonPath("$.data.variancePercent").value(-4.0));

    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/close", workOrderId)
                .with(principal(fixture.reviewerId(), "ORG_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CLOSED"));
  }

  private UUID createTask(UUID workOrderId) throws Exception {
    String response =
        mockMvc
            .perform(
                post("/api/v1/maintenance/work-orders/{id}/tasks", workOrderId)
                    .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("name", "Replace support"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(objectMapper.readTree(response).path("data").path("id").asString());
  }

  private void createAndVerifyWorkLog(UUID workOrderId, UUID taskId) throws Exception {
    String response =
        mockMvc
            .perform(
                post("/api/v1/maintenance/work-orders/{id}/work-logs", workOrderId)
                    .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "taskId",
                                taskId,
                                "startedAt",
                                java.time.Instant.now().minusSeconds(3600).toString(),
                                "endedAt",
                                java.time.Instant.now().toString(),
                                "hours",
                                1,
                                "asLeftCondition",
                                "Installed",
                                "testReadings",
                                "{\"narrative\":\"Replaced support\"}"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID workLogId =
        UUID.fromString(objectMapper.readTree(response).path("data").path("id").asString());
    mockMvc
        .perform(
            post(
                    "/api/v1/maintenance/work-orders/{id}/work-logs/{logId}/submit",
                    workOrderId,
                    workLogId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post(
                    "/api/v1/maintenance/work-orders/{id}/work-logs/{logId}/verify",
                    workOrderId,
                    workLogId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER")))
        .andExpect(status().isOk());
  }

  private void submitCompletionReport(UUID workOrderId) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/reports", workOrderId)
                .with(principal(fixture.reportAuthorId(), "MAINTENANCE_ENGINEER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "contentSnapshot", "{\"summary\":\"Support replaced\"}",
                            "approvedScopeHash", "scope-hash",
                            "workLogSnapshotHash", "work-log-hash",
                            "actualCostSnapshotHash", "actual-cost-hash"))))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/reports/1/verify", workOrderId)
                .with(principal(fixture.reportAuthorId(), "MAINTENANCE_ENGINEER")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/reports/1/submit", workOrderId)
                .with(principal(fixture.reportAuthorId(), "MAINTENANCE_ENGINEER")))
        .andExpect(status().isOk());
  }

  private void createAndApproveEstimate(UUID workOrderId) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/tasks", workOrderId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Replace support"))))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/estimate-versions", workOrderId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "currency", "VND",
                            "snapshot", "{}",
                            "lines",
                                List.of(
                                    Map.of(
                                        "lineKind",
                                        "MATERIAL",
                                        "description",
                                        "Support",
                                        "quantity",
                                        1,
                                        "unitRate",
                                        100000))))))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/estimate-versions/1/submit", workOrderId)
                .with(principal(fixture.leadId(), "MAINTENANCE_ENGINEER")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/estimate-versions/1/approve", workOrderId)
                .with(principal(fixture.approverId(), "ORG_ADMIN")))
        .andExpect(status().isOk());
  }

  private void assignDefaultTeam(UUID workOrderId) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/team", workOrderId)
                .with(principal(fixture.ownerId(), "ORG_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "leadUserId", fixture.leadId(),
                            "reportAuthorUserId", fixture.reportAuthorId(),
                            "acceptingReviewerUserId", fixture.reviewerId()))))
        .andExpect(status().isOk());
  }

  private UUID createWorkOrder(UUID actorId) throws Exception {
    String response =
        mockMvc
            .perform(
                post("/api/v1/maintenance/work-orders")
                    .with(principal(actorId, "ORG_ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createWorkOrderBody()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(objectMapper.readTree(response).path("data").path("id").asString());
  }

  private String createWorkOrderBody() throws Exception {
    return objectMapper.writeValueAsString(
        Map.of(
            "assetId", fixture.assetId(),
            "sourceReportVersionId", fixture.reportVersionId(),
            "sourceFindingId", fixture.findingId(),
            "budgetApproverUserId", fixture.approverId(),
            "priority", "HIGH",
            "correctiveScope", "{\"summary\":\"Replace corroded support\"}",
            "acceptanceCriteria", "{\"checks\":[\"load test\"]}"));
  }

  private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor principal(
      UUID userId, String role) {
    return SecurityMockMvcRequestPostProcessors.jwt()
        .jwt(token -> token.subject(userId.toString()))
        .authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role));
  }
}
