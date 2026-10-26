package com.smartdroneinspection.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.subscriptions.SubscriptionEntitlement;
import com.smartdroneinspection.users.UserAccess;
import com.smartdroneinspection.workforce.api.dto.request.ProvisionWorkforceUserRequest;
import com.smartdroneinspection.workforce.api.dto.request.UpdateWorkforceRolesRequest;
import com.smartdroneinspection.workforce.api.dto.request.UpdateWorkforceStatusRequest;
import com.smartdroneinspection.workforce.service.WorkforceService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkforceServiceTest {

  @Mock UserAccess users;
  @Mock SubscriptionEntitlement entitlement;

  @InjectMocks WorkforceService service;

  private UUID actorId;
  private UUID organizationId;
  private UUID subjectId;
  private UserAccess.WorkforceMember subject;

  @BeforeEach
  void setUp() {
    actorId = UUID.randomUUID();
    organizationId = UUID.randomUUID();
    subjectId = UUID.randomUUID();
    subject =
        new UserAccess.WorkforceMember(
            subjectId,
            "engineer@example.test",
            "Engineer",
            Set.of("INSPECTOR"),
            "ACTIVE",
            organizationId);
    when(users.findActiveUser(actorId))
        .thenReturn(
            Optional.of(new UserAccess.ActiveUser(actorId, Set.of("ORG_MANAGER"), organizationId)));
  }

  @Test
  void provisionsMemberInActorOrganizationAndChecksEntitlementWithActorScope() {
    when(users.provisionWorkforceUser(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new UserAccess.ProvisionedWorkforceMember(subject, "Temp-Password-1"));

    var response =
        service.provision(
            actorId,
            new ProvisionWorkforceUserRequest(
                "engineer@example.test", "Engineer", Set.of("INSPECTOR")),
            "127.0.0.1",
            "JUnit",
            "test-correlation");

    verify(entitlement).requireActive(eq(organizationId), any());
    verify(users)
        .provisionWorkforceUser(
            eq(actorId),
            eq(organizationId),
            eq("engineer@example.test"),
            eq("Engineer"),
            eq(Set.of("INSPECTOR")),
            eq("127.0.0.1"),
            eq("JUnit"),
            eq("test-correlation"));
    assertThat(response.user().userId()).isEqualTo(subjectId);
    assertThat(response.user().organizationId()).isEqualTo(organizationId);
    assertThat(response.temporaryPassword()).isEqualTo("Temp-Password-1");
  }

  @Test
  void rejectsPrivilegedRoleWideningBeforeAnyLifecycleWrite() {
    assertThatThrownBy(
            () ->
                service.provision(
                    actorId,
                    new ProvisionWorkforceUserRequest(
                        "escalated@example.test", "Escalated", Set.of("ADMIN")),
                    "127.0.0.1",
                    "JUnit",
                    "cid"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ADMIN");

    verify(users, never())
        .provisionWorkforceUser(any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void rejectsOrganizationAdministratorRoleOnRoleUpdateBeforeLifecycleWrite() {
    assertForbiddenRoleUpdate("ORG_MANAGER");
  }

  @Test
  void rejectsPlatformAdministratorRoleOnRoleUpdateBeforeLifecycleWrite() {
    assertForbiddenRoleUpdate("ADMIN");
  }

  private void assertForbiddenRoleUpdate(String role) {
    assertThatThrownBy(
            () ->
                service.updateRoles(
                    actorId,
                    subjectId,
                    new UpdateWorkforceRolesRequest(Set.of(role)),
                    "127.0.0.1",
                    "JUnit",
                    "cid"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(role);

    verify(users, never()).updateWorkforceRoles(any(), any(), any(), any(), any(), any());
  }

  @Test
  void resolvesActiveActorOnlyOnceWhenUpdatingRolesWithinOrganization() {
    when(users.findWorkforceMember(eq(subjectId), eq(organizationId)))
        .thenReturn(Optional.of(subject));
    when(users.updateWorkforceRoles(any(), any(), any(), any(), any(), any())).thenReturn(subject);

    service.updateRoles(
        actorId,
        subjectId,
        new UpdateWorkforceRolesRequest(Set.of("MAINTENANCE_ENGINEER")),
        "127.0.0.1",
        "JUnit",
        "cid");

    verify(users).findActiveUser(actorId);
  }

  @Test
  void roleUpdateDoesNotWriteWhenTargetIsPrivileged() {
    when(users.findWorkforceMember(eq(subjectId), eq(organizationId)))
        .thenReturn(
            Optional.of(
                new UserAccess.WorkforceMember(
                    subjectId,
                    subject.email(),
                    subject.fullName(),
                    Set.of("ORG_MANAGER"),
                    subject.status(),
                    organizationId)));

    assertThatThrownBy(
            () ->
                service.updateRoles(
                    actorId,
                    subjectId,
                    new UpdateWorkforceRolesRequest(Set.of("INSPECTOR")),
                    "127.0.0.1",
                    "JUnit",
                    "cid"))
        .isInstanceOf(BusinessException.class)
        .hasMessage("User was not found.");

    verify(users, never()).updateWorkforceRoles(any(), any(), any(), any(), any(), any());
  }

  @Test
  void rejectsRoleUpdateWhenCurrentSubjectHasMultipleRolesIncludingPrivilegedRole() {
    when(users.findWorkforceMember(eq(subjectId), eq(organizationId)))
        .thenReturn(
            Optional.of(
                new UserAccess.WorkforceMember(
                    subjectId,
                    subject.email(),
                    subject.fullName(),
                    Set.of("INSPECTOR", "ORG_MANAGER"),
                    subject.status(),
                    organizationId)));

    assertThatThrownBy(
            () ->
                service.updateRoles(
                    actorId,
                    subjectId,
                    new UpdateWorkforceRolesRequest(Set.of("MAINTENANCE_ENGINEER")),
                    "127.0.0.1",
                    "JUnit",
                    "cid"))
        .isInstanceOf(BusinessException.class)
        .hasMessage("User was not found.");

    verify(users, never()).updateWorkforceRoles(any(), any(), any(), any(), any(), any());
  }

  @Test
  void statusChangeIsAttributedToActorAndDelegatesToUsersModule() {
    when(users.findWorkforceMember(eq(subjectId), eq(organizationId)))
        .thenReturn(Optional.of(subject));
    when(users.updateWorkforceStatus(any(), any(), any(), any(), any(), any()))
        .thenReturn(
            new UserAccess.WorkforceMember(
                subjectId,
                subject.email(),
                subject.fullName(),
                subject.roles(),
                "SUSPENDED",
                organizationId));

    var response =
        service.updateStatus(
            actorId,
            subjectId,
            new UpdateWorkforceStatusRequest("SUSPENDED"),
            "127.0.0.1",
            "JUnit",
            "cid");

    verify(entitlement).requireActive(eq(organizationId), any());
    verify(users)
        .updateWorkforceStatus(
            eq(actorId), eq(subjectId), eq("SUSPENDED"), eq("127.0.0.1"), eq("JUnit"), eq("cid"));
    assertThat(response.status()).isEqualTo("SUSPENDED");
  }

  @Test
  void roleChangeDelegatesSessionRevokingLifecycleUpdate() {
    when(users.findWorkforceMember(eq(subjectId), eq(organizationId)))
        .thenReturn(Optional.of(subject));
    when(users.updateWorkforceRoles(any(), any(), any(), any(), any(), any()))
        .thenReturn(
            new UserAccess.WorkforceMember(
                subjectId,
                subject.email(),
                subject.fullName(),
                Set.of("MAINTENANCE_ENGINEER"),
                "ACTIVE",
                organizationId));

    var response =
        service.updateRoles(
            actorId,
            subjectId,
            new UpdateWorkforceRolesRequest(Set.of("MAINTENANCE_ENGINEER")),
            "127.0.0.1",
            "JUnit",
            "cid");

    verify(entitlement).requireActive(eq(organizationId), any());
    verify(users)
        .updateWorkforceRoles(
            eq(actorId),
            eq(subjectId),
            eq(Set.of("MAINTENANCE_ENGINEER")),
            eq("127.0.0.1"),
            eq("JUnit"),
            eq("cid"));
    assertThat(response.roles()).containsExactly("MAINTENANCE_ENGINEER");
  }

  @Test
  void listIsRestrictedToActorOrganization() {
    when(users.listWorkforceMembers(eq(organizationId))).thenReturn(List.of(subject));

    var members = service.list(actorId);

    // Tenant scope is enforced by the organization-scoped users query, so the service must pass the
    // actor's own organization rather than querying unfiltered.
    verify(users).listWorkforceMembers(eq(organizationId));
    assertThat(members).extracting(m -> m.email()).containsExactly("engineer@example.test");
    assertThat(members).extracting(m -> m.organizationId()).containsOnly(organizationId);
  }

  @Test
  void privilegedSameOrganizationAccountIsNotDisclosed() {
    UUID otherAdminId = UUID.randomUUID();
    when(users.findWorkforceMember(eq(otherAdminId), eq(organizationId)))
        .thenReturn(
            Optional.of(
                new UserAccess.WorkforceMember(
                    otherAdminId,
                    "peer-admin@example.test",
                    "Peer Admin",
                    Set.of("ORG_MANAGER"),
                    "ACTIVE",
                    organizationId)));

    assertThatThrownBy(
            () ->
                service.updateStatus(
                    actorId,
                    otherAdminId,
                    new UpdateWorkforceStatusRequest("SUSPENDED"),
                    "127.0.0.1",
                    "JUnit",
                    "cid"))
        .isInstanceOf(BusinessException.class)
        .hasMessage("User was not found.");

    verify(users, never()).updateWorkforceStatus(any(), any(), any(), any(), any(), any());
  }

  @Test
  void foreignOrganizationUserIsNotDisclosed() {
    UUID foreignUserId = UUID.randomUUID();
    when(users.findWorkforceMember(eq(foreignUserId), eq(organizationId)))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.updateStatus(
                    actorId,
                    foreignUserId,
                    new UpdateWorkforceStatusRequest("SUSPENDED"),
                    "127.0.0.1",
                    "JUnit",
                    "cid"))
        .isInstanceOf(BusinessException.class)
        .hasMessage("User was not found.");

    verify(users, never()).updateWorkforceStatus(any(), any(), any(), any(), any(), any());
  }

  @Test
  void suspendedOrganizationAdministratorCannotProvision() {
    when(users.findActiveUser(actorId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.provision(
                    actorId,
                    new ProvisionWorkforceUserRequest(
                        "blocked@example.test", "Blocked", Set.of("INSPECTOR")),
                    "127.0.0.1",
                    "JUnit",
                    "cid"))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            failure ->
                assertThat(((BusinessException) failure).code())
                    .isEqualTo("WORKFORCE_ACCESS_DENIED"));

    verify(users, never())
        .provisionWorkforceUser(any(), any(), any(), any(), any(), any(), any(), any());
  }
}
