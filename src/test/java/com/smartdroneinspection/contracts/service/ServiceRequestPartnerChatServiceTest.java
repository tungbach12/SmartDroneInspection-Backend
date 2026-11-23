package com.smartdroneinspection.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.contracts.api.dto.response.PartnerChatThreadResponse;
import com.smartdroneinspection.contracts.domain.ServiceRequest;
import com.smartdroneinspection.contracts.domain.ServiceRequest.RequestType;
import com.smartdroneinspection.contracts.domain.ServiceRequest.ServiceRequestStatus;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence.OccurrenceStatus;
import com.smartdroneinspection.contracts.domain.ServiceRequestPartnerMessage;
import com.smartdroneinspection.contracts.repository.ServiceRequestOccurrenceRepository;
import com.smartdroneinspection.contracts.repository.ServiceRequestPartnerMessageAttachmentRepository;
import com.smartdroneinspection.contracts.repository.ServiceRequestPartnerMessageRepository;
import com.smartdroneinspection.contracts.repository.ServiceRequestRepository;
import com.smartdroneinspection.inspections.spi.Mf2InspectionAssignment;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import com.smartdroneinspection.users.UserAccess.ActiveUser;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class ServiceRequestPartnerChatServiceTest {

  private ServiceRequestRepository requests;
  private ServiceRequestOccurrenceRepository occurrences;
  private ServiceRequestPartnerMessageRepository messages;
  private ServiceRequestPartnerMessageAttachmentRepository attachments;
  private AssetRepository assets;
  private UserAccess users;
  private Mf2InspectionAssignment assignment;
  private ServiceRequestPartnerChatService service;

  private UUID customerId;
  private UUID managerId;
  private UUID inspectorId;
  private UUID otherInspectorId;
  private UUID otherManagerId;
  private UUID requestId;
  private UUID inspectionId;
  private ServiceRequest managedRequest;
  private ServiceRequestOccurrence assignedOccurrence;

  @BeforeEach
  void setUp() {
    requests = mock(ServiceRequestRepository.class);
    occurrences = mock(ServiceRequestOccurrenceRepository.class);
    messages = mock(ServiceRequestPartnerMessageRepository.class);
    attachments = mock(ServiceRequestPartnerMessageAttachmentRepository.class);
    assets = mock(AssetRepository.class);
    users = mock(UserAccess.class);
    assignment = mock(Mf2InspectionAssignment.class);

    service =
        new ServiceRequestPartnerChatService(
            requests,
            occurrences,
            messages,
            attachments,
            assets,
            users,
            new Mf2ActorPolicy(),
            assignment,
            Optional.empty());

    customerId = UUID.randomUUID();
    managerId = UUID.randomUUID();
    inspectorId = UUID.randomUUID();
    otherInspectorId = UUID.randomUUID();
    otherManagerId = UUID.randomUUID();
    requestId = UUID.randomUUID();
    inspectionId = UUID.randomUUID();
    UUID assetId = UUID.randomUUID();
    UUID droneId = UUID.randomUUID();

    managedRequest =
        new ServiceRequest(
            customerId,
            assetId,
            customerId,
            "Bridge inspection",
            LocalDate.now().plusDays(2),
            LocalDate.now().plusDays(2),
            "Asia/Ho_Chi_Minh",
            LocalTime.of(8, 0),
            LocalTime.of(12, 0),
            RequestType.ONE_TIME,
            null,
            null,
            null,
            null);
    ReflectionTestUtils.setField(managedRequest, "id", requestId);
    managedRequest.claimByManager(managerId, Instant.now());

    assignedOccurrence =
        new ServiceRequestOccurrence(
            requestId,
            1,
            Instant.now().plus(2, ChronoUnit.DAYS),
            Instant.now().plus(2, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS));
    assignedOccurrence.assign(inspectorId, droneId, inspectionId, managerId);

    Asset asset = mock(Asset.class);
    when(asset.getName()).thenReturn("Can Tho Bridge");
    when(asset.getCode()).thenReturn("CTB-01");
    when(assets.findById(assetId)).thenReturn(Optional.of(asset));

    when(users.findActiveUser(managerId))
        .thenReturn(Optional.of(user(managerId, Roles.MANAGER, "Tran Manager")));
    when(users.findUser(managerId))
        .thenReturn(Optional.of(user(managerId, Roles.MANAGER, "Tran Manager")));
    when(users.findActiveUser(inspectorId))
        .thenReturn(Optional.of(user(inspectorId, Roles.INSPECTOR, "Le Inspector")));
    when(users.findUser(inspectorId))
        .thenReturn(Optional.of(user(inspectorId, Roles.INSPECTOR, "Le Inspector")));
    when(users.findActiveUser(customerId))
        .thenReturn(Optional.of(user(customerId, Roles.CUSTOMER, "Saigon Customer")));
    when(users.findUser(customerId))
        .thenReturn(Optional.of(user(customerId, Roles.CUSTOMER, "Saigon Customer")));
    when(users.findActiveUser(otherInspectorId))
        .thenReturn(Optional.of(user(otherInspectorId, Roles.INSPECTOR, "Other Inspector")));
    when(users.findActiveUser(otherManagerId))
        .thenReturn(Optional.of(user(otherManagerId, Roles.MANAGER, "Other Manager")));

    when(requests.findById(requestId)).thenReturn(Optional.of(managedRequest));
    when(requests.findWithLockById(requestId)).thenReturn(Optional.of(managedRequest));
    when(requests.findByAssignedManagerIdAndStatusNotOrderByCreatedAtDesc(
            managerId, ServiceRequestStatus.CANCELLED))
        .thenReturn(List.of(managedRequest));
    when(requests.findAllById(any())).thenReturn(List.of(managedRequest));

    when(occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId))
        .thenReturn(List.of(assignedOccurrence));
    when(occurrences.findByInspectorIdAndStatusOrderByPlannedStartAtAsc(
            inspectorId, OccurrenceStatus.ASSIGNED))
        .thenReturn(List.of(assignedOccurrence));
    when(occurrences.findByRequestIdAndInspectorIdAndStatusOrderByOccurrenceIndexAsc(
            requestId, inspectorId, OccurrenceStatus.ASSIGNED))
        .thenReturn(List.of(assignedOccurrence));
    when(assignment.findAssignmentState(inspectionId))
        .thenReturn(
            Optional.of(
                new Mf2InspectionAssignment.AssignmentAcceptance(
                    "ACCEPTED", false, null, Instant.now())));
  }

  private static ActiveUser user(UUID id, String role, String fullName) {
    return new ActiveUser(
        id,
        Set.of(role),
        fullName,
        role.toLowerCase() + "@test.local",
        null,
        null,
        null,
        null,
        null,
        null);
  }

  @Test
  void assignedInspectorAndManagingManagerCanListThreadsAndExchangeMessages() {
    when(messages.saveAndFlush(any(ServiceRequestPartnerMessage.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(messages.findByRequestIdAndInspectorIdOrderByCreatedAtAscIdAsc(requestId, inspectorId))
        .thenReturn(
            List.of(
                new ServiceRequestPartnerMessage(
                    requestId,
                    inspectorId,
                    inspectorId,
                    "Permit uploaded, ready for flight",
                    false)));

    List<PartnerChatThreadResponse> inspectorThreads = service.listThreads(inspectorId);
    assertThat(inspectorThreads).hasSize(1);
    assertThat(inspectorThreads.getFirst().counterpartName()).isEqualTo("Tran Manager");
    assertThat(inspectorThreads.getFirst().customerName()).isEqualTo("Saigon Customer");
    assertThat(inspectorThreads.getFirst().assetName()).isEqualTo("Can Tho Bridge");

    List<PartnerChatThreadResponse> managerThreads = service.listThreads(managerId);
    assertThat(managerThreads).hasSize(1);
    assertThat(managerThreads.getFirst().counterpartName()).isEqualTo("Le Inspector");

    PartnerChatThreadResponse posted =
        service.postMessage(
            inspectorId, requestId, inspectorId, "Permit uploaded, ready for flight", List.of());
    assertThat(posted.messages()).hasSize(1);
    assertThat(posted.messages().getFirst().body()).isEqualTo("Permit uploaded, ready for flight");
  }

  @Test
  void customerUnassignedInspectorAndOtherManagerAreForbidden() {
    assertThatThrownBy(() -> service.listThreads(customerId))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).status())
        .isEqualTo(HttpStatus.FORBIDDEN);

    assertThatThrownBy(() -> service.getThread(otherInspectorId, requestId, inspectorId))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).status())
        .isEqualTo(HttpStatus.FORBIDDEN);

    assertThatThrownBy(() -> service.getThread(otherManagerId, requestId, inspectorId))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).status())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void declinedAssignmentExcludesInspectorFromPartnerChat() {
    when(assignment.findAssignmentState(inspectionId))
        .thenReturn(
            Optional.of(
                new Mf2InspectionAssignment.AssignmentAcceptance(
                    "DECLINED", true, "Weather conflict", Instant.now())));

    assertThat(service.listThreads(inspectorId)).isEmpty();
    assertThat(service.listThreads(managerId)).isEmpty();
    assertThatThrownBy(() -> service.getThread(inspectorId, requestId, inspectorId))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).status())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }
}
