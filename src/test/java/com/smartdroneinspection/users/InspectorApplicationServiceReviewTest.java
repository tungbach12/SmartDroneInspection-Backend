package com.smartdroneinspection.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.domain.InspectorApplication;
import com.smartdroneinspection.users.domain.InspectorProfile;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.InspectorApplicationRepository;
import com.smartdroneinspection.users.repository.InspectorProfileRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import com.smartdroneinspection.users.service.InspectorApplicationService;
import com.smartdroneinspection.users.service.InspectorPartnerLicenseDocumentService;
import com.smartdroneinspection.users.service.InspectorProfileService;
import com.smartdroneinspection.users.service.LoginOtpProperties;
import com.smartdroneinspection.users.service.PasswordPolicy;
import com.smartdroneinspection.users.service.PilotLicenseClassService;
import com.smartdroneinspection.users.service.RegistrationOtpService;
import com.smartdroneinspection.users.service.SecurityAuditService;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class InspectorApplicationServiceReviewTest {

  private static final UUID APPLICATION_ID = UUID.randomUUID();
  private static final UUID MANAGER_ID = UUID.randomUUID();
  private static final String EMAIL = "pilot@example.test";

  @Mock InspectorApplicationRepository applications;
  @Mock UserRepository users;
  @Mock InspectorProfileRepository profiles;
  @Mock PilotLicenseClassService licenseClasses;
  @Mock RegistrationOtpService otp;
  @Mock SecurityAuditService audit;
  @Mock InspectorPartnerLicenseDocumentService documents;
  @Mock ObjectProvider<EvidenceObjectStore> objectStore;
  @Mock ApplicationEventPublisher events;
  @Mock ObjectProvider<JavaMailSender> mailSender;
  @Mock LoginOtpProperties mailProperties;
  @Mock PasswordEncoder passwords;
  @Mock PasswordPolicy passwordPolicy;
  @Mock InspectorProfileService profileService;

  private InspectorApplicationService service;

  @BeforeEach
  void setUp() {
    service =
        new InspectorApplicationService(
            applications,
            users,
            profiles,
            licenseClasses,
            otp,
            audit,
            documents,
            objectStore,
            events,
            mailSender,
            mailProperties,
            passwords,
            passwordPolicy,
            profileService);
  }

  @Test
  void listsPendingAndReadsApplicationById() {
    InspectorApplication application = application();
    when(applications.findAllByStatusOrderByCreatedAtAsc("PENDING_REVIEW"))
        .thenReturn(List.of(application));
    when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application));

    var pending = service.listPending();
    var single = service.get(APPLICATION_ID);

    assertThat(pending).hasSize(1);
    assertThat(pending.getFirst().email()).isEqualTo(EMAIL);
    assertThat(single.status()).isEqualTo("PENDING_REVIEW");
    assertThat(single.hasLicenseScan()).isTrue();
    assertThat(single.hasSignedContract()).isFalse();
  }

  @Test
  void listsApprovedApplicationsThatStillNeedPartnerContract() {
    InspectorApplication missingContract = application();
    missingContract.approve(MANAGER_ID, UUID.randomUUID());
    InspectorApplication withContract = application();
    UUID inspectorWithContract = UUID.randomUUID();
    withContract.approve(MANAGER_ID, inspectorWithContract);
    when(applications.findAllByStatusOrderByCreatedAtAsc("APPROVED"))
        .thenReturn(List.of(missingContract, withContract));

    InspectorProfile approvedProfile = new InspectorProfile(UUID.randomUUID());
    InspectorProfile contractedProfile = new InspectorProfile(inspectorWithContract);
    contractedProfile.recordSignedPartnerContract("contracts/c.pdf", "sha");
    when(profiles.findById(missingContract.getApprovedUserId())).thenReturn(Optional.empty());
    when(profiles.findById(inspectorWithContract)).thenReturn(Optional.of(contractedProfile));

    var result = service.listApprovedAwaitingContract();

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().id()).isEqualTo(APPLICATION_ID);
  }

  @Test
  void approveCreatesInspectorAccountAndProfile() {
    InspectorApplication application = application();
    application.attachSignedContract("contracts/contract.pdf", "sha-1");
    when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
    when(users.findByNormalizedEmail(EMAIL)).thenReturn(Optional.empty());
    when(mailSender.getIfAvailable()).thenReturn(mock(JavaMailSender.class));
    when(mailProperties.getEmailFrom()).thenReturn("noreply@example.test");
    when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    var response = service.approve(APPLICATION_ID, MANAGER_ID, null, null, "correlation-1");

    assertThat(response.email()).isEqualTo(EMAIL);
    assertThat(response.roles()).contains("INSPECTOR");
    assertThat(application.getStatus()).isEqualTo("APPROVED");
    assertThat(application.getReviewedByUserId()).isEqualTo(MANAGER_ID);
    verify(profiles).saveAndFlush(any(InspectorProfile.class));
    verify(applications).saveAndFlush(application);
    verify(audit)
        .record(
            eq(MANAGER_ID),
            eq(null),
            eq("INSPECTOR_APPLICATION_APPROVED"),
            eq("APPROVED"),
            eq(null),
            eq(null),
            eq("correlation-1"));
  }

  @Test
  void approveRejectsNonPendingApplication() {
    InspectorApplication application = application();
    application.reject(MANAGER_ID, "Documents incomplete");
    when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application));

    assertBusinessCode(
        () -> service.approve(APPLICATION_ID, MANAGER_ID, null, null, null),
        "APPLICATION_NOT_PENDING");
  }

  @Test
  void approveRequiresSignedContract() {
    InspectorApplication application = application();
    when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application));

    assertBusinessCode(
        () -> service.approve(APPLICATION_ID, MANAGER_ID, null, null, null),
        "SIGNED_CONTRACT_REQUIRED");
  }

  @Test
  void approveRejectsExistingAccountOrMissingMailTransport() {
    InspectorApplication application = application();
    application.attachSignedContract("contracts/contract.pdf", "sha-1");
    when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
    when(users.findByNormalizedEmail(EMAIL))
        .thenReturn(Optional.of(new User(EMAIL, "Pilot", "hash", UserStatus.ACTIVE)));
    assertBusinessCode(
        () -> service.approve(APPLICATION_ID, MANAGER_ID, null, null, null),
        "EMAIL_ALREADY_EXISTS");

    when(users.findByNormalizedEmail(EMAIL)).thenReturn(Optional.empty());
    when(mailSender.getIfAvailable()).thenReturn(null);
    assertBusinessCode(
        () -> service.approve(APPLICATION_ID, MANAGER_ID, null, null, null),
        "APPLICATION_EMAIL_UNAVAILABLE");
  }

  @Test
  void rejectSendsEmailAndPurgesRejectedApplication() throws Exception {
    InspectorApplication application = application();
    when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
    when(mailSender.getIfAvailable()).thenReturn(mock(JavaMailSender.class));
    when(mailProperties.getEmailFrom()).thenReturn("noreply@example.test");

    service.reject(APPLICATION_ID, MANAGER_ID, " Documents incomplete ", null, null, null);

    assertThat(application.getStatus()).isEqualTo("REJECTED");
    assertThat(application.getNotificationState()).isEqualTo("SENT");
    assertThat(application.getRejectionReason()).isEqualTo("Documents incomplete");
    verify(documents)
        .deleteApplicationLicenseScan("inspector-partner-applications/pending/scan.pdf");
    verify(applications).delete(application);
    verify(applications).flush();
  }

  @Test
  void rejectFailsWhenEmailTransportIsUnavailable() {
    InspectorApplication application = application();
    when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
    when(mailSender.getIfAvailable()).thenReturn(null);

    assertBusinessCode(
        () -> service.reject(APPLICATION_ID, MANAGER_ID, "reason", null, null, null),
        "APPLICATION_EMAIL_UNAVAILABLE");
  }

  @Test
  void rejectReportsMailFailureAndKeepsApplication() {
    InspectorApplication application = application();
    when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
    JavaMailSender sender = mock(JavaMailSender.class);
    when(mailSender.getIfAvailable()).thenReturn(sender);
    when(mailProperties.getEmailFrom()).thenReturn("noreply@example.test");
    doThrow(new MailSendException("smtp down"))
        .when(sender)
        .send(any(org.springframework.mail.SimpleMailMessage.class));

    assertBusinessCode(
        () -> service.reject(APPLICATION_ID, MANAGER_ID, "reason", null, null, null),
        "APPLICATION_EMAIL_FAILED");
    assertThat(application.getStatus()).isEqualTo("REJECTED");
  }

  @Test
  void opensLicenseScanAndSignedContract() {
    InspectorApplication application = application();
    application.attachSignedContract("inspector-partner-applications/contracts/c.pdf", "sha");
    when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
    when(documents.openApplicationLicenseScan("inspector-partner-applications/pending/scan.pdf"))
        .thenReturn(
            new InspectorPartnerLicenseDocumentService.InputStreamContent(
                "application/pdf", new java.io.ByteArrayInputStream(new byte[] {1})));
    when(documents.openContract("inspector-partner-applications/contracts/c.pdf"))
        .thenReturn(
            new InspectorPartnerLicenseDocumentService.InputStreamContent(
                "application/pdf", new java.io.ByteArrayInputStream(new byte[] {2})));

    assertThat(service.openLicenseScan(APPLICATION_ID).contentType()).isEqualTo("application/pdf");
    assertThat(service.openSignedContract(APPLICATION_ID).contentType())
        .isEqualTo("application/pdf");
  }

  @Test
  void openSignedContractRejectsMissingContract() {
    InspectorApplication application = application();
    when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application));

    assertBusinessCode(
        () -> service.openSignedContract(APPLICATION_ID), "SIGNED_CONTRACT_NOT_FOUND");
  }

  @Test
  void uploadsSignedContractForPendingAndApprovedApplications() {
    InspectorApplication application = application();
    when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
    when(documents.uploadContract(any()))
        .thenReturn(
            new InspectorPartnerLicenseDocumentService.ContractUploadResponse(
                "inspector-partner-applications/contracts/c.pdf", "sha-1"));

    var response =
        service.uploadSignedContract(
            APPLICATION_ID,
            new MockMultipartFile("file", "c.pdf", "application/pdf", new byte[] {1}),
            MANAGER_ID);

    assertThat(response.hasSignedContract()).isTrue();
    assertThat(application.getSignedContractFileKey())
        .isEqualTo("inspector-partner-applications/contracts/c.pdf");

    UUID approvedUserId = UUID.randomUUID();
    application.approve(MANAGER_ID, approvedUserId);
    service.uploadSignedContract(
        APPLICATION_ID,
        new MockMultipartFile("file", "c.pdf", "application/pdf", new byte[] {1}),
        MANAGER_ID);
    verify(profileService)
        .recordSignedPartnerContract(
            approvedUserId, "inspector-partner-applications/contracts/c.pdf", "sha-1", MANAGER_ID);
  }

  @Test
  void uploadSignedContractRejectsInvalidState() {
    InspectorApplication application = application();
    application.reject(MANAGER_ID, "reason");
    when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application));

    assertBusinessCode(
        () ->
            service.uploadSignedContract(
                APPLICATION_ID,
                new MockMultipartFile("file", "c.pdf", "application/pdf", new byte[] {1}),
                MANAGER_ID),
        "APPLICATION_INVALID_STATE");
  }

  @Test
  void retriesRejectionCleanupOnlyAfterEmailWasSent() {
    InspectorApplication application = application();
    application.reject(MANAGER_ID, "reason");
    when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application));

    assertBusinessCode(
        () -> service.retryRejectionCleanup(APPLICATION_ID), "REJECTION_CLEANUP_NOT_READY");

    application.markNotificationSent();
    service.retryRejectionCleanup(APPLICATION_ID);

    verify(applications).delete(application);
  }

  @Test
  void cleanupFailureIsReportedAndRetriedLater() throws Exception {
    InspectorApplication application = application();
    application.reject(MANAGER_ID, "reason");
    application.markNotificationSent();
    when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
    doThrow(new IOException("minio down"))
        .when(documents)
        .deleteApplicationLicenseScan(anyString());

    assertBusinessCode(
        () -> service.retryRejectionCleanup(APPLICATION_ID), "APPLICATION_FILE_CLEANUP_FAILED");
    assertThat(application.getCleanupAttempts()).isEqualTo(1);
  }

  @Test
  void uploadsPendingLicenseScanAndRejectsMissingApplication() {
    when(documents.uploadApplicationLicenseScan(any())).thenReturn("pending/scan.pdf");

    String key =
        service.uploadApplicationLicenseScan(
            new MockMultipartFile("file", "scan.pdf", "application/pdf", new byte[] {1}));

    assertThat(key).isEqualTo("pending/scan.pdf");
    when(applications.findById(APPLICATION_ID)).thenReturn(Optional.empty());
    assertBusinessCode(() -> service.get(APPLICATION_ID), "APPLICATION_NOT_FOUND");
  }

  private static InspectorApplication application() {
    InspectorApplication application =
        new InspectorApplication(
            EMAIL,
            EMAIL,
            "Pilot",
            "0900",
            "123456789",
            "Hanoi",
            "LIC-1",
            "Class A",
            Instant.now().plusSeconds(3600),
            "inspector-partner-applications/pending/scan.pdf",
            "trace-1",
            "password-hash");
    org.springframework.test.util.ReflectionTestUtils.setField(application, "id", APPLICATION_ID);
    return application;
  }

  private static void assertBusinessCode(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, String code) {
    assertThatThrownBy(callable)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> {
              assertThat(exception.code()).isEqualTo(code);
              assertThat(exception.status())
                  .isIn(
                      HttpStatus.BAD_REQUEST,
                      HttpStatus.CONFLICT,
                      HttpStatus.NOT_FOUND,
                      HttpStatus.SERVICE_UNAVAILABLE);
            });
  }
}
