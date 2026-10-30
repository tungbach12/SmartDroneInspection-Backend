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
import com.smartdroneinspection.assets.domain.enums.AssetStatus;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.contracts.api.dto.request.AssignOccurrenceRequest;
import com.smartdroneinspection.contracts.api.dto.request.CreateServiceRequest;
import com.smartdroneinspection.contracts.api.dto.request.IssueServiceQuoteRequest;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
    when(partners.isEligibleInspector(eq(inspectorId), any(Instant.class))).thenReturn(true);
    when(partners.isEligibleDrone(droneId, inspectorId)).thenReturn(true);
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
    ServiceRequest request = request(LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 10));
    when(requests.findWithLockById(any())).thenReturn(Optional.of(request));
    when(quotes.findFirstByRequestIdOrderByVersionNoDesc(any())).thenReturn(Optional.empty());
    when(occurrences.findByRequestIdOrderByOccurrenceIndexAsc(any())).thenReturn(List.of());
    when(messages.findByRequestIdOrderByCreatedAtAscIdAsc(any())).thenReturn(List.of());

    IssueServiceQuoteRequest quoteInput =
        quote(Instant.parse("2027-02-01T09:00:00Z"), Instant.parse("2027-02-01T11:00:00Z"));

    service.issueQuote(managerId, UUID.randomUUID(), quoteInput);

    ArgumentCaptor<ServiceRequestQuote> captor = ArgumentCaptor.forClass(ServiceRequestQuote.class);
    verify(quotes).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getVersionNo()).isEqualTo(1);
    assertThat(captor.getValue().getPricePerOccurrenceUsd()).isEqualByComparingTo("150.00");
  }

  @Test
  void customerCanAcceptOnlyTheLatestQuoteVersion() {
    UUID requestId = UUID.randomUUID();
    ServiceRequest request = request(LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 10));
    request.markQuoted();
    when(requests.findWithLockByIdAndOrganizationId(requestId, orgId))
        .thenReturn(Optional.of(request));
    ServiceRequestQuote latestQuote = mock(ServiceRequestQuote.class);
    when(latestQuote.getVersionNo()).thenReturn(2);
    when(quotes.findFirstByRequestIdOrderByVersionNoDesc(requestId))
        .thenReturn(Optional.of(latestQuote));

    assertThatThrownBy(() -> service.acceptQuote(customerId, requestId, 1))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Only the latest quote version can be accepted");
  }

  @Test
  void customerAndManagerCanViewOnlyTheirRequestScopes() {
    UUID requestId = UUID.randomUUID();
    ServiceRequest customerRequest =
        requestWithId(requestId, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 10));
    when(requests.findByIdAndOrganizationId(requestId, orgId))
        .thenReturn(Optional.of(customerRequest));
    when(quotes.findByRequestIdOrderByVersionNoDesc(any())).thenReturn(List.of());
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
    when(quotes.findByRequestIdOrderByVersionNoDesc(requestId)).thenReturn(List.of());
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
  void recurringRequestCreatesCountBoundOccurrencesAfterSigning() throws IOException {
    UUID requestId = UUID.randomUUID();
    ServiceRequest request = recurringRequest(requestId, 3, null);
    request.markQuoted();
    request.acceptQuote(1, customerId);
    ServiceRequestQuote quote =
        quoteEntity(
            requestId,
            1,
            Instant.parse("2027-02-01T09:00:00Z"),
            Instant.parse("2027-02-01T10:00:00Z"));
    when(requests.findWithLockById(requestId)).thenReturn(Optional.of(request));
    when(quotes.findByRequestIdAndVersionNo(requestId, 1)).thenReturn(Optional.of(quote));
    when(occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId)).thenReturn(List.of());
    when(quotes.findByRequestIdOrderByVersionNoDesc(requestId)).thenReturn(List.of(quote));
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
    request.acceptQuote(1, customerId);
    ServiceRequestQuote quote =
        quoteEntity(
            requestId,
            1,
            Instant.parse("2027-02-02T09:00:00Z"),
            Instant.parse("2027-02-02T10:00:00Z"));
    when(requests.findWithLockById(requestId)).thenReturn(Optional.of(request));
    when(quotes.findByRequestIdAndVersionNo(requestId, 1)).thenReturn(Optional.of(quote));
    when(occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId)).thenReturn(List.of());
    when(quotes.findByRequestIdOrderByVersionNoDesc(requestId)).thenReturn(List.of(quote));
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
            LocalDate.of(2027, 1, 1),
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
    request.acceptQuote(1, customerId);
    ServiceRequestQuote quote =
        quoteEntity(
            requestId,
            1,
            Instant.parse("2027-01-31T09:00:00Z"),
            Instant.parse("2027-01-31T10:00:00Z"));
    when(requests.findWithLockById(requestId)).thenReturn(Optional.of(request));
    when(quotes.findByRequestIdAndVersionNo(requestId, 1)).thenReturn(Optional.of(quote));
    when(occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId)).thenReturn(List.of());
    when(quotes.findByRequestIdOrderByVersionNoDesc(requestId)).thenReturn(List.of(quote));
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
    request.acceptQuote(1, customerId);
    ServiceRequestQuote quote =
        quoteEntity(
            requestId,
            1,
            Instant.parse("2027-02-08T09:00:00Z"),
            Instant.parse("2027-02-08T10:00:00Z"));
    when(requests.findWithLockById(requestId)).thenReturn(Optional.of(request));
    when(quotes.findByRequestIdAndVersionNo(requestId, 1)).thenReturn(Optional.of(quote));
    when(occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId)).thenReturn(List.of());
    when(quotes.findByRequestIdOrderByVersionNoDesc(requestId)).thenReturn(List.of(quote));
    when(messages.findByRequestIdOrderByCreatedAtAscIdAsc(requestId)).thenReturn(List.of());
    when(occurrences.save(any(ServiceRequestOccurrence.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    assertThatThrownBy(() -> service.uploadSignedContract(managerId, requestId, pdf("signed.pdf")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("outside the requested recurrence range");
    verify(occurrences, org.mockito.Mockito.times(3)).save(any(ServiceRequestOccurrence.class));
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
    request.acceptQuote(1, customerId);
    ServiceRequestQuote quote =
        quoteEntity(
            requestId,
            1,
            Instant.parse("2027-02-01T09:00:00Z"),
            Instant.parse("2027-02-01T10:00:00Z"));
    when(requests.findWithLockById(requestId)).thenReturn(Optional.of(request));
    when(quotes.findByRequestIdAndVersionNo(requestId, 1)).thenReturn(Optional.of(quote));
    when(occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId)).thenReturn(List.of());
    when(quotes.findByRequestIdOrderByVersionNoDesc(requestId)).thenReturn(List.of(quote));
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
    request.acceptQuote(1, customerId);
    ServiceRequestQuote quote =
        quoteEntity(
            requestId,
            1,
            Instant.parse("2027-01-25T09:00:00Z"),
            Instant.parse("2027-01-25T10:00:00Z"));
    when(requests.findWithLockById(requestId)).thenReturn(Optional.of(request));
    when(quotes.findByRequestIdAndVersionNo(requestId, 1)).thenReturn(Optional.of(quote));
    when(occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId)).thenReturn(List.of());
    when(quotes.findByRequestIdOrderByVersionNoDesc(requestId)).thenReturn(List.of(quote));
    when(messages.findByRequestIdOrderByCreatedAtAscIdAsc(requestId)).thenReturn(List.of());

    assertThatThrownBy(() -> service.uploadSignedContract(managerId, requestId, pdf("signed.pdf")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("outside the requested date or daily time window");
    verify(occurrences, never()).save(any(ServiceRequestOccurrence.class));
  }

  @Test
  void rejectsUnsupportedEmptyOversizedAndUnreadableContractFiles() throws IOException {
    UUID requestId = UUID.randomUUID();
    ServiceRequest request = requestWithId(requestId, LocalDate.now(), LocalDate.now().plusDays(1));
    request.markQuoted();
    request.acceptQuote(1, customerId);
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
        .hasMessageContaining("Only PDF, JPEG, and PNG");
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
    ServiceRequest request = requestWithId(requestId, LocalDate.now(), LocalDate.now().plusDays(1));
    request.markQuoted();
    request.acceptQuote(1, customerId);
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

    ServiceRequest accepted = request(LocalDate.now(), LocalDate.now().plusDays(1));
    accepted.markQuoted();
    accepted.acceptQuote(1, customerId);
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
        requestWithId(requestId, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 10));
    request.markQuoted();
    request.acceptQuote(1, customerId);
    ServiceRequestQuote quote =
        quoteEntity(
            requestId,
            1,
            Instant.parse("2027-02-01T09:00:00Z"),
            Instant.parse("2027-02-01T10:00:00Z"));
    when(requests.findWithLockById(requestId)).thenReturn(Optional.of(request));
    when(quotes.findByRequestIdAndVersionNo(requestId, 1)).thenReturn(Optional.of(quote));
    when(occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId)).thenReturn(List.of());
    when(quotes.findByRequestIdOrderByVersionNoDesc(requestId)).thenReturn(List.of(quote));
    when(messages.findByRequestIdOrderByCreatedAtAscIdAsc(requestId)).thenReturn(List.of());
    when(occurrences.save(any(ServiceRequestOccurrence.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.uploadSignedContract(managerId, requestId, pdf("signed/contract.pdf"));

    verify(objectStore).put(any(String.class), eq("application/pdf"), eq(8L), any());
    verify(occurrences).save(any(ServiceRequestOccurrence.class));
    assertThat(request.getContractFileName()).isEqualTo("signed_contract.pdf");
    assertThat(request.getContractChecksumSha256()).hasSize(64);
  }

  @Test
  void deletesUploadedContractObjectWhenPersistenceFails() throws IOException {
    UUID requestId = UUID.randomUUID();
    ServiceRequest request = request(LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 10));
    request.markQuoted();
    request.acceptQuote(1, customerId);
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
        requestWithId(requestId, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 10));
    request.markQuoted();
    request.acceptQuote(1, customerId);
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
    when(quotes.findByRequestIdOrderByVersionNoDesc(requestId)).thenReturn(List.of());
    when(messages.findByRequestIdOrderByCreatedAtAscIdAsc(requestId)).thenReturn(List.of());
    when(assignment.assign(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(UUID.randomUUID());

    Instant assignedStart = Instant.parse("2027-02-03T11:00:00Z");
    Instant assignedEnd = Instant.parse("2027-02-03T12:00:00Z");
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
  void rejectsAnOutOfWindowFlexibleAssignmentWithoutChangingOccurrence() {
    UUID requestId = UUID.randomUUID();
    UUID occurrenceId = UUID.randomUUID();
    ServiceRequest request = request(LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 10));
    request.markQuoted();
    request.acceptQuote(1, customerId);
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
                        Instant.parse("2027-02-03T18:00:00Z"),
                        Instant.parse("2027-02-03T19:00:00Z"),
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
    ServiceRequest request = request(LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 10));
    request.markQuoted();
    request.acceptQuote(1, customerId);
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
                        Instant.parse("2027-02-03T11:00:00Z"),
                        Instant.parse("2027-02-03T12:00:00Z"),
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
                service.issueQuote(
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
        requestWithId(requestId, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 10));
    request.markQuoted();
    request.acceptQuote(1, customerId);
    request.recordSignedContract(
        "contracts/requests/private-key",
        "signed_contract.pdf",
        "application/pdf",
        8,
        "a".repeat(64),
        managerId);
    when(requests.findByIdAndOrganizationId(requestId, orgId)).thenReturn(Optional.of(request));
    when(requests.findById(requestId)).thenReturn(Optional.of(request));
    when(objectStore.open("contracts/requests/private-key"))
        .thenAnswer(invocation -> new java.io.ByteArrayInputStream("%PDF-e2e".getBytes()));

    var customerFile = service.openSignedContract(customerId, requestId);
    assertThat(customerFile.fileName()).isEqualTo("signed_contract.pdf");
    assertThat(customerFile.contentType()).isEqualTo("application/pdf");
    assertThat(customerFile.sizeBytes()).isEqualTo(8);
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
    request.acceptQuote(1, customerId);
    ServiceRequestQuote quote =
        quoteEntity(
            requestId,
            1,
            Instant.parse("2027-02-08T09:30:00Z"),
            Instant.parse("2027-02-08T10:30:00Z"));
    when(requests.findWithLockById(requestId)).thenReturn(Optional.of(request));
    when(quotes.findByRequestIdAndVersionNo(requestId, 1)).thenReturn(Optional.of(quote));
    when(occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId)).thenReturn(List.of());
    when(quotes.findByRequestIdOrderByVersionNoDesc(requestId)).thenReturn(List.of(quote));
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

  private ServiceRequest request(LocalDate from, LocalDate until) {
    return requestWithId(UUID.randomUUID(), from, until);
  }

  private ServiceRequest requestWithId(UUID requestId, LocalDate from, LocalDate until) {
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

  private ServiceRequestQuote quoteEntity(UUID requestId, int version, Instant start, Instant end) {
    return new ServiceRequestQuote(
        requestId,
        version,
        "Bridge scope",
        start,
        end,
        inspectorId,
        droneId,
        new BigDecimal("150.00"),
        managerId);
  }

  private MockMultipartFile pdf(String fileName) {
    return new MockMultipartFile("file", fileName, "application/pdf", "%PDF-e2e".getBytes());
  }
}
