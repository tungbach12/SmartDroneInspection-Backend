package com.smartdroneinspection.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.shared.exception.AuthException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.api.dto.request.InspectorApplicationSubmissionRequest;
import com.smartdroneinspection.users.domain.InspectorApplication;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.events.InspectorApplicationSubmittedEvent;
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
import java.time.Instant;
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
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class InspectorApplicationServiceTest {

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
  void submittingApplicationDoesNotCreateAnInspectorAccount() {
    String email = "pilot@example.test";
    UUID id = UUID.randomUUID();
    InspectorApplication application =
        new InspectorApplication(
            email,
            email,
            "Pilot",
            "0900",
            "123456789",
            "Hanoi",
            "LIC-1",
            "Class A",
            Instant.now().plusSeconds(3600),
            "license-scan.pdf",
            "trace-1",
            "password-hash");
    when(users.findByNormalizedEmail(email)).thenReturn(Optional.empty());
    when(applications.existsByNormalizedEmail(email)).thenReturn(false);
    when(licenseClasses.requireActiveName("Class A")).thenReturn("Class A");
    when(documents.openApplicationLicenseScan("license-scan.pdf"))
        .thenReturn(
            new InspectorPartnerLicenseDocumentService.InputStreamContent(
                "application/pdf", new java.io.ByteArrayInputStream(new byte[] {1, 2, 3})));
    when(applications.saveAndFlush(any(InspectorApplication.class))).thenReturn(application);
    when(mailProperties.getEmailFrom()).thenReturn("noreply@example.test");
    when(mailSender.getIfAvailable()).thenReturn(mock(JavaMailSender.class));
    when(passwords.encode("CorrectHorse9!")).thenReturn("password-hash");

    var response =
        service.submit(
            new InspectorApplicationSubmissionRequest(
                email,
                "Pilot",
                "0900",
                "123456789",
                "Hanoi",
                "LIC-1",
                "Class A",
                Instant.now().plusSeconds(3600),
                "license-scan.pdf",
                "CorrectHorse9!",
                "challenge",
                "123456"),
            null,
            null,
            "trace-1");

    assertThat(response.email()).isEqualTo(email);
    verify(events).publishEvent(new InspectorApplicationSubmittedEvent(application.getId()));
    verify(users).findByNormalizedEmail(email);
    org.mockito.Mockito.verifyNoMoreInteractions(users);
  }

  @Test
  void duplicateActiveUserEmailIsRejectedBeforeApplicationCreation() {
    String email = "pilot@example.test";
    User existing = new User(email, "Pilot", "hash", UserStatus.ACTIVE, ActorZone.INSPECTOR);
    when(users.findByNormalizedEmail(email)).thenReturn(Optional.of(existing));

    assertThatThrownBy(
            () ->
                service.submit(
                    new InspectorApplicationSubmissionRequest(
                        email,
                        "Pilot",
                        null,
                        "123456789",
                        "Hanoi",
                        "LIC-1",
                        "Class A",
                        null,
                        "license-scan.pdf",
                        "CorrectHorse9!",
                        "challenge",
                        "123456"),
                    null,
                    null,
                    null))
        .isInstanceOf(AuthException.class)
        .satisfies(
            error -> assertThat(((AuthException) error).status()).isEqualTo(HttpStatus.CONFLICT));
  }
}
