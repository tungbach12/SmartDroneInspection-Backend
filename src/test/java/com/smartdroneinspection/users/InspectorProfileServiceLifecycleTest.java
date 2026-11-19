package com.smartdroneinspection.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.domain.InspectorFlightLog;
import com.smartdroneinspection.users.domain.InspectorProfile;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.InspectorFlightLogRepository;
import com.smartdroneinspection.users.repository.InspectorProfileRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import com.smartdroneinspection.users.service.InspectorPartnerLicenseDocumentService;
import com.smartdroneinspection.users.service.InspectorProfileService;
import com.smartdroneinspection.users.service.PilotLicenseClassService;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

class InspectorProfileServiceLifecycleTest {

  private static final UUID INSPECTOR_ID = UUID.randomUUID();
  private static final UUID ACTOR_ID = UUID.randomUUID();

  private UserRepository users;
  private InspectorFlightLogRepository flightLogs;
  private InspectorProfileRepository profiles;
  private InspectorPartnerLicenseDocumentService documents;
  private InspectorProfileService service;
  private User inspector;
  private InspectorProfile profile;

  @BeforeEach
  void setUp() {
    users = mock(UserRepository.class);
    flightLogs = mock(InspectorFlightLogRepository.class);
    profiles = mock(InspectorProfileRepository.class);
    documents = mock(InspectorPartnerLicenseDocumentService.class);
    service =
        new InspectorProfileService(
            users, flightLogs, profiles, documents, mock(PilotLicenseClassService.class));
    service.setEventPublisher(mock(ApplicationEventPublisher.class));

    inspector =
        new User("inspector@example.test", "Inspector", "0900", null, "hash", UserStatus.ACTIVE);
    inspector.addRole(UserRole.INSPECTOR);
    ReflectionTestUtils.setField(inspector, "id", INSPECTOR_ID);
    profile = new InspectorProfile(INSPECTOR_ID);
    when(users.findById(INSPECTOR_ID)).thenReturn(Optional.of(inspector));
    when(profiles.findById(INSPECTOR_ID)).thenReturn(Optional.of(profile));
    when(profiles.save(any(InspectorProfile.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void listsInspectorsAndReadsProfile() {
    when(users.findAllInspectors()).thenReturn(List.of(inspector));

    assertThat(service.listInspectors()).hasSize(1);
    assertThat(service.getProfile(INSPECTOR_ID).email()).isEqualTo("inspector@example.test");
  }

  @Test
  void uploadsLicenseScanAndMovesRejectedOnboardingBackToPending() {
    profile.setOnboardingStatus("REJECTED");
    when(documents.upload(any()))
        .thenReturn(
            new InspectorPartnerLicenseDocumentService.UploadResponse(
                "inspector-partner-applications/licenses/new.pdf"));

    var response =
        service.uploadLicenseScan(
            INSPECTOR_ID,
            new MockMultipartFile("file", "license.pdf", "application/pdf", new byte[] {1}),
            ACTOR_ID);

    assertThat(response.pilotLicenseFileKey())
        .isEqualTo("inspector-partner-applications/licenses/new.pdf");
    assertThat(profile.getOnboardingStatus()).isEqualTo("PENDING_REVIEW");
    verify(profiles).save(profile);
  }

  @Test
  void uploadsAndRecordsSignedPartnerContract() {
    when(documents.uploadContract(any()))
        .thenReturn(
            new InspectorPartnerLicenseDocumentService.ContractUploadResponse(
                "inspector-partner-applications/contracts/c.pdf", "sha-1"));

    var response =
        service.uploadPartnerContract(
            INSPECTOR_ID,
            new MockMultipartFile("file", "c.pdf", "application/pdf", new byte[] {1}),
            ACTOR_ID);

    assertThat(response.partnerContractFileKey())
        .isEqualTo("inspector-partner-applications/contracts/c.pdf");
    assertThat(response.partnerContractSha256()).isEqualTo("sha-1");
    assertThat(profile.getOnboardingStatus()).isEqualTo("APPROVED");
  }

  @Test
  void opensPartnerContractAndGeneratesDraftPdf() {
    profile.recordSignedPartnerContract("inspector-partner-applications/contracts/c.pdf", "sha");
    when(documents.openContract("inspector-partner-applications/contracts/c.pdf"))
        .thenReturn(
            new InspectorPartnerLicenseDocumentService.InputStreamContent(
                "application/pdf", new ByteArrayInputStream(new byte[] {1})));
    when(documents.renderDraftPartnerContractPdf(inspector, profile)).thenReturn(new byte[] {2});

    assertThat(service.openPartnerContract(INSPECTOR_ID).contentType())
        .isEqualTo("application/pdf");
    assertThat(service.generateDraftPartnerContractPdf(INSPECTOR_ID)).containsExactly(2);
  }

  @Test
  void partnerContractIsRequiredBeforeOpening() {
    assertBusinessCode(() -> service.openPartnerContract(INSPECTOR_ID), "NOT_FOUND");
  }

  @Test
  void approvesOnboardingOnlyWithBothScans() {
    assertBusinessCode(
        () -> service.approveOnboarding(INSPECTOR_ID, ACTOR_ID), "ONBOARDING_DOCUMENTS_INCOMPLETE");

    profile.updatePilotLicense(
        "LIC-1",
        "CLASS_B",
        LocalDate.of(2025, 1, 1),
        Instant.now().plusSeconds(86400),
        "CAA",
        "licenses/pilot.pdf");
    profile.recordSignedPartnerContract("contracts/c.pdf", "sha");
    var response = service.approveOnboarding(INSPECTOR_ID, ACTOR_ID);

    assertThat(response.onboardingStatus()).isEqualTo("APPROVED");
  }

  @Test
  void rejectsOnboardingAndOpensLicenseScan() throws Exception {
    profile.setPilotLicenseFileKey("licenses/pilot.pdf");
    when(documents.open("licenses/pilot.pdf"))
        .thenReturn(
            new InspectorPartnerLicenseDocumentService.InputStreamContent(
                "application/pdf", new ByteArrayInputStream(new byte[] {3})));

    assertThat(service.openLicenseScan(INSPECTOR_ID).stream().readAllBytes()).containsExactly(3);

    var response = service.rejectOnboarding(INSPECTOR_ID, " Blurry scan ", ACTOR_ID);
    assertThat(response.onboardingStatus()).isEqualTo("REJECTED");
    assertThat(response.rejectionReason()).isEqualTo("Blurry scan");
  }

  @Test
  void licenseScanIsRequiredBeforeOpening() {
    assertBusinessCode(() -> service.openLicenseScan(INSPECTOR_ID), "NOT_FOUND");
  }

  @Test
  void listsAndDeletesOwnFlightLogs() {
    InspectorFlightLog log = mock(InspectorFlightLog.class);
    when(log.getId()).thenReturn(UUID.randomUUID());
    when(log.getInspectorUserId()).thenReturn(INSPECTOR_ID);
    when(log.getFlightCode()).thenReturn("FL-1");
    when(log.getFlightDate()).thenReturn(LocalDate.of(2027, 1, 1));
    when(log.getMissionTitle()).thenReturn("Bridge");
    when(log.getDroneModel()).thenReturn("M350");
    when(log.getFlightDurationMinutes()).thenReturn(30);
    when(log.getInspectionType()).thenReturn("INITIAL");
    when(flightLogs.findByInspectorUserIdOrderByFlightDateDesc(INSPECTOR_ID))
        .thenReturn(List.of(log));
    when(flightLogs.findById(log.getId())).thenReturn(Optional.of(log));

    assertThat(service.listFlightLogs(INSPECTOR_ID)).hasSize(1);
    service.deleteFlightLog(INSPECTOR_ID, log.getId(), ACTOR_ID);
    verify(flightLogs).delete(log);
  }

  @Test
  void deleteFlightLogRejectsMissingOrForeignLog() {
    UUID logId = UUID.randomUUID();
    when(flightLogs.findById(logId)).thenReturn(Optional.empty());
    assertBusinessCode(() -> service.deleteFlightLog(INSPECTOR_ID, logId, ACTOR_ID), "NOT_FOUND");

    InspectorFlightLog foreign = mock(InspectorFlightLog.class);
    when(foreign.getInspectorUserId()).thenReturn(UUID.randomUUID());
    when(flightLogs.findById(logId)).thenReturn(Optional.of(foreign));
    assertBusinessCode(
        () -> service.deleteFlightLog(INSPECTOR_ID, logId, ACTOR_ID), "INVALID_FLIGHT_LOG");
  }

  private static void assertBusinessCode(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, String code) {
    assertThatThrownBy(callable)
        .isInstanceOfSatisfying(
            BusinessException.class, exception -> assertThat(exception.code()).isEqualTo(code));
  }
}
