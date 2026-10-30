package com.smartdroneinspection.contracts.service;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.enums.AssetStatus;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.contracts.api.dto.request.AssignOccurrenceRequest;
import com.smartdroneinspection.contracts.api.dto.request.ConfirmOccurrencePaymentRequest;
import com.smartdroneinspection.contracts.api.dto.request.CreateServiceRequest;
import com.smartdroneinspection.contracts.api.dto.request.IssueServiceQuoteRequest;
import com.smartdroneinspection.contracts.api.dto.request.RecordOccurrencePayoutRequest;
import com.smartdroneinspection.contracts.api.dto.response.OccurrenceFinanceResponse;
import com.smartdroneinspection.contracts.api.dto.response.PayOSPaymentLinkResponse;
import com.smartdroneinspection.contracts.api.dto.response.ServiceRequestMessageResponse;
import com.smartdroneinspection.contracts.api.dto.response.ServiceRequestOccurrenceResponse;
import com.smartdroneinspection.contracts.api.dto.response.ServiceRequestQuoteResponse;
import com.smartdroneinspection.contracts.api.dto.response.ServiceRequestResponse;
import com.smartdroneinspection.contracts.domain.InspectionFinanceSettings;
import com.smartdroneinspection.contracts.domain.ServiceRequest;
import com.smartdroneinspection.contracts.domain.ServiceRequest.RequestType;
import com.smartdroneinspection.contracts.domain.ServiceRequestMessage;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence.OccurrencePaymentStatus;
import com.smartdroneinspection.contracts.domain.ServiceRequestQuote;
import com.smartdroneinspection.contracts.gateway.PayOSGateway;
import com.smartdroneinspection.contracts.repository.ServiceRequestMessageRepository;
import com.smartdroneinspection.contracts.repository.ServiceRequestOccurrenceRepository;
import com.smartdroneinspection.contracts.repository.ServiceRequestQuoteRepository;
import com.smartdroneinspection.contracts.repository.ServiceRequestRepository;
import com.smartdroneinspection.inspections.spi.InspectionReportAccess;
import com.smartdroneinspection.inspections.spi.Mf2InspectionAssignment;
import com.smartdroneinspection.shared.api.InspectionPaymentGate;
import com.smartdroneinspection.shared.api.InspectionReportAcceptancePort;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.UserAccess;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ContractWorkflowService
    implements InspectionPaymentGate, InspectionReportAcceptancePort {

  static final long MAX_CONTRACT_SIZE_BYTES = 10L * 1024 * 1024;
  private static final String STORAGE_UNAVAILABLE = "STORAGE_UNAVAILABLE";
  private static final String REQUEST_NOT_FOUND = "REQUEST_NOT_FOUND";
  private static final String REQUEST_NOT_FOUND_DETAIL = "Service request not found";
  private static final Set<String> ALLOWED_CONTRACT_TYPES =
      Set.of(
          "application/pdf",
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
          "image/jpeg",
          "image/png");

  private final ServiceRequestRepository requests;
  private final ServiceRequestMessageRepository messages;
  private final ServiceRequestQuoteRepository quotes;
  private final ServiceRequestOccurrenceRepository occurrences;
  private final AssetRepository assets;
  private final UserAccess users;
  private final Mf2ActorPolicy actorPolicy;
  private final PartnerRegistryService partners;
  private final Mf2InspectionAssignment assignment;
  private final Optional<EvidenceObjectStore> objectStore;
  private final InspectionFinanceSettingsService financeSettingsService;
  private final Optional<InspectionReportAccess> reportAccess;
  private final PayOSGateway payOSGateway;

  @org.springframework.beans.factory.annotation.Autowired
  public ContractWorkflowService(
      ServiceRequestRepository requests,
      ServiceRequestMessageRepository messages,
      ServiceRequestQuoteRepository quotes,
      ServiceRequestOccurrenceRepository occurrences,
      AssetRepository assets,
      UserAccess users,
      Mf2ActorPolicy actorPolicy,
      PartnerRegistryService partners,
      Mf2InspectionAssignment assignment,
      Optional<EvidenceObjectStore> objectStore,
      InspectionFinanceSettingsService financeSettingsService,
      Optional<InspectionReportAccess> reportAccess,
      PayOSGateway payOSGateway) {
    this.requests = requests;
    this.messages = messages;
    this.quotes = quotes;
    this.occurrences = occurrences;
    this.assets = assets;
    this.users = users;
    this.actorPolicy = actorPolicy;
    this.partners = partners;
    this.assignment = assignment;
    this.objectStore = objectStore;
    this.financeSettingsService = financeSettingsService;
    this.reportAccess = reportAccess;
    this.payOSGateway = payOSGateway;
  }

  public ContractWorkflowService(
      ServiceRequestRepository requests,
      ServiceRequestMessageRepository messages,
      ServiceRequestQuoteRepository quotes,
      ServiceRequestOccurrenceRepository occurrences,
      AssetRepository assets,
      UserAccess users,
      Mf2ActorPolicy actorPolicy,
      PartnerRegistryService partners,
      Mf2InspectionAssignment assignment,
      Optional<EvidenceObjectStore> objectStore,
      InspectionFinanceSettingsService financeSettingsService,
      Optional<InspectionReportAccess> reportAccess) {
    this(
        requests,
        messages,
        quotes,
        occurrences,
        assets,
        users,
        actorPolicy,
        partners,
        assignment,
        objectStore,
        financeSettingsService,
        reportAccess,
        new DisabledPayOSGatewayDummy());
  }

  public ContractWorkflowService(
      ServiceRequestRepository requests,
      ServiceRequestMessageRepository messages,
      ServiceRequestQuoteRepository quotes,
      ServiceRequestOccurrenceRepository occurrences,
      AssetRepository assets,
      UserAccess users,
      Mf2ActorPolicy actorPolicy,
      PartnerRegistryService partners,
      Mf2InspectionAssignment assignment,
      Optional<EvidenceObjectStore> objectStore) {
    this(
        requests,
        messages,
        quotes,
        occurrences,
        assets,
        users,
        actorPolicy,
        partners,
        assignment,
        objectStore,
        new InspectionFinanceSettingsService(null, users),
        Optional.empty(),
        new DisabledPayOSGatewayDummy());
  }

  @Transactional
  public ServiceRequestResponse create(UUID actorId, CreateServiceRequest input) {
    UserAccess.ActiveUser actor = requireActor(actorId, Mf2ActorPolicy.ActorKind.CUSTOMER);
    Asset asset =
        assets
            .findByIdAndOrganizationId(input.assetId(), actor.organizationId())
            .filter(value -> value.getStatus() != AssetStatus.RETIRED)
            .orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "ASSET_NOT_FOUND", "Asset not found"));
    LocalTime start = input.windowStart() == null ? LocalTime.of(8, 0) : input.windowStart();
    LocalTime end = input.windowEnd() == null ? LocalTime.of(17, 0) : input.windowEnd();
    ServiceRequest request;
    try {
      request =
          new ServiceRequest(
              actor.organizationId(),
              asset.getId(),
              actor.id(),
              input.description(),
              input.requestedFrom(),
              input.requestedUntil(),
              input.timezone(),
              start,
              end,
              input.requestType(),
              input.recurrenceUnit(),
              input.recurrenceInterval(),
              input.occurrenceCount(),
              input.recurrenceEndDate());
    } catch (IllegalArgumentException exception) {
      throw failure(HttpStatus.BAD_REQUEST, "REQUEST_INVALID", exception.getMessage());
    }
    return toResponse(requests.saveAndFlush(request));
  }

  @Transactional(readOnly = true)
  public List<ServiceRequestResponse> list(UUID actorId) {
    UserAccess.ActiveUser actor = requireActor(actorId, null);
    if (actorPolicy.actorKind(actor.roles(), actor.organizationId())
        == Mf2ActorPolicy.ActorKind.CUSTOMER) {
      return requests.findByOrganizationIdOrderByCreatedAtDesc(actor.organizationId()).stream()
          .map(this::toResponse)
          .toList();
    }
    if (actorPolicy.actorKind(actor.roles(), actor.organizationId())
        == Mf2ActorPolicy.ActorKind.MANAGER) {
      return requests.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }
    throw forbidden();
  }

  @Transactional(readOnly = true)
  public ServiceRequestResponse get(UUID actorId, UUID requestId) {
    UserAccess.ActiveUser actor = requireActor(actorId, null);
    return toResponse(requireRequestForActor(actor, requestId));
  }

  @Transactional
  public ServiceRequestResponse postMessage(UUID actorId, UUID requestId, String body) {
    UserAccess.ActiveUser actor = requireActor(actorId, null);
    ServiceRequest request = requireRequestForActor(actor, requestId);
    ServiceRequestMessage message;
    try {
      message = new ServiceRequestMessage(request.getId(), actor.id(), body);
    } catch (IllegalArgumentException exception) {
      throw failure(HttpStatus.BAD_REQUEST, "MESSAGE_INVALID", exception.getMessage());
    }
    messages.saveAndFlush(message);
    return toResponse(request);
  }

  @Transactional
  public ServiceRequestResponse issueQuote(
      UUID actorId, UUID requestId, IssueServiceQuoteRequest input) {
    UserAccess.ActiveUser manager = requireActor(actorId, Mf2ActorPolicy.ActorKind.MANAGER);
    ServiceRequest request = requireRequestForManagerLocked(manager, requestId);
    quotes.findWithLockByRequestId(requestId);
    requireRequestedOccurrenceWindow(
        request, input.firstOccurrenceStartAt(), input.firstOccurrenceEndAt());
    if (!partners.isEligibleInspector(input.inspectorId(), input.firstOccurrenceStartAt())) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "INVALID_PARTNER_INSPECTOR",
          "Selected Inspector is not an eligible registered partner");
    }
    if (!partners.isEligibleDrone(input.droneId(), input.inspectorId())) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "INVALID_PARTNER_DRONE",
          "Selected drone must be approved for the selected active partner Inspector");
    }

    // New inspection services use VND and require all 3 settlement rates to be configured
    String currency = input.resolvedCurrency();
    if ("VND".equalsIgnoreCase(currency)) {
      financeSettingsService.requireConfiguredRates();
    }

    quotes.findWithLockByRequestId(requestId);
    requests.flush();
    ServiceRequestQuote previous =
        quotes.findFirstByRequestIdOrderByVersionNoDesc(requestId).orElse(null);
    int nextVersion = previous == null ? 1 : previous.getVersionNo() + 1;
    ServiceRequestQuote quote;
    try {
      quote =
          new ServiceRequestQuote(
              requestId,
              nextVersion,
              input.scopeSnapshot(),
              input.firstOccurrenceStartAt(),
              input.firstOccurrenceEndAt(),
              input.inspectorId(),
              input.droneId(),
              currency,
              input.pricePerOccurrenceVnd(),
              input.pricePerOccurrenceUsd(),
              manager.id());
      request.markQuoted();
    } catch (IllegalArgumentException | IllegalStateException | ArithmeticException exception) {
      throw failure(HttpStatus.BAD_REQUEST, "QUOTE_INVALID", exception.getMessage());
    }
    quotes.saveAndFlush(quote);
    requests.saveAndFlush(request);
    return toResponse(request);
  }

  @Transactional
  public ServiceRequestResponse acceptQuote(UUID actorId, UUID requestId, Integer versionNo) {
    UserAccess.ActiveUser actor = requireActor(actorId, Mf2ActorPolicy.ActorKind.CUSTOMER);
    ServiceRequest request = requireCustomerRequestLocked(actor, requestId);
    quotes.findWithLockByRequestId(requestId);
    ServiceRequestQuote latest =
        quotes
            .findFirstByRequestIdOrderByVersionNoDesc(requestId)
            .orElseThrow(
                () ->
                    failure(HttpStatus.CONFLICT, "QUOTE_NOT_FOUND", "There is no quote to accept"));
    if (versionNo == null || latest.getVersionNo() != versionNo) {
      throw failure(
          HttpStatus.CONFLICT, "QUOTE_STALE", "Only the latest quote version can be accepted");
    }
    try {
      request.acceptQuote(latest.getVersionNo(), actor.id());
      materializeOccurrences(request, latest);
    } catch (IllegalStateException exception) {
      throw failure(HttpStatus.CONFLICT, "QUOTE_ACCEPTANCE_INVALID", exception.getMessage());
    }
    requests.saveAndFlush(request);
    return toResponse(request);
  }

  /**
   * Upload signed contract for a specific occurrence.
   *
   * <p>Every occurrence (whether one-time or recurring) requires its own signed contract before
   * assignment.
   */
  @Transactional
  public ServiceRequestResponse uploadOccurrenceSignedContract(
      UUID actorId, UUID requestId, UUID occurrenceId, MultipartFile file) {
    UserAccess.ActiveUser manager = requireActor(actorId, Mf2ActorPolicy.ActorKind.MANAGER);
    ServiceRequest request = requireRequestForManagerLocked(manager, requestId);
    ServiceRequestOccurrence occurrence =
        occurrences
            .findWithLockById(occurrenceId)
            .filter(value -> requestId.equals(value.getRequestId()))
            .orElseThrow(
                () ->
                    failure(HttpStatus.NOT_FOUND, "OCCURRENCE_NOT_FOUND", "Occurrence not found"));

    byte[] content = validateAndExtractFile(file);
    String contentType = file.getContentType().toLowerCase(java.util.Locale.ROOT);
    String fileName = sanitizeFileName(file.getOriginalFilename());
    String objectKey =
        "contracts/requests/"
            + requestId
            + "/occurrences/"
            + occurrenceId
            + "/"
            + UUID.randomUUID();

    EvidenceObjectStore store = requireStore();
    try {
      store.put(objectKey, contentType, content.length, new ByteArrayInputStream(content));
    } catch (IOException exception) {
      throw failure(HttpStatus.BAD_GATEWAY, "STORAGE_FAILED", "Failed to store signed contract");
    }

    try {
      occurrence.recordSignedContract(
          objectKey, fileName, contentType, content.length, sha256(content), manager.id());
      occurrences.saveAndFlush(occurrence);

      // If this is a one-time request and parent request metadata is empty, synchronize parent
      if (request.getRequestType() == RequestType.ONE_TIME
          && request.getContractObjectKey() == null) {
        request.recordSignedContract(
            objectKey, fileName, contentType, content.length, sha256(content), manager.id());
        requests.saveAndFlush(request);
      }
    } catch (RuntimeException exception) {
      try {
        store.delete(objectKey);
      } catch (IOException ignored) {
      }
      throw exception;
    }

    return toResponse(request);
  }

  /** Scoped download of an occurrence's signed contract file. */
  @Transactional(readOnly = true)
  public DownloadedContract openOccurrenceSignedContract(
      UUID actorId, UUID requestId, UUID occurrenceId) {
    UserAccess.ActiveUser actor = requireActor(actorId, null);
    ServiceRequest request = requireRequestForActor(actor, requestId);
    ServiceRequestOccurrence occurrence =
        occurrences
            .findById(occurrenceId)
            .filter(value -> requestId.equals(value.getRequestId()))
            .orElseThrow(
                () ->
                    failure(HttpStatus.NOT_FOUND, "OCCURRENCE_NOT_FOUND", "Occurrence not found"));
    if (!occurrence.hasSignedContract()) {
      throw failure(
          HttpStatus.NOT_FOUND,
          "CONTRACT_NOT_FOUND",
          "No signed contract attached to this occurrence");
    }
    EvidenceObjectStore store = requireStore();
    try {
      InputStream stream = store.open(occurrence.getContractObjectKey());
      return new DownloadedContract(
          occurrence.getContractFileName(),
          occurrence.getContractContentType(),
          occurrence.getContractSizeBytes(),
          stream);
    } catch (IOException ex) {
      throw failure(
          HttpStatus.NOT_FOUND, "CONTRACT_NOT_FOUND", "Stored contract could not be opened");
    }
  }

  /** Legacy parent request contract upload: attaches to request and occurrence 1 for ONE_TIME. */
  @Transactional
  public ServiceRequestResponse uploadSignedContract(
      UUID actorId, UUID requestId, MultipartFile file) {
    UserAccess.ActiveUser manager = requireActor(actorId, Mf2ActorPolicy.ActorKind.MANAGER);
    ServiceRequest request = requireRequestForManagerLocked(manager, requestId);
    if (request.getStatus() != ServiceRequest.ServiceRequestStatus.ACCEPTED) {
      throw failure(
          HttpStatus.CONFLICT,
          "CONTRACT_UPLOAD_INVALID",
          "Customer must accept a quote before the signed contract is uploaded");
    }
    byte[] content = validateAndExtractFile(file);
    String contentType = file.getContentType().toLowerCase(java.util.Locale.ROOT);
    String fileName = sanitizeFileName(file.getOriginalFilename());
    String objectKey = "contracts/requests/" + requestId + "/" + UUID.randomUUID();

    EvidenceObjectStore store = requireStore();
    try {
      store.put(objectKey, contentType, content.length, new ByteArrayInputStream(content));
    } catch (IOException exception) {
      throw failure(HttpStatus.BAD_GATEWAY, "STORAGE_FAILED", "Failed to store signed contract");
    }

    try {
      request.recordSignedContract(
          objectKey, fileName, contentType, content.length, sha256(content), manager.id());
      requests.saveAndFlush(request);

      ServiceRequestQuote acceptedQuote =
          quotes
              .findByRequestIdAndVersionNo(request.getId(), request.getAcceptedQuoteVersion())
              .orElse(null);
      materializeOccurrences(request, acceptedQuote);

      // For ONE_TIME request, attach to the single occurrence as well
      if (request.getRequestType() == RequestType.ONE_TIME) {
        occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId).stream()
            .findFirst()
            .ifPresent(
                occ -> {
                  if (!occ.hasSignedContract()) {
                    occ.recordSignedContract(
                        objectKey,
                        fileName,
                        contentType,
                        content.length,
                        sha256(content),
                        manager.id());
                    occurrences.saveAndFlush(occ);
                  }
                });
      }
    } catch (RuntimeException exception) {
      try {
        store.delete(objectKey);
      } catch (IOException ignored) {
      }
      throw exception;
    }
    return toResponse(request);
  }

  @Transactional(readOnly = true)
  public SignedContractFile openSignedContract(UUID actorId, UUID requestId) {
    UserAccess.ActiveUser actor = requireActor(actorId, null);
    ServiceRequest request = requireRequestForActor(actor, requestId);
    String objectKey = request.getContractObjectKey();
    if (objectKey == null || objectKey.isBlank()) {
      throw failure(
          HttpStatus.NOT_FOUND, "CONTRACT_NOT_FOUND", "Signed contract document was not found");
    }
    EvidenceObjectStore store =
        objectStore.orElseThrow(
            () ->
                failure(
                    HttpStatus.BAD_GATEWAY,
                    STORAGE_UNAVAILABLE,
                    "Contract storage is not configured"));
    try {
      InputStream stream = store.open(objectKey);
      return new SignedContractFile(
          request.getContractFileName(),
          request.getContractContentType(),
          request.getContractSizeBytes(),
          stream);
    } catch (IOException exception) {
      throw failure(HttpStatus.BAD_GATEWAY, STORAGE_UNAVAILABLE, "Contract storage is unavailable");
    }
  }

  @Transactional
  public ServiceRequestResponse assignOccurrence(
      UUID actorId, UUID requestId, UUID occurrenceId, AssignOccurrenceRequest input) {
    UserAccess.ActiveUser manager = requireActor(actorId, Mf2ActorPolicy.ActorKind.MANAGER);
    ServiceRequest request = requireRequestForManagerLocked(manager, requestId);
    if (request.getStatus() != ServiceRequest.ServiceRequestStatus.ACCEPTED
        && request.getStatus() != ServiceRequest.ServiceRequestStatus.CONTRACT_SIGNED
        && request.getStatus() != ServiceRequest.ServiceRequestStatus.ASSIGNED) {
      throw failure(
          HttpStatus.CONFLICT,
          "ASSIGNMENT_INVALID",
          "A signed contract is required before assignment");
    }
    ServiceRequestOccurrence occurrence =
        occurrences
            .findWithLockById(occurrenceId)
            .filter(value -> requestId.equals(value.getRequestId()))
            .orElseThrow(
                () ->
                    failure(HttpStatus.NOT_FOUND, "OCCURRENCE_NOT_FOUND", "Occurrence not found"));

    // Check per-occurrence signed contract gate
    if (!occurrence.hasSignedContract()) {
      // Legacy compatibility: ONE_TIME occurrence index 1 may inherit from request if uploaded
      // there
      if (request.getRequestType() == RequestType.ONE_TIME
          && occurrence.getOccurrenceIndex() == 1
          && request.getContractObjectKey() != null) {
        occurrence.recordSignedContract(
            request.getContractObjectKey(),
            request.getContractFileName(),
            request.getContractContentType(),
            request.getContractSizeBytes() != null ? request.getContractSizeBytes() : 0L,
            request.getContractChecksumSha256(),
            request.getContractUploadedByUserId() != null
                ? request.getContractUploadedByUserId()
                : manager.id());
        occurrences.saveAndFlush(occurrence);
      } else {
        throw failure(
            HttpStatus.CONFLICT,
            "OCCURRENCE_CONTRACT_REQUIRED",
            "Occurrence must have its own signed contract attached before assignment");
      }
    }

    requireRequestedOccurrenceWindow(request, input.plannedStartAt(), input.plannedEndAt());
    if (occurrences.hasScheduleConflict(
        input.inspectorId(), input.droneId(), input.plannedStartAt(), input.plannedEndAt())) {
      throw failure(
          HttpStatus.CONFLICT,
          "SCHEDULE_CONFLICT",
          "Selected Inspector or drone has another assigned contract occurrence during this window");
    }
    partners.lockEligibleInspector(input.inspectorId(), input.plannedStartAt());
    partners.lockEligibleDrone(input.droneId(), input.inspectorId());
    UUID inspectionId =
        assignment.assign(
            request.getOrganizationId(),
            request.getAssetId(),
            input.inspectorId(),
            input.droneId(),
            input.plannedStartAt(),
            input.plannedEndAt(),
            manager.id());
    occurrence.assign(
        input.inspectorId(),
        input.droneId(),
        inspectionId,
        input.plannedStartAt(),
        input.plannedEndAt(),
        manager.id());
    occurrences.saveAndFlush(occurrence);
    boolean anyPending =
        occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId).stream()
            .anyMatch(
                value ->
                    value.getStatus()
                        == ServiceRequestOccurrence.OccurrenceStatus.PENDING_ASSIGNMENT);
    if (!anyPending) {
      request.markAssigned();
      requests.saveAndFlush(request);
    }
    return toResponse(request);
  }

  /** Customer reviews and explicitly accepts the published report version. */
  @Transactional
  public ServiceRequestResponse customerAcceptReport(
      UUID actorId, UUID requestId, UUID occurrenceId, UUID versionId) {
    UserAccess.ActiveUser customer = requireActor(actorId, Mf2ActorPolicy.ActorKind.CUSTOMER);
    ServiceRequest request = requireCustomerRequestLocked(customer, requestId);
    ServiceRequestOccurrence occurrence =
        occurrences
            .findWithLockById(occurrenceId)
            .filter(value -> requestId.equals(value.getRequestId()))
            .orElseThrow(
                () ->
                    failure(HttpStatus.NOT_FOUND, "OCCURRENCE_NOT_FOUND", "Occurrence not found"));
    if (occurrence.getInspectionId() == null) {
      throw failure(
          HttpStatus.CONFLICT,
          "INSPECTION_NOT_ASSIGNED",
          "Occurrence has not been assigned an inspection");
    }
    if (versionId == null) {
      throw failure(
          HttpStatus.BAD_REQUEST, "REPORT_VERSION_REQUIRED", "Report version ID is required");
    }

    if (reportAccess.isPresent()
        && !reportAccess.get().isReportPublished(occurrence.getInspectionId(), versionId)) {
      throw failure(
          HttpStatus.CONFLICT,
          "REPORT_NOT_PUBLISHED",
          "Only a published report version can be accepted by the customer");
    }

    try {
      occurrence.acceptReport(versionId, customer.id());
    } catch (IllegalStateException ex) {
      throw failure(HttpStatus.CONFLICT, "REPORT_ACCEPTANCE_INVALID", ex.getMessage());
    }
    occurrences.saveAndFlush(occurrence);
    return toResponse(request);
  }

  /** Manager manually confirms exact matching bank transfer receipt from Customer. */
  @Transactional
  public ServiceRequestResponse confirmOccurrencePayment(
      UUID actorId, UUID requestId, UUID occurrenceId, ConfirmOccurrencePaymentRequest input) {
    UserAccess.ActiveUser manager = requireActor(actorId, Mf2ActorPolicy.ActorKind.MANAGER);
    ServiceRequest request = requireRequestForManagerLocked(manager, requestId);
    ServiceRequestOccurrence occurrence =
        occurrences
            .findWithLockById(occurrenceId)
            .filter(value -> requestId.equals(value.getRequestId()))
            .orElseThrow(
                () ->
                    failure(HttpStatus.NOT_FOUND, "OCCURRENCE_NOT_FOUND", "Occurrence not found"));
    if (occurrence.getReportAcceptedAt() == null
        || occurrence.getPaymentStatus() == OccurrencePaymentStatus.PENDING) {
      throw failure(
          HttpStatus.CONFLICT,
          "PAYMENT_NOT_DUE",
          "Customer must accept the published report before payment can be confirmed");
    }
    if (occurrence.getTotalPrice() != null
        && input.amount().compareTo(occurrence.getTotalPrice()) != 0) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "PAYMENT_AMOUNT_MISMATCH",
          "Payment amount must exactly match agreed total: " + occurrence.getTotalPrice());
    }
    try {
      occurrence.confirmPayment(input.paymentReference(), input.amount(), manager.id());
    } catch (IllegalStateException ex) {
      throw failure(HttpStatus.CONFLICT, "PAYMENT_CONFIRMATION_INVALID", ex.getMessage());
    }
    occurrences.saveAndFlush(occurrence);
    return toResponse(request);
  }

  /** Manager manually records external bank transfer payout to the assigned Inspector. */
  @Transactional
  public ServiceRequestResponse recordOccurrencePayout(
      UUID actorId, UUID requestId, UUID occurrenceId, RecordOccurrencePayoutRequest input) {
    UserAccess.ActiveUser manager = requireActor(actorId, Mf2ActorPolicy.ActorKind.MANAGER);
    ServiceRequest request = requireRequestForManagerLocked(manager, requestId);
    ServiceRequestOccurrence occurrence =
        occurrences
            .findWithLockById(occurrenceId)
            .filter(value -> requestId.equals(value.getRequestId()))
            .orElseThrow(
                () ->
                    failure(HttpStatus.NOT_FOUND, "OCCURRENCE_NOT_FOUND", "Occurrence not found"));
    if (occurrence.getReportAcceptedAt() == null
        || occurrence.getPaymentStatus() != OccurrencePaymentStatus.PAID) {
      throw failure(
          HttpStatus.CONFLICT,
          "PAYOUT_NOT_ELIGIBLE",
          "Inspector payout requires both customer report acceptance and confirmed customer payment");
    }
    if (occurrence.getInspectorId() == null) {
      throw failure(
          HttpStatus.CONFLICT, "INSPECTOR_NOT_ASSIGNED", "No inspector assigned to receive payout");
    }
    try {
      occurrence.recordPayout(input.payoutReference(), manager.id());
    } catch (IllegalStateException ex) {
      throw failure(HttpStatus.CONFLICT, "PAYOUT_RECORD_INVALID", ex.getMessage());
    }
    occurrences.saveAndFlush(occurrence);
    return toResponse(request);
  }

  /** Retrieves role-scoped financial details for an occurrence. */
  @Transactional(readOnly = true)
  public OccurrenceFinanceResponse getOccurrenceFinance(
      UUID actorId, UUID requestId, UUID occurrenceId) {
    UserAccess.ActiveUser actor = requireActor(actorId, null);
    ServiceRequest request = requireRequestForActor(actor, requestId);
    ServiceRequestOccurrence occurrence =
        occurrences
            .findById(occurrenceId)
            .filter(value -> requestId.equals(value.getRequestId()))
            .orElseThrow(
                () ->
                    failure(HttpStatus.NOT_FOUND, "OCCURRENCE_NOT_FOUND", "Occurrence not found"));

    if (actor.hasRole(Roles.ADMIN) || actor.hasRole(Roles.MANAGER)) {
      return OccurrenceFinanceResponse.forManager(occurrence);
    }
    if (actor.hasRole(Roles.CUSTOMER)
        && actor.organizationId() != null
        && actor.organizationId().equals(request.getOrganizationId())) {
      return OccurrenceFinanceResponse.forCustomer(occurrence);
    }
    if (actor.hasRole(Roles.INSPECTOR) && actor.id().equals(occurrence.getInspectorId())) {
      return OccurrenceFinanceResponse.forInspector(occurrence);
    }
    throw forbidden();
  }

  /** Customer creates a PayOS VietQR payment link for an occurrence awaiting payment. */
  @Transactional
  public PayOSPaymentLinkResponse createPayOSPaymentLink(
      UUID actorId, UUID requestId, UUID occurrenceId) {
    UserAccess.ActiveUser actor = requireActor(actorId, null);
    ServiceRequest request = requireRequestForActor(actor, requestId);
    if (!actor.hasRole(Roles.ADMIN) && !actor.hasRole(Roles.MANAGER)) {
      if (!actor.hasRole(Roles.CUSTOMER)
          || actor.organizationId() == null
          || !actor.organizationId().equals(request.getOrganizationId())) {
        throw forbidden();
      }
    }
    ServiceRequestOccurrence occurrence =
        occurrences
            .findWithLockById(occurrenceId)
            .filter(value -> requestId.equals(value.getRequestId()))
            .orElseThrow(
                () ->
                    failure(HttpStatus.NOT_FOUND, "OCCURRENCE_NOT_FOUND", "Occurrence not found"));

    if (occurrence.getReportAcceptedAt() == null
        || occurrence.getPaymentStatus() != OccurrencePaymentStatus.PAYMENT_DUE) {
      throw failure(
          HttpStatus.CONFLICT,
          "PAYMENT_NOT_DUE",
          "Customer must accept the published report before payment link can be created");
    }
    if (occurrence.getCurrency() != null && !"VND".equalsIgnoreCase(occurrence.getCurrency())) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "CURRENCY_UNSUPPORTED",
          "PayOS online checkout is only supported for VND contracts. Currency is "
              + occurrence.getCurrency());
    }
    if (occurrence.getTotalPrice() == null
        || occurrence.getTotalPrice().compareTo(java.math.BigDecimal.ZERO) <= 0) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "PAYMENT_AMOUNT_INVALID",
          "Occurrence total price must be greater than zero");
    }

    long orderCode;
    if (occurrence.getPayosOrderCode() != null && occurrence.getPayosOrderCode() > 0) {
      orderCode = occurrence.getPayosOrderCode();
    } else {
      Long nextCode = occurrences.getNextPayosOrderCode();
      orderCode = (nextCode != null) ? nextCode : System.currentTimeMillis();
    }

    long amount = occurrence.getTotalPrice().longValue();
    String description = "SDI OCC " + occurrence.getOccurrenceIndex();
    if (description.length() > 25) {
      description = description.substring(0, 25);
    }

    String returnUrl = null;
    String cancelUrl = null;
    if (occurrence.getInspectionId() != null) {
      returnUrl = payOSGateway.resolveReturnUrl(occurrence.getInspectionId().toString(), orderCode);
      cancelUrl = payOSGateway.resolveCancelUrl(occurrence.getInspectionId().toString());
    }

    PayOSGateway.PayOSPaymentLink link =
        payOSGateway.createPaymentLink(orderCode, amount, description, returnUrl, cancelUrl);

    occurrence.assignPayOSOrder(orderCode, link.paymentLinkId());
    occurrences.saveAndFlush(occurrence);

    return new PayOSPaymentLinkResponse(
        orderCode, link.checkoutUrl(), link.qrCode(), amount, link.paymentLinkId());
  }

  /** Webhook applies verified PayOS online payment to an occurrence. */
  @Transactional
  public void applyPayOSWebhookPayment(long orderCode, long amount, String reference) {
    ServiceRequestOccurrence occurrence =
        occurrences
            .findWithLockByPayosOrderCode(orderCode)
            .orElseThrow(
                () ->
                    failure(
                        HttpStatus.NOT_FOUND,
                        "OCCURRENCE_NOT_FOUND",
                        "Occurrence with orderCode " + orderCode + " not found"));

    if (occurrence.getPaymentStatus() == OccurrencePaymentStatus.PAID) {
      return; // Idempotent no-op
    }

    if (occurrence.getTotalPrice() == null
        || occurrence.getTotalPrice().compareTo(java.math.BigDecimal.valueOf(amount)) != 0) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "PAYMENT_AMOUNT_MISMATCH",
          "Payment amount "
              + amount
              + " does not match agreed occurrence price "
              + occurrence.getTotalPrice());
    }

    String paymentRef =
        (reference != null && !reference.isBlank()) ? reference.trim() : ("PAYOS-" + orderCode);
    occurrence.confirmOnlinePayment(paymentRef, occurrence.getTotalPrice(), "PAYOS");
    occurrences.saveAndFlush(occurrence);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean isPaymentDue(UUID inspectionId) {
    if (inspectionId == null) {
      return false;
    }
    return occurrences
        .findByInspectionId(inspectionId)
        .map(occurrence -> occurrence.getPaymentStatus() != OccurrencePaymentStatus.PAID)
        .orElse(false);
  }

  @Override
  @Transactional
  public void acceptReport(UUID actorId, UUID inspectionId, UUID reportVersionId) {
    if (actorId == null || inspectionId == null || reportVersionId == null) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "ARGUMENT_INVALID",
          "Actor, inspection and version IDs are required");
    }
    ServiceRequestOccurrence occurrence =
        occurrences
            .findWithLockByInspectionId(inspectionId)
            .orElseThrow(
                () ->
                    failure(
                        HttpStatus.NOT_FOUND,
                        "OCCURRENCE_NOT_FOUND",
                        "No contract occurrence linked to inspection"));
    ServiceRequest request =
        requests
            .findById(occurrence.getRequestId())
            .orElseThrow(
                () -> failure(HttpStatus.NOT_FOUND, REQUEST_NOT_FOUND, REQUEST_NOT_FOUND_DETAIL));
    customerAcceptReport(actorId, request.getId(), occurrence.getId(), reportVersionId);
  }

  @Override
  @Transactional
  public InspectionReportAcceptancePort.PayOSCheckoutDetails createPayOSPaymentLink(
      UUID actorId, UUID inspectionId) {
    if (actorId == null || inspectionId == null) {
      throw failure(
          HttpStatus.BAD_REQUEST, "ARGUMENT_INVALID", "Actor and inspection IDs are required");
    }
    ServiceRequestOccurrence occurrence =
        occurrences
            .findWithLockByInspectionId(inspectionId)
            .orElseThrow(
                () ->
                    failure(
                        HttpStatus.NOT_FOUND,
                        "OCCURRENCE_NOT_FOUND",
                        "No contract occurrence linked to inspection"));
    ServiceRequest request =
        requests
            .findById(occurrence.getRequestId())
            .orElseThrow(
                () -> failure(HttpStatus.NOT_FOUND, REQUEST_NOT_FOUND, REQUEST_NOT_FOUND_DETAIL));

    PayOSPaymentLinkResponse resp =
        createPayOSPaymentLink(actorId, request.getId(), occurrence.getId());
    return new InspectionReportAcceptancePort.PayOSCheckoutDetails(
        resp.orderCode(), resp.amount(), resp.paymentLinkId(), resp.checkoutUrl(), resp.qrCode());
  }

  private void materializeOccurrences(ServiceRequest request, ServiceRequestQuote quote) {
    if (!occurrences.findByRequestIdOrderByOccurrenceIndexAsc(request.getId()).isEmpty()) {
      return;
    }
    if (quote == null) {
      quote =
          quotes
              .findByRequestIdAndVersionNo(request.getId(), request.getAcceptedQuoteVersion())
              .orElse(null);
    }
    if (quote == null) {
      return;
    }

    int count = request.getRequestType() == RequestType.ONE_TIME ? 1 : 0;
    if (request.getRequestType() == RequestType.RECURRING && request.getOccurrenceCount() != null) {
      count = request.getOccurrenceCount();
    } else if (request.getRequestType() == RequestType.RECURRING) {
      count = estimateOccurrenceCount(request, quote);
    }

    ZoneId zone = ZoneId.of(request.getTimezone());
    LocalDate firstDate = quote.getFirstOccurrenceStartAt().atZone(zone).toLocalDate();
    LocalTime firstTime = quote.getFirstOccurrenceStartAt().atZone(zone).toLocalTime();
    Duration duration =
        Duration.between(quote.getFirstOccurrenceStartAt(), quote.getFirstOccurrenceEndAt());

    Optional<InspectionFinanceSettings> currentSettings =
        financeSettingsService != null
            ? financeSettingsService.findCurrentSettings()
            : Optional.empty();

    for (int index = 0; index < count; index++) {
      LocalDate date = index == 0 ? firstDate : recurrenceDate(request, firstDate, index);
      if (date.isAfter(request.getRequestedUntil())
          || (request.getRecurrenceEndDate() != null
              && date.isAfter(request.getRecurrenceEndDate()))) {
        throw failure(
            HttpStatus.BAD_REQUEST,
            "RECURRENCE_INVALID",
            "Occurrence falls outside the requested recurrence range");
      }
      Instant start =
          index == 0
              ? quote.getFirstOccurrenceStartAt()
              : date.atTime(firstTime).atZone(zone).toInstant();
      Instant end = start.plus(duration);
      if (date.isBefore(request.getRequestedFrom())
          || start.atZone(zone).toLocalTime().isBefore(request.getWindowStart())
          || end.atZone(zone).toLocalTime().isAfter(request.getWindowEnd())) {
        throw failure(
            HttpStatus.BAD_REQUEST,
            "RECURRENCE_INVALID",
            "Occurrence falls outside the requested date or daily time window");
      }
      ServiceRequestOccurrence occurrence =
          new ServiceRequestOccurrence(request.getId(), index + 1, start, end);

      if (quote.getEffectivePrice() != null) {
        if (currentSettings.isPresent() && currentSettings.get().isFullyConfigured()) {
          InspectionFinanceSettings s = currentSettings.get();
          InspectionFinanceCalculator.FinanceCalculation calc =
              InspectionFinanceCalculator.calculate(
                  quote.getEffectivePrice(),
                  s.getVatRate(),
                  s.getCommissionRate(),
                  s.getTncnRate());
          occurrence.snapshotFinance(
              quote.getCurrency(),
              calc.totalPrice(),
              calc.serviceBaseAmount(),
              calc.vatRate(),
              calc.vatAmount(),
              calc.commissionRate(),
              calc.commissionAmount(),
              calc.inspectorGrossPayout(),
              calc.tncnRate(),
              calc.tncnAmount(),
              calc.inspectorNetPayout());
        } else {
          occurrence.snapshotFinance(
              quote.getCurrency(),
              quote.getEffectivePrice(),
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null);
        }
      }
      occurrences.save(occurrence);
    }
    occurrences.flush();
  }

  private int estimateOccurrenceCount(ServiceRequest request, ServiceRequestQuote quote) {
    ZoneId zone = ZoneId.of(request.getTimezone());
    LocalDate firstDate = quote.getFirstOccurrenceStartAt().atZone(zone).toLocalDate();
    LocalDate lastDate =
        request.getRecurrenceEndDate().isBefore(request.getRequestedUntil())
            ? request.getRecurrenceEndDate()
            : request.getRequestedUntil();
    int count = 0;
    LocalDate date = firstDate;
    while (!date.isAfter(lastDate) && count < 100) {
      count++;
      date = recurrenceDate(request, firstDate, count);
    }
    if (count < 1) {
      throw failure(
          HttpStatus.BAD_REQUEST, "RECURRENCE_INVALID", "Recurring range contains no occurrences");
    }
    return count;
  }

  private LocalDate recurrenceDate(
      ServiceRequest request, LocalDate firstDate, int occurrenceIndex) {
    int offset = Math.multiplyExact(occurrenceIndex, request.getRecurrenceInterval());
    return switch (request.getRecurrenceUnit()) {
      case "DAY" -> firstDate.plusDays(offset);
      case "WEEK" -> firstDate.plusWeeks(offset);
      case "MONTH" -> firstDate.plusMonths(offset);
      default ->
          throw failure(
              HttpStatus.BAD_REQUEST, "RECURRENCE_INVALID", "Recurrence cadence is invalid");
    };
  }

  private void requireRequestedOccurrenceWindow(
      ServiceRequest request, Instant start, Instant end) {
    if (start == null || end == null || !end.isAfter(start)) {
      throw failure(HttpStatus.BAD_REQUEST, "INVALID_DATES", "Occurrence end must be after start");
    }
    ZoneId zone = ZoneId.of(request.getTimezone());
    LocalDate date = start.atZone(zone).toLocalDate();
    LocalDate endDate = end.minusNanos(1).atZone(zone).toLocalDate();
    if (date.isBefore(request.getRequestedFrom())
        || date.isAfter(request.getRequestedUntil())
        || endDate.isAfter(request.getRequestedUntil())) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "OUTSIDE_REQUESTED_WINDOW",
          "Occurrence must fit the requested date range");
    }
    LocalTime localStart = start.atZone(zone).toLocalTime();
    LocalTime localEnd = end.atZone(zone).toLocalTime();
    if (localStart.isBefore(request.getWindowStart()) || localEnd.isAfter(request.getWindowEnd())) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "OUTSIDE_REQUESTED_WINDOW",
          "Occurrence must fit the requested daily time window");
    }
  }

  private byte[] validateAndExtractFile(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw failure(
          HttpStatus.BAD_REQUEST, "CONTRACT_FILE_INVALID", "Signed contract file is required");
    }
    String contentType =
        file.getContentType() == null
            ? ""
            : file.getContentType().toLowerCase(java.util.Locale.ROOT);
    if (!ALLOWED_CONTRACT_TYPES.contains(contentType)) {
      throw failure(
          HttpStatus.UNSUPPORTED_MEDIA_TYPE,
          "CONTRACT_FILE_INVALID",
          "Only PDF, DOCX, JPEG, and PNG files are allowed");
    }
    if (file.getSize() > MAX_CONTRACT_SIZE_BYTES) {
      throw failure(
          HttpStatus.PAYLOAD_TOO_LARGE,
          "CONTRACT_TOO_LARGE",
          "Maximum signed contract size is 10 MB");
    }
    byte[] content;
    try {
      content = file.getBytes();
    } catch (IOException exception) {
      throw failure(
          HttpStatus.BAD_REQUEST, "CONTRACT_FILE_INVALID", "The signed contract could not be read");
    }
    if (content.length == 0 || content.length > MAX_CONTRACT_SIZE_BYTES) {
      throw failure(
          HttpStatus.PAYLOAD_TOO_LARGE,
          "CONTRACT_FILE_INVALID",
          "The signed contract size is invalid");
    }
    if (!hasExpectedSignature(content, contentType)) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "CONTRACT_FILE_INVALID",
          "Uploaded file content does not match its declared type");
    }
    return content;
  }

  private EvidenceObjectStore requireStore() {
    return objectStore.orElseThrow(
        () ->
            failure(
                HttpStatus.BAD_GATEWAY,
                "STORAGE_UNAVAILABLE",
                "Contract storage is not configured"));
  }

  private ServiceRequestResponse toResponse(ServiceRequest request) {
    return ServiceRequestResponse.from(
        request,
        quotes.findByRequestIdOrderByVersionNoDesc(request.getId()).stream()
            .map(ServiceRequestQuoteResponse::from)
            .toList(),
        occurrences.findByRequestIdOrderByOccurrenceIndexAsc(request.getId()).stream()
            .map(ServiceRequestOccurrenceResponse::from)
            .toList(),
        messages.findByRequestIdOrderByCreatedAtAscIdAsc(request.getId()).stream()
            .map(ServiceRequestMessageResponse::from)
            .toList());
  }

  private ServiceRequest requireRequestForActor(UserAccess.ActiveUser actor, UUID requestId) {
    if (actorPolicy.actorKind(actor.roles(), actor.organizationId())
        == Mf2ActorPolicy.ActorKind.CUSTOMER) {
      return requireCustomerRequest(actor, requestId);
    }
    if (actorPolicy.actorKind(actor.roles(), actor.organizationId())
        == Mf2ActorPolicy.ActorKind.MANAGER) {
      return requireRequestForManager(actor, requestId);
    }
    if (actor.hasRole(Roles.INSPECTOR)) {
      return requests
          .findById(requestId)
          .orElseThrow(
              () -> failure(HttpStatus.NOT_FOUND, REQUEST_NOT_FOUND, REQUEST_NOT_FOUND_DETAIL));
    }
    throw forbidden();
  }

  private ServiceRequest requireCustomerRequest(UserAccess.ActiveUser customer, UUID requestId) {
    return requests
        .findByIdAndOrganizationId(requestId, customer.organizationId())
        .orElseThrow(
            () -> failure(HttpStatus.NOT_FOUND, REQUEST_NOT_FOUND, REQUEST_NOT_FOUND_DETAIL));
  }

  private ServiceRequest requireCustomerRequestLocked(
      UserAccess.ActiveUser customer, UUID requestId) {
    return requests
        .findWithLockByIdAndOrganizationId(requestId, customer.organizationId())
        .orElseThrow(
            () -> failure(HttpStatus.NOT_FOUND, REQUEST_NOT_FOUND, REQUEST_NOT_FOUND_DETAIL));
  }

  private ServiceRequest requireRequestForManager(UserAccess.ActiveUser manager, UUID requestId) {
    return requests
        .findById(requestId)
        .orElseThrow(
            () -> failure(HttpStatus.NOT_FOUND, REQUEST_NOT_FOUND, REQUEST_NOT_FOUND_DETAIL));
  }

  private ServiceRequest requireRequestForManagerLocked(
      UserAccess.ActiveUser manager, UUID requestId) {
    return requests
        .findWithLockById(requestId)
        .orElseThrow(
            () -> failure(HttpStatus.NOT_FOUND, REQUEST_NOT_FOUND, REQUEST_NOT_FOUND_DETAIL));
  }

  private UserAccess.ActiveUser requireActor(UUID actorId, Mf2ActorPolicy.ActorKind expected) {
    UserAccess.ActiveUser actor = users.findActiveUser(actorId).orElseThrow(this::forbidden);
    if (actorPolicy.actorKind(actor.roles(), actor.organizationId())
            == Mf2ActorPolicy.ActorKind.NONE
        && !actor.hasRole(Roles.INSPECTOR)) {
      throw forbidden();
    }
    if (expected != null
        && actorPolicy.actorKind(actor.roles(), actor.organizationId()) != expected) {
      throw forbidden();
    }
    return actor;
  }

  private BusinessException forbidden() {
    return failure(HttpStatus.FORBIDDEN, "FORBIDDEN", "The actor cannot access this MF2 action");
  }

  private static BusinessException failure(HttpStatus status, String code, String detail) {
    return new BusinessException(status, code, detail);
  }

  private static String sanitizeFileName(String name) {
    if (name == null || name.isBlank()) return "signed-contract";
    String sanitized = name.replaceAll("[\\\\/\\r\\n]", "_");
    return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
  }

  private static boolean hasExpectedSignature(byte[] bytes, String contentType) {
    if ("application/pdf".equals(contentType)) {
      return bytes.length >= 5
          && bytes[0] == '%'
          && bytes[1] == 'P'
          && bytes[2] == 'D'
          && bytes[3] == 'F'
          && bytes[4] == '-';
    }
    if ("application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        .equals(contentType)) {
      return bytes.length >= 4
          && bytes[0] == 0x50
          && bytes[1] == 0x4B
          && bytes[2] == 0x03
          && bytes[3] == 0x04;
    }
    if ("image/png".equals(contentType)) {
      byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
      return bytes.length >= signature.length
          && java.util.Arrays.equals(java.util.Arrays.copyOf(bytes, signature.length), signature);
    }
    return bytes.length >= 3
        && (bytes[0] & 0xff) == 0xff
        && (bytes[1] & 0xff) == 0xd8
        && (bytes[2] & 0xff) == 0xff;
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }

  public record SignedContractFile(
      String fileName, String contentType, Long sizeBytes, InputStream stream) {}

  public record DownloadedContract(
      String fileName, String contentType, Long sizeBytes, InputStream stream) {}

  private static final class DisabledPayOSGatewayDummy implements PayOSGateway {
    @Override
    public PayOSPaymentLink createPaymentLink(
        long orderCode, long amount, String description, String returnUrl, String cancelUrl) {
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "PAYMENT_GATEWAY_DISABLED",
          "Online payments via PayOS are not enabled. Use corporate wire transfer instead.");
    }

    @Override
    public boolean verifyWebhookSignature(
        java.util.Map<String, Object> data, String receivedSignature) {
      return false;
    }
  }
}
