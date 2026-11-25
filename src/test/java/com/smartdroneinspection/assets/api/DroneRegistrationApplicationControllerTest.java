package com.smartdroneinspection.assets.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.assets.api.dto.request.ReviewDroneRegistrationApplicationRequest;
import com.smartdroneinspection.assets.api.dto.request.SubmitDroneRegistrationApplicationRequest;
import com.smartdroneinspection.assets.api.dto.request.UpdateDroneServiceabilityRequest;
import com.smartdroneinspection.assets.api.dto.response.DroneEvidenceUploadResponse;
import com.smartdroneinspection.assets.api.dto.response.DroneRegistrationApplicationPageResponse;
import com.smartdroneinspection.assets.api.dto.response.DroneRegistrationApplicationResponse;
import com.smartdroneinspection.assets.api.dto.response.InspectorDroneResponse;
import com.smartdroneinspection.assets.domain.enums.DroneServiceability;
import com.smartdroneinspection.assets.service.DroneEvidenceUploadService;
import com.smartdroneinspection.assets.service.DroneEvidenceUploadService.EvidenceContent;
import com.smartdroneinspection.assets.service.DroneRegistrationApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;

class DroneRegistrationApplicationControllerTest {

  private static final UUID INSPECTOR_ID = UUID.randomUUID();

  private DroneRegistrationApplicationService applications;
  private DroneEvidenceUploadService evidenceUploads;
  private DroneRegistrationApplicationController controller;
  private Jwt jwt;

  @BeforeEach
  void setUp() {
    applications = mock(DroneRegistrationApplicationService.class);
    evidenceUploads = mock(DroneEvidenceUploadService.class);
    controller = new DroneRegistrationApplicationController(applications, evidenceUploads);
    jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn(INSPECTOR_ID.toString());
  }

  @Test
  void uploadsEvidenceAndStreamsApplicationAndDroneEvidence() {
    MockMultipartFile file =
        new MockMultipartFile("file", "scan.pdf", "application/pdf", new byte[] {1});
    DroneEvidenceUploadResponse upload = mock(DroneEvidenceUploadResponse.class);
    when(evidenceUploads.upload(INSPECTOR_ID, "REGISTRATION", file)).thenReturn(upload);
    EvidenceContent content =
        new EvidenceContent("application/pdf", new ByteArrayInputStream(new byte[] {2}));
    when(applications.openEvidence(eq(INSPECTOR_ID), any(), eq("REGISTRATION")))
        .thenReturn(content);
    when(applications.openDroneImage(eq(INSPECTOR_ID), any(), eq(0))).thenReturn(content);

    assertThat(controller.uploadEvidence(jwt, "REGISTRATION", file).data()).isSameAs(upload);

    var applicationResponse = controller.evidence(jwt, UUID.randomUUID(), "REGISTRATION");
    var droneResponse = controller.droneImage(jwt, UUID.randomUUID(), 0);

    assertThat(applicationResponse.getHeaders().getCacheControl()).isEqualTo("no-store");
    assertThat(applicationResponse.getBody()).isNotNull();
    assertThat(droneResponse.getBody()).isNotNull();
  }

  @Test
  void submitsAndResubmitsApplications() {
    HttpServletRequest request = request();
    SubmitDroneRegistrationApplicationRequest body =
        mock(SubmitDroneRegistrationApplicationRequest.class);
    DroneRegistrationApplicationResponse response =
        mock(DroneRegistrationApplicationResponse.class);
    when(applications.submit(INSPECTOR_ID, body, "127.0.0.1", "JUnit", "corr"))
        .thenReturn(response);
    when(applications.resubmit(
            eq(INSPECTOR_ID), any(), eq(body), eq("127.0.0.1"), eq("JUnit"), eq("corr")))
        .thenReturn(response);

    assertThat(controller.submit(jwt, body, request).data()).isSameAs(response);
    assertThat(controller.resubmit(jwt, UUID.randomUUID(), body, request).data())
        .isSameAs(response);
  }

  @Test
  void listsApplicationsDronesAndReviewQueues() {
    DroneRegistrationApplicationResponse application =
        mock(DroneRegistrationApplicationResponse.class);
    InspectorDroneResponse drone = mock(InspectorDroneResponse.class);
    when(applications.mine(INSPECTOR_ID)).thenReturn(List.of(application));
    when(applications.inspectorDrones(INSPECTOR_ID)).thenReturn(List.of(drone));
    when(applications.pending(INSPECTOR_ID)).thenReturn(List.of(application));
    when(applications.history(INSPECTOR_ID)).thenReturn(List.of(application));

    assertThat(controller.mine(jwt).data()).hasSize(1);
    assertThat(controller.inspectorDrones(jwt).data()).hasSize(1);
    assertThat(controller.pending(jwt).data()).hasSize(1);
    assertThat(controller.history(jwt).data()).hasSize(1);

    var paged =
        new DroneRegistrationApplicationPageResponse(List.of(application), 1, 10, 1, 1, 1, 1, 0, 0);
    when(applications.listForReview(INSPECTOR_ID, 1, 10, "PENDING", "CLASS_A", "Mavic", "NEWEST"))
        .thenReturn(paged);
    assertThat(controller.listForReview(jwt, 1, 10, "PENDING", "CLASS_A", "Mavic", "NEWEST").data())
        .isSameAs(paged);
  }

  @Test
  void updatesServiceabilityAndDeletesDrone() {
    InspectorDroneResponse drone = mock(InspectorDroneResponse.class);
    when(applications.updateMyDroneServiceability(
            eq(INSPECTOR_ID), any(), eq(DroneServiceability.MAINTENANCE)))
        .thenReturn(drone);

    assertThat(
            controller
                .updateMyDroneServiceability(
                    jwt,
                    UUID.randomUUID(),
                    new UpdateDroneServiceabilityRequest(DroneServiceability.MAINTENANCE))
                .data())
        .isSameAs(drone);

    controller.deleteMyDrone(jwt, UUID.randomUUID());
    verify(applications).deleteMyDrone(eq(INSPECTOR_ID), any());
  }

  @Test
  void approvesAndRejectsApplications() {
    HttpServletRequest request = request();
    DroneRegistrationApplicationResponse response =
        mock(DroneRegistrationApplicationResponse.class);
    when(applications.approve(eq(INSPECTOR_ID), any(), eq("127.0.0.1"), eq("JUnit"), eq("corr")))
        .thenReturn(response);
    when(applications.reject(
            eq(INSPECTOR_ID),
            any(),
            any(ReviewDroneRegistrationApplicationRequest.class),
            eq("127.0.0.1"),
            eq("JUnit"),
            eq("corr")))
        .thenReturn(response);

    assertThat(controller.approve(jwt, UUID.randomUUID(), request).data()).isSameAs(response);
    assertThat(
            controller
                .reject(
                    jwt,
                    UUID.randomUUID(),
                    new ReviewDroneRegistrationApplicationRequest("reason"),
                    request)
                .data())
        .isSameAs(response);
  }

  private static HttpServletRequest request() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    when(request.getHeader("User-Agent")).thenReturn("JUnit");
    when(request.getHeader("X-Correlation-ID")).thenReturn("corr");
    return request;
  }
}
