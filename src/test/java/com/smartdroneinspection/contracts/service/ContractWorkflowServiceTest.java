package com.smartdroneinspection.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.enums.AssetStatus;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.contracts.api.dto.request.CreateServiceRequest;
import com.smartdroneinspection.contracts.api.dto.request.IssueServiceQuoteRequest;
import com.smartdroneinspection.contracts.domain.ServiceRequest;
import com.smartdroneinspection.contracts.domain.ServiceRequest.RequestType;
import com.smartdroneinspection.contracts.domain.ServiceRequestQuote;
import com.smartdroneinspection.contracts.repository.ServiceRequestMessageRepository;
import com.smartdroneinspection.contracts.repository.ServiceRequestOccurrenceRepository;
import com.smartdroneinspection.contracts.repository.ServiceRequestQuoteRepository;
import com.smartdroneinspection.contracts.repository.ServiceRequestRepository;
import com.smartdroneinspection.inspections.spi.Mf2InspectionAssignment;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.UserAccess;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ContractWorkflowServiceTest {

  private ServiceRequestRepository requests;
  private ServiceRequestMessageRepository messages;
  private ServiceRequestQuoteRepository quotes;
  private ServiceRequestOccurrenceRepository occurrences;
  private AssetRepository assets;
  private UserAccess users;
  private Mf2ActorPolicy actorPolicy;
  private PartnerRegistryService partners;
  private Mf2InspectionAssignment assignment;
  private EvidenceObjectStore objectStore;
  private ContractWorkflowService service;

  private final UUID customerId = UUID.randomUUID();
  private final UUID managerId = UUID.randomUUID();
  private final UUID orgId = UUID.randomUUID();
  private final UUID assetId = UUID.randomUUID();
  private final UUID inspectorId = UUID.randomUUID();
  private final UUID droneId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    requests = mock(ServiceRequestRepository.class);
    messages = mock(ServiceRequestMessageRepository.class);
    quotes = mock(ServiceRequestQuoteRepository.class);
    occurrences = mock(ServiceRequestOccurrenceRepository.class);
    assets = mock(AssetRepository.class);
    users = mock(UserAccess.class);
    actorPolicy = new Mf2ActorPolicy();
    partners = mock(PartnerRegistryService.class);
    assignment = mock(Mf2InspectionAssignment.class);
    objectStore = mock(EvidenceObjectStore.class);

    service =
        new ContractWorkflowService(
            requests,
            messages,
            quotes,
            occurrences,
            assets,
            users,
            actorPolicy,
            partners,
            assignment,
            Optional.of(objectStore));

    when(users.findActiveUser(customerId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(customerId, Set.of(Roles.CUSTOMER), orgId, "Customer")));
    when(users.findActiveUser(managerId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(managerId, Set.of(Roles.MANAGER), null, "Manager")));
  }

  @Test
  void customerCreatesValidRequestWithDefaultDailyWindow() {
    Asset asset = mock(Asset.class);
    when(asset.getId()).thenReturn(assetId);
    when(asset.getStatus()).thenReturn(AssetStatus.ACTIVE);
    when(assets.findByIdAndOrganizationId(assetId, orgId)).thenReturn(Optional.of(asset));
    when(requests.saveAndFlush(any(ServiceRequest.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    CreateServiceRequest input =
        new CreateServiceRequest(
            assetId,
            "Bridge inspection",
            LocalDate.of(2027, 2, 1),
            LocalDate.of(2027, 2, 10),
            "Asia/Ho_Chi_Minh",
            null,
            null,
            RequestType.ONE_TIME,
            null,
            null,
            null,
            null);

    var response = service.create(customerId, input);

    ArgumentCaptor<ServiceRequest> captor = ArgumentCaptor.forClass(ServiceRequest.class);
    verify(requests).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getWindowStart()).isEqualTo(LocalTime.of(8, 0));
    assertThat(captor.getValue().getWindowEnd()).isEqualTo(LocalTime.of(17, 0));
    assertThat(response.description()).isEqualTo("Bridge inspection");
  }

  @Test
  void managerAppendsNewQuoteVersionWithoutMutatingPrevious() {
    ServiceRequest request =
        new ServiceRequest(
            orgId,
            assetId,
            customerId,
            "Bridge inspection",
            LocalDate.of(2027, 2, 1),
            LocalDate.of(2027, 2, 10),
            "UTC",
            LocalTime.of(8, 0),
            LocalTime.of(17, 0),
            RequestType.ONE_TIME,
            null,
            null,
            null,
            null);

    when(requests.findWithLockById(any())).thenReturn(Optional.of(request));
    when(partners.isEligibleInspector(any(), any())).thenReturn(true);
    when(partners.isEligibleDrone(any())).thenReturn(true);
    when(quotes.findFirstByRequestIdOrderByVersionNoDesc(any())).thenReturn(Optional.empty());
    when(quotes.findByRequestIdOrderByVersionNoDesc(any())).thenReturn(List.of());
    when(occurrences.findByRequestIdOrderByOccurrenceIndexAsc(any())).thenReturn(List.of());
    when(messages.findByRequestIdOrderByCreatedAtAscIdAsc(any())).thenReturn(List.of());

    Instant start = Instant.parse("2027-02-01T09:00:00Z");
    Instant end = Instant.parse("2027-02-01T11:00:00Z");

    IssueServiceQuoteRequest quoteInput =
        new IssueServiceQuoteRequest(
            "Visual inspection scope", start, end, inspectorId, droneId, new BigDecimal("150.00"));

    service.issueQuote(managerId, UUID.randomUUID(), quoteInput);

    ArgumentCaptor<ServiceRequestQuote> captor = ArgumentCaptor.forClass(ServiceRequestQuote.class);
    verify(quotes).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getVersionNo()).isEqualTo(1);
    assertThat(captor.getValue().getPricePerOccurrenceUsd()).isEqualByComparingTo("150.00");
  }

  @Test
  void customerCanAcceptOnlyTheLatestQuoteVersion() {
    UUID reqId = UUID.randomUUID();
    ServiceRequest request =
        new ServiceRequest(
            orgId,
            assetId,
            customerId,
            "Bridge inspection",
            LocalDate.of(2027, 2, 1),
            LocalDate.of(2027, 2, 10),
            "UTC",
            LocalTime.of(8, 0),
            LocalTime.of(17, 0),
            RequestType.ONE_TIME,
            null,
            null,
            null,
            null);
    request.markQuoted();

    when(requests.findWithLockByIdAndOrganizationId(reqId, orgId)).thenReturn(Optional.of(request));

    ServiceRequestQuote latestQuote = mock(ServiceRequestQuote.class);
    when(latestQuote.getVersionNo()).thenReturn(2);
    when(quotes.findFirstByRequestIdOrderByVersionNoDesc(reqId))
        .thenReturn(Optional.of(latestQuote));

    assertThatThrownBy(() -> service.acceptQuote(customerId, reqId, 1))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Only the latest quote version can be accepted");
  }
}
