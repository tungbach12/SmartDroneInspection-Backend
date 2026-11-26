package com.smartdroneinspection.users.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.shared.exception.AuthException;
import com.smartdroneinspection.users.api.dto.request.CreateInspectorFlightLogRequest;
import com.smartdroneinspection.users.api.dto.request.RejectInspectorOnboardingRequest;
import com.smartdroneinspection.users.api.dto.request.UpdateInspectorProfileRequest;
import com.smartdroneinspection.users.api.dto.response.InspectorFlightLogResponse;
import com.smartdroneinspection.users.api.dto.response.InspectorProfileResponse;
import com.smartdroneinspection.users.service.InspectorPartnerLicenseDocumentService;
import com.smartdroneinspection.users.service.InspectorProfileService;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;

class InspectorProfileControllerTest {

  private static final UUID INSPECTOR_ID = UUID.randomUUID();

  private InspectorProfileService service;
  private InspectorProfileController controller;
  private Jwt adminJwt;
  private Jwt managerJwt;
  private Jwt inspectorJwt;

  @BeforeEach
  void setUp() {
    service = mock(InspectorProfileService.class);
    controller = new InspectorProfileController(service);
    adminJwt = jwt(UUID.randomUUID(), List.of("ADMIN"));
    managerJwt = jwt(UUID.randomUUID(), List.of("MANAGER"));
    inspectorJwt = jwt(INSPECTOR_ID, List.of("INSPECTOR"));
  }

  @Test
  void listsAndReadsProfiles() {
    InspectorProfileResponse profile = mock(InspectorProfileResponse.class);
    when(service.listInspectors()).thenReturn(List.of(profile));
    when(service.getProfile(INSPECTOR_ID)).thenReturn(profile);
    when(service.getProfile(UUID.fromString(inspectorJwt.getSubject()))).thenReturn(profile);

    assertThat(controller.listInspectors().data()).hasSize(1);
    assertThat(controller.getProfile(adminJwt, INSPECTOR_ID).data()).isSameAs(profile);
    assertThat(controller.getMyProfile(inspectorJwt).data()).isSameAs(profile);
  }

  @Test
  void updatesProfileAndUploadsScansAndContracts() {
    UpdateInspectorProfileRequest request = mock(UpdateInspectorProfileRequest.class);
    InspectorProfileResponse profile = mock(InspectorProfileResponse.class);
    MockMultipartFile file =
        new MockMultipartFile("file", "scan.pdf", "application/pdf", new byte[] {1});
    when(service.updateProfile(eq(INSPECTOR_ID), eq(request), any())).thenReturn(profile);
    when(service.uploadLicenseScan(eq(INSPECTOR_ID), eq(file), any())).thenReturn(profile);
    when(service.uploadPartnerContract(eq(INSPECTOR_ID), eq(file), any())).thenReturn(profile);

    assertThat(controller.updateProfile(managerJwt, INSPECTOR_ID, request).data())
        .isSameAs(profile);
    assertThat(controller.uploadLicenseScan(managerJwt, INSPECTOR_ID, file).data())
        .isSameAs(profile);
    assertThat(controller.uploadPartnerContract(managerJwt, INSPECTOR_ID, file).data())
        .isSameAs(profile);
    assertThatThrownBy(() -> controller.updateProfile(adminJwt, INSPECTOR_ID, request))
        .isInstanceOfSatisfying(
            AuthException.class, error -> assertThat(error.code()).isEqualTo("FORBIDDEN"));
    assertThatThrownBy(() -> controller.uploadLicenseScan(adminJwt, INSPECTOR_ID, file))
        .isInstanceOfSatisfying(
            AuthException.class, error -> assertThat(error.code()).isEqualTo("FORBIDDEN"));
    assertThatThrownBy(() -> controller.uploadPartnerContract(adminJwt, INSPECTOR_ID, file))
        .isInstanceOfSatisfying(
            AuthException.class, error -> assertThat(error.code()).isEqualTo("FORBIDDEN"));
  }

  @Test
  void streamsLicenseScanAndPartnerContract() {
    var content =
        new InspectorPartnerLicenseDocumentService.InputStreamContent(
            "application/pdf", new ByteArrayInputStream(new byte[] {1}));
    when(service.openLicenseScan(INSPECTOR_ID)).thenReturn(content);
    when(service.openPartnerContract(INSPECTOR_ID)).thenReturn(content);

    var license = controller.viewLicenseScan(adminJwt, INSPECTOR_ID);
    var contract = controller.viewPartnerContract(adminJwt, INSPECTOR_ID);

    assertThat(license.getHeaders().getCacheControl()).isEqualTo("no-store");
    assertThat(license.getBody()).isNotNull();
    assertThat(contract.getBody()).isNotNull();
  }

  @Test
  void downloadsDraftContractAndReviewsOnboarding() {
    UUID inspectorId = UUID.randomUUID();
    when(service.generateDraftPartnerContractPdf(inspectorId)).thenReturn(new byte[] {1, 2});
    InspectorProfileResponse profile = mock(InspectorProfileResponse.class);
    when(service.approveOnboarding(eq(inspectorId), any())).thenReturn(profile);
    when(service.rejectOnboarding(eq(inspectorId), eq("reason"), any())).thenReturn(profile);

    var pdf = controller.downloadDraftPartnerContractPdf(adminJwt, inspectorId);
    var approved = controller.approveOnboarding(managerJwt, inspectorId);
    var rejected =
        controller.rejectOnboarding(
            managerJwt, inspectorId, new RejectInspectorOnboardingRequest("reason"));

    assertThat(pdf.getBody()).containsExactly(1, 2);
    assertThat(pdf.getHeaders().getContentDisposition().getFilename())
        .contains(inspectorId.toString().substring(0, 8));
    assertThat(approved.data()).isSameAs(profile);
    assertThat(rejected.data()).isSameAs(profile);
  }

  @Test
  void managesFlightLogs() {
    InspectorFlightLogResponse log = mock(InspectorFlightLogResponse.class);
    CreateInspectorFlightLogRequest request = mock(CreateInspectorFlightLogRequest.class);
    when(service.listFlightLogs(INSPECTOR_ID)).thenReturn(List.of(log));
    when(service.recordFlightLog(eq(INSPECTOR_ID), eq(request), any())).thenReturn(log);

    assertThat(controller.listFlightLogs(adminJwt, INSPECTOR_ID).data()).hasSize(1);
    assertThat(controller.getMyFlightLogs(inspectorJwt).data()).hasSize(1);
    assertThat(controller.recordFlightLog(managerJwt, INSPECTOR_ID, request).data()).isSameAs(log);

    controller.deleteFlightLog(managerJwt, INSPECTOR_ID, UUID.randomUUID());
    verify(service).deleteFlightLog(eq(INSPECTOR_ID), any(), any());
  }

  @Test
  void inspectorCanOnlyReadOwnProfile() {
    InspectorProfileResponse profile = mock(InspectorProfileResponse.class);
    when(service.getProfile(INSPECTOR_ID)).thenReturn(profile);

    assertThat(controller.getProfile(inspectorJwt, INSPECTOR_ID).data()).isSameAs(profile);
    assertThatThrownBy(() -> controller.getProfile(inspectorJwt, UUID.randomUUID()))
        .isInstanceOfSatisfying(
            AuthException.class, error -> assertThat(error.code()).isEqualTo("FORBIDDEN"));
  }

  private static Jwt jwt(UUID subject, List<String> roles) {
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn(subject.toString());
    when(jwt.getClaimAsStringList("roles")).thenReturn(roles);
    return jwt;
  }
}
