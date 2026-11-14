package com.smartdroneinspection.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.shared.api.CustomerAirspaceEvaluation;
import com.smartdroneinspection.shared.api.CustomerAirspacePolicy;
import com.smartdroneinspection.shared.api.CustomerAirspaceStatus;
import com.smartdroneinspection.shared.api.CustomerRegistrationEvent;
import com.smartdroneinspection.shared.exception.AuthException;
import com.smartdroneinspection.users.api.dto.request.FirstAssetRegistrationRequest;
import com.smartdroneinspection.users.api.dto.request.OrganizationRegistrationRequest;
import com.smartdroneinspection.users.domain.ProspectiveCustomerRegistration;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.LoginOtpChannel;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.OtpChallengeRepository;
import com.smartdroneinspection.users.repository.ProspectiveCustomerRegistrationRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import com.smartdroneinspection.users.service.LoginOtpDelivery;
import com.smartdroneinspection.users.service.LoginOtpProperties;
import com.smartdroneinspection.users.service.OrganizationRegistrationService;
import com.smartdroneinspection.users.service.PasswordPolicy;
import com.smartdroneinspection.users.service.RegistrationOtpService;
import com.smartdroneinspection.users.service.SecurityAuditService;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class OrganizationRegistrationServiceTest {

  private UserRepository users;
  private ProspectiveCustomerRegistrationRepository prospectiveRegistrations;
  private ApplicationEventPublisher events;
  private CustomerAirspacePolicy airspace;
  private PasswordEncoder passwords;
  private SecurityAuditService audit;
  private RegistrationOtpService registrationOtp;
  private OrganizationRegistrationService service;

  private final UUID categoryId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    users = mock(UserRepository.class);
    prospectiveRegistrations = mock(ProspectiveCustomerRegistrationRepository.class);
    events = mock(ApplicationEventPublisher.class);
    airspace = mock(CustomerAirspacePolicy.class);
    passwords = mock(PasswordEncoder.class);
    audit = mock(SecurityAuditService.class);
    registrationOtp = mock(RegistrationOtpService.class);

    when(passwords.encode(any())).thenReturn("hashed-password");
    when(registrationOtp.isReady()).thenReturn(false);

    service =
        new OrganizationRegistrationService(
            users,
            prospectiveRegistrations,
            events,
            airspace,
            passwords,
            new PasswordPolicy(),
            audit,
            registrationOtp);
  }

  @Test
  void rejectedProspectCanReRegisterIntoPendingReviewWithCorrectedCoordinates() {
    String email = "prospect@company.test";
    UUID userId = UUID.randomUUID();
    UUID regId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();

    User rejectedUser =
        new User(
            email, "Old Name", null, null, "old-hash", UserStatus.DISABLED, ActorZone.CUSTOMER);
    rejectedUser.addRole(UserRole.CUSTOMER);
    ReflectionTestUtils.setField(rejectedUser, "id", userId);

    ProspectiveCustomerRegistration rejectedReg =
        new ProspectiveCustomerRegistration(
            userId,
            "Old Org",
            "OLD_ORG",
            categoryId,
            "OLD-01",
            "Old Asset",
            null,
            "Old Location",
            BigDecimal.valueOf(10.90),
            BigDecimal.valueOf(106.70),
            null,
            null,
            null,
            "RESTRICTED_ZONE");
    rejectedReg.reject(adminId, "Wrong coordinates");
    ReflectionTestUtils.setField(rejectedReg, "id", regId);

    when(users.findByNormalizedEmail(email)).thenReturn(Optional.of(rejectedUser));
    when(prospectiveRegistrations.findByUserId(userId)).thenReturn(Optional.of(rejectedReg));
    when(airspace.evaluateCustomerOnboarding(BigDecimal.valueOf(10.95), BigDecimal.valueOf(106.75)))
        .thenReturn(
            new CustomerAirspaceEvaluation(CustomerAirspaceStatus.RESTRICTED_ZONE, "Needs review"));
    when(prospectiveRegistrations.existsByOrganizationCodeIgnoreCaseAndStatus("NEW_ORG", "PENDING"))
        .thenReturn(false);
    when(users.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    when(prospectiveRegistrations.saveAndFlush(any(ProspectiveCustomerRegistration.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    OrganizationRegistrationRequest request =
        new OrganizationRegistrationRequest(
            email,
            "Corrected Name",
            "New Org",
            "NEW_ORG",
            "ValidPassw0rd!2026",
            new FirstAssetRegistrationRequest(
                "NEW-01",
                "Corrected Bridge",
                "Desc",
                categoryId,
                "District 2",
                BigDecimal.valueOf(10.95),
                BigDecimal.valueOf(106.75),
                null,
                null,
                null));

    var response = service.register(request, "127.0.0.1", "agent", "corr-1");

    assertThat(response.registrationStatus()).isEqualTo("PENDING_AIRSPACE_REVIEW");
    assertThat(rejectedReg.getStatus()).isEqualTo("PENDING");
    assertThat(rejectedReg.getReviewNote()).isNull();
    assertThat(rejectedReg.getOrganizationCode()).isEqualTo("NEW_ORG");
    assertThat(rejectedUser.getFullName()).isEqualTo("Corrected Name");
    verify(audit)
        .record(
            eq(null),
            eq(userId),
            eq("CUSTOMER_AIRSPACE_REVIEW_RESUBMITTED"),
            eq("SUCCESS"),
            any(),
            any(),
            any());
  }

  @Test
  void rejectedProspectCanReRegisterIntoClearZoneAndBecomeActiveCustomer() {
    String email = "clear@company.test";
    UUID userId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();

    User rejectedUser =
        new User(
            email, "Old Name", null, null, "old-hash", UserStatus.DISABLED, ActorZone.CUSTOMER);
    rejectedUser.addRole(UserRole.CUSTOMER);
    ReflectionTestUtils.setField(rejectedUser, "id", userId);

    ProspectiveCustomerRegistration rejectedReg =
        new ProspectiveCustomerRegistration(
            userId,
            "Old Org",
            "OLD_ORG",
            categoryId,
            "OLD-01",
            "Old Asset",
            null,
            "Old Location",
            BigDecimal.valueOf(10.90),
            BigDecimal.valueOf(106.70),
            null,
            null,
            null,
            "RESTRICTED_ZONE");
    rejectedReg.reject(adminId, "Please choose clear zone");

    when(users.findByNormalizedEmail(email)).thenReturn(Optional.of(rejectedUser));
    when(prospectiveRegistrations.findByUserId(userId)).thenReturn(Optional.of(rejectedReg));
    when(airspace.evaluateCustomerOnboarding(BigDecimal.valueOf(10.55), BigDecimal.valueOf(106.85)))
        .thenReturn(new CustomerAirspaceEvaluation(CustomerAirspaceStatus.CLEAR_ZONE, "Clear"));
    when(prospectiveRegistrations.existsByOrganizationCodeIgnoreCaseAndStatus(
            "CLEAR_ORG", "PENDING"))
        .thenReturn(false);
    when(users.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    OrganizationRegistrationRequest request =
        new OrganizationRegistrationRequest(
            email,
            "Clear Admin",
            "Clear Infrastructure",
            "CLEAR_ORG",
            "ValidPassw0rd!2026",
            new FirstAssetRegistrationRequest(
                "AST-01",
                "Clear Bridge",
                null,
                categoryId,
                "Zone A",
                BigDecimal.valueOf(10.55),
                BigDecimal.valueOf(106.85),
                null,
                null,
                null));

    var response = service.register(request, "127.0.0.1", "agent", "corr-2");

    assertThat(response.registrationStatus()).isEqualTo("ACTIVE");
    assertThat(rejectedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(rejectedUser.getActorZone()).isEqualTo(ActorZone.CUSTOMER);
    verify(events).publishEvent(any(CustomerRegistrationEvent.class));
  }

  @Test
  void pendingProspectAndActiveUserStillRejectDuplicateEmail() {
    String pendingEmail = "pending@company.test";
    UUID pendingUserId = UUID.randomUUID();
    User pendingUser =
        new User(
            pendingEmail,
            "Pending Name",
            null,
            null,
            "hash",
            UserStatus.DISABLED,
            ActorZone.CUSTOMER);
    ReflectionTestUtils.setField(pendingUser, "id", pendingUserId);
    ProspectiveCustomerRegistration pendingReg =
        new ProspectiveCustomerRegistration(
            pendingUserId,
            "Pend Org",
            "PEND_ORG",
            categoryId,
            "P-01",
            "Asset",
            null,
            "Loc",
            BigDecimal.valueOf(10.90),
            BigDecimal.valueOf(106.70),
            null,
            null,
            null,
            "RESTRICTED_ZONE");

    when(users.findByNormalizedEmail(pendingEmail)).thenReturn(Optional.of(pendingUser));
    when(prospectiveRegistrations.findByUserId(pendingUserId)).thenReturn(Optional.of(pendingReg));
    when(airspace.evaluateCustomerOnboarding(any(), any()))
        .thenReturn(new CustomerAirspaceEvaluation(CustomerAirspaceStatus.CLEAR_ZONE, "Clear"));

    OrganizationRegistrationRequest req =
        new OrganizationRegistrationRequest(
            pendingEmail,
            "Name",
            "Org",
            "ORG_CODE",
            "ValidPassw0rd!2026",
            new FirstAssetRegistrationRequest(
                "A-1",
                "Bridge",
                null,
                categoryId,
                "Loc",
                BigDecimal.valueOf(10.55),
                BigDecimal.valueOf(106.85),
                null,
                null,
                null));

    assertThatThrownBy(() -> service.register(req, "127.0.0.1", "agent", "corr-3"))
        .isInstanceOf(AuthException.class)
        .hasMessageContaining("Email is already in use");
  }

  @Test
  void registrationOtpAllowsRejectedProspectEmailAndBlocksPending() {
    OtpChallengeRepository challenges = mock(OtpChallengeRepository.class);
    LoginOtpProperties props = new LoginOtpProperties();
    props.setEnabled(true);
    props.setSmtpHost("smtp.test");
    props.setEmailFrom("noreply@test.local");
    props.setChallengePepper("test-pepper");
    LoginOtpDelivery delivery = mock(LoginOtpDelivery.class);

    RegistrationOtpService otpService =
        new RegistrationOtpService(
            challenges, users, Optional.of(prospectiveRegistrations), props, delivery);

    String rejectedEmail = "rejected@company.test";
    UUID rejectedId = UUID.randomUUID();
    User rejectedUser =
        new User(
            rejectedEmail, "Rejected", null, null, "hash", UserStatus.DISABLED, ActorZone.CUSTOMER);
    ReflectionTestUtils.setField(rejectedUser, "id", rejectedId);
    ProspectiveCustomerRegistration rejectedReg =
        new ProspectiveCustomerRegistration(
            rejectedId,
            "Org",
            "ORG",
            categoryId,
            "A-1",
            "Asset",
            null,
            "Loc",
            BigDecimal.valueOf(10.9),
            BigDecimal.valueOf(106.7),
            null,
            null,
            null,
            "RESTRICTED_ZONE");
    rejectedReg.reject(UUID.randomUUID(), "Rejected");

    when(users.findByNormalizedEmail(rejectedEmail)).thenReturn(Optional.of(rejectedUser));
    when(prospectiveRegistrations.findByUserId(rejectedId)).thenReturn(Optional.of(rejectedReg));
    when(challenges.countByPurposeAndDestinationAndCreatedAtAfter(any(), any(), any()))
        .thenReturn(0L);
    when(challenges.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var otpResult = otpService.send(rejectedEmail, LoginOtpChannel.EMAIL, "127.0.0.1");
    assertThat(otpResult.challengeToken()).isNotBlank();

    String activeEmail = "active@company.test";
    User activeUser =
        new User(activeEmail, "Active", null, null, "hash", UserStatus.ACTIVE, ActorZone.CUSTOMER);
    when(users.findByNormalizedEmail(activeEmail)).thenReturn(Optional.of(activeUser));

    assertThatThrownBy(() -> otpService.send(activeEmail, LoginOtpChannel.EMAIL, "127.0.0.1"))
        .isInstanceOf(AuthException.class)
        .hasMessageContaining("Email is already registered");
  }
}
