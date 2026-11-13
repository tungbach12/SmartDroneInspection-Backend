package com.smartdroneinspection.contracts.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartdroneinspection.contracts.service.ContractWorkflowService;
import com.smartdroneinspection.shared.exception.GlobalExceptionHandler;
import com.smartdroneinspection.shared.exception.ProblemText;
import java.io.ByteArrayInputStream;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ContractWorkflowControllerTest {

  private ContractWorkflowService workflow;
  private MockMvc mockMvc;
  private final UUID managerId = UUID.randomUUID();
  private final UUID requestId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    workflow = mock(ContractWorkflowService.class);
    Jwt jwt =
        Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .subject(managerId.toString())
            .claim("roles", java.util.List.of("MANAGER"))
            .build();
    var resolver =
        new org.springframework.web.method.support.HandlerMethodArgumentResolver() {
          @Override
          public boolean supportsParameter(org.springframework.core.MethodParameter parameter) {
            return parameter.hasParameterAnnotation(
                org.springframework.security.core.annotation.AuthenticationPrincipal.class);
          }

          @Override
          public Object resolveArgument(
              org.springframework.core.MethodParameter parameter,
              org.springframework.web.method.support.ModelAndViewContainer container,
              org.springframework.web.context.request.NativeWebRequest request,
              org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
            return jwt;
          }
        };
    ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
    messages.setBasename("messages");
    messages.setDefaultEncoding("UTF-8");
    mockMvc =
        MockMvcBuilders.standaloneSetup(new ContractWorkflowController(workflow))
            .setCustomArgumentResolvers(resolver)
            .setControllerAdvice(new GlobalExceptionHandler(new ProblemText(messages)))
            .build();
  }

  @Test
  void downloadsSignedContractAsAttachmentWithoutExposingObjectKey() throws Exception {
    when(workflow.openSignedContract(managerId, requestId))
        .thenReturn(
            new ContractWorkflowService.SignedContractFile(
                "signed-contract.pdf",
                "application/pdf",
                8L,
                new ByteArrayInputStream("%PDF-e2e".getBytes())));

    mockMvc
        .perform(get("/api/v1/contracts/requests/{requestId}/signed-contract", requestId))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
        .andExpect(content().bytes("%PDF-e2e".getBytes()))
        .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "8"))
        .andExpect(
            header()
                .string(
                    HttpHeaders.CONTENT_DISPOSITION,
                    org.hamcrest.Matchers.containsString("filename=\"signed-contract.pdf\"")));

    verify(workflow).openSignedContract(managerId, requestId);
  }

  @Test
  void marksMessagesAsRead() throws Exception {
    mockMvc
        .perform(post("/api/v1/contracts/requests/{requestId}/messages/read", requestId))
        .andExpect(status().isOk());

    verify(workflow).markMessagesRead(managerId, requestId);
  }

  @Test
  void listsActivePartnerCapabilities() throws Exception {
    when(workflow.listPartnerCapabilities(managerId))
        .thenReturn(
            java.util.List.of(
                new com.smartdroneinspection.contracts.service.PartnerRegistryService
                    .DroneFeatureOption(
                    "THERMAL_CAMERA", "Camera nhiệt", "Thermal Camera", 1, true, "CAPABILITY")));

    mockMvc
        .perform(get("/api/v1/contracts/requests/partner-capabilities"))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$.data[0].code")
                .value("THERMAL_CAMERA"));

    verify(workflow).listPartnerCapabilities(managerId);
  }

  @Test
  void evaluatesPartnerCandidatesForServiceRequest() throws Exception {
    UUID inspectorId = UUID.randomUUID();
    UUID droneId = UUID.randomUUID();
    when(workflow.evaluatePartnerCandidates(
            org.mockito.ArgumentMatchers.eq(managerId),
            org.mockito.ArgumentMatchers.eq(requestId),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            java.util.List.of(
                com.smartdroneinspection.contracts.api.dto.response.PartnerCandidateResponse.from(
                    new com.smartdroneinspection.contracts.service.PartnerCandidateEvaluation
                        .Result(
                        inspectorId,
                        droneId,
                        "Tran Minh Quan",
                        "https://cdn.example.test/avatars/quan.png",
                        "SDI-700",
                        "Matrice 350 RTK",
                        true,
                        java.util.List.of("THERMAL_CAMERA", "RTK_POSITIONING"),
                        com.smartdroneinspection.contracts.service.PartnerCandidateEvaluation.Status
                            .PASS,
                        com.smartdroneinspection.contracts.service.PartnerCandidateEvaluation.Status
                            .MATCH,
                        new java.math.BigDecimal("120.0"),
                        new java.math.BigDecimal("80.00"),
                        new java.math.BigDecimal("1200.00"),
                        java.util.List.of(),
                        java.util.List.of(),
                        "Evaluation is advisory. It does not confirm a flight permit or clearance."))));

    mockMvc
        .perform(
            post("/api/v1/contracts/requests/{requestId}/partner-candidates", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "plannedStartAt": "2026-12-04T01:00:00Z",
                      "plannedEndAt": "2026-12-04T03:00:00Z",
                      "requiredDroneFeatureCodes": ["THERMAL_CAMERA"]
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$.data[0].inspectorAvatarUrl")
                .value("https://cdn.example.test/avatars/quan.png"))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$.data[0].droneHasImage")
                .value(true))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$.data[0].droneFeatureCodes")
                .value(
                    org.hamcrest.Matchers.containsInAnyOrder("THERMAL_CAMERA", "RTK_POSITIONING")))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$.data[0].eligibility")
                .value("PASS"))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$.data[0].capabilityFit")
                .value("MATCH"));
  }
}
