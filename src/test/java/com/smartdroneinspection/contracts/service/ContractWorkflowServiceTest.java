package com.smartdroneinspection.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.enums.AssetStatus;
import com.smartdroneinspection.assets.repository.AssetRepository;
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
    when(occurrences.hasScheduleConflict(any(), any(), any(), any())).thenReturn(false);
    when(occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId))
        .thenReturn(List.of(occurrence));
    when(quotes.findByRequestIdOrderByVersionNoDesc(requestId)).thenReturn(List.of());
    when(messages.findByRequestIdOrderByCreatedAtAscIdAsc(requestId)).thenReturn(List.of());
    when(assignment.assign(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(UUID.randomUUID());

    AssignOccurrenceRequest input =
        new AssignOccurrenceRequest(
            occurrence.getPlannedStartAt(), occurrence.getPlannedEndAt(), inspectorId, droneId);
    var response = service.assignOccurrence(managerId, requestId, occurrenceId, input);

    assertThat(occurrence.getStatus())
        .isEqualTo(ServiceRequestOccurrence.OccurrenceStatus.ASSIGNED);
    assertThat(request.getStatus()).isEqualTo(ServiceRequestStatus.ASSIGNED);
    assertThat(response.status()).isEqualTo(ServiceRequestStatus.ASSIGNED);
  }

  @Test
  @DisplayName(
      "Per-occurrence signed contract gate blocks assignment when occurrence has no contract")
  void assignOccurrenceBlockedWhenOccurrenceHasNoSignedContract() {
    UUID requestId = UUID.randomUUID();
    UUID occurrenceId = UUID.randomUUID();
    ServiceRequest request = recurringRequest(requestId, 2, null);
    request.markQuoted();
    request.acceptQuote(1, customerId);

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
            "Occurrence must have its own signed contract attached before assignment");
  }

  @Test
  @DisplayName("Recurring request occurrence 2 cannot be satisfied by occurrence 1 contract")
  void recurringRequestOccurrencesIndependentlyGatedBySignedContract() throws IOException {
    UUID requestId = UUID.randomUUID();
    UUID occ1Id = UUID.randomUUID();
    UUID occ2Id = UUID.randomUUID();
    ServiceRequest request = recurringRequest(requestId, 2, null);
    request.markQuoted();
    request.acceptQuote(1, customerId);

    ServiceRequestOccurrence occ1 =
        new ServiceRequestOccurrence(
            requestId,
            1,
            Instant.parse("2027-02-01T09:00:00Z"),
            Instant.parse("2027-02-01T10:00:00Z"));
    occ1.recordSignedContract(
        "contracts/occ1", "signed-occ1.pdf", "application/pdf", 123L, "sha1".repeat(16), managerId);

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
    when(quotes.findByRequestIdOrderByVersionNoDesc(requestId)).thenReturn(List.of());
    when(messages.findByRequestIdOrderByCreatedAtAscIdAsc(requestId)).thenReturn(List.of());
    when(assignment.assign(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(UUID.randomUUID());

    // Occ 1 has its contract, can be assigned
    AssignOccurrenceRequest input1 =
        new AssignOccurrenceRequest(
            occ1.getPlannedStartAt(), occ1.getPlannedEndAt(), inspectorId, droneId);
    var resp1 = service.assignOccurrence(managerId, requestId, occ1Id, input1);
    assertThat(occ1.getStatus()).isEqualTo(ServiceRequestOccurrence.OccurrenceStatus.ASSIGNED);

    // Occ 2 does not have its own contract, assignment must be blocked!
    AssignOccurrenceRequest input2 =
        new AssignOccurrenceRequest(
            occ2.getPlannedStartAt(), occ2.getPlannedEndAt(), inspectorId, droneId);
    assertThatThrownBy(() -> service.assignOccurrence(managerId, requestId, occ2Id, input2))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(
            "Occurrence must have its own signed contract attached before assignment");
  }

  @Test
  @DisplayName("Manager can upload signed DOCX and PDF directly to an occurrence")
  void managerCanUploadSignedDocxAndPdfForOccurrence() throws IOException {
    UUID requestId = UUID.randomUUID();
    UUID occurrenceId = UUID.randomUUID();
    ServiceRequest request =
        requestWithId(requestId, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 10));
    request.markQuoted();
    request.acceptQuote(1, customerId);

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
    when(quotes.findByRequestIdOrderByVersionNoDesc(requestId)).thenReturn(List.of());
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

    assertThat(occurrence.hasSignedContract()).isTrue();
    assertThat(occurrence.getContractFileName()).isEqualTo("signed-contract.docx");
    assertThat(occurrence.getContractContentType())
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
        requestWithId(requestId, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 10));
    when(requests.findWithLockById(requestId)).thenReturn(Optional.of(request));

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

    assertThatThrownBy(() -> service.issueQuote(managerId, requestId, vndQuote))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Admin must configure inspection commission, VAT, and TNCN rates");
  }

  @Test
  @DisplayName("Accepting VND quote snapshots inclusive VAT split, commission, and payout amounts")
  void acceptVndQuoteSnapshotsFinanceAmountsPerOccurrence() {
    UUID requestId = UUID.randomUUID();
    ServiceRequest request =
        requestWithId(requestId, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 10));
    request.markQuoted();

    ServiceRequestQuote quote =
        new ServiceRequestQuote(
            requestId,
            1,
            "Bridge inspection",
            Instant.parse("2027-02-01T09:00:00Z"),
            Instant.parse("2027-02-01T11:00:00Z"),
            inspectorId,
            droneId,
            "VND",
            new BigDecimal("11000000.00"),
            null,
            managerId);

    when(requests.findWithLockByIdAndOrganizationId(requestId, orgId))
        .thenReturn(Optional.of(request));
    when(quotes.findFirstByRequestIdOrderByVersionNoDesc(requestId)).thenReturn(Optional.of(quote));
    when(quotes.findByRequestIdAndVersionNo(requestId, 1)).thenReturn(Optional.of(quote));
    when(occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId)).thenReturn(List.of());
    when(quotes.findByRequestIdOrderByVersionNoDesc(requestId)).thenReturn(List.of(quote));
    when(messages.findByRequestIdOrderByCreatedAtAscIdAsc(requestId)).thenReturn(List.of());

    InspectionFinanceSettings settings =
        new InspectionFinanceSettings(
            new BigDecimal("15.00"), new BigDecimal("10.00"), new BigDecimal("10.00"), managerId);
    when(financeSettingsService.findCurrentSettings()).thenReturn(Optional.of(settings));

    ArgumentCaptor<ServiceRequestOccurrence> captor =
        ArgumentCaptor.forClass(ServiceRequestOccurrence.class);

    service.acceptQuote(customerId, requestId, 1);

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
    when(quotes.findByRequestIdOrderByVersionNoDesc(requestId)).thenReturn(List.of());
    when(occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId))
        .thenReturn(List.of(occurrence));
    when(messages.findByRequestIdOrderByCreatedAtAscIdAsc(requestId)).thenReturn(List.of());

    service.customerAcceptReport(customerId, requestId, occurrenceId, reportVersionId);

    assertThat(occurrence.getReportAcceptedAt()).isNotNull();
    assertThat(occurrence.getReportAcceptedByUserId()).isEqualTo(customerId);
    assertThat(occurrence.getAcceptedReportVersionId()).isEqualTo(reportVersionId);
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
    when(quotes.findByRequestIdOrderByVersionNoDesc(requestId)).thenReturn(List.of());
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
    assertThat(occurrence.getPaymentConfirmedByUserId()).isEqualTo(managerId);

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
    when(quotes.findByRequestIdOrderByVersionNoDesc(requestId)).thenReturn(List.of());
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
    assertThat(occurrence.getPayoutRecordedByUserId()).isEqualTo(managerId);

    // Duplicate payout recording is idempotent
    service.recordOccurrencePayout(managerId, requestId, occurrenceId, payoutReq);
    assertThat(occurrence.getPayoutStatus())
        .isEqualTo(ServiceRequestOccurrence.OccurrencePayoutStatus.PAID);
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
