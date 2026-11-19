package com.smartdroneinspection.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.shared.api.CustomerAirspaceEvaluation;
import com.smartdroneinspection.shared.api.CustomerAirspacePolicy;
import com.smartdroneinspection.shared.api.CustomerAirspaceStatus;
import com.smartdroneinspection.shared.api.CustomerRegistrationEvent;
import com.smartdroneinspection.shared.exception.AuthException;
import com.smartdroneinspection.users.api.dto.request.ApproveProspectiveCustomerRequest;
import com.smartdroneinspection.users.api.dto.request.RejectProspectiveCustomerRequest;
import com.smartdroneinspection.users.domain.ProspectiveCustomerRegistration;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.ProspectiveCustomerRegistrationRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import com.smartdroneinspection.users.service.ProspectiveCustomerReviewService;
import com.smartdroneinspection.users.service.SecurityAuditService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ProspectiveCustomerReviewServiceTest {

  private static final UUID ADMIN_ID = UUID.randomUUID();
  private static final UUID REGISTRATION_ID = UUID.randomUUID();
  private static final UUID PROSPECT_ID = UUID.randomUUID();

  @Mock ProspectiveCustomerRegistrationRepository registrations;
  @Mock UserRepository users;
  @Mock ApplicationEventPublisher events;
  @Mock CustomerAirspacePolicy airspace;
  @Mock SecurityAuditService audit;

  private ProspectiveCustomerReviewService service;

  @BeforeEach
  void setUp() {
    service = new ProspectiveCustomerReviewService(registrations, users, events, airspace, audit);
  }

  @Test
  void pendingReturnsOnlyPendingRegistrationsForAnAdmin() {
    when(users.findDetailedById(ADMIN_ID)).thenReturn(Optional.of(admin()));
    when(registrations.findAllByStatusOrderByCreatedAtAsc("PENDING"))
        .thenReturn(List.of(registration()));

    var response = service.pending(ADMIN_ID);

    assertThat(response).hasSize(1);
    assertThat(response.getFirst().status()).isEqualTo("PENDING");
  }

  @Test
  void pendingRejectsNonAdminAccounts() {
    User manager = new User("manager@example.test", "Manager", "hash", UserStatus.ACTIVE);
    manager.addRole(UserRole.MANAGER);
    when(users.findDetailedById(ADMIN_ID)).thenReturn(Optional.of(manager));

    assertAuthCode(() -> service.pending(ADMIN_ID), "ADMIN_REQUIRED");
  }

  @Test
  void pendingTreatsInactiveOrUnknownAdminAsNotFound() {
    when(users.findDetailedById(ADMIN_ID)).thenReturn(Optional.empty());

    assertAuthCode(() -> service.pending(ADMIN_ID), "REGISTRATION_NOT_FOUND");
  }

  @Test
  void approveActivatesProspectAndPublishesRegistrationEvent() {
    ProspectiveCustomerRegistration registration = registration();
    User prospect = prospect(UserStatus.DISABLED);
    when(users.findDetailedById(ADMIN_ID)).thenReturn(Optional.of(admin()));
    when(registrations.findByIdAndStatus(REGISTRATION_ID, "PENDING"))
        .thenReturn(Optional.of(registration));
    when(users.findDetailedById(PROSPECT_ID)).thenReturn(Optional.of(prospect));
    when(airspace.evaluateCustomerOnboarding(
            registration.getLatitude(), registration.getLongitude()))
        .thenReturn(new CustomerAirspaceEvaluation(CustomerAirspaceStatus.CLEAR_ZONE, "clear"));

    var response =
        service.approve(
            ADMIN_ID,
            REGISTRATION_ID,
            new ApproveProspectiveCustomerRequest("Checked"),
            "127.0.0.1",
            "JUnit",
            "correlation-1");

    assertThat(response.status()).isEqualTo("APPROVED");
    assertThat(prospect.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(prospect.roleValues()).containsExactly("CUSTOMER");
    verify(registrations).saveAndFlush(registration);
    ArgumentCaptor<CustomerRegistrationEvent> eventCaptor =
        ArgumentCaptor.forClass(CustomerRegistrationEvent.class);
    verify(events).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().assetCode()).isEqualTo("ASSET-1");
    verify(audit)
        .record(
            ADMIN_ID,
            null,
            "CUSTOMER_AIRSPACE_REVIEW_APPROVED",
            "SUCCESS",
            "127.0.0.1",
            "JUnit",
            "correlation-1");
  }

  @Test
  void approveRejectsNoFlyZoneRegistrations() {
    ProspectiveCustomerRegistration registration = registration();
    beanForApproval(registration, prospect(UserStatus.DISABLED));
    when(airspace.evaluateCustomerOnboarding(
            registration.getLatitude(), registration.getLongitude()))
        .thenReturn(new CustomerAirspaceEvaluation(CustomerAirspaceStatus.NO_FLY_ZONE, "no-fly"));

    assertAuthCode(
        () ->
            service.approve(
                ADMIN_ID,
                REGISTRATION_ID,
                new ApproveProspectiveCustomerRequest(null),
                null,
                null,
                null),
        "ASSET_IN_NO_FLY_ZONE");
  }

  @Test
  void approveRequiresReviewNoteForRestrictedAirspace() {
    ProspectiveCustomerRegistration registration = registration();
    beanForApproval(registration, prospect(UserStatus.DISABLED));
    when(airspace.evaluateCustomerOnboarding(
            registration.getLatitude(), registration.getLongitude()))
        .thenReturn(
            new CustomerAirspaceEvaluation(CustomerAirspaceStatus.RESTRICTED_ZONE, "restricted"));

    assertAuthCode(
        () ->
            service.approve(
                ADMIN_ID,
                REGISTRATION_ID,
                new ApproveProspectiveCustomerRequest("  "),
                null,
                null,
                null),
        "AIRSPACE_REVIEW_NOTE_REQUIRED");
  }

  @Test
  void approveAllowsRestrictedAirspaceWithReviewNote() {
    ProspectiveCustomerRegistration registration = registration();
    beanForApproval(registration, prospect(UserStatus.DISABLED));
    when(airspace.evaluateCustomerOnboarding(
            registration.getLatitude(), registration.getLongitude()))
        .thenReturn(
            new CustomerAirspaceEvaluation(CustomerAirspaceStatus.RESTRICTED_ZONE, "restricted"));

    var response =
        service.approve(
            ADMIN_ID,
            REGISTRATION_ID,
            new ApproveProspectiveCustomerRequest("Manual review completed"),
            null,
            null,
            null);

    assertThat(response.status()).isEqualTo("APPROVED");
    assertThat(response.reviewNote()).isEqualTo("Manual review completed");
  }

  @Test
  void approveRejectsDuplicateAssetCode() {
    ProspectiveCustomerRegistration registration = registration();
    beanForApproval(registration, prospect(UserStatus.DISABLED));
    when(airspace.evaluateCustomerOnboarding(
            registration.getLatitude(), registration.getLongitude()))
        .thenReturn(new CustomerAirspaceEvaluation(CustomerAirspaceStatus.CLEAR_ZONE, "clear"));
    when(registrations.existsByOrganizationCodeIgnoreCaseAndAssetCodeIgnoreCaseAndStatus(
            "ORG-1", "ASSET-1", "APPROVED"))
        .thenReturn(true);

    assertAuthCode(
        () ->
            service.approve(
                ADMIN_ID,
                REGISTRATION_ID,
                new ApproveProspectiveCustomerRequest(null),
                null,
                null,
                null),
        "ASSET_CODE_ALREADY_EXISTS");
  }

  @Test
  void approveRejectsProspectThatIsNotAwaitingReview() {
    ProspectiveCustomerRegistration registration = registration();
    when(users.findDetailedById(ADMIN_ID)).thenReturn(Optional.of(admin()));
    when(registrations.findByIdAndStatus(REGISTRATION_ID, "PENDING"))
        .thenReturn(Optional.of(registration));
    when(users.findDetailedById(PROSPECT_ID)).thenReturn(Optional.of(prospect(UserStatus.ACTIVE)));

    assertAuthCode(
        () ->
            service.approve(
                ADMIN_ID,
                REGISTRATION_ID,
                new ApproveProspectiveCustomerRequest(null),
                null,
                null,
                null),
        "INVALID_REVIEW_STATE");
  }

  @Test
  void approveRejectsMissingRegistration() {
    when(users.findDetailedById(ADMIN_ID)).thenReturn(Optional.of(admin()));
    when(registrations.findByIdAndStatus(REGISTRATION_ID, "PENDING")).thenReturn(Optional.empty());

    assertAuthCode(
        () ->
            service.approve(
                ADMIN_ID,
                REGISTRATION_ID,
                new ApproveProspectiveCustomerRequest(null),
                null,
                null,
                null),
        "REGISTRATION_NOT_FOUND");
  }

  @Test
  void rejectDisablesProspectAndRecordsReview() {
    ProspectiveCustomerRegistration registration = registration();
    User prospect = prospect(UserStatus.DISABLED);
    when(users.findDetailedById(ADMIN_ID)).thenReturn(Optional.of(admin()));
    when(registrations.findByIdAndStatus(REGISTRATION_ID, "PENDING"))
        .thenReturn(Optional.of(registration));
    when(users.findDetailedById(PROSPECT_ID)).thenReturn(Optional.of(prospect));

    var response =
        service.reject(
            ADMIN_ID,
            REGISTRATION_ID,
            new RejectProspectiveCustomerRequest("  Coordinates outside service area  "),
            "10.0.0.1",
            "JUnit",
            "correlation-2");

    assertThat(response.status()).isEqualTo("REJECTED");
    assertThat(response.reviewNote()).isEqualTo("Coordinates outside service area");
    assertThat(prospect.getStatus()).isEqualTo(UserStatus.DISABLED);
    verify(registrations).saveAndFlush(registration);
    verify(audit)
        .record(
            ADMIN_ID,
            PROSPECT_ID,
            "CUSTOMER_AIRSPACE_REVIEW_REJECTED",
            "SUCCESS",
            "10.0.0.1",
            "JUnit",
            "correlation-2");
  }

  private void beanForApproval(ProspectiveCustomerRegistration registration, User prospect) {
    when(users.findDetailedById(ADMIN_ID)).thenReturn(Optional.of(admin()));
    when(registrations.findByIdAndStatus(REGISTRATION_ID, "PENDING"))
        .thenReturn(Optional.of(registration));
    when(users.findDetailedById(PROSPECT_ID)).thenReturn(Optional.of(prospect));
  }

  private static User admin() {
    User admin = new User("admin@example.test", "Admin", "hash", UserStatus.ACTIVE);
    admin.addRole(UserRole.ADMIN);
    return admin;
  }

  private static User prospect(UserStatus status) {
    User prospect = new User("customer@example.test", "Customer", "hash", status);
    prospect.addRole(UserRole.CUSTOMER);
    return prospect;
  }

  private static ProspectiveCustomerRegistration registration() {
    return new ProspectiveCustomerRegistration(
        PROSPECT_ID,
        "Customer Org",
        "ORG-1",
        UUID.randomUUID(),
        "ASSET-1",
        "Bridge",
        "Bridge inspection",
        "Hanoi",
        new BigDecimal("21.027764"),
        new BigDecimal("105.834160"),
        "Owned",
        new BigDecimal("32.50"),
        new BigDecimal("1500.00"),
        "CLEAR_ZONE");
  }

  private static void assertAuthCode(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, String expectedCode) {
    assertThatThrownBy(callable)
        .isInstanceOfSatisfying(
            AuthException.class, exception -> assertThat(exception.code()).isEqualTo(expectedCode));
  }
}
