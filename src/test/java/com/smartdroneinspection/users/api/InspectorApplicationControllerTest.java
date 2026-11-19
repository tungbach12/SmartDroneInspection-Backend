package com.smartdroneinspection.users.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.users.api.dto.request.InspectorApplicationReviewRequest;
import com.smartdroneinspection.users.api.dto.request.InspectorApplicationSubmissionRequest;
import com.smartdroneinspection.users.api.dto.response.InspectorApplicationResponse;
import com.smartdroneinspection.users.api.dto.response.UserResponse;
import com.smartdroneinspection.users.service.InspectorApplicationService;
import com.smartdroneinspection.users.service.InspectorPartnerLicenseDocumentService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;

class InspectorApplicationControllerTest {

  private static final UUID ADMIN_ID = UUID.randomUUID();
  private static final UUID APPLICATION_ID = UUID.randomUUID();

  private InspectorApplicationService service;
  private InspectorApplicationController controller;
  private Jwt adminJwt;
  private HttpServletRequest request;

  @BeforeEach
  void setUp() {
    service = mock(InspectorApplicationService.class);
    controller = new InspectorApplicationController(service);
    adminJwt = mock(Jwt.class);
    when(adminJwt.getSubject()).thenReturn(ADMIN_ID.toString());
    request = mock(HttpServletRequest.class);
    when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    when(request.getHeader("User-Agent")).thenReturn("JUnit");
    when(request.getHeader("X-Correlation-ID")).thenReturn("corr");
  }

  @Test
  void submitsAndListsApplications() {
    InspectorApplicationSubmissionRequest body = mock(InspectorApplicationSubmissionRequest.class);
    InspectorApplicationResponse application = mock(InspectorApplicationResponse.class);
    when(service.submit(body, "127.0.0.1", "JUnit", "corr")).thenReturn(application);
    when(service.listPending()).thenReturn(List.of(application));
    when(service.listApprovedAwaitingContract()).thenReturn(List.of(application));
    when(service.get(APPLICATION_ID)).thenReturn(application);

    var submitted = controller.submit(body, request);
    assertThat(submitted.getStatusCode().value()).isEqualTo(201);
    assertThat(submitted.getHeaders().getCacheControl()).isEqualTo("no-store");
    assertThat(controller.listPending().data()).hasSize(1);
    assertThat(controller.listApprovedAwaitingContract().data()).hasSize(1);
    assertThat(controller.get(APPLICATION_ID).data()).isSameAs(application);
  }

  @Test
  void reviewsAndApprovesApplications() {
    UserResponse user = mock(UserResponse.class);
    InspectorApplicationResponse application = mock(InspectorApplicationResponse.class);
    when(service.approve(APPLICATION_ID, ADMIN_ID, "127.0.0.1", "JUnit", "corr")).thenReturn(user);
    when(service.uploadApplicationLicenseScan(any())).thenReturn("pending/scan.pdf");
    when(service.uploadSignedContract(eq(APPLICATION_ID), any(), eq(ADMIN_ID)))
        .thenReturn(application);

    assertThat(controller.approve(adminJwt, APPLICATION_ID, request).data()).isSameAs(user);
    assertThat(
            controller
                .uploadLicenseScan(
                    new MockMultipartFile("file", "scan.pdf", "application/pdf", new byte[] {1}))
                .data())
        .containsEntry("fileKey", "pending/scan.pdf");
    assertThat(
            controller
                .uploadSignedContract(
                    adminJwt,
                    APPLICATION_ID,
                    new MockMultipartFile(
                        "file", "contract.pdf", "application/pdf", new byte[] {1}))
                .data())
        .isSameAs(application);

    controller.retryRejectedCleanup(APPLICATION_ID);
    verify(service).retryRejectionCleanup(APPLICATION_ID);
    controller.reject(
        adminJwt, APPLICATION_ID, mock(InspectorApplicationReviewRequest.class), request);
    verify(service)
        .reject(eq(APPLICATION_ID), eq(ADMIN_ID), any(), eq("127.0.0.1"), eq("JUnit"), eq("corr"));
  }

  @Test
  void streamsLicenseScanAndSignedContract() {
    var content =
        new InspectorPartnerLicenseDocumentService.InputStreamContent(
            "application/pdf", new ByteArrayInputStream(new byte[] {1}));
    when(service.openLicenseScan(APPLICATION_ID)).thenReturn(content);
    when(service.openSignedContract(APPLICATION_ID)).thenReturn(content);

    assertThat(controller.licenseScan(APPLICATION_ID).getBody()).isNotNull();
    assertThat(controller.signedContract(APPLICATION_ID).getBody()).isNotNull();
  }
}
