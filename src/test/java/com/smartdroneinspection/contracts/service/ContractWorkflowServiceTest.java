package com.smartdroneinspection.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.enums.AssetStatus;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.contracts.api.dto.request.AcceptServiceQuoteRequest;
import com.smartdroneinspection.contracts.api.dto.request.AssignOccurrenceRequest;
import com.smartdroneinspection.contracts.api.dto.request.ConfirmOccurrencePaymentRequest;
import com.smartdroneinspection.contracts.api.dto.request.CreateServiceRequest;
import com.smartdroneinspection.contracts.api.dto.request.IssueServiceQuoteRequest;
import com.smartdroneinspection.contracts.api.dto.request.RecordOccurrencePayoutRequest;
import com.smartdroneinspection.contracts.domain.InspectionFinanceSettings;
import com.smartdroneinspection.contracts.domain.ServiceRequest;
import com.smartdroneinspection.contracts.domain.ServiceRequest.RequestType;
import com.smartdroneinspection.contracts.domain.ServiceRequest.ServiceRequestStatus;
import com.smartdroneinspection.contracts.domain.ServiceRequestMessage;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence;
import com.smartdroneinspection.contracts.domain.ServiceRequestQuote;
import com.smartdroneinspection.contracts.repository.ServiceRequestMessageRepository;
import com.smartdroneinspection.contracts.repository.ServiceRequestOccurrenceRepository;
import com.smartdroneinspection.contracts.repository.ServiceRequestQuoteRepository;
import com.smartdroneinspection.contracts.repository.ServiceRequestRepository;
import com.smartdroneinspection.inspections.spi.InspectionReportAccess;
import com.smartdroneinspection.inspections.spi.Mf2InspectionAssignment;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.UserAccess;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

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
  private InspectionFinanceSettingsService financeSettingsService;
  private InspectionReportAccess reportAccess;
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
    financeSettingsService = mock(InspectionFinanceSettingsService.class);
    reportAccess = mock(InspectionReportAccess.class);

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
            Optional.of(objectStore),
            financeSettingsService,
            Optional.of(reportAccess));

    when(users.findActiveUser(customerId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(customerId, Set.of(Roles.CUSTOMER), orgId, "Customer")));
    when(users.findActiveUser(managerId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(managerId, Set.of(Roles.MANAGER), null, "Manager")));
    when(users.findActiveUser(inspectorId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(
                    inspectorId,
                    Set.of(Roles.INSPECTOR),
                    null,
                    "INSPECTOR_PARTNER",
                    "Inspector",
                    "inspector@example.test",
                    "LIC-TEST-001",
                    "CLASS_A",
                    Instant.parse("2030-01-01T00:00:00Z"),
                    "licenses/test.pdf",
                    null)));
    Asset quoteAsset = mock(Asset.class);
    when(quoteAsset.getStatus()).thenReturn(AssetStatus.ACTIVE);
    when(quoteAsset.getId()).thenReturn(assetId);
    when(assets.findByIdAndOrganizationId(assetId, orgId)).thenReturn(Optional.of(quoteAsset));
    Drone lockedDrone = mock(Drone.class);
    when(lockedDrone.getId()).thenReturn(droneId);
    when(lockedDrone.getInspectorOwnerId()).thenReturn(inspectorId);
    when(lockedDrone.getSerialNumber()).thenReturn("SDI-TEST-001");
    when(lockedDrone.getModel()).thenReturn("Test Drone");
    when(lockedDrone.isEligibleForAssignment()).thenReturn(true);
    when(lockedDrone.getRegistrationExpiresAt()).thenReturn(Instant.parse("2030-01-01T00:00:00Z"));
    when(lockedDrone.getRegistrationNumber()).thenReturn("REG-TEST-001");
    when(lockedDrone.getFeatures()).thenReturn(List.of());
    when(lockedDrone.resolveMtowKg()).thenReturn(1.0);
    when(lockedDrone.requiresClassBLicense()).thenReturn(false);
    when(partners.lockEligibleDrone(eq(droneId), eq(inspectorId), any(Instant.class)))
        .thenReturn(lockedDrone);
    when(partners.evaluateCandidate(
            eq(managerId),
            eq(inspectorId),
            eq(droneId),
            any(Instant.class),
            any(Instant.class),
            any(Asset.class),
            any(),
            any(boolean.class),
            any(boolean.class)))
        .thenReturn(
            new PartnerCandidateEvaluation.Result(
                inspectorId,
                droneId,
                "Inspector",
                null,
                "SDI-TEST-001",
                "Test Drone",
                false,
                List.of(),
                PartnerCandidateEvaluation.Status.PASS,
                PartnerCandidateEvaluation.Status.MATCH,
                null,
                null,
                null,
                List.of(),
                List.of(),
                "Advisory"));
    when(partners.isEligibleInspector(eq(inspectorId), any(Instant.class))).thenReturn(true);
    when(partners.isEligibleDrone(droneId, inspectorId)).thenReturn(true);
    when(partners.isEligibleInspectorForAllDates(eq(inspectorId), any())).thenReturn(true);
    when(partners.isEligibleDroneForAllDates(eq(droneId), eq(inspectorId), any())).thenReturn(true);
    when(quotes.findWithLockByRequestId(any())).thenReturn(Optional.empty());
  }

  @Test
  void customerCreatesValidOneTimeRequestWithOneRequestedDate() {
    Asset asset = mock(Asset.class);
    when(asset.getId()).thenReturn(assetId);
    when(asset.getName()).thenReturn("Bridge 1");
    when(asset.getStatus()).thenReturn(AssetStatus.ACTIVE);
    when(assets.findByIdAndOrganizationId(assetId, orgId)).thenReturn(Optional.of(asset));
    when(requests.saveAndFlush(any(ServiceRequest.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    CreateServiceRequest input =
        new CreateServiceRequest(
            assetId,
            null,
            LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(20),
            null,
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
    assertThat(response.description()).contains("Bridge 1", "Inspection");
  }

  @Test
  void rejectsOneTimeRequestLessThanTwentyDaysAhead() {
    Asset asset = mock(Asset.class);
    when(asset.getId()).thenReturn(assetId);
    when(asset.getName()).thenReturn("Bridge 1");
    when(asset.getStatus()).thenReturn(AssetStatus.ACTIVE);
    when(assets.findByIdAndOrganizationId(assetId, orgId)).thenReturn(Optional.of(asset));
    CreateServiceRequest input =
        new CreateServiceRequest(
            assetId,
            null,
            LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(19),
            null,
            "Asia/Ho_Chi_Minh",
            null,
            null,
            RequestType.ONE_TIME,
            null,
            null,
            null,
            null);

    assertThatThrownBy(() -> service.create(customerId, input))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("at least 20 days");
    verify(requests, never()).saveAndFlush(any(ServiceRequest.class));
  }

  @Test
  void recurringCountRequestDerivesEndDateFromMonthInterval() {
    Asset asset = mock(Asset.class);
    when(asset.getId()).thenReturn(assetId);
    when(asset.getName()).thenReturn("Bridge 1");
    when(asset.getStatus()).thenReturn(AssetStatus.ACTIVE);
    when(assets.findByIdAndOrganizationId(assetId, orgId)).thenReturn(Optional.of(asset));
    when(requests.saveAndFlush(any(ServiceRequest.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    LocalDate start = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(20);
    CreateServiceRequest input =
        new CreateServiceRequest(
            assetId,
            null,
            start,
            null,
            "Asia/Ho_Chi_Minh",
            null,
            null,
            RequestType.RECURRING,
            "MONTH",
            6,
            4,
            null);

    var response = service.create(customerId, input);

    ArgumentCaptor<ServiceRequest> captor = ArgumentCaptor.forClass(ServiceRequest.class);
    verify(requests).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getRequestedUntil()).isEqualTo(start.plusMonths(18));
    assertThat(response.description()).contains("Bridge 1", "6 month(s)", "4 occurrence(s)");
  }

  @Test
  void rejectsEndDateRecurrenceThatContainsOnlyOneMonthlyOccurrence() {
    Asset asset = mock(Asset.class);
    when(asset.getId()).thenReturn(assetId);
    when(asset.getName()).thenReturn("Bridge 1");
    when(asset.getStatus()).thenReturn(AssetStatus.ACTIVE);
    when(assets.findByIdAndOrganizationId(assetId, orgId)).thenReturn(Optional.of(asset));
    LocalDate start = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(20);
    CreateServiceRequest input =
        new CreateServiceRequest(
            assetId,
            null,
            start,
            null,
            "Asia/Ho_Chi_Minh",
            null,
            null,
            RequestType.RECURRING,
            "MONTH",
            6,
            null,
            start.plusMonths(5));

    assertThatThrownBy(() -> service.create(customerId, input))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("between 2 and 100");
    verify(requests, never()).saveAndFlush(any(ServiceRequest.class));
  }

  @Test
  void rejectsRecurringQuoteWhenPilotLicenseExpiresBeforeLastOccurrence() {
    UUID requestId = UUID.randomUUID();
    LocalDate requestedFrom = LocalDate.of(2026, 12, 4);
    ServiceRequest request =
        new ServiceRequest(
            orgId,
            assetId,
            customerId,
            "Bridge inspection",
            requestedFrom,
            LocalDate.of(2028, 6, 4),
            "Asia/Ho_Chi_Minh",
            LocalTime.of(8, 0),
            LocalTime.of(17, 0),
            RequestType.RECURRING,
            "MONTH",
            6,
            4,
            null);
    setRequestId(request, requestId);
    when(requests.findWithLockById(requestId)).thenReturn(Optional.of(request));
    when(quotes.findWithLockByRequestId(requestId)).thenReturn(Optional.empty());
    when(partners.isEligibleInspectorForAllDates(eq(inspectorId), any())).thenReturn(false);

    assertThatThrownBy(
            () ->
                service.saveQuote(
                    managerId,
                    requestId,
                    quote(
                        Instant.parse("2026-12-04T01:00:00Z"),
                        Instant.parse("2026-12-04T03:00:00Z"))))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(
            "valid partner profile and pilot license for every scheduled occurrence");
    verify(quotes, never()).saveAndFlush(any(ServiceRequestQuote.class));
    verify(requests, never()).saveAndFlush(any(ServiceRequest.class));
  }

  @Test
  void acceptsRecurringQuoteWhenInspectorLicenseCoversEveryOccurrence() {
    UUID requestId = UUID.randomUUID();
    LocalDate requestedFrom = LocalDate.of(2026, 12, 4);
    ServiceRequest request =
        new ServiceRequest(
            orgId,
            assetId,
            customerId,
            "Bridge inspection",
            requestedFrom,
            LocalDate.of(2028, 6, 4),
            "Asia/Ho_Chi_Minh",
            LocalTime.of(8, 0),
            LocalTime.of(17, 0),
            RequestType.RECURRING,
            "MONTH",
            6,
            4,
            null);
    setRequestId(request, requestId);
    when(requests.findWithLockById(requestId)).thenReturn(Optional.of(request));
    when(quotes.findWithLockByRequestId(requestId)).thenReturn(Optional.empty());
    when(partners.isEligibleInspectorForAllDates(eq(inspectorId), any())).thenReturn(true);
    when(partners.isEligibleDroneForAllDates(eq(droneId), eq(inspectorId), any())).thenReturn(true);
    when(quotes.saveAndFlush(any(ServiceRequestQuote.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(requests.saveAndFlush(any(ServiceRequest.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId)).thenReturn(List.of());
    when(messages.findByRequestIdOrderByCreatedAtAscIdAsc(requestId)).thenReturn(List.of());

    service.saveQuote(
        managerId,
        requestId,
        quote(Instant.parse("2026-12-04T01:00:00Z"), Instant.parse("2026-12-04T03:00:00Z")));

    ArgumentCaptor<ServiceRequestQuote> captor = ArgumentCaptor.forClass(ServiceRequestQuote.class);
    verify(quotes).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getRequestId()).isEqualTo(requestId);
    ArgumentCaptor<List<Instant>> startsCaptor = ArgumentCaptor.forClass(List.class);
    verify(partners).isEligibleInspectorForAllDates(eq(inspectorId), startsCaptor.capture());
    assertThat(startsCaptor.getValue())
        .containsExactly(
            Instant.parse("2026-12-04T01:00:00Z"),
            Instant.parse("2027-06-04T01:00:00Z"),
            Instant.parse("2027-12-04T01:00:00Z"),
            Instant.parse("2028-06-04T01:00:00Z"));
  }

  @Test
  void rejectsAcceptingRecurringQuoteIfPilotLicenseExpiresBeforeLastOccurrence() {
    UUID requestId = UUID.randomUUID();
    LocalDate requestedFrom = LocalDate.of(2026, 12, 4);
    ServiceRequest request =
        new ServiceRequest(
            orgId,
            assetId,
            customerId,
            "Bridge inspection",
            requestedFrom,
            LocalDate.of(2028, 6, 4),
            "Asia/Ho_Chi_Minh",
            LocalTime.of(8, 0),
            LocalTime.of(17, 0),
            RequestType.RECURRING,
            "MONTH",
            6,
            4,
            null);
    request.markQuoted();
    setRequestId(request, requestId);
    ServiceRequestQuote currentQuote =
        quoteEntity(
            requestId,
            Instant.parse("2026-12-04T01:00:00Z"),
            Instant.parse("2026-12-04T03:00:00Z"));
    ReflectionTestUtils.setField(currentQuote, "rowVersion", 3L);
    when(requests.findWithLockByIdAndOrganizationId(requestId, orgId))
        .thenReturn(Optional.of(request));
    when(quotes.findWithLockByRequestId(requestId)).thenReturn(Optional.of(currentQuote));
    when(partners.isEligibleInspectorForAllDates(eq(inspectorId), any())).thenReturn(false);

    assertThatThrownBy(
            () -> service.acceptQuote(customerId, requestId, new AcceptServiceQuoteRequest(3L)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("pilot license must remain valid for every scheduled occurrence");
    verify(requests, never()).saveAndFlush(any(ServiceRequest.class));
    verify(occurrences, never()).save(any(ServiceRequestOccurrence.class));
  }

  @Test
  void managerCreatesSingleQuoteForOpenRequest() {
    UUID requestId = UUID.randomUUID();
    ServiceRequest request =
        requestWithId(requestId, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 1));
    when(requests.findWithLockById(requestId)).thenReturn(Optional.of(request));
    when(quotes.findWithLockByRequestId(requestId)).thenReturn(Optional.empty());
    when(occurrences.findByRequestIdOrderByOccurrenceIndexAsc(any())).thenReturn(List.of());
    when(messages.findByRequestIdOrderByCreatedAtAscIdAsc(any())).thenReturn(List.of());

    IssueServiceQuoteRequest quoteInput =
        quote(Instant.parse("2027-02-01T09:00:00Z"), Instant.parse("2027-02-01T11:00:00Z"));

    service.saveQuote(managerId, requestId, quoteInput);

    ArgumentCaptor<ServiceRequestQuote> captor = ArgumentCaptor.forClass(ServiceRequestQuote.class);
    verify(quotes).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getRequestId()).isEqualTo(requestId);
    assertThat(captor.getValue().getPricePerOccurrenceVnd()).isEqualByComparingTo("150.00");
  }

  @Test
  void rejectsRecurringQuoteWhoseFirstDateDiffersFromCustomerRequestedDate() {
    UUID requestId = UUID.randomUUID();
    LocalDate requestedFrom = LocalDate.of(2027, 2, 1);
    ServiceRequest request =
        new ServiceRequest(
            orgId,
            assetId,
            customerId,
            "Bridge inspection",
            requestedFrom,
            requestedFrom.plusMonths(1),
            "UTC",
            LocalTime.of(8, 0),
            LocalTime.of(17, 0),
            RequestType.RECURRING,
            "MONTH",
            1,
            2,
            null);
    setRequestId(request, requestId);
    when(requests.findWithLockById(requestId)).thenReturn(Optional.of(request));
    when(quotes.findWithLockByRequestId(requestId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.saveQuote(
                    managerId,
                    requestId,
                    quote(
                        Instant.parse("2027-02-02T09:00:00Z"),
                        Instant.parse("2027-02-02T10:00:00Z"))))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("must match the customer-requested start date");
    verify(quotes, never()).saveAndFlush(any(ServiceRequestQuote.class));
  }

  @Test
  void customerCannotAcceptWhenQuoteChangedSinceItWasViewed() {
    UUID requestId = UUID.randomUUID();
    ServiceRequest request = request(LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 1));
    request.markQuoted();
    when(requests.findWithLockByIdAndOrganizationId(requestId, orgId))
        .thenReturn(Optional.of(request));
    ServiceRequestQuote currentQuote =
        quoteEntity(
            requestId,
            Instant.parse("2027-02-01T09:00:00Z"),
            Instant.parse("2027-02-01T10:00:00Z"));
    ReflectionTestUtils.setField(currentQuote, "rowVersion", 3L);
    when(quotes.findWithLockByRequestId(requestId)).thenReturn(Optional.of(currentQuote));
    when(partners.isEligibleInspectorForAllDates(eq(inspectorId), any())).thenReturn(true);
    when(partners.isEligibleDroneForAllDates(eq(droneId), eq(inspectorId), any())).thenReturn(true);

    assertThatThrownBy(
            () -> service.acceptQuote(customerId, requestId, new AcceptServiceQuoteRequest(2L)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("The quote changed");
  }

  @Test
  void customerAndManagerCanViewOnlyTheirRequestScopes() {
    UUID requestId = UUID.randomUUID();
    ServiceRequest customerRequest =
        requestWithId(requestId, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 10));
    when(requests.findByIdAndOrganizationId(requestId, orgId))
        .thenReturn(Optional.of(customerRequest));
    when(quotes.findByRequestId(any())).thenReturn(Optional.empty());
    when(occurrences.findByRequestIdOrderByOccurrenceIndexAsc(any())).thenReturn(List.of());
    when(messages.findByRequestIdOrderByCreatedAtAscIdAsc(any())).thenReturn(List.of());

    assertThat(service.get(customerId, requestId).organizationId()).isEqualTo(orgId);

    ServiceRequest managerRequest = request(LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 10));
    when(requests.findById(requestId)).thenReturn(Optional.of(managerRequest));
    assertThat(service.get(managerId, requestId).id()).isEqualTo(managerRequest.getId());

    when(requests.findByOrganizationIdOrderByCreatedAtDesc(orgId))
        .thenReturn(List.of(customerRequest));
    when(requests.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(managerRequest));
    assertThat(service.list(customerId)).hasSize(1);
    assertThat(service.list(managerId)).hasSize(1);
  }

  @Test
  void customerCanPostRequestMessage() {
    UUID requestId = UUID.randomUUID();
    ServiceRequest request =
        requestWithId(requestId, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 10));
    when(requests.findByIdAndOrganizationId(requestId, orgId)).thenReturn(Optional.of(request));
    when(quotes.findByRequestId(requestId)).thenReturn(Optional.empty());
    when(occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId)).thenReturn(List.of());
    when(messages.findByRequestIdOrderByCreatedAtAscIdAsc(requestId)).thenReturn(List.of());
    when(messages.saveAndFlush(any(ServiceRequestMessage.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var response = service.postMessage(customerId, requestId, "Please inspect the supports.");

    ArgumentCaptor<ServiceRequestMessage> captor =
        ArgumentCaptor.forClass(ServiceRequestMessage.class);
    verify(messages).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getBody()).isEqualTo("Please inspect the supports.");
    assertThat(response.messages()).isEmpty();
  }

  @Test
  void recurringEndDateScheduleRejectsMoreThanOneHundredOccurrences() throws IOException {
    UUID requestId = UUID.randomUUID();
    LocalDate requestedFrom = LocalDate.of(2027, 1, 1);
    LocalDate recurrenceEnd = requestedFrom.plusMonths(100);
    ServiceRequest request =
        new ServiceRequest(
            orgId,
            assetId,
            customerId,
            "Bridge inspection",
            requestedFrom,
            recurrenceEnd,
            "UTC",
            LocalTime.of(8, 0),
            LocalTime.of(17, 0),
            RequestType.RECURRING,
            "MONTH",
            1,
            null,
            recurrenceEnd);
    setRequestId(request, requestId);
    request.markQuoted();
    ServiceRequestQuote quote =
        quoteEntity(
            requestId,
            Instant.parse("2027-01-01T09:00:00Z"),
            Instant.parse("2027-01-01T10:00:00Z"));
    request.acceptQuote(quote, customerId);
    when(requests.findWithLockById(requestId)).thenReturn(Optional.of(request));
    when(quotes.findByRequestId(requestId)).thenReturn(Optional.of(quote));
    when(quotes.findWithLockByRequestId(requestId)).thenReturn(Optional.of(quote));
    when(occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId)).thenReturn(List.of());
    when(messages.findByRequestIdOrderByCreatedAtAscIdAsc(requestId)).thenReturn(List.of());

    assertThatThrownBy(() -> service.uploadSignedContract(managerId, requestId, pdf("signed.pdf")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("cannot contain more than 100");
    verify(occurrences, never()).save(any(ServiceRequestOccurrence.class));
  }

  @Test
  void recurringScheduleEndDateMustIncludeAtLeastTwoOccurrences() throws IOException {
    UUID requestId = UUID.randomUUID();
    LocalDate requestedFrom = LocalDate.of(2027, 1, 1);
    LocalDate recurrenceEnd = LocalDate.of(2027, 6, 1);
    ServiceRequest request =
        new ServiceRequest(
            orgId,
            assetId,
            customerId,
            "Bridge inspection",
            requestedFrom,
            recurrenceEnd,
            "UTC",
            LocalTime.of(8, 0),
            LocalTime.of(17, 0),
            RequestType.RECURRING,
            "MONTH",
            6,
            null,
            recurrenceEnd);
    setRequestId(request, requestId);
    request.markQuoted();
    ServiceRequestQuote quote =
        quoteEntity(
            requestId,
            Instant.parse("2027-01-01T09:00:00Z"),
            Instant.parse("2027-01-01T10:00:00Z"));
    request.acceptQuote(quote, customerId);
    when(requests.findWithLockById(requestId)).thenReturn(Optional.of(request));
    when(quotes.findByRequestId(requestId)).thenReturn(Optional.of(quote));
    when(quotes.findWithLockByRequestId(requestId)).thenReturn(Optional.of(quote));
    when(occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId)).thenReturn(List.of());
    when(messages.findByRequestIdOrderByCreatedAtAscIdAsc(requestId)).thenReturn(List.of());

    assertThatThrownBy(() -> service.uploadSignedContract(managerId, requestId, pdf("signed.pdf")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("at least two inspection occurrences");
    verify(occurrences, never()).save(any(ServiceRequestOccurrence.class));
  }

  @Test
  void rejectsEndDateRequestResolvingToMoreThanOneHundredOccurrences() {
    Asset asset = mock(Asset.class);
    when(asset.getId()).thenReturn(assetId);
    when(asset.getName()).thenReturn("Bridge 1");
    when(asset.getStatus()).thenReturn(AssetStatus.ACTIVE);
    when(assets.findByIdAndOrganizationId(assetId, orgId)).thenReturn(Optional.of(asset));
    LocalDate requestedFrom = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(20);
    CreateServiceRequest input =
        new CreateServiceRequest(
            assetId,
            null,
            requestedFrom,
            null,
            "Asia/Ho_Chi_Minh",
            null,
            null,
            RequestType.RECURRING,
            "MONTH",
            1,
            null,
            requestedFrom.plusMonths(100));

    assertThatThrownBy(() -> service.create(customerId, input))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("between 2 and 100 occurrences");
    verify(requests, never()).saveAndFlush(any(ServiceRequest.class));
  }

  @Test
  void recurringRequestCreatesCountBoundOccurrencesAfterSigning() throws IOException {
    UUID requestId = UUID.randomUUID();
    ServiceRequest request = recurringRequest(requestId, 3, null);
    request.markQuoted();
    ServiceRequestQuote quote =
        quoteEntity(
            requestId,
            Instant.parse("2027-02-01T09:00:00Z"),
            Instant.parse("2027-02-01T10:00:00Z"));
    request.acceptQuote(quote, customerId);
    when(requests.findWithLockById(requestId)).thenReturn(Optional.of(request));
    when(quotes.findByRequestId(requestId)).thenReturn(Optional.of(quote));
    when(quotes.findWithLockByRequestId(requestId)).thenReturn(Optional.of(quote));
    when(quotes.findByRequestId(requestId)).thenReturn(Optional.of(quote));
    when(occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId)).thenReturn(List.of());
    when(messages.findByRequestIdOrderByCreatedAtAscIdAsc(requestId)).thenReturn(List.of());
    when(occurrences.save(any(ServiceRequestOccurrence.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.uploadSignedContract(managerId, requestId, pdf("signed.pdf"));

    ArgumentCaptor<ServiceRequestOccurrence> captor =
        ArgumentCaptor.forClass(ServiceRequestOccurrence.class);
    verify(occurrences, org.mockito.Mockito.times(3)).save(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(ServiceRequestOccurrence::getOccurrenceIndex)
        .containsExactly(1, 2, 3);
    assertThat(captor.getAllValues())
        .extracting(ServiceRequestOccurrence::getPlannedStartAt)
        .containsExactly(
            Instant.parse("2027-02-01T09:00:00Z"),
            Instant.parse("2027-02-08T09:00:00Z"),
            Instant.parse("2027-02-15T09:00:00Z"));
    assertThat(request.getStatus()).isEqualTo(ServiceRequestStatus.CONTRACT_SIGNED);
  }

  @Test
  void recurringRequestEndDateCountsOnlyDatesAtOrAfterQuotedStart() throws IOException {
    UUID requestId = UUID.randomUUID();
    ServiceRequest request = recurringRequest(requestId, null, LocalDate.of(2027, 2, 15));
    request.markQuoted();
    ServiceRequestQuote quote =
        quoteEntity(
            requestId,
            Instant.parse("2027-02-02T09:00:00Z"),
            Instant.parse("2027-02-02T10:00:00Z"));
    request.acceptQuote(quote, customerId);
    when(requests.findWithLockById(requestId)).thenReturn(Optional.of(request));
    when(quotes.findByRequestId(requestId)).thenReturn(Optional.of(quote));
    when(quotes.findWithLockByRequestId(requestId)).thenReturn(Optional.of(quote));
    when(quotes.findByRequestId(requestId)).thenReturn(Optional.of(quote));
    when(occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId)).thenReturn(List.of());
    when(messages.findByRequestIdOrderByCreatedAtAscIdAsc(requestId)).thenReturn(List.of());
    when(occurrences.save(any(ServiceRequestOccurrence.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.uploadSignedContract(managerId, requestId, pdf("signed.pdf"));

    ArgumentCaptor<ServiceRequestOccurrence> captor =
        ArgumentCaptor.forClass(ServiceRequestOccurrence.class);
    verify(occurrences, org.mockito.Mockito.times(2)).save(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(ServiceRequestOccurrence::getOccurrenceIndex)
        .containsExactly(1, 2);
    assertThat(captor.getAllValues())
        .extracting(ServiceRequestOccurrence::getPlannedStartAt)
        .containsExactly(
            Instant.parse("2027-02-02T09:00:00Z"), Instant.parse("2027-02-09T09:00:00Z"));
  }

  @Test
  void monthlyOccurrencesStayAnchoredToQuotedMonthEnd() throws IOException {
    UUID requestId = UUID.randomUUID();
    ServiceRequest request =
        new ServiceRequest(
            orgId,
            assetId,
            customerId,
            "Bridge inspection",
            LocalDate.of(2027, 1, 31),
            LocalDate.of(2027, 4, 30),
            "UTC",
            LocalTime.of(8, 0),
            LocalTime.of(17, 0),
            RequestType.RECURRING,
            "MONTH",
            1,
            4,
            null);
    setRequestId(request, requestId);
    request.markQuoted();
    ServiceRequestQuote quote =
        quoteEntity(
            requestId,
            Instant.parse("2027-01-31T09:00:00Z"),
            Instant.parse("2027-01-31T10:00:00Z"));
    request.acceptQuote(quote, customerId);
    when(requests.findWithLockById(requestId)).thenReturn(Optional.of(request));
    when(quotes.findByRequestId(requestId)).thenReturn(Optional.of(quote));
    when(quotes.findWithLockByRequestId(requestId)).thenReturn(Optional.of(quote));
    when(quotes.findByRequestId(requestId)).thenReturn(Optional.of(quote));
    when(occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId)).thenReturn(List.of());
    when(messages.findByRequestIdOrderByCreatedAtAscIdAsc(requestId)).thenReturn(List.of());
    when(occurrences.save(any(ServiceRequestOccurrence.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.uploadSignedContract(managerId, requestId, pdf("signed.pdf"));

    ArgumentCaptor<ServiceRequestOccurrence> captor =
        ArgumentCaptor.forClass(ServiceRequestOccurrence.class);
    verify(occurrences, org.mockito.Mockito.times(4)).save(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(ServiceRequestOccurrence::getPlannedStartAt)
        .containsExactly(
            Instant.parse("2027-01-31T09:00:00Z"),
            Instant.parse("2027-02-28T09:00:00Z"),
            Instant.parse("2027-03-31T09:00:00Z"),
            Instant.parse("2027-04-30T09:00:00Z"));
  }

  @Test
  void rejectsCountBoundOccurrencesPastTheRequestedUntilDate() throws IOException {
    UUID requestId = UUID.randomUUID();
    ServiceRequest request = recurringRequest(requestId, 5, null);
    request.markQuoted();
    ServiceRequestQuote quote =
        quoteEntity(
            requestId,
            Instant.parse("2027-02-08T09:00:00Z"),
            Instant.parse("2027-02-08T10:00:00Z"));
    request.acceptQuote(quote, customerId);
    when(requests.findWithLockById(requestId)).thenReturn(Optional.of(request));
    when(quotes.findByRequestId(requestId)).thenReturn(Optional.of(quote));
    when(quotes.findWithLockByRequestId(requestId)).thenReturn(Optional.of(quote));
    when(quotes.findByRequestId(requestId)).thenReturn(Optional.of(quote));
    when(occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId)).thenReturn(List.of());
    when(messages.findByRequestIdOrderByCreatedAtAscIdAsc(requestId)).thenReturn(List.of());
    when(occurrences.save(any(ServiceRequestOccurrence.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    assertThatThrownBy(() -> service.uploadSignedContract(managerId, requestId, pdf("signed.pdf")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("contract term has changed");
    verify(occurrences, never()).save(any(ServiceRequestOccurrence.class));
  }

  @Test
  void estimatesOccurrencesWhenRecurrenceEndDateIsEarlierThanRequestUntil() throws IOException {
    UUID requestId = UUID.randomUUID();
    ServiceRequest request =
        new ServiceRequest(
            orgId,
            assetId,
            customerId,
            "Bridge inspection",
            LocalDate.of(2027, 2, 1),
            LocalDate.of(2027, 3, 31),
            "UTC",
            LocalTime.of(8, 0),
            LocalTime.of(17, 0),
            RequestType.RECURRING,
            "WEEK",
            1,
            null,
            LocalDate.of(2027, 2, 15));
    setRequestId(request, requestId);
    request.markQuoted();
    ServiceRequestQuote quote =
        quoteEntity(
            requestId,
            Instant.parse("2027-02-01T09:00:00Z"),
            Instant.parse("2027-02-01T10:00:00Z"));
    request.acceptQuote(quote, customerId);
    when(requests.findWithLockById(requestId)).thenReturn(Optional.of(request));
    when(quotes.findByRequestId(requestId)).thenReturn(Optional.of(quote));
    when(quotes.findWithLockByRequestId(requestId)).thenReturn(Optional.of(quote));
    when(quotes.findByRequestId(requestId)).thenReturn(Optional.of(quote));
    when(occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId)).thenReturn(List.of());
    when(messages.findByRequestIdOrderByCreatedAtAscIdAsc(requestId)).thenReturn(List.of());
    when(occurrences.save(any(ServiceRequestOccurrence.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.uploadSignedContract(managerId, requestId, pdf("signed.pdf"));

    ArgumentCaptor<ServiceRequestOccurrence> captor =
        ArgumentCaptor.forClass(ServiceRequestOccurrence.class);
    verify(occurrences, org.mockito.Mockito.times(3)).save(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(ServiceRequestOccurrence::getPlannedStartAt)
        .containsExactly(
            Instant.parse("2027-02-01T09:00:00Z"),
            Instant.parse("2027-02-08T09:00:00Z"),
            Instant.parse("2027-02-15T09:00:00Z"));
  }

  @Test
  void rejectsRecurringQuoteStartBeforeTheRequestedFromDate() throws IOException {
    UUID requestId = UUID.randomUUID();
    ServiceRequest request = recurringRequest(requestId, 2, null);
    request.markQuoted();
    ServiceRequestQuote quote =
        quoteEntity(
            requestId,
            Instant.parse("2027-01-25T09:00:00Z"),
            Instant.parse("2027-01-25T10:00:00Z"));
    request.acceptQuote(quote, customerId);
    when(requests.findWithLockById(requestId)).thenReturn(Optional.of(request));
    when(quotes.findByRequestId(requestId)).thenReturn(Optional.of(quote));
    when(quotes.findWithLockByRequestId(requestId)).thenReturn(Optional.of(quote));
    when(quotes.findByRequestId(requestId)).thenReturn(Optional.of(quote));
    when(occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId)).thenReturn(List.of());
    when(messages.findByRequestIdOrderByCreatedAtAscIdAsc(requestId)).thenReturn(List.of());

    assertThatThrownBy(() -> service.uploadSignedContract(managerId, requestId, pdf("signed.pdf")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("first quoted occurrence cannot be earlier");
    verify(occurrences, never()).save(any(ServiceRequestOccurrence.class));
  }

  @Test
  void rejectsUnsupportedEmptyOversizedAndUnreadableContractFiles() throws IOException {
    UUID requestId = UUID.randomUUID();
    ServiceRequest request = requestWithId(requestId, LocalDate.now(), LocalDate.now());
    request.markQuoted();
    request.acceptQuote(
        quoteEntity(
            requestId, Instant.now().plusSeconds(86_400), Instant.now().plusSeconds(90_000)),
        customerId);
    when(requests.findWithLockById(requestId)).thenReturn(Optional.of(request));

    assertThatThrownBy(() -> service.uploadSignedContract(managerId, requestId, null))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Signed contract file is required");
    assertThatThrownBy(
            () ->
                service.uploadSignedContract(
                    managerId,
                    requestId,
                    new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0])))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Signed contract file is required");
    assertThatThrownBy(
            () ->
                service.uploadSignedContract(
                    managerId,
                    requestId,
                    new MockMultipartFile("file", "contract.txt", "text/plain", "text".getBytes())))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Only PDF");
    assertThatThrownBy(
            () ->
                service.uploadSignedContract(
                    managerId,
                    requestId,
                    new MockMultipartFile(
                        "file", "large.pdf", "application/pdf", new byte[10 * 1024 * 1024 + 1])))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Maximum signed contract size");

    MockMultipartFile unreadable = mock(MockMultipartFile.class);
    when(unreadable.isEmpty()).thenReturn(false);
    when(unreadable.getContentType()).thenReturn("application/pdf");
    when(unreadable.getSize()).thenReturn(8L);
    doAnswer(
            invocation -> {
              throw new IOException("read failed");
            })
        .when(unreadable)
        .getBytes();
    assertThatThrownBy(() -> service.uploadSignedContract(managerId, requestId, unreadable))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("could not be read");
  }

  @Test
  void rejectsContractWhenStorageIsMissingOrWriteFails() throws IOException {
    UUID requestId = UUID.randomUUID();
    ServiceRequest request = requestWithId(requestId, LocalDate.now(), LocalDate.now());
    request.markQuoted();
    request.acceptQuote(
        quoteEntity(
            requestId,
            LocalDate.now().atTime(9, 0).toInstant(java.time.ZoneOffset.UTC),
            LocalDate.now().atTime(10, 0).toInstant(java.time.ZoneOffset.UTC)),
        customerId);
    when(requests.findWithLockById(requestId)).thenReturn(Optional.of(request));
    ContractWorkflowService noStorageService =
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
            Optional.empty());
    assertThatThrownBy(
            () -> noStorageService.uploadSignedContract(managerId, requestId, pdf("signed.pdf")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Contract storage is not configured");

    doThrow(new IOException("write failed"))
        .when(objectStore)
        .put(any(String.class), eq("application/pdf"), eq(8L), any(InputStream.class));
    assertThatThrownBy(() -> service.uploadSignedContract(managerId, requestId, pdf("signed.pdf")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Failed to store signed contract");
  }

  @Test
  void uploadRejectsUnacceptedRequestAndInvalidFileSignature() {
    UUID requestId = UUID.randomUUID();
    when(requests.findWithLockById(requestId))
        .thenReturn(Optional.of(request(LocalDate.now(), LocalDate.now().plusDays(1))));
    assertThatThrownBy(() -> service.uploadSignedContract(managerId, requestId, pdf("signed.pdf")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Customer must accept a quote");

    ServiceRequest accepted = requestWithId(requestId, LocalDate.now(), LocalDate.now());
    accepted.markQuoted();
    accepted.acceptQuote(
        quoteEntity(
            requestId,
            LocalDate.now().atTime(9, 0).toInstant(java.time.ZoneOffset.UTC),
            LocalDate.now().atTime(10, 0).toInstant(java.time.ZoneOffset.UTC)),
        customerId);
    when(requests.findWithLockById(requestId)).thenReturn(Optional.of(accepted));
    assertThatThrownBy(
            () ->
                service.uploadSignedContract(
                    managerId,
                    requestId,
                    new MockMultipartFile(
                        "file", "bad.pdf", "application/pdf", "not a PDF".getBytes())))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("does not match its declared type");
  }

  @Test
  void uploadsSignedPdfAndMaterializesOneTimeOccurrence() throws IOException {
    UUID requestId = UUID.randomUUID();
    ServiceRequest request =
        requestWithId(requestId, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 1));
    request.markQuoted();
    ServiceRequestQuote quote =
        quoteEntity(
            requestId,
            Instant.parse("2027-02-01T09:00:00Z"),
            Instant.parse("2027-02-01T10:00:00Z"));
    request.acceptQuote(quote, customerId);
    when(requests.findWithLockById(requestId)).thenReturn(Optional.of(request));
    when(quotes.findByRequestId(requestId)).thenReturn(Optional.of(quote));
    when(quotes.findWithLockByRequestId(requestId)).thenReturn(Optional.of(quote));
    when(quotes.findByRequestId(requestId)).thenReturn(Optional.of(quote));
    when(occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId)).thenReturn(List.of());
    when(messages.findByRequestIdOrderByCreatedAtAscIdAsc(requestId)).thenReturn(List.of());
    when(occurrences.save(any(ServiceRequestOccurrence.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.uploadSignedContract(managerId, requestId, pdf("signed/contract.pdf"));

    verify(objectStore).put(any(String.class), eq("application/pdf"), eq(8L), any());
    verify(occurrences).save(any(ServiceRequestOccurrence.class));
    assertThat(request.getContractFileName()).isEqualTo("signed_contract.pdf");
    assertThat(request.hasSignedContract()).isTrue();
  }

  @Test
  void deletesUploadedContractObjectWhenPersistenceFails() throws IOException {
    UUID requestId = UUID.randomUUID();
    ServiceRequest request =
        requestWithId(requestId, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 1));
    request.markQuoted();
    request.acceptQuote(
        quoteEntity(
            requestId,
            Instant.parse("2027-02-01T09:00:00Z"),
            Instant.parse("2027-02-01T10:00:00Z")),
        customerId);
    when(requests.findWithLockById(requestId)).thenReturn(Optional.of(request));
    doThrow(new IllegalStateException("persist failed")).when(requests).saveAndFlush(request);

    assertThatThrownBy(() -> service.uploadSignedContract(managerId, requestId, pdf("signed.pdf")))
        .isInstanceOf(IllegalStateException.class);
    verify(objectStore).delete(any(String.class));
  }

  @Test
  void assignsPendingOccurrenceAndUpdatesRequestWhenAllOccurrencesAssigned() {
    UUID requestId = UUID.randomUUID();
    UUID occurrenceId = UUID.randomUUID();
    ServiceRequest request =
        requestWithId(requestId, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 1));
    request.markQuoted();
    request.acceptQuote(
        quoteEntity(
            requestId,
            Instant.parse("2027-02-01T09:00:00Z"),
            Instant.parse("2027-02-01T10:00:00Z")),
        customerId);
    request.recordSignedContract(
        "contracts/key", "signed.pdf", "application/pdf", 8, "a".repeat(64), managerId);
    ServiceRequestOccurrence occurrence =
        new ServiceRequestOccurrence(
            requestId,
            1,
            Instant.parse("2027-02-01T09:00:00Z"),
            Instant.parse("2027-02-01T10:00:00Z"));
    when(requests.findWithLockById(requestId)).thenReturn(Optional.of(request));
    when(occurrences.findWithLockById(occurrenceId)).thenReturn(Optional.of(occurrence));
    when(occurrences.hasScheduleConflict(any(), any(), any(), any())).thenReturn(false);
    when(occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId))
        .thenReturn(List.of(occurrence));
    when(quotes.findByRequestId(requestId)).thenReturn(Optional.empty());
    when(messages.findByRequestIdOrderByCreatedAtAscIdAsc(requestId)).thenReturn(List.of());
    when(assignment.assign(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(UUID.randomUUID());

    Instant assignedStart = Instant.parse("2027-02-01T11:00:00Z");
    Instant assignedEnd = Instant.parse("2027-02-01T12:00:00Z");
    AssignOccurrenceRequest input =
        new AssignOccurrenceRequest(assignedStart, assignedEnd, inspectorId, droneId);
    var response = service.assignOccurrence(managerId, requestId, occurrenceId, input);

    assertThat(occurrence.getPlannedStartAt()).isEqualTo(input.plannedStartAt());
    assertThat(occurrence.getPlannedEndAt()).isEqualTo(input.plannedEndAt());
    assertThat(occurrence.getStatus())
        .isEqualTo(ServiceRequestOccurrence.OccurrenceStatus.ASSIGNED);
    assertThat(request.getStatus()).isEqualTo(ServiceRequestStatus.ASSIGNED);
    assertThat(response.status()).isEqualTo(ServiceRequestStatus.ASSIGNED);
    assertThat(response.occurrences())
        .singleElement()
        .satisfies(
            value -> {
              assertThat(value.plannedStartAt()).isEqualTo(assignedStart);
              assertThat(value.plannedEndAt()).isEqualTo(assignedEnd);
            });
    verify(assignment)
        .assign(orgId, assetId, inspectorId, droneId, assignedStart, assignedEnd, managerId);
  }

  @Test
  void rejectsAnOutOfDateAssignmentWithoutChangingOccurrence() {
    UUID requestId = UUID.randomUUID();
    UUID occurrenceId = UUID.randomUUID();
    ServiceRequest request =
        requestWithId(requestId, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 1));
    request.markQuoted();
    request.acceptQuote(
        quoteEntity(
            requestId,
            Instant.parse("2027-02-01T09:00:00Z"),
            Instant.parse("2027-02-01T10:00:00Z")),
        customerId);
    request.recordSignedContract(
        "contracts/key", "signed.pdf", "application/pdf", 8, "a".repeat(64), managerId);
    ServiceRequestOccurrence occurrence =
        new ServiceRequestOccurrence(
            requestId,
            1,
            Instant.parse("2027-02-01T09:00:00Z"),
            Instant.parse("2027-02-01T10:00:00Z"));
    when(requests.findWithLockById(requestId)).thenReturn(Optional.of(request));
    when(occurrences.findWithLockById(occurrenceId)).thenReturn(Optional.of(occurrence));

    assertThatThrownBy(
            () ->
                service.assignOccurrence(
                    managerId,
                    requestId,
                    occurrenceId,
                    new AssignOccurrenceRequest(
                        Instant.parse("2027-02-01T18:00:00Z"),
                        Instant.parse("2027-02-01T19:00:00Z"),
                        inspectorId,
                        droneId)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("requested daily time window");

    assertThat(occurrence.getPlannedStartAt()).isEqualTo(Instant.parse("2027-02-01T09:00:00Z"));
    assertThat(occurrence.getStatus())
        .isEqualTo(ServiceRequestOccurrence.OccurrenceStatus.PENDING_ASSIGNMENT);
    verify(assignment, never()).assign(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void rejectsConflictingFlexibleAssignmentWithoutChangingOccurrence() {
    UUID requestId = UUID.randomUUID();
    UUID occurrenceId = UUID.randomUUID();
    ServiceRequest request =
        requestWithId(requestId, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 1));
    request.markQuoted();
    request.acceptQuote(
        quoteEntity(
            requestId,
            Instant.parse("2027-02-01T09:00:00Z"),
            Instant.parse("2027-02-01T10:00:00Z")),
        customerId);
    request.recordSignedContract(
        "contracts/key", "signed.pdf", "application/pdf", 8, "a".repeat(64), managerId);
    ServiceRequestOccurrence occurrence =
        new ServiceRequestOccurrence(
            requestId,
            1,
            Instant.parse("2027-02-01T09:00:00Z"),
            Instant.parse("2027-02-01T10:00:00Z"));
    when(requests.findWithLockById(requestId)).thenReturn(Optional.of(request));
    when(occurrences.findWithLockById(occurrenceId)).thenReturn(Optional.of(occurrence));
    when(occurrences.hasScheduleConflict(any(), any(), any(), any())).thenReturn(true);

    assertThatThrownBy(
            () ->
                service.assignOccurrence(
                    managerId,
                    requestId,
                    occurrenceId,
                    new AssignOccurrenceRequest(
                        Instant.parse("2027-02-01T11:00:00Z"),
                        Instant.parse("2027-02-01T12:00:00Z"),
                        inspectorId,
                        droneId)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("another assigned contract occurrence");

    assertThat(occurrence.getPlannedStartAt()).isEqualTo(Instant.parse("2027-02-01T09:00:00Z"));
    assertThat(occurrence.getStatus())
        .isEqualTo(ServiceRequestOccurrence.OccurrenceStatus.PENDING_ASSIGNMENT);
    verify(assignment, never()).assign(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void rejectsPlatformAdminFromCommercialQuoteWorkflow() {
    UUID adminId = UUID.randomUUID();
    when(users.findActiveUser(adminId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(adminId, Set.of(Roles.ADMIN), null, "Platform Admin")));

    assertThatThrownBy(
            () ->
                service.saveQuote(
                    adminId,
                    UUID.randomUUID(),
                    quote(
                        Instant.parse("2027-02-01T09:00:00Z"),
                        Instant.parse("2027-02-01T10:00:00Z"))))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("cannot access this MF2 action");
  }

  @Test
  void opensSignedContractOnlyForAuthorizedRequestActors() throws IOException {
    UUID requestId = UUID.randomUUID();
    ServiceRequest request =
        requestWithId(requestId, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 1));
    request.markQuoted();
    request.acceptQuote(
        quoteEntity(
            requestId,
            Instant.parse("2027-02-01T09:00:00Z"),
            Instant.parse("2027-02-01T10:00:00Z")),
        customerId);
    request.recordSignedContract(
        "contracts/requests/private-key",
        "signed_contract.pdf",
        "application/pdf",
        8,
        "a".repeat(64),
        managerId);
    when(requests.findByIdAndOrganizationId(requestId, orgId)).thenReturn(Optional.of(request));
    when(requests.findById(requestId)).thenReturn(Optional.of(request));
    when(objectStore.open("contracts/requests/private-key/signed_contract.pdf"))
        .thenAnswer(invocation -> new java.io.ByteArrayInputStream("%PDF-e2e".getBytes()));

    var customerFile = service.openSignedContract(customerId, requestId);
    assertThat(customerFile.fileName()).isEqualTo("signed_contract.pdf");
    assertThat(customerFile.contentType()).isEqualTo("application/pdf");
    assertThat(customerFile.stream().readAllBytes()).isEqualTo("%PDF-e2e".getBytes());

    var managerFile = service.openSignedContract(managerId, requestId);
    assertThat(managerFile.stream().readAllBytes()).isEqualTo("%PDF-e2e".getBytes());

    UUID foreignCustomerId = UUID.randomUUID();
    UUID foreignOrganizationId = UUID.randomUUID();
    when(users.findActiveUser(foreignCustomerId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(
                    foreignCustomerId, Set.of(Roles.CUSTOMER), foreignOrganizationId, "Customer")));
    when(requests.findByIdAndOrganizationId(requestId, foreignOrganizationId))
        .thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.openSignedContract(foreignCustomerId, requestId))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Service request not found");

    UUID adminId = UUID.randomUUID();
    when(users.findActiveUser(adminId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(adminId, Set.of(Roles.ADMIN), null, "Platform Admin")));
    assertThatThrownBy(() -> service.openSignedContract(adminId, requestId))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("cannot access this MF2 action");
  }

  @Test
  void materializedRecurringOccurrencesPreserveTimeAndHaveDistinctDates() throws IOException {
    UUID requestId = UUID.randomUUID();
    ServiceRequest request = recurringRequest(requestId, 3, null);
    request.markQuoted();
    ServiceRequestQuote quote =
        quoteEntity(
            requestId,
            Instant.parse("2027-02-08T09:30:00Z"),
            Instant.parse("2027-02-08T10:30:00Z"));
    request.acceptQuote(quote, customerId);
    when(requests.findWithLockById(requestId)).thenReturn(Optional.of(request));
    when(quotes.findByRequestId(requestId)).thenReturn(Optional.of(quote));
    when(quotes.findWithLockByRequestId(requestId)).thenReturn(Optional.of(quote));
    when(quotes.findByRequestId(requestId)).thenReturn(Optional.of(quote));
    when(occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId)).thenReturn(List.of());
    when(messages.findByRequestIdOrderByCreatedAtAscIdAsc(requestId)).thenReturn(List.of());
    when(occurrences.save(any(ServiceRequestOccurrence.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.uploadSignedContract(managerId, requestId, pdf("signed.pdf"));

    ArgumentCaptor<ServiceRequestOccurrence> captor =
        ArgumentCaptor.forClass(ServiceRequestOccurrence.class);
    verify(occurrences, org.mockito.Mockito.times(3)).save(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(ServiceRequestOccurrence::getPlannedStartAt)
        .containsExactly(
            Instant.parse("2027-02-08T09:30:00Z"),
            Instant.parse("2027-02-15T09:30:00Z"),
            Instant.parse("2027-02-22T09:30:00Z"));
  }

  @Test
  @DisplayName("Signed contract gate blocks assignment when request has no contract")
  void assignOccurrenceBlockedWhenOccurrenceHasNoSignedContract() {
    UUID requestId = UUID.randomUUID();
    UUID occurrenceId = UUID.randomUUID();
    ServiceRequest request = recurringRequest(requestId, 2, null);
    request.markQuoted();
    request.acceptQuote(
        quoteEntity(
            requestId,
            Instant.parse("2027-02-01T09:00:00Z"),
            Instant.parse("2027-02-01T10:00:00Z")),
        customerId);

    ServiceRequestOccurrence occurrence =
        new ServiceRequestOccurrence(
            requestId,
            2,
            Instant.parse("2027-02-08T09:00:00Z"),
            Instant.parse("2027-02-08T10:00:00Z"));

    when(requests.findWithLockById(requestId)).thenReturn(Optional.of(request));
    when(occurrences.findWithLockById(occurrenceId)).thenReturn(Optional.of(occurrence));

    AssignOccurrenceRequest input =
        new AssignOccurrenceRequest(
            occurrence.getPlannedStartAt(), occurrence.getPlannedEndAt(), inspectorId, droneId);

    assertThatThrownBy(() -> service.assignOccurrence(managerId, requestId, occurrenceId, input))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(
            "Service request must have a signed contract attached before assignment");
  }

  @Test
  @DisplayName("Recurring request parent signed contract covers all occurrences")
  void recurringRequestOccurrencesCoveredByParentSignedContract() {
    UUID requestId = UUID.randomUUID();
    UUID occ1Id = UUID.randomUUID();
    UUID occ2Id = UUID.randomUUID();
    ServiceRequest request = recurringRequest(requestId, 2, null);
    request.markQuoted();
    request.acceptQuote(
        quoteEntity(
            requestId,
            Instant.parse("2027-02-01T09:00:00Z"),
            Instant.parse("2027-02-01T10:00:00Z")),
        customerId);
    request.recordSignedContract("contracts/requests/" + requestId + "/signed-master.pdf");

    ServiceRequestOccurrence occ1 =
        new ServiceRequestOccurrence(
            requestId,
            1,
            Instant.parse("2027-02-01T09:00:00Z"),
            Instant.parse("2027-02-01T10:00:00Z"));

    ServiceRequestOccurrence occ2 =
        new ServiceRequestOccurrence(
            requestId,
            2,
            Instant.parse("2027-02-08T09:00:00Z"),
            Instant.parse("2027-02-08T10:00:00Z"));

    when(requests.findWithLockById(requestId)).thenReturn(Optional.of(request));
    when(occurrences.findWithLockById(occ1Id)).thenReturn(Optional.of(occ1));
    when(occurrences.findWithLockById(occ2Id)).thenReturn(Optional.of(occ2));
    when(occurrences.hasScheduleConflict(any(), any(), any(), any())).thenReturn(false);
    when(occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId))
        .thenReturn(List.of(occ1, occ2));
    when(quotes.findByRequestId(requestId)).thenReturn(Optional.empty());
    when(messages.findByRequestIdOrderByCreatedAtAscIdAsc(requestId)).thenReturn(List.of());
    when(assignment.assign(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(UUID.randomUUID());

    AssignOccurrenceRequest input1 =
        new AssignOccurrenceRequest(
            occ1.getPlannedStartAt(), occ1.getPlannedEndAt(), inspectorId, droneId);
    service.assignOccurrence(managerId, requestId, occ1Id, input1);
    assertThat(occ1.getStatus()).isEqualTo(ServiceRequestOccurrence.OccurrenceStatus.ASSIGNED);

    AssignOccurrenceRequest input2 =
        new AssignOccurrenceRequest(
            occ2.getPlannedStartAt(), occ2.getPlannedEndAt(), inspectorId, droneId);
    service.assignOccurrence(managerId, requestId, occ2Id, input2);
    assertThat(occ2.getStatus()).isEqualTo(ServiceRequestOccurrence.OccurrenceStatus.ASSIGNED);
  }

  @Test
  @DisplayName("Manager can upload signed DOCX and PDF directly via occurrence endpoint")
  void managerCanUploadSignedDocxAndPdfForOccurrence() throws IOException {
    UUID requestId = UUID.randomUUID();
    UUID occurrenceId = UUID.randomUUID();
    ServiceRequest request =
        requestWithId(requestId, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 1));
    request.markQuoted();
    request.acceptQuote(
        quoteEntity(
            requestId,
            Instant.parse("2027-02-01T09:00:00Z"),
            Instant.parse("2027-02-01T10:00:00Z")),
        customerId);

    ServiceRequestOccurrence occurrence =
        new ServiceRequestOccurrence(
            requestId,
            1,
            Instant.parse("2027-02-01T09:00:00Z"),
            Instant.parse("2027-02-01T10:00:00Z"));

    when(requests.findWithLockById(requestId)).thenReturn(Optional.of(request));
    when(occurrences.findWithLockById(occurrenceId)).thenReturn(Optional.of(occurrence));
    when(occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId))
        .thenReturn(List.of(occurrence));
    when(quotes.findByRequestId(requestId)).thenReturn(Optional.empty());
    when(messages.findByRequestIdOrderByCreatedAtAscIdAsc(requestId)).thenReturn(List.of());

    // DOCX with valid PK magic bytes: 0x50, 0x4B, 0x03, 0x04
    byte[] docxBytes = new byte[] {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00};
    MockMultipartFile docxFile =
        new MockMultipartFile(
            "file",
            "signed-contract.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            docxBytes);

    service.uploadOccurrenceSignedContract(managerId, requestId, occurrenceId, docxFile);

    assertThat(request.hasSignedContract()).isTrue();
    assertThat(request.getContractFileName()).isEqualTo("signed-contract.docx");
    assertThat(request.getContractContentType())
        .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    verify(objectStore)
        .put(
            any(String.class),
            eq("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            eq((long) docxBytes.length),
            any());
  }

  @Test
  @DisplayName("Payable VND quote issuance is blocked when Admin settlement rates are unset")
  void issueVndQuoteRequiresAdminFinanceSettingsConfigured() {
    UUID requestId = UUID.randomUUID();
    ServiceRequest request =
        requestWithId(requestId, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 1));
    when(requests.findWithLockById(requestId)).thenReturn(Optional.of(request));
    when(quotes.findWithLockByRequestId(requestId)).thenReturn(Optional.empty());

    // Admin rates unset
    doThrow(
            new BusinessException(
                HttpStatus.CONFLICT,
                "FINANCE_SETTINGS_REQUIRED",
                "Admin must configure inspection commission, VAT, and TNCN rates before payable quotes can be issued"))
        .when(financeSettingsService)
        .requireConfiguredRates();

    IssueServiceQuoteRequest vndQuote =
        new IssueServiceQuoteRequest(
            "Bridge inspection",
            Instant.parse("2027-02-01T09:00:00Z"),
            Instant.parse("2027-02-01T11:00:00Z"),
            inspectorId,
            droneId,
            new BigDecimal("11000000.00"),
            null);

    assertThatThrownBy(() -> service.saveQuote(managerId, requestId, vndQuote))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Admin must configure inspection commission, VAT, and TNCN rates");
  }

  @Test
  @DisplayName("Accepting VND quote snapshots inclusive VAT split, commission, and payout amounts")
  void acceptVndQuoteSnapshotsFinanceAmountsPerOccurrence() {
    UUID requestId = UUID.randomUUID();
    ServiceRequest request =
        requestWithId(requestId, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 1));
    request.markQuoted();

    ServiceRequestQuote quote =
        new ServiceRequestQuote(
            requestId,
            "Bridge inspection",
            Instant.parse("2027-02-01T09:00:00Z"),
            Instant.parse("2027-02-01T11:00:00Z"),
            inspectorId,
            droneId,
            new BigDecimal("11000000.00"),
            managerId);
    ReflectionTestUtils.setField(quote, "id", UUID.randomUUID());

    when(requests.findWithLockByIdAndOrganizationId(requestId, orgId))
        .thenReturn(Optional.of(request));
    when(quotes.findByRequestId(requestId)).thenReturn(Optional.of(quote));
    when(quotes.findWithLockByRequestId(requestId)).thenReturn(Optional.of(quote));
    when(quotes.findByRequestId(requestId)).thenReturn(Optional.of(quote));
    when(occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId)).thenReturn(List.of());
    when(messages.findByRequestIdOrderByCreatedAtAscIdAsc(requestId)).thenReturn(List.of());

    InspectionFinanceSettings settings =
        new InspectionFinanceSettings(
            new BigDecimal("15.00"), new BigDecimal("10.00"), new BigDecimal("10.00"), managerId);
    when(financeSettingsService.findCurrentSettings()).thenReturn(Optional.of(settings));

    ArgumentCaptor<ServiceRequestOccurrence> captor =
        ArgumentCaptor.forClass(ServiceRequestOccurrence.class);

    service.acceptQuote(
        customerId, requestId, new AcceptServiceQuoteRequest(quote.getRowVersion()));

    verify(occurrences).save(captor.capture());
    ServiceRequestOccurrence materialized = captor.getValue();

    assertThat(materialized.getCurrency()).isEqualTo("VND");
    assertThat(materialized.getTotalPrice()).isEqualByComparingTo("11000000.00");
    assertThat(materialized.getServiceBaseAmount()).isEqualByComparingTo("10000000.00");
    assertThat(materialized.getVatRate()).isEqualByComparingTo("10.00");
    assertThat(materialized.getVatAmount()).isEqualByComparingTo("1000000.00");
    assertThat(materialized.getCommissionRate()).isEqualByComparingTo("15.00");
    assertThat(materialized.getCommissionAmount()).isEqualByComparingTo("1500000.00");
    assertThat(materialized.getInspectorGrossPayout()).isEqualByComparingTo("8500000.00");
    assertThat(materialized.getTncnRate()).isEqualByComparingTo("10.00");
    assertThat(materialized.getTncnAmount()).isEqualByComparingTo("850000.00");
    assertThat(materialized.getInspectorNetPayout()).isEqualByComparingTo("7650000.00");
  }

  @Test
  @DisplayName("Subsequent Admin settings updates do not mutate existing occurrence snapshots")
  void adminSettingsUpdateDoesNotMutateExistingOccurrenceSnapshots() {
    UUID requestId = UUID.randomUUID();
    ServiceRequestOccurrence occurrence =
        new ServiceRequestOccurrence(
            requestId,
            1,
            Instant.parse("2027-02-01T09:00:00Z"),
            Instant.parse("2027-02-01T10:00:00Z"));

    occurrence.snapshotFinance(
        "VND",
        new BigDecimal("11000000.00"),
        new BigDecimal("10000000.00"),
        new BigDecimal("10.00"),
        new BigDecimal("1000000.00"),
        new BigDecimal("15.00"),
        new BigDecimal("1500000.00"),
        new BigDecimal("8500000.00"),
        new BigDecimal("10.00"),
        new BigDecimal("850000.00"),
        new BigDecimal("7650000.00"));

    // Attempting a second snapshot with new rates has no effect (immutable)
    occurrence.snapshotFinance(
        "VND",
        new BigDecimal("11000000.00"),
        new BigDecimal("10000000.00"),
        new BigDecimal("8.00"),
        new BigDecimal("800000.00"),
        new BigDecimal("25.00"),
        new BigDecimal("2500000.00"),
        new BigDecimal("7500000.00"),
        new BigDecimal("20.00"),
        new BigDecimal("1500000.00"),
        new BigDecimal("6000000.00"));

    assertThat(occurrence.getCommissionRate()).isEqualByComparingTo("15.00");
    assertThat(occurrence.getCommissionAmount()).isEqualByComparingTo("1500000.00");
    assertThat(occurrence.getVatRate()).isEqualByComparingTo("10.00");
    assertThat(occurrence.getInspectorNetPayout()).isEqualByComparingTo("7650000.00");
  }

  @Test
  @DisplayName(
      "Customer accepting published report transitions payment status to PAYMENT_DUE not PAID")
  void customerAcceptPublishedReportTransitionsToPaymentDueNotPaid() {
    UUID requestId = UUID.randomUUID();
    UUID occurrenceId = UUID.randomUUID();
    UUID inspectionId = UUID.randomUUID();
    UUID reportVersionId = UUID.randomUUID();

    ServiceRequest request =
        requestWithId(requestId, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 10));
    ServiceRequestOccurrence occurrence =
        new ServiceRequestOccurrence(
            requestId,
            1,
            Instant.parse("2027-02-01T09:00:00Z"),
            Instant.parse("2027-02-01T10:00:00Z"));
    occurrence.assign(inspectorId, droneId, inspectionId, managerId);

    when(requests.findWithLockByIdAndOrganizationId(requestId, orgId))
        .thenReturn(Optional.of(request));
    when(occurrences.findWithLockById(occurrenceId)).thenReturn(Optional.of(occurrence));
    when(reportAccess.isReportPublished(inspectionId, reportVersionId)).thenReturn(true);
    when(quotes.findByRequestId(requestId)).thenReturn(Optional.empty());
    when(occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId))
        .thenReturn(List.of(occurrence));
    when(messages.findByRequestIdOrderByCreatedAtAscIdAsc(requestId)).thenReturn(List.of());

    service.customerAcceptReport(customerId, requestId, occurrenceId, reportVersionId);

    assertThat(occurrence.getReportAcceptedAt()).isNotNull();
    assertThat(occurrence.getPaymentStatus())
        .isEqualTo(ServiceRequestOccurrence.OccurrencePaymentStatus.PAYMENT_DUE);
  }

  @Test
  @DisplayName("Customer cannot accept an unpublished report")
  void customerCannotAcceptUnpublishedReport() {
    UUID requestId = UUID.randomUUID();
    UUID occurrenceId = UUID.randomUUID();
    UUID inspectionId = UUID.randomUUID();
    UUID reportVersionId = UUID.randomUUID();

    ServiceRequest request =
        requestWithId(requestId, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 10));
    ServiceRequestOccurrence occurrence =
        new ServiceRequestOccurrence(
            requestId,
            1,
            Instant.parse("2027-02-01T09:00:00Z"),
            Instant.parse("2027-02-01T10:00:00Z"));
    occurrence.assign(inspectorId, droneId, inspectionId, managerId);

    when(requests.findWithLockByIdAndOrganizationId(requestId, orgId))
        .thenReturn(Optional.of(request));
    when(occurrences.findWithLockById(occurrenceId)).thenReturn(Optional.of(occurrence));
    when(reportAccess.isReportPublished(inspectionId, reportVersionId)).thenReturn(false);

    assertThatThrownBy(
            () ->
                service.customerAcceptReport(customerId, requestId, occurrenceId, reportVersionId))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Only a published report version can be accepted");
  }

  @Test
  @DisplayName("Manager confirms exact matching payment amount; mismatched amount is rejected")
  void managerPaymentConfirmationRequiresMatchingAmount() {
    UUID requestId = UUID.randomUUID();
    UUID occurrenceId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();

    ServiceRequest request =
        requestWithId(requestId, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 10));
    ServiceRequestOccurrence occurrence =
        new ServiceRequestOccurrence(
            requestId,
            1,
            Instant.parse("2027-02-01T09:00:00Z"),
            Instant.parse("2027-02-01T10:00:00Z"));
    occurrence.snapshotFinance(
        "VND",
        new BigDecimal("11000000.00"),
        new BigDecimal("10000000.00"),
        new BigDecimal("10.00"),
        new BigDecimal("1000000.00"),
        new BigDecimal("15.00"),
        new BigDecimal("1500000.00"),
        new BigDecimal("8500000.00"),
        new BigDecimal("10.00"),
        new BigDecimal("850000.00"),
        new BigDecimal("7650000.00"));
    occurrence.acceptReport(versionId, customerId);

    when(requests.findWithLockById(requestId)).thenReturn(Optional.of(request));
    when(occurrences.findWithLockById(occurrenceId)).thenReturn(Optional.of(occurrence));
    when(quotes.findByRequestId(requestId)).thenReturn(Optional.empty());
    when(occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId))
        .thenReturn(List.of(occurrence));
    when(messages.findByRequestIdOrderByCreatedAtAscIdAsc(requestId)).thenReturn(List.of());

    // Mismatched amount rejected
    ConfirmOccurrencePaymentRequest wrongAmount =
        new ConfirmOccurrencePaymentRequest(
            "TXN-12345", new BigDecimal("10000000.00"), "Mismatched");
    assertThatThrownBy(
            () -> service.confirmOccurrencePayment(managerId, requestId, occurrenceId, wrongAmount))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Payment amount must exactly match agreed total");

    // Exact matching amount confirms payment
    ConfirmOccurrencePaymentRequest matching =
        new ConfirmOccurrencePaymentRequest(
            "TXN-12345", new BigDecimal("11000000.00"), "Verified receipt");
    service.confirmOccurrencePayment(managerId, requestId, occurrenceId, matching);

    assertThat(occurrence.getPaymentStatus())
        .isEqualTo(ServiceRequestOccurrence.OccurrencePaymentStatus.PAID);
    assertThat(occurrence.getPayoutStatus())
        .isEqualTo(ServiceRequestOccurrence.OccurrencePayoutStatus.ELIGIBLE);
    assertThat(occurrence.getPaymentReference()).isEqualTo("TXN-12345");
    assertThat(occurrence.getPaymentConfirmedAt()).isNotNull();

    // Duplicate exact confirmation is idempotent
    service.confirmOccurrencePayment(managerId, requestId, occurrenceId, matching);
    assertThat(occurrence.getPaymentStatus())
        .isEqualTo(ServiceRequestOccurrence.OccurrencePaymentStatus.PAID);
  }

  @Test
  @DisplayName("Inspector payout is gated on both report acceptance and confirmed payment")
  void managerPayoutRecordingGatedOnBothAcceptedReportAndConfirmedPayment() {
    UUID requestId = UUID.randomUUID();
    UUID occurrenceId = UUID.randomUUID();
    UUID inspectionId = UUID.randomUUID();

    ServiceRequest request =
        requestWithId(requestId, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 10));
    ServiceRequestOccurrence occurrence =
        new ServiceRequestOccurrence(
            requestId,
            1,
            Instant.parse("2027-02-01T09:00:00Z"),
            Instant.parse("2027-02-01T10:00:00Z"));
    occurrence.assign(inspectorId, droneId, inspectionId, managerId);
    occurrence.snapshotFinance(
        "VND",
        new BigDecimal("11000000.00"),
        new BigDecimal("10000000.00"),
        new BigDecimal("10.00"),
        new BigDecimal("1000000.00"),
        new BigDecimal("15.00"),
        new BigDecimal("1500000.00"),
        new BigDecimal("8500000.00"),
        new BigDecimal("10.00"),
        new BigDecimal("850000.00"),
        new BigDecimal("7650000.00"));

    when(requests.findWithLockById(requestId)).thenReturn(Optional.of(request));
    when(occurrences.findWithLockById(occurrenceId)).thenReturn(Optional.of(occurrence));
    when(quotes.findByRequestId(requestId)).thenReturn(Optional.empty());
    when(occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId))
        .thenReturn(List.of(occurrence));
    when(messages.findByRequestIdOrderByCreatedAtAscIdAsc(requestId)).thenReturn(List.of());

    RecordOccurrencePayoutRequest payoutReq =
        new RecordOccurrencePayoutRequest("PAYOUT-REF-999", "Transfer completed");

    // Gate 1: Blocked before report acceptance
    assertThatThrownBy(
            () -> service.recordOccurrencePayout(managerId, requestId, occurrenceId, payoutReq))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(
            "Inspector payout requires both customer report acceptance and confirmed customer payment");

    // Customer accepts report
    occurrence.acceptReport(UUID.randomUUID(), customerId);

    // Gate 2: Still blocked before payment confirmation
    assertThatThrownBy(
            () -> service.recordOccurrencePayout(managerId, requestId, occurrenceId, payoutReq))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(
            "Inspector payout requires both customer report acceptance and confirmed customer payment");

    // Manager confirms payment
    occurrence.confirmPayment("TXN-12345", new BigDecimal("11000000.00"), managerId);

    // Both gates satisfied: payout succeeds!
    service.recordOccurrencePayout(managerId, requestId, occurrenceId, payoutReq);

    assertThat(occurrence.getPayoutStatus())
        .isEqualTo(ServiceRequestOccurrence.OccurrencePayoutStatus.PAID);
    assertThat(occurrence.getPayoutReference()).isEqualTo("PAYOUT-REF-999");
    assertThat(occurrence.getPayoutRecordedAt()).isNotNull();

    // Duplicate payout recording is idempotent
    service.recordOccurrencePayout(managerId, requestId, occurrenceId, payoutReq);
    assertThat(occurrence.getPayoutStatus())
        .isEqualTo(ServiceRequestOccurrence.OccurrencePayoutStatus.PAID);
  }

  private ServiceRequest request(LocalDate from, LocalDate until) {
    return requestWithId(UUID.randomUUID(), from, until);
  }

  private ServiceRequest requestWithId(UUID requestId, LocalDate from, LocalDate until) {
    if (until.isAfter(from)) {
      until = from;
    }
    ServiceRequest request =
        new ServiceRequest(
            orgId,
            assetId,
            customerId,
            "Bridge inspection",
            from,
            until,
            "UTC",
            LocalTime.of(8, 0),
            LocalTime.of(17, 0),
            RequestType.ONE_TIME,
            null,
            null,
            null,
            null);
    setRequestId(request, requestId);
    return request;
  }

  private ServiceRequest recurringRequest(UUID requestId, Integer count, LocalDate endDate) {
    ServiceRequest request =
        new ServiceRequest(
            orgId,
            assetId,
            customerId,
            "Bridge inspection",
            LocalDate.of(2027, 2, 1),
            LocalDate.of(2027, 2, 28),
            "UTC",
            LocalTime.of(8, 0),
            LocalTime.of(17, 0),
            RequestType.RECURRING,
            "WEEK",
            1,
            count,
            endDate);
    setRequestId(request, requestId);
    return request;
  }

  private static void setRequestId(ServiceRequest request, UUID requestId) {
    ReflectionTestUtils.setField(request, "id", requestId);
  }

  private IssueServiceQuoteRequest quote(Instant start, Instant end) {
    return new IssueServiceQuoteRequest(
        "Visual inspection scope", start, end, inspectorId, droneId, new BigDecimal("150.00"));
  }

  private ServiceRequestQuote quoteEntity(UUID requestId, Instant start, Instant end) {
    ServiceRequestQuote quote =
        new ServiceRequestQuote(
            requestId,
            "Bridge scope",
            start,
            end,
            inspectorId,
            droneId,
            new BigDecimal("150.00"),
            managerId);
    ReflectionTestUtils.setField(quote, "id", UUID.randomUUID());
    return quote;
  }

  private MockMultipartFile pdf(String fileName) {
    return new MockMultipartFile("file", fileName, "application/pdf", "%PDF-e2e".getBytes());
  }
}
