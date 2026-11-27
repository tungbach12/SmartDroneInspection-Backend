package com.smartdroneinspection.contracts.service;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.enums.AssetStatus;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.contracts.api.dto.request.AcceptServiceQuoteRequest;
import com.smartdroneinspection.contracts.api.dto.request.AssignOccurrenceRequest;
import com.smartdroneinspection.contracts.api.dto.request.CancelOccurrenceRefundRequest;
import com.smartdroneinspection.contracts.api.dto.request.CompleteOccurrenceRefundRequest;
import com.smartdroneinspection.contracts.api.dto.request.ConfirmOccurrencePaymentRequest;
import com.smartdroneinspection.contracts.api.dto.request.CreateServiceRequest;
import com.smartdroneinspection.contracts.api.dto.request.EvaluatePartnerCandidatesRequest;
import com.smartdroneinspection.contracts.api.dto.request.InitiateOccurrenceRefundRequest;
import com.smartdroneinspection.contracts.api.dto.request.IssueServiceQuoteRequest;
import com.smartdroneinspection.contracts.api.dto.request.ReconcileRefundAllocationRequest;
import com.smartdroneinspection.contracts.api.dto.request.RecordInspectorReceivableRecoveryRequest;
import com.smartdroneinspection.contracts.api.dto.request.RecordOccurrencePayoutRequest;
import com.smartdroneinspection.contracts.api.dto.response.FinanceHistoryItemResponse;
import com.smartdroneinspection.contracts.api.dto.response.FinanceHistoryPageResponse;
import com.smartdroneinspection.contracts.api.dto.response.ManagerFinanceHistoryResponse;
import com.smartdroneinspection.contracts.api.dto.response.OccurrenceFinanceResponse;
import com.smartdroneinspection.contracts.api.dto.response.PartnerCandidatePageResponse;
import com.smartdroneinspection.contracts.api.dto.response.PartnerCandidateResponse;
import com.smartdroneinspection.contracts.api.dto.response.SepayPaymentLinkResponse;
import com.smartdroneinspection.contracts.api.dto.response.ServiceManagerContactResponse;
import com.smartdroneinspection.contracts.api.dto.response.ServiceRequestMessageResponse;
import com.smartdroneinspection.contracts.api.dto.response.ServiceRequestOccurrenceResponse;
import com.smartdroneinspection.contracts.api.dto.response.ServiceRequestQuoteResponse;
import com.smartdroneinspection.contracts.api.dto.response.ServiceRequestResponse;
import com.smartdroneinspection.contracts.api.dto.response.ServiceRequestSchedulingPolicyResponse;
import com.smartdroneinspection.contracts.api.dto.response.VnPayPaymentLinkResponse;
import com.smartdroneinspection.contracts.domain.InspectionFinanceSettings;
import com.smartdroneinspection.contracts.domain.ServiceRefund;
import com.smartdroneinspection.contracts.domain.ServiceRequest;
import com.smartdroneinspection.contracts.domain.ServiceRequest.RequestType;
import com.smartdroneinspection.contracts.domain.ServiceRequestMessage;
import com.smartdroneinspection.contracts.domain.ServiceRequestMessageAttachment;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence.OccurrencePaymentStatus;
import com.smartdroneinspection.contracts.domain.ServiceRequestQuote;
import com.smartdroneinspection.contracts.events.CustomerReportAcceptedEvent;
import com.smartdroneinspection.contracts.events.InspectorPayoutSettledEvent;
import com.smartdroneinspection.contracts.events.InspectorReceivableRecoveryEvent;
import com.smartdroneinspection.contracts.events.OccurrenceRefundEvent;
import com.smartdroneinspection.contracts.events.PaymentSettledEvent;
import com.smartdroneinspection.contracts.events.ServiceQuoteAcceptedEvent;
import com.smartdroneinspection.contracts.events.ServiceQuoteSavedEvent;
import com.smartdroneinspection.contracts.events.ServiceRequestCreatedEvent;
import com.smartdroneinspection.contracts.events.ServiceRequestManagerChangedEvent;
import com.smartdroneinspection.contracts.events.SignedContractUploadedEvent;
import com.smartdroneinspection.contracts.gateway.SepayGateway;
import com.smartdroneinspection.contracts.gateway.VnPayGateway;
import com.smartdroneinspection.contracts.repository.InspectorReceivableRepository;
import com.smartdroneinspection.contracts.repository.ServiceRefundRepository;
import com.smartdroneinspection.contracts.repository.ServiceRequestMessageAttachmentRepository;
import com.smartdroneinspection.contracts.repository.ServiceRequestMessageRepository;
import com.smartdroneinspection.contracts.repository.ServiceRequestOccurrenceRepository;
import com.smartdroneinspection.contracts.repository.ServiceRequestQuoteRepository;
import com.smartdroneinspection.contracts.repository.ServiceRequestRepository;
import com.smartdroneinspection.inspections.spi.InspectionReportAccess;
import com.smartdroneinspection.inspections.spi.Mf2InspectionAssignment;
import com.smartdroneinspection.shared.api.InspectionPaymentGate;
import com.smartdroneinspection.shared.api.InspectionReportAcceptancePort;
import com.smartdroneinspection.shared.api.PlatformOperationsPolicyPort;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.UserAccess;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ContractWorkflowService
    implements InspectionPaymentGate, InspectionReportAcceptancePort {

  static final long MAX_CONTRACT_SIZE_BYTES = 10L * 1024 * 1024;
  static final long MAX_MESSAGE_ATTACHMENT_TOTAL_BYTES = 20L * 1024 * 1024;
  static final int MAX_MESSAGE_ATTACHMENT_COUNT = 5;
  private static final long MAX_MESSAGE_ATTACHMENT_FILE_BYTES = 10L * 1024 * 1024;
  private static final String STORAGE_UNAVAILABLE = "STORAGE_UNAVAILABLE";
  private static final String REQUEST_NOT_FOUND = "REQUEST_NOT_FOUND";
  private static final String REQUEST_NOT_FOUND_DETAIL = "Service request not found";
  private static final Set<String> ALLOWED_CONTRACT_TYPES =
      Set.of(
          "application/pdf",
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
          "image/jpeg",
          "image/png");
  private static final String DOCX_CONTENT_TYPE =
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
  private static final Set<String> ALLOWED_MESSAGE_ATTACHMENT_TYPES =
      Set.of("image/png", "image/jpeg", "image/webp", "application/pdf", DOCX_CONTENT_TYPE);

  private final ServiceRequestRepository requests;
  private final ServiceRequestMessageRepository messages;
  private Optional<ServiceRequestMessageAttachmentRepository> messageAttachments = Optional.empty();
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
  private final SepayGateway sepayGateway;
  private final VnPayGateway vnPayGateway;
  private final JdbcTemplate jdbcTemplate;
  private final ApplicationEventPublisher events;
  private Optional<ServiceRefundRepository> refundRepository = Optional.empty();
  private Optional<InspectorReceivableRepository> receivables = Optional.empty();
  private Optional<PlatformOperationsPolicyPort> operationsSettings = Optional.empty();
  private PaymentDueProperties paymentDueProperties = new PaymentDueProperties();

  @Autowired
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
      SepayGateway sepayGateway,
      VnPayGateway vnPayGateway,
      JdbcTemplate jdbcTemplate,
      ApplicationEventPublisher events) {
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
    this.sepayGateway = sepayGateway;
    this.vnPayGateway = vnPayGateway;
    this.jdbcTemplate = jdbcTemplate;
    this.events = events;
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
      Optional<InspectionReportAccess> reportAccess,
      SepayGateway sepayGateway,
      VnPayGateway vnPayGateway,
      JdbcTemplate jdbcTemplate) {
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
        sepayGateway,
        vnPayGateway,
        jdbcTemplate,
        null);
  }

  @Autowired
  void setMessageAttachments(
      Optional<ServiceRequestMessageAttachmentRepository> messageAttachments) {
    this.messageAttachments = messageAttachments != null ? messageAttachments : Optional.empty();
  }

  @Autowired
  void setRefundRepository(Optional<ServiceRefundRepository> refundRepository) {
    this.refundRepository = refundRepository != null ? refundRepository : Optional.empty();
  }

  @Autowired
  void setReceivables(Optional<InspectorReceivableRepository> receivables) {
    this.receivables = receivables != null ? receivables : Optional.empty();
  }

  @Autowired
  void setOperationsSettings(Optional<PlatformOperationsPolicyPort> operationsSettings) {
    this.operationsSettings = operationsSettings != null ? operationsSettings : Optional.empty();
  }

  @Autowired
  void setPaymentDueProperties(PaymentDueProperties paymentDueProperties) {
    this.paymentDueProperties =
        paymentDueProperties != null ? paymentDueProperties : new PaymentDueProperties();
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
      Optional<InspectionReportAccess> reportAccess,
      SepayGateway sepayGateway,
      VnPayGateway vnPayGateway) {
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
        sepayGateway,
        vnPayGateway,
        null);
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
      Optional<InspectionReportAccess> reportAccess,
      SepayGateway sepayGateway) {
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
        sepayGateway,
        new DisabledVnPayGatewayDummy(),
        null);
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
        new DisabledSepayGatewayDummy(),
        new DisabledVnPayGatewayDummy(),
        null);
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
        new DisabledSepayGatewayDummy(),
        new DisabledVnPayGatewayDummy(),
        null);
  }

  @Transactional
  public ServiceRequestResponse create(UUID actorId, CreateServiceRequest input) {
    UserAccess.ActiveUser actor = requireActor(actorId, Mf2ActorPolicy.ActorKind.CUSTOMER);
    UUID sourceInspectionId = input.sourceInspectionId();
    Asset asset =
        (sourceInspectionId == null
                ? assets.findByIdAndOwnerCustomerId(input.assetId(), actor.ownerCustomerId())
                : assets.findWithLockByIdAndOwnerCustomerId(
                    input.assetId(), actor.ownerCustomerId()))
            .filter(value -> value.getStatus() != AssetStatus.RETIRED)
            .orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "ASSET_NOT_FOUND", "Asset not found"));
    if (sourceInspectionId != null) {
      assignment.validateReinspectionSource(
          actor.ownerCustomerId(), asset.getId(), sourceInspectionId);
    }
    if (requests.existsActiveRequestForAsset(
        actor.ownerCustomerId(), asset.getId(), sourceInspectionId == null, sourceInspectionId)) {
      if (sourceInspectionId != null) {
        throw failure(
            HttpStatus.CONFLICT,
            "REINSPECTION_ALREADY_OPEN",
            "An active re-inspection request already exists for this source inspection");
      }
      throw failure(
          HttpStatus.CONFLICT,
          "DUPLICATE_ACTIVE_REQUEST",
          "This asset already has an active service request in progress");
    }
    LocalTime start = input.windowStart() == null ? LocalTime.of(8, 0) : input.windowStart();
    LocalTime end = input.windowEnd() == null ? LocalTime.of(17, 0) : input.windowEnd();
    ZoneId customerZone;
    try {
      customerZone = ZoneId.of(input.timezone());
    } catch (java.time.DateTimeException exception) {
      throw failure(HttpStatus.BAD_REQUEST, "REQUEST_INVALID", "Customer timezone is invalid");
    }
    LocalDate customerToday = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
    int leadDays = resolveServiceRequestLeadDays();
    LocalDate minimumStartDate = customerToday.plusDays(leadDays);
    if (input.requestedFrom() == null || input.requestedFrom().isBefore(minimumStartDate)) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "REQUEST_TOO_SOON",
          "The first inspection date must be at least "
              + leadDays
              + " days from today to allow contract and flight-permit processing");
    }

    LocalDate requestedUntil;
    if (input.requestType() == RequestType.ONE_TIME) {
      requestedUntil = input.requestedFrom();
    } else if (input.occurrenceCount() != null) {
      if (input.occurrenceCount() < 2 || input.occurrenceCount() > 100) {
        throw failure(
            HttpStatus.BAD_REQUEST,
            "RECURRENCE_INVALID",
            "A recurring request must contain between 2 and 100 occurrences");
      }
      requestedUntil =
          recurrenceDate(
              input.recurrenceUnit(),
              input.recurrenceInterval(),
              input.requestedFrom(),
              input.occurrenceCount() - 1);
    } else if (input.recurrenceEndDate() != null) {
      requestedUntil = input.recurrenceEndDate();
      int estimatedCount = 0;
      for (int index = 0; index < 101; index++) {
        LocalDate occurrenceDate =
            recurrenceDate(
                input.recurrenceUnit(), input.recurrenceInterval(), input.requestedFrom(), index);
        if (occurrenceDate.isAfter(requestedUntil)) break;
        estimatedCount++;
      }
      if (estimatedCount < 2 || estimatedCount > 100) {
        throw failure(
            HttpStatus.BAD_REQUEST,
            "RECURRENCE_INVALID",
            "A recurring request must contain between 2 and 100 occurrences before its end date");
      }
    } else {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "REQUEST_INVALID",
          "A recurring request requires an occurrence count or recurrence end date");
    }

    String description = input.description();
    if (!StringUtils.hasText(description)) {
      String cadence =
          input.requestType() == RequestType.ONE_TIME
              ? "Inspection"
              : input.recurrenceInterval() != null && input.recurrenceInterval() == 1
                  ? "Monthly inspection"
                  : "Every " + input.recurrenceInterval() + "-month inspection";
      description = asset.getName() + " · " + cadence;
    }

    ServiceRequest request;
    try {
      request =
          new ServiceRequest(
              actor.ownerCustomerId(),
              asset.getId(),
              actor.id(),
              description,
              input.requestedFrom(),
              requestedUntil,
              input.timezone(),
              start,
              end,
              input.requestType(),
              input.recurrenceUnit(),
              input.recurrenceInterval(),
              input.occurrenceCount(),
              input.recurrenceEndDate(),
              input.sourceInspectionId());
    } catch (IllegalArgumentException | ArithmeticException exception) {
      throw failure(HttpStatus.BAD_REQUEST, "REQUEST_INVALID", exception.getMessage());
    }
    request.markCustomerOwned(actor.id());
    ServiceRequest saved = requests.saveAndFlush(request);
    if (events != null) {
      events.publishEvent(
          new ServiceRequestCreatedEvent(
              saved.getId(),
              saved.getOwnerCustomerId(),
              saved.getDescription(),
              saved.getRequestedFrom()));
    }
    return toResponse(saved);
  }

  @Transactional(readOnly = true)
  public ServiceRequestSchedulingPolicyResponse getSchedulingPolicy(UUID actorId) {
    requireActor(actorId, null);
    return new ServiceRequestSchedulingPolicyResponse(resolveServiceRequestLeadDays());
  }

  private int resolveServiceRequestLeadDays() {
    return operationsSettings
        .map(PlatformOperationsPolicyPort::getServiceRequestLeadDays)
        .orElse(PlatformOperationsPolicyPort.DEFAULT_SERVICE_REQUEST_LEAD_DAYS);
  }

  @Transactional(readOnly = true)
  public List<ServiceRequestResponse> list(UUID actorId) {
    UserAccess.ActiveUser actor = requireActor(actorId, null);
    if (actorPolicy.actorKind(actor.roles()) == Mf2ActorPolicy.ActorKind.CUSTOMER) {
      List<ServiceRequest> customerRequests =
          requests.findByOwnerCustomerIdOrderByCreatedAtDesc(actor.ownerCustomerId());
      Map<UUID, Asset> assetById = loadAssetMap(customerRequests);
      return customerRequests.stream()
          .map(
              request ->
                  toResponse(request, true, null, true, null, assetById.get(request.getAssetId())))
          .toList();
    }
    if (actor.hasRole(Roles.ADMIN)) {
      return requests.findAllByOrderByCreatedAtDesc().stream()
          .map(this::toAdminAssignmentResponse)
          .toList();
    }
    if (actorPolicy.actorKind(actor.roles()) == Mf2ActorPolicy.ActorKind.MANAGER) {
      List<ServiceRequest> queue = requests.findManagerQueue(actor.id());
      Map<UUID, Asset> assetById = loadAssetMap(queue);
      Map<UUID, UserAccess.ActiveUser> customersById = loadRequestCustomers(queue);
      return queue.stream()
          .map(
              request ->
                  withAssignmentOptions(
                      actor,
                      toResponse(
                          request,
                          false,
                          actor,
                          request.getAssignedManagerId() != null
                              && request.getAssignedManagerId().equals(actor.id()),
                          customersById.get(request.getOwnerCustomerId()),
                          assetById.get(request.getAssetId()))))
          .toList();
    }
    throw forbidden();
  }

  @Transactional(readOnly = true)
  public ServiceRequestResponse get(UUID actorId, UUID requestId) {
    UserAccess.ActiveUser actor = requireActor(actorId, null);
    if (actor.hasRole(Roles.ADMIN)) {
      // ADMIN holds only the assignment registry: a pure ADMIN has actor kind NONE, so it must be
      // resolved before requireRequestForActor rejects it, and it never gets Manager details.
      return toAdminAssignmentResponse(requireAdminRequest(actor, requestId));
    }
    boolean includeManagerContact =
        actorPolicy.actorKind(actor.roles()) == Mf2ActorPolicy.ActorKind.CUSTOMER;
    ServiceRequest request = requireRequestForActor(actor, requestId);
    boolean isManager = actorPolicy.actorKind(actor.roles()) == Mf2ActorPolicy.ActorKind.MANAGER;
    boolean includeManagerProfile = includeManagerContact && request.getAssignedManagerId() != null;
    boolean managerOwnsRequest = isManager && actor.id().equals(request.getAssignedManagerId());
    UserAccess.ActiveUser ownerCustomer =
        isManager ? users.findActiveUser(request.getOwnerCustomerId()).orElse(null) : null;
    ServiceRequestResponse response =
        toResponse(
            request,
            includeManagerProfile,
            isManager ? actor : null,
            !isManager || managerOwnsRequest,
            ownerCustomer);
    return isManager ? withAssignmentOptions(actor, response) : response;
  }

  @Transactional
  public ServiceRequestResponse claimRequest(UUID actorId, UUID requestId) {
    UserAccess.ActiveUser manager = requireActor(actorId, Mf2ActorPolicy.ActorKind.MANAGER);
    ServiceRequest request =
        requests
            .findWithLockById(requestId)
            .orElseThrow(
                () -> failure(HttpStatus.NOT_FOUND, REQUEST_NOT_FOUND, REQUEST_NOT_FOUND_DETAIL));
    if (request.getStatus() != ServiceRequest.ServiceRequestStatus.OPEN
        && request.getStatus() != ServiceRequest.ServiceRequestStatus.QUOTED) {
      throw failure(
          HttpStatus.CONFLICT,
          "REQUEST_NOT_CLAIMABLE",
          "Only open or quoted service requests can be claimed");
    }
    if (request.getAssignedManagerId() != null) {
      throw failure(
          HttpStatus.CONFLICT,
          "REQUEST_ALREADY_ASSIGNED",
          "This service request is already assigned to a Manager");
    }
    Instant assignedAt = Instant.now();
    try {
      request.claimByManager(manager.id(), assignedAt);
    } catch (IllegalStateException exception) {
      throw failure(HttpStatus.CONFLICT, "REQUEST_ASSIGNMENT_INVALID", exception.getMessage());
    }
    requests.saveAndFlush(request);
    recordManagerAssignmentAudit(manager.id(), request, null, manager.id(), "CLAIMED");
    return withAssignmentOptions(
        manager,
        toResponse(
            request,
            false,
            manager,
            true,
            users.findActiveUser(request.getOwnerCustomerId()).orElse(null)));
  }

  @Transactional
  public ServiceRequestResponse reassignRequest(UUID actorId, UUID requestId, UUID newManagerId) {
    UserAccess.ActiveUser admin = requireAdmin(actorId);
    ServiceRequest request = requireAdminRequestLocked(admin, requestId);
    UserAccess.ServiceManagerContact newManager =
        users
            .findActiveServiceManager(newManagerId)
            .orElseThrow(
                () ->
                    failure(
                        HttpStatus.BAD_REQUEST,
                        "MANAGER_NOT_ACTIVE",
                        "The selected assignee is not an active Manager"));
    UUID previousManagerId = request.getAssignedManagerId();
    Instant now = Instant.now();
    request.reassignManager(newManager.id(), admin.id(), now);
    requests.saveAndFlush(request);
    recordManagerAssignmentAudit(
        admin.id(),
        request,
        previousManagerId,
        newManager.id(),
        previousManagerId == null ? "ASSIGNED" : "REASSIGNED");
    if (events != null) {
      events.publishEvent(
          new ServiceRequestManagerChangedEvent(
              request.getId(),
              request.getOwnerCustomerId(),
              previousManagerId,
              newManager.id(),
              previousManagerId == null ? "ASSIGNED" : "REASSIGNED",
              request.getDescription(),
              now));
    }
    return toAdminAssignmentResponse(request);
  }

  @Transactional
  public ServiceRequestResponse unassignRequest(UUID actorId, UUID requestId) {
    UserAccess.ActiveUser admin = requireAdmin(actorId);
    ServiceRequest request = requireAdminRequestLocked(admin, requestId);
    UUID previousManagerId = request.getAssignedManagerId();
    if (previousManagerId == null) {
      throw failure(
          HttpStatus.CONFLICT,
          "REQUEST_NOT_ASSIGNED",
          "This service request has no assigned Manager");
    }
    Instant now = Instant.now();
    request.unassignManager(admin.id(), now);
    requests.saveAndFlush(request);
    recordManagerAssignmentAudit(admin.id(), request, previousManagerId, null, "UNASSIGNED");
    if (events != null) {
      events.publishEvent(
          new ServiceRequestManagerChangedEvent(
              request.getId(),
              request.getOwnerCustomerId(),
              previousManagerId,
              null,
              "UNASSIGNED",
              request.getDescription(),
              now));
    }
    return toAdminAssignmentResponse(request);
  }

  @Transactional
  public ServiceRequestResponse postMessage(UUID actorId, UUID requestId, String body) {
    return postMessage(actorId, requestId, body, List.of());
  }

  @Transactional
  public ServiceRequestResponse postMessage(
      UUID actorId, UUID requestId, String body, List<MultipartFile> files) {
    UserAccess.ActiveUser actor = requireActor(actorId, null);
    ServiceRequest request = requireRequestForMessageMutation(actor, requestId);
    requireCanUseRequestMessages(actor, request);
    List<MessageAttachmentUpload> uploads = validateMessageAttachments(files);
    if (!StringUtils.hasText(body) && uploads.isEmpty()) {
      throw failure(
          HttpStatus.BAD_REQUEST, "MESSAGE_INVALID", "Message text or an attachment is required");
    }
    ServiceRequestMessage message;
    try {
      message = new ServiceRequestMessage(request.getId(), actor.id(), body, !uploads.isEmpty());
    } catch (IllegalArgumentException exception) {
      throw failure(HttpStatus.BAD_REQUEST, "MESSAGE_INVALID", exception.getMessage());
    }

    EvidenceObjectStore store = uploads.isEmpty() ? null : requireStore();
    List<String> storedKeys = new java.util.ArrayList<>();
    try {
      for (MessageAttachmentUpload upload : uploads) {
        String objectKey =
            "contracts/requests/"
                + requestId
                + "/messages/"
                + UUID.randomUUID()
                + "/"
                + upload.fileName();
        store.put(
            objectKey,
            upload.contentType(),
            upload.content().length,
            new ByteArrayInputStream(upload.content()));
        storedKeys.add(objectKey);
        message.addAttachment(
            upload.fileName(),
            upload.contentType(),
            upload.content().length,
            sha256(upload.content()),
            objectKey);
      }
      messages.saveAndFlush(message);
      if (!message.getAttachments().isEmpty()) {
        messageAttachments
            .orElseThrow(
                () ->
                    failure(
                        HttpStatus.BAD_GATEWAY,
                        STORAGE_UNAVAILABLE,
                        "Message attachment persistence is not configured"))
            .saveAllAndFlush(message.getAttachments());
      }
    } catch (IOException exception) {
      deleteStoredObjects(store, storedKeys);
      throw failure(HttpStatus.BAD_GATEWAY, "STORAGE_FAILED", "Failed to store message attachment");
    } catch (RuntimeException exception) {
      deleteStoredObjects(store, storedKeys);
      throw exception;
    }
    return toResponse(request);
  }

  @Transactional(readOnly = true)
  public MessageAttachmentFile openMessageAttachment(
      UUID actorId, UUID requestId, UUID messageId, UUID attachmentId) {
    UserAccess.ActiveUser actor = requireActor(actorId, null);
    ServiceRequest request = requireRequestForMessageRead(actor, requestId);
    requireCanUseRequestMessages(actor, request);
    ServiceRequestMessage message =
        messages
            .findById(messageId)
            .filter(value -> requestId.equals(value.getRequestId()))
            .orElseThrow(
                () -> failure(HttpStatus.NOT_FOUND, "MESSAGE_NOT_FOUND", "Message not found"));
    ServiceRequestMessageAttachment attachment =
        messageAttachments
            .flatMap(
                repository ->
                    repository.findByIdAndMessage_IdAndMessage_RequestId(
                        attachmentId, messageId, requestId))
            .orElseThrow(
                () ->
                    failure(
                        HttpStatus.NOT_FOUND,
                        "MESSAGE_ATTACHMENT_NOT_FOUND",
                        "Message attachment not found"));
    try {
      return new MessageAttachmentFile(
          attachment.getFileName(),
          attachment.getContentType(),
          attachment.getSizeBytes(),
          objectStore
              .orElseThrow(
                  () ->
                      failure(
                          HttpStatus.BAD_GATEWAY,
                          STORAGE_UNAVAILABLE,
                          "Message attachment storage is not configured"))
              .open(attachment.getObjectKey()));
    } catch (IOException exception) {
      throw failure(
          HttpStatus.BAD_GATEWAY, "STORAGE_FAILED", "Message attachment could not be opened");
    }
  }

  private ServiceRequest requireRequestForMessageRead(UserAccess.ActiveUser actor, UUID requestId) {
    if (actorPolicy.actorKind(actor.roles()) == Mf2ActorPolicy.ActorKind.CUSTOMER) {
      return requireCustomerRequest(actor, requestId);
    }
    if (actorPolicy.actorKind(actor.roles()) == Mf2ActorPolicy.ActorKind.MANAGER) {
      return requireRequestForManager(actor, requestId);
    }
    throw forbidden();
  }

  private ServiceRequest requireRequestForMessageMutation(
      UserAccess.ActiveUser actor, UUID requestId) {
    if (actorPolicy.actorKind(actor.roles()) == Mf2ActorPolicy.ActorKind.CUSTOMER) {
      return requireCustomerRequestLocked(actor, requestId);
    }
    if (actorPolicy.actorKind(actor.roles()) == Mf2ActorPolicy.ActorKind.MANAGER) {
      return requireRequestForManagerLocked(actor, requestId);
    }
    throw forbidden();
  }

  private void requireCanUseRequestMessages(UserAccess.ActiveUser actor, ServiceRequest request) {
    if (actor.hasRole(Roles.ADMIN)) {
      throw forbidden();
    }
    if (actorPolicy.actorKind(actor.roles()) == Mf2ActorPolicy.ActorKind.MANAGER) {
      requireAssignedManager(request, actor);
    }
  }

  private List<MessageAttachmentUpload> validateMessageAttachments(List<MultipartFile> files) {
    if (files == null || files.isEmpty()) return List.of();
    if (files.size() > MAX_MESSAGE_ATTACHMENT_COUNT) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "MESSAGE_ATTACHMENT_COUNT",
          "A message can include at most 5 files");
    }
    List<MessageAttachmentUpload> validated = new java.util.ArrayList<>();
    long totalBytes = 0;
    for (MultipartFile file : files) {
      if (file == null || file.isEmpty()) {
        throw failure(
            HttpStatus.BAD_REQUEST, "MESSAGE_ATTACHMENT_INVALID", "Attachments must not be empty");
      }
      if (file.getSize() > MAX_MESSAGE_ATTACHMENT_FILE_BYTES) {
        throw failure(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "MESSAGE_ATTACHMENT_TOO_LARGE",
            "Each attachment is limited to 10 MiB");
      }
      totalBytes += file.getSize();
      if (totalBytes > MAX_MESSAGE_ATTACHMENT_TOTAL_BYTES) {
        throw failure(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "MESSAGE_ATTACHMENTS_TOO_LARGE",
            "Attachments are limited to 20 MiB total");
      }
      byte[] content;
      try {
        content = file.getBytes();
      } catch (IOException exception) {
        throw failure(
            HttpStatus.BAD_REQUEST, "MESSAGE_ATTACHMENT_INVALID", "Attachment could not be read");
      }
      String contentType = resolveMessageAttachmentType(content, file.getContentType());
      validated.add(
          new MessageAttachmentUpload(
              sanitizeMessageAttachmentFileName(file.getOriginalFilename()), contentType, content));
    }
    return List.copyOf(validated);
  }

  private static String resolveMessageAttachmentType(byte[] bytes, String declaredType) {
    String declared = declaredType == null ? "" : declaredType.toLowerCase(java.util.Locale.ROOT);
    if (startsWith(bytes, new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10})
        && declared.equals("image/png")) return "image/png";
    if (bytes.length >= 3
        && bytes[0] == (byte) 0xff
        && bytes[1] == (byte) 0xd8
        && bytes[2] == (byte) 0xff
        && declared.equals("image/jpeg")) return "image/jpeg";
    if (bytes.length >= 12
        && asciiAt(bytes, 0, "RIFF")
        && asciiAt(bytes, 8, "WEBP")
        && declared.equals("image/webp")) return "image/webp";
    if (startsWith(bytes, "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
        && declared.equals("application/pdf")) return "application/pdf";
    if (bytes.length >= 4
        && bytes[0] == 'P'
        && bytes[1] == 'K'
        && (bytes[2] == 3 && bytes[3] == 4 || bytes[2] == 5 && bytes[3] == 6)
        && DOCX_CONTENT_TYPE.equals(declared)) return DOCX_CONTENT_TYPE;
    throw failure(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "MESSAGE_ATTACHMENT_TYPE",
        "Allowed attachments are PNG, JPG, WEBP, PDF, and DOCX files with matching content");
  }

  private static boolean startsWith(byte[] value, byte[] signature) {
    if (value.length < signature.length) return false;
    for (int index = 0; index < signature.length; index++) {
      if (value[index] != signature[index]) return false;
    }
    return true;
  }

  private static boolean asciiAt(byte[] value, int offset, String expected) {
    if (value.length < offset + expected.length()) return false;
    for (int index = 0; index < expected.length(); index++) {
      if (value[offset + index] != (byte) expected.charAt(index)) return false;
    }
    return true;
  }

  private static String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static void deleteStoredObjects(EvidenceObjectStore store, List<String> keys) {
    if (store == null) return;
    for (String key : keys) {
      try {
        store.delete(key);
      } catch (IOException ignored) {
        // The metadata transaction rolls back; a cleanup failure is an orphan for storage audit.
      }
    }
  }

  private record MessageAttachmentUpload(String fileName, String contentType, byte[] content) {}

  public record MessageAttachmentFile(
      String fileName, String contentType, long sizeBytes, InputStream stream) {}

  @Transactional
  public ServiceRequestResponse markMessagesRead(UUID actorId, UUID requestId) {
    UserAccess.ActiveUser actor = requireActor(actorId, null);
    ServiceRequest request = requireRequestForActor(actor, requestId);
    if (actor.hasRole(Roles.ADMIN)) {
      throw forbidden();
    }
    if (actorPolicy.actorKind(actor.roles()) == Mf2ActorPolicy.ActorKind.MANAGER) {
      requireAssignedManager(request, actor);
    }
    List<ServiceRequestMessage> unread =
        messages.findByRequestIdAndAuthorUserIdNotAndReadAtIsNull(request.getId(), actor.id());
    if (!unread.isEmpty()) {
      Instant now = Instant.now();
      unread.forEach(m -> m.markRead(now));
      messages.saveAllAndFlush(unread);
    }
    return toResponse(request);
  }

  @Transactional(readOnly = true)
  public List<UserAccess.ServiceManagerContact> listActiveServiceManagers(UUID actorId) {
    UserAccess.ActiveUser actor = requireActor(actorId, null);
    if (!actor.hasRole(Roles.ADMIN)) {
      throw forbidden();
    }
    return users.listActiveServiceManagers();
  }

  @Transactional(readOnly = true)
  public List<PartnerRegistryService.DroneFeatureOption> listPartnerCapabilities(UUID actorId) {
    requireActor(actorId, Mf2ActorPolicy.ActorKind.MANAGER);
    return partners.listActiveCapabilities();
  }

  @Transactional(readOnly = true)
  public List<PartnerCandidateResponse> evaluatePartnerCandidates(
      UUID actorId, UUID requestId, EvaluatePartnerCandidatesRequest input) {
    UserAccess.ActiveUser manager = requireActor(actorId, Mf2ActorPolicy.ActorKind.MANAGER);
    ServiceRequest request = requireRequestForManager(manager, requestId);
    requireAssignedManager(request, manager);
    requireQuoteFirstOccurrenceWindow(request, input.plannedStartAt(), input.plannedEndAt());
    if (request.getRequestType() == RequestType.RECURRING) {
      validateRecurringQuoteSchedule(request, input.plannedStartAt());
    }
    Asset asset =
        assets
            .findByIdAndOwnerCustomerId(request.getAssetId(), request.getOwnerCustomerId())
            .filter(value -> value.getStatus() != AssetStatus.RETIRED)
            .orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "ASSET_NOT_FOUND", "Asset not found"));
    partners.validateRequiredDroneFeatureCodes(input.requiredDroneFeatureCodes());
    List<Instant> starts = proposedOccurrenceStarts(request, input.plannedStartAt());
    return partners
        .evaluateCandidates(
            manager.id(),
            asset,
            input.plannedStartAt(),
            input.requiredDroneFeatureCodes(),
            starts,
            (inspectorId, droneId) ->
                occurrences.hasScheduleConflict(
                    inspectorId, droneId, input.plannedStartAt(), input.plannedEndAt()),
            (inspectorId, droneId) ->
                assignment.hasPlatformScheduleConflict(
                    inspectorId, droneId, input.plannedStartAt(), input.plannedEndAt()))
        .stream()
        .map(PartnerCandidateResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public PartnerCandidatePageResponse evaluatePartnerCandidatesPaged(
      UUID actorId,
      UUID requestId,
      EvaluatePartnerCandidatesRequest input,
      int page,
      int pageSize,
      String search,
      String eligibility,
      String altitude,
      BigDecimal minimumCeilingMeters,
      String capabilityCode) {
    List<PartnerCandidateResponse> all = evaluatePartnerCandidates(actorId, requestId, input);
    int safePage = Math.max(page, 1);
    int safePageSize = Math.min(Math.max(pageSize, 1), 100);
    String normalizedQuery =
        search != null && !search.isBlank() ? search.trim().toLowerCase() : null;
    String eligibilityMode =
        eligibility == null || eligibility.isBlank()
            ? "ELIGIBLE_ONLY"
            : eligibility.trim().toUpperCase(java.util.Locale.ROOT);
    String altitudeMode =
        altitude == null || altitude.isBlank()
            ? "ALL"
            : altitude.trim().toUpperCase(java.util.Locale.ROOT);
    String capability =
        capabilityCode != null
                && !capabilityCode.isBlank()
                && !"ALL".equalsIgnoreCase(capabilityCode)
            ? capabilityCode.trim()
            : null;

    long eligibleCount =
        all.stream().filter(ContractWorkflowService::isSelectableCandidate).count();
    long unavailableCount = all.size() - eligibleCount;

    List<PartnerCandidateResponse> filtered =
        all.stream()
            .filter(
                candidate -> {
                  boolean selectable = isSelectableCandidate(candidate);
                  if ("UNAVAILABLE_ONLY".equals(eligibilityMode) && selectable) {
                    return false;
                  }
                  if ("ELIGIBLE_ONLY".equals(eligibilityMode) && !selectable) {
                    return false;
                  }
                  if (normalizedQuery != null) {
                    boolean matchesName =
                        candidate.inspectorName() != null
                            && candidate.inspectorName().toLowerCase().contains(normalizedQuery);
                    boolean matchesModel =
                        candidate.droneModel() != null
                            && candidate.droneModel().toLowerCase().contains(normalizedQuery);
                    boolean matchesSerial =
                        candidate.droneSerialNumber() != null
                            && candidate
                                .droneSerialNumber()
                                .toLowerCase()
                                .contains(normalizedQuery);
                    if (!matchesName && !matchesModel && !matchesSerial) {
                      return false;
                    }
                  }
                  if ("COVERS_STRUCTURE".equals(altitudeMode)) {
                    if (candidate.droneMaxAltitudeMeters() == null
                        || candidate.structureHeightMeters() == null
                        || candidate
                                .droneMaxAltitudeMeters()
                                .compareTo(candidate.structureHeightMeters())
                            < 0) {
                      return false;
                    }
                  } else if ("MINIMUM_CEILING".equals(altitudeMode)) {
                    if (minimumCeilingMeters == null
                        || candidate.droneMaxAltitudeMeters() == null
                        || candidate.droneMaxAltitudeMeters().compareTo(minimumCeilingMeters) < 0) {
                      return false;
                    }
                  }
                  if (capability != null
                      && (candidate.droneFeatureCodes() == null
                          || !candidate.droneFeatureCodes().contains(capability))) {
                    return false;
                  }
                  return true;
                })
            .toList();

    long totalCount = filtered.size();
    int totalPages = totalCount == 0 ? 0 : (int) Math.ceil((double) totalCount / safePageSize);
    int fromIndex = Math.min((safePage - 1) * safePageSize, filtered.size());
    int toIndex = Math.min(fromIndex + safePageSize, filtered.size());
    return new PartnerCandidatePageResponse(
        filtered.subList(fromIndex, toIndex),
        safePage,
        safePageSize,
        totalCount,
        totalPages,
        eligibleCount,
        unavailableCount,
        all.size());
  }

  private static boolean isSelectableCandidate(PartnerCandidateResponse candidate) {
    return candidate.eligibility() == PartnerCandidateEvaluation.Status.PASS
        && candidate.capabilityFit() == PartnerCandidateEvaluation.Status.MATCH;
  }

  @Transactional
  public ServiceRequestResponse saveQuote(
      UUID actorId, UUID requestId, IssueServiceQuoteRequest input) {
    UserAccess.ActiveUser manager = requireActor(actorId, Mf2ActorPolicy.ActorKind.MANAGER);
    ServiceRequest request = requireRequestForManagerLocked(manager, requestId);
    requireAssignedManager(request, manager);
    ServiceRequestQuote existing = quotes.findWithLockByRequestId(requestId).orElse(null);
    if (request.getStatus() != ServiceRequest.ServiceRequestStatus.OPEN
        && request.getStatus() != ServiceRequest.ServiceRequestStatus.QUOTED) {
      throw failure(HttpStatus.CONFLICT, "QUOTE_LOCKED", "Accepted service terms cannot be edited");
    }
    if (existing != null && request.getStatus() == ServiceRequest.ServiceRequestStatus.OPEN) {
      throw failure(
          HttpStatus.CONFLICT, "QUOTE_STATE_INVALID", "Request quote state is inconsistent");
    }
    requireQuoteFirstOccurrenceWindow(
        request, input.firstOccurrenceStartAt(), input.firstOccurrenceEndAt());
    if (request.getRequestType() == RequestType.RECURRING) {
      validateRecurringQuoteSchedule(request, input.firstOccurrenceStartAt());
    }
    partners.validateRequiredDroneFeatureCodes(input.requiredDroneFeatureCodes());

    // New inspection services use VND and require all 3 settlement rates to be configured.
    if ("VND".equals(input.resolvedCurrency())) {
      financeSettingsService.requireConfiguredRates();
    }
    BigDecimal previousPrice = existing == null ? null : existing.getPricePerOccurrenceVnd();
    ServiceRequest.ServiceRequestStatus previousStatus = request.getStatus();
    ServiceRequestQuote quote;
    try {
      if (existing == null) {
        quote =
            new ServiceRequestQuote(
                requestId,
                input.scopeSnapshot(),
                input.firstOccurrenceStartAt(),
                input.firstOccurrenceEndAt(),
                input.resolvedPrice(),
                input.requiredDroneFeatureCodes(),
                manager.id());
        quotes.saveAndFlush(quote);
      } else {
        existing.revise(
            input.scopeSnapshot(),
            input.firstOccurrenceStartAt(),
            input.firstOccurrenceEndAt(),
            input.resolvedPrice(),
            input.requiredDroneFeatureCodes(),
            manager.id());
        quote = quotes.saveAndFlush(existing);
      }
      request.markQuoted();
    } catch (IllegalArgumentException | IllegalStateException | ArithmeticException exception) {
      throw failure(HttpStatus.BAD_REQUEST, "QUOTE_INVALID", exception.getMessage());
    }
    if (input.inspectionItems() != null) {
      request.setInspectionItems(input.inspectionItems());
    }
    requests.saveAndFlush(request);
    recordQuoteAudit(manager, request, quote, previousPrice, previousStatus);
    if (events != null) {
      events.publishEvent(
          new ServiceQuoteSavedEvent(
              request.getId(),
              request.getOwnerCustomerId(),
              request.getDescription(),
              existing != null,
              quote.getEffectivePrice(),
              quote.getCurrency(),
              Instant.now()));
    }
    return toResponse(request);
  }

  /** Replaces the per-request inspection checkpoints managed by the assigned Service Manager. */
  @Transactional
  public ServiceRequestResponse updateInspectionItems(
      UUID actorId, UUID requestId, List<String> inspectionItems) {
    UserAccess.ActiveUser manager = requireActor(actorId, Mf2ActorPolicy.ActorKind.MANAGER);
    ServiceRequest request = requireRequestForManagerLocked(manager, requestId);
    requireAssignedManager(request, manager);
    boolean anyDispatched =
        occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId).stream()
            .anyMatch(occ -> occ.getInspectionId() != null);
    if (anyDispatched
        || request.getStatus() == ServiceRequest.ServiceRequestStatus.ASSIGNED
        || request.getStatus() == ServiceRequest.ServiceRequestStatus.CANCELLED) {
      throw failure(
          HttpStatus.CONFLICT,
          "CHECKPOINTS_LOCKED",
          "Inspection checkpoints cannot be modified after dispatch has started");
    }
    request.setInspectionItems(inspectionItems);
    requests.saveAndFlush(request);
    return toResponse(request);
  }

  @Transactional
  public ServiceRequestResponse acceptQuote(
      UUID actorId, UUID requestId, AcceptServiceQuoteRequest input) {
    UserAccess.ActiveUser actor = requireActor(actorId, Mf2ActorPolicy.ActorKind.CUSTOMER);
    ServiceRequest request = requireCustomerRequestLocked(actor, requestId);
    ServiceRequestQuote quote =
        quotes
            .findWithLockByRequestId(requestId)
            .orElseThrow(
                () ->
                    failure(HttpStatus.CONFLICT, "QUOTE_NOT_FOUND", "There is no quote to accept"));
    long expectedVersion =
        input == null || input.quoteRowVersion() == null
            ? quote.getRowVersion()
            : input.quoteRowVersion();
    if (quote.getRowVersion() != expectedVersion) {
      throw failure(
          HttpStatus.CONFLICT, "QUOTE_STALE", "The quote changed. Review it before accepting.");
    }
    validatePartnerEligibilityForQuote(request, quote);
    try {
      request.acceptQuote(quote, actor.id());
      requests.saveAndFlush(request);
      recordAcceptedQuoteAudit(actor, request, quote);
      materializeOccurrences(request, quote);
    } catch (IllegalStateException exception) {
      throw failure(HttpStatus.CONFLICT, "QUOTE_ACCEPTANCE_INVALID", exception.getMessage());
    }
    requests.saveAndFlush(request);
    if (events != null) {
      events.publishEvent(
          new ServiceQuoteAcceptedEvent(
              request.getId(),
              request.getOwnerCustomerId(),
              request.getAssignedManagerId(),
              request.getDescription(),
              Instant.now()));
    }
    return toResponse(request);
  }

  /** Upload signed contract via occurrence endpoint (attaches to parent service request). */
  @Transactional
  public ServiceRequestResponse uploadOccurrenceSignedContract(
      UUID actorId, UUID requestId, UUID occurrenceId, MultipartFile file) {
    UserAccess.ActiveUser manager = requireActor(actorId, Mf2ActorPolicy.ActorKind.MANAGER);
    ServiceRequest request = requireRequestForManagerLocked(manager, requestId);
    occurrences
        .findWithLockById(occurrenceId)
        .filter(value -> requestId.equals(value.getRequestId()))
        .orElseThrow(
            () -> failure(HttpStatus.NOT_FOUND, "OCCURRENCE_NOT_FOUND", "Occurrence not found"));

    byte[] content = validateAndExtractFile(file);
    String contentType = file.getContentType().toLowerCase(java.util.Locale.ROOT);
    String fileName = sanitizeFileName(file.getOriginalFilename());
    String objectKey = "contracts/requests/" + requestId + "/" + UUID.randomUUID() + "/" + fileName;

    EvidenceObjectStore store = requireStore();
    try {
      store.put(objectKey, contentType, content.length, new ByteArrayInputStream(content));
    } catch (IOException exception) {
      throw failure(HttpStatus.BAD_GATEWAY, "STORAGE_FAILED", "Failed to store signed contract");
    }

    try {
      request.recordSignedContract(objectKey);
      requests.saveAndFlush(request);
    } catch (RuntimeException exception) {
      try {
        store.delete(objectKey);
      } catch (IOException ignored) {
      }
      throw exception;
    }

    if (events != null) {
      events.publishEvent(
          new SignedContractUploadedEvent(
              request.getId(),
              request.getOwnerCustomerId(),
              request.getDescription(),
              Instant.now()));
    }
    return toResponse(request);
  }

  /** Scoped download of a signed contract file via occurrence endpoint. */
  @Transactional(readOnly = true)
  public DownloadedContract openOccurrenceSignedContract(
      UUID actorId, UUID requestId, UUID occurrenceId) {
    UserAccess.ActiveUser actor = requireActor(actorId, null);
    ServiceRequest request = requireRequestForActor(actor, requestId);
    if (actor.hasRole(Roles.MANAGER)) {
      requireAssignedManager(request, actor);
    }
    if (actor.hasRole(Roles.ADMIN)) {
      throw forbidden();
    }
    occurrences
        .findById(occurrenceId)
        .filter(value -> requestId.equals(value.getRequestId()))
        .orElseThrow(
            () -> failure(HttpStatus.NOT_FOUND, "OCCURRENCE_NOT_FOUND", "Occurrence not found"));
    if (!request.hasSignedContract()) {
      throw failure(
          HttpStatus.NOT_FOUND,
          "CONTRACT_NOT_FOUND",
          "No signed contract attached to this occurrence");
    }
    EvidenceObjectStore store = requireStore();
    try {
      InputStream stream = store.open(request.getContractObjectKey());
      return new DownloadedContract(
          request.getContractFileName(),
          request.getContractContentType(),
          request.getContractSizeBytes(),
          stream);
    } catch (IOException ex) {
      throw failure(
          HttpStatus.NOT_FOUND, "CONTRACT_NOT_FOUND", "Stored contract could not be opened");
    }
  }

  /** Parent request signed contract upload. */
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
    String objectKey = "contracts/requests/" + requestId + "/" + UUID.randomUUID() + "/" + fileName;

    EvidenceObjectStore store = requireStore();
    try {
      store.put(objectKey, contentType, content.length, new ByteArrayInputStream(content));
    } catch (IOException exception) {
      throw failure(HttpStatus.BAD_GATEWAY, "STORAGE_FAILED", "Failed to store signed contract");
    }

    try {
      request.recordSignedContract(objectKey);
      requests.saveAndFlush(request);

      ServiceRequestQuote acceptedQuote = quotes.findByRequestId(request.getId()).orElse(null);
      materializeOccurrences(request, acceptedQuote);
    } catch (RuntimeException exception) {
      try {
        store.delete(objectKey);
      } catch (IOException ignored) {
      }
      throw exception;
    }
    if (events != null) {
      events.publishEvent(
          new SignedContractUploadedEvent(
              request.getId(),
              request.getOwnerCustomerId(),
              request.getDescription(),
              Instant.now()));
    }
    return toResponse(request);
  }

  @Transactional(readOnly = true)
  public SignedContractFile openSignedContract(UUID actorId, UUID requestId) {
    UserAccess.ActiveUser actor = requireActor(actorId, null);
    ServiceRequest request = requireRequestForActor(actor, requestId);
    if (actor.hasRole(Roles.MANAGER)) {
      requireAssignedManager(request, actor);
    }
    if (actor.hasRole(Roles.ADMIN)) {
      throw forbidden();
    }
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

    if (!request.hasSignedContract()) {
      throw failure(
          HttpStatus.CONFLICT,
          "OCCURRENCE_CONTRACT_REQUIRED",
          "Service request must have a signed contract attached before assignment");
    }

    Map<String, Object> acceptedSnapshot = request.getAcceptedTermsSnapshot();
    if (acceptedSnapshot == null) {
      throw failure(
          HttpStatus.CONFLICT,
          "ACCEPTED_TERMS_MISSING",
          "The accepted quote snapshot is required before dispatch");
    }
    ServiceRequestQuote acceptedQuote =
        quotes
            .findByRequestId(requestId)
            .orElseThrow(
                () ->
                    failure(
                        HttpStatus.CONFLICT,
                        "QUOTE_NOT_FOUND",
                        "The accepted quote is required before dispatch"));
    requireQuotedOccurrenceWindow(
        request,
        acceptedQuote.getFirstOccurrenceStartAt(),
        input.plannedStartAt(),
        input.plannedEndAt());
    List<String> acceptedFeatures = stringList(acceptedSnapshot.get("requiredDroneFeatureCodes"));
    partners.validateRequiredDroneFeatureCodes(acceptedFeatures);
    Asset dispatchAsset =
        assets
            .findByIdAndOwnerCustomerId(request.getAssetId(), request.getOwnerCustomerId())
            .filter(value -> value.getStatus() != AssetStatus.RETIRED)
            .orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "ASSET_NOT_FOUND", "Asset not found"));
    if (input.inspectorId() == null || input.droneId() == null) {
      throw failure(
          HttpStatus.BAD_REQUEST, "PARTNER_PAIR_INVALID", "Inspector and drone are required");
    }
    boolean contractConflict =
        occurrences.hasScheduleConflict(
            input.inspectorId(), input.droneId(), input.plannedStartAt(), input.plannedEndAt());
    boolean inspectionConflict =
        assignment.hasPlatformScheduleConflict(
            input.inspectorId(), input.droneId(), input.plannedStartAt(), input.plannedEndAt());
    var candidate =
        partners.evaluateCandidate(
            manager.id(),
            input.inspectorId(),
            input.droneId(),
            input.plannedStartAt(),
            input.plannedEndAt(),
            dispatchAsset,
            acceptedFeatures,
            contractConflict,
            inspectionConflict);
    requireCandidateEligible(candidate, HttpStatus.CONFLICT);
    if (contractConflict) {
      throw failure(
          HttpStatus.CONFLICT,
          "SCHEDULE_CONFLICT",
          "Selected Inspector or drone has another assigned contract occurrence during this window");
    }
    partners.lockEligibleInspector(input.inspectorId(), input.plannedStartAt());
    Drone lockedDrone =
        partners.lockEligibleDrone(input.droneId(), input.inspectorId(), input.plannedStartAt());
    var lockedCandidate =
        PartnerCandidateEvaluation.evaluate(
            input.inspectorId(),
            lockedDrone.getId(),
            candidate.inspectorName(),
            candidate.inspectorAvatarUrl(),
            lockedDrone.getSerialNumber(),
            lockedDrone.getModel(),
            !lockedDrone.getImageFileKeys().isEmpty(),
            users.findActiveUser(input.inspectorId()).orElse(null),
            lockedDrone,
            input.plannedStartAt(),
            occurrences.hasScheduleConflict(
                input.inspectorId(), input.droneId(), input.plannedStartAt(), input.plannedEndAt()),
            assignment.hasPlatformScheduleConflict(
                input.inspectorId(), input.droneId(), input.plannedStartAt(), input.plannedEndAt()),
            true,
            dispatchAsset,
            acceptedFeatures);
    requireCandidateEligible(lockedCandidate, HttpStatus.CONFLICT);
    List<String> requestItems = request.getInspectionItems();
    UUID inspectionId;
    if (!requestItems.isEmpty()) {
      inspectionId =
          assignment.assign(
              request.getOwnerCustomerId(),
              request.getAssetId(),
              input.inspectorId(),
              input.droneId(),
              input.plannedStartAt(),
              input.plannedEndAt(),
              manager.id(),
              request.getSourceInspectionId(),
              requestItems);
    } else if (request.getSourceInspectionId() != null) {
      inspectionId =
          assignment.assign(
              request.getOwnerCustomerId(),
              request.getAssetId(),
              input.inspectorId(),
              input.droneId(),
              input.plannedStartAt(),
              input.plannedEndAt(),
              manager.id(),
              request.getSourceInspectionId());
    } else {
      inspectionId =
          assignment.assign(
              request.getOwnerCustomerId(),
              request.getAssetId(),
              input.inspectorId(),
              input.droneId(),
              input.plannedStartAt(),
              input.plannedEndAt(),
              manager.id());
    }
    occurrence.assign(
        input.inspectorId(),
        input.droneId(),
        inspectionId,
        input.plannedStartAt(),
        input.plannedEndAt(),
        manager.id());
    if (occurrence.getTotalPrice() == null && request.getAcceptedQuoteId() != null) {
      quotes
          .findByRequestId(request.getId())
          .map(ServiceRequestQuote::getEffectivePrice)
          .ifPresent(
              price -> {
                Optional<InspectionFinanceSettings> currentSettings =
                    financeSettingsService != null
                        ? financeSettingsService.findCurrentSettings()
                        : Optional.empty();
                if (currentSettings.isPresent() && currentSettings.get().isFullyConfigured()) {
                  InspectionFinanceSettings s = currentSettings.get();
                  occurrence.snapshotFinance(
                      price, s.getVatRate(), s.getCommissionRate(), s.getTncnRate());
                } else {
                  occurrence.snapshotFinance(price, null, null, null);
                }
              });
    }
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
      occurrence.startPaymentDueWindow(paymentDueProperties.dueWindow());
    } catch (IllegalStateException ex) {
      throw failure(HttpStatus.CONFLICT, "REPORT_ACCEPTANCE_INVALID", ex.getMessage());
    }
    occurrences.saveAndFlush(occurrence);
    if (events != null) {
      events.publishEvent(
          new CustomerReportAcceptedEvent(
              request.getId(),
              occurrence.getId(),
              occurrence.getInspectionId(),
              request.getOwnerCustomerId(),
              request.getAssignedManagerId(),
              occurrence.getPaymentDueAt(),
              Instant.now()));
    }
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
    if (occurrence.getReportAcceptedAt() == null) {
      throw failure(
          HttpStatus.CONFLICT,
          "PAYMENT_NOT_DUE",
          "Customer must accept the published report before payment can be confirmed");
    }
    boolean alreadyPaid = occurrence.getPaymentStatus() == OccurrencePaymentStatus.PAID;
    if (!alreadyPaid && occurrence.getPaymentStatus() != OccurrencePaymentStatus.PAYMENT_DUE) {
      throw failure(
          HttpStatus.CONFLICT, "PAYMENT_NOT_DUE", "Payment must be due before confirmation");
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
    if (!alreadyPaid) {
      publishPaymentSettled(occurrence);
    }
    return toResponse(request);
  }

  @Transactional
  public ServiceRequestResponse cancelRequest(UUID actorId, UUID requestId, String reason) {
    UserAccess.ActiveUser manager = requireActor(actorId, Mf2ActorPolicy.ActorKind.MANAGER);
    ServiceRequest request = requireRequestForManagerLocked(manager, requestId);
    String cleanReason = reason != null && !reason.isBlank() ? reason.trim() : null;
    if (request.getStatus() == ServiceRequest.ServiceRequestStatus.CANCELLED) {
      return toResponse(request);
    }
    if (request.getStatus() != ServiceRequest.ServiceRequestStatus.OPEN
        && request.getStatus() != ServiceRequest.ServiceRequestStatus.QUOTED) {
      throw failure(
          HttpStatus.CONFLICT, "INVALID_STATE", "Only open or quoted requests may be cancelled.");
    }
    request.cancelOpenOrQuoted();
    requests.saveAndFlush(request);
    String notice =
        cleanReason != null
            ? "Request cancelled by Manager: " + cleanReason
            : "Request cancelled by Manager.";
    messages.saveAndFlush(new ServiceRequestMessage(requestId, manager.id(), notice, false));
    return toResponse(request);
  }

  @Transactional
  public ServiceRequestResponse cancelOccurrence(
      UUID actorId, UUID requestId, UUID occurrenceId, String reason) {
    UserAccess.ActiveUser manager = requireActor(actorId, Mf2ActorPolicy.ActorKind.MANAGER);
    ServiceRequest request = requireRequestForManagerLocked(manager, requestId);
    if (request.getStatus() != ServiceRequest.ServiceRequestStatus.CONTRACT_SIGNED
        && request.getStatus() != ServiceRequest.ServiceRequestStatus.ASSIGNED) {
      throw failure(
          HttpStatus.CONFLICT, "INVALID_STATE", "Only contracted occurrences may be cancelled.");
    }
    ServiceRequestOccurrence occurrence = requireOccurrenceLocked(requestId, occurrenceId);
    if (occurrence.getStatus() == ServiceRequestOccurrence.OccurrenceStatus.CANCELLED) {
      return toResponse(request);
    }
    String cleanReason = reason != null && !reason.isBlank() ? reason.trim() : null;
    cancelSingleOccurrence(manager.id(), occurrence, cleanReason);
    if (cleanReason != null) {
      messages.saveAndFlush(
          new ServiceRequestMessage(
              requestId,
              manager.id(),
              "Scheduled flight #" + occurrence.getOccurrenceIndex() + " cancelled: " + cleanReason,
              false));
    }
    return toResponse(request);
  }

  private void cancelSingleOccurrence(
      UUID actorId, ServiceRequestOccurrence occurrence, String cleanReason) {
    if (occurrence.getPaymentStatus() == OccurrencePaymentStatus.PAID
        || occurrence.getReportAcceptedAt() != null) {
      throw failure(
          HttpStatus.CONFLICT,
          "OCCURRENCE_ALREADY_SETTLED",
          "Cannot cancel an occurrence whose report was accepted or payment was settled");
    }
    if (occurrence.getInspectionId() != null) {
      assignment.cancelInspection(actorId, occurrence.getInspectionId(), cleanReason);
    }
    try {
      occurrence.syncCancelFromInspection();
    } catch (IllegalStateException ex) {
      throw failure(HttpStatus.CONFLICT, "OCCURRENCE_ALREADY_SETTLED", ex.getMessage());
    }
    occurrences.saveAndFlush(occurrence);
  }

  private void publishPaymentSettled(ServiceRequestOccurrence occurrence) {
    if (events == null || occurrence == null || occurrence.getId() == null) {
      return;
    }
    events.publishEvent(
        new PaymentSettledEvent(
            occurrence.getId(),
            occurrence.getRequestId(),
            occurrence.getInspectionId(),
            occurrence.getInspectorId(),
            occurrence.getPaymentReference(),
            occurrence.getTotalPrice(),
            occurrence.getPaymentMethod(),
            occurrence.getPaymentConfirmedAt() != null
                ? occurrence.getPaymentConfirmedAt()
                : Instant.now()));
  }

  @Transactional
  public ServiceRequestResponse initiateOccurrenceRefund(
      UUID actorId, UUID requestId, UUID occurrenceId, InitiateOccurrenceRefundRequest input) {
    UserAccess.ActiveUser manager = requireActor(actorId, Mf2ActorPolicy.ActorKind.MANAGER);
    ServiceRequest request = requireRequestForManagerLocked(manager, requestId);
    ServiceRequestOccurrence occurrence = requireOccurrenceLocked(requestId, occurrenceId);
    if (refundRepository.isPresent()
        && refundRepository
            .get()
            .existsByRequestReferenceIgnoreCase(input.requestReference().trim())) {
      throw failure(
          HttpStatus.CONFLICT,
          "REFUND_REQUEST_REFERENCE_DUPLICATE",
          "Refund request reference has already been used");
    }
    ServiceRefund refund;
    try {
      refund =
          occurrence.initiateRefund(
              input.amount(),
              input.requestReference(),
              input.reason(),
              input.inspectorReceivableAmount(),
              input.platformAbsorbedAmount(),
              manager.id());
      occurrences.saveAndFlush(occurrence);
    } catch (IllegalArgumentException | IllegalStateException ex) {
      throw failure(HttpStatus.CONFLICT, "REFUND_INITIATION_INVALID", ex.getMessage());
    }
    if (events != null) {
      events.publishEvent(
          new OccurrenceRefundEvent(
              refund.getId(),
              occurrence.getId(),
              request.getId(),
              occurrence.getInspectionId(),
              request.getOwnerCustomerId(),
              occurrence.getInspectorId(),
              request.getAssignedManagerId(),
              "INITIATED",
              refund.getAmount(),
              refund.getRequestReference(),
              refund.getReason(),
              Instant.now()));
    }
    return toResponse(request);
  }

  @Transactional
  public ServiceRequestResponse completeOccurrenceRefund(
      UUID actorId,
      UUID requestId,
      UUID occurrenceId,
      UUID refundId,
      CompleteOccurrenceRefundRequest input) {
    UserAccess.ActiveUser manager = requireActor(actorId, Mf2ActorPolicy.ActorKind.MANAGER);
    ServiceRequest request = requireRequestForManagerLocked(manager, requestId);
    ServiceRequestOccurrence occurrence = requireOccurrenceLocked(requestId, occurrenceId);
    if (refundRepository.isPresent()
        && refundRepository.get().existsByReferenceIgnoreCase(input.externalReference().trim())) {
      throw failure(
          HttpStatus.CONFLICT,
          "REFUND_REFERENCE_DUPLICATE",
          "Refund transfer reference has already been recorded");
    }
    try {
      occurrence.completeRefund(
          refundId,
          input.inspectorPayoutReduction(),
          input.platformCommissionAdjustment(),
          input.vatAdjustment(),
          input.tncnAdjustment(),
          input.platformAbsorbedAmount(),
          input.externalReference(),
          manager.id(),
          requireReceivables());
      occurrences.saveAndFlush(occurrence);
    } catch (IllegalArgumentException | IllegalStateException ex) {
      throw failure(HttpStatus.CONFLICT, "REFUND_COMPLETION_INVALID", ex.getMessage());
    }
    if (events != null) {
      events.publishEvent(
          new OccurrenceRefundEvent(
              refundId,
              occurrence.getId(),
              request.getId(),
              occurrence.getInspectionId(),
              request.getOwnerCustomerId(),
              occurrence.getInspectorId(),
              request.getAssignedManagerId(),
              "COMPLETED",
              null,
              input.externalReference(),
              null,
              Instant.now()));
    }
    return toResponse(request);
  }

  @Transactional
  public ServiceRequestResponse cancelOccurrenceRefund(
      UUID actorId,
      UUID requestId,
      UUID occurrenceId,
      UUID refundId,
      CancelOccurrenceRefundRequest input) {
    UserAccess.ActiveUser manager = requireActor(actorId, Mf2ActorPolicy.ActorKind.MANAGER);
    ServiceRequest request = requireRequestForManagerLocked(manager, requestId);
    ServiceRequestOccurrence occurrence = requireOccurrenceLocked(requestId, occurrenceId);
    try {
      occurrence.cancelRefund(refundId, input.reason(), manager.id());
      occurrences.saveAndFlush(occurrence);
    } catch (IllegalArgumentException | IllegalStateException ex) {
      throw failure(HttpStatus.CONFLICT, "REFUND_CANCELLATION_INVALID", ex.getMessage());
    }
    if (events != null) {
      events.publishEvent(
          new OccurrenceRefundEvent(
              refundId,
              occurrence.getId(),
              request.getId(),
              occurrence.getInspectionId(),
              request.getOwnerCustomerId(),
              occurrence.getInspectorId(),
              request.getAssignedManagerId(),
              "CANCELLED",
              null,
              null,
              input.reason(),
              Instant.now()));
    }
    return toResponse(request);
  }

  @Transactional
  public ServiceRequestResponse reconcileRefundAllocation(
      UUID actorId,
      UUID requestId,
      UUID occurrenceId,
      UUID refundId,
      ReconcileRefundAllocationRequest input) {
    UserAccess.ActiveUser manager = requireActor(actorId, Mf2ActorPolicy.ActorKind.MANAGER);
    ServiceRequest request = requireRequestForManagerLocked(manager, requestId);
    ServiceRequestOccurrence occurrence = requireOccurrenceLocked(requestId, occurrenceId);
    try {
      occurrence.reconcileRefundAllocation(
          refundId,
          input.inspectorReceivableAmount(),
          input.platformAbsorbedPlanAmount(),
          input.inspectorPayoutReduction(),
          input.platformCommissionAdjustment(),
          input.vatAdjustment(),
          input.tncnAdjustment(),
          input.platformAbsorbedAmount(),
          manager.id(),
          input.reconciliationReference(),
          input.note(),
          requireReceivables());
      occurrences.saveAndFlush(occurrence);
    } catch (IllegalArgumentException | IllegalStateException ex) {
      throw failure(HttpStatus.CONFLICT, "REFUND_ALLOCATION_INVALID", ex.getMessage());
    }
    return toResponse(request);
  }

  @Transactional
  public ServiceRequestResponse recordInspectorReceivableRecovery(
      UUID actorId,
      UUID requestId,
      UUID occurrenceId,
      UUID receivableId,
      RecordInspectorReceivableRecoveryRequest input) {
    UserAccess.ActiveUser manager = requireActor(actorId, Mf2ActorPolicy.ActorKind.MANAGER);
    ServiceRequest request = requireRequestForManagerLocked(manager, requestId);
    ServiceRequestOccurrence occurrence = requireOccurrenceLocked(requestId, occurrenceId);
    try {
      occurrence.recordReceivableRecovery(
          receivableId,
          input.amount(),
          input.externalReference(),
          input.note(),
          manager.id(),
          requireReceivables());
      occurrences.saveAndFlush(occurrence);
    } catch (IllegalArgumentException | IllegalStateException ex) {
      throw failure(HttpStatus.CONFLICT, "RECEIVABLE_RECOVERY_INVALID", ex.getMessage());
    }
    if (events != null && occurrence.getInspectorId() != null) {
      events.publishEvent(
          new InspectorReceivableRecoveryEvent(
              receivableId,
              occurrence.getId(),
              request.getId(),
              occurrence.getInspectorId(),
              input.amount(),
              input.externalReference(),
              Instant.now()));
    }
    return toResponse(request);
  }

  private ServiceRequestOccurrence requireOccurrenceLocked(UUID requestId, UUID occurrenceId) {
    return occurrences
        .findWithLockById(occurrenceId)
        .filter(value -> requestId.equals(value.getRequestId()))
        .orElseThrow(
            () -> failure(HttpStatus.NOT_FOUND, "OCCURRENCE_NOT_FOUND", "Occurrence not found"));
  }

  private InspectorReceivableRepository requireReceivables() {
    return receivables.orElseThrow(
        () ->
            failure(
                HttpStatus.SERVICE_UNAVAILABLE,
                "RECEIVABLES_UNAVAILABLE",
                "Inspector receivables are not configured"));
  }

  /**
   * Owning Customer or assigned Manager clears a resolvable unpaid gateway order before
   * switching/retrying.
   */
  @Transactional
  public ServiceRequestResponse cancelUnpaidPaymentOrder(
      UUID actorId, UUID requestId, UUID occurrenceId) {
    UserAccess.ActiveUser actor = requireActor(actorId, null);
    if (actor.hasRole(Roles.ADMIN)) {
      throw forbidden();
    }
    ServiceRequest request;
    if (actor.hasRole(Roles.MANAGER)) {
      request = requireRequestForManagerLocked(actor, requestId);
    } else if (actor.hasRole(Roles.CUSTOMER)) {
      request = requireCustomerRequestLocked(actor, requestId);
    } else {
      throw forbidden();
    }
    ServiceRequestOccurrence occurrence =
        occurrences
            .findWithLockById(occurrenceId)
            .filter(value -> requestId.equals(value.getRequestId()))
            .orElseThrow(
                () ->
                    failure(HttpStatus.NOT_FOUND, "OCCURRENCE_NOT_FOUND", "Occurrence not found"));
    Long orderCode = occurrence.getGatewayOrderCode();
    String method = occurrence.getPaymentMethod();
    if (orderCode == null || orderCode <= 0 || method == null || method.isBlank()) {
      throw failure(
          HttpStatus.CONFLICT, "PAYMENT_ORDER_NOT_FOUND", "No active payment order is recorded");
    }
    if (occurrence.getPaymentStatus() != OccurrencePaymentStatus.PAYMENT_DUE) {
      throw failure(
          HttpStatus.CONFLICT,
          "PAYMENT_ORDER_NOT_CANCELLABLE",
          "Only an unpaid due order can be cancelled");
    }
    if ("SEPAY".equalsIgnoreCase(method) || "VNPAY".equalsIgnoreCase(method)) {
      // Clear any pending online gateway order immediately so customers can retry or switch
      // payment methods smoothly. Late notifications for superseded order codes are audited in
      // gateway_inbound_transactions.
    } else if (!"MANUAL_WIRE".equalsIgnoreCase(method)) {
      throw failure(
          HttpStatus.CONFLICT,
          "PAYMENT_METHOD_UNSUPPORTED",
          "The saved payment method cannot be cancelled through this workflow");
    }
    occurrence.clearUnpaidPaymentOrder(orderCode, method);
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
    if (occurrence.isPayoutHeldForRefund()) {
      throw failure(
          HttpStatus.CONFLICT,
          "PAYOUT_HELD_FOR_REFUND",
          "Inspector payout is held while a customer refund is recorded and reconciled");
    }
    if (occurrence.getPayoutStatus() == ServiceRequestOccurrence.OccurrencePayoutStatus.PENDING) {
      if (occurrence.getInspectionId() != null
          && reportAccess.isPresent()
          && reportAccess.get().hasApprovedOrPublishedReport(occurrence.getInspectionId())) {
        occurrence.markPayoutEligible();
      } else {
        throw failure(
            HttpStatus.CONFLICT,
            "PAYOUT_NOT_ELIGIBLE",
            "Inspector payout requires an approved inspection report");
      }
    }
    if (occurrence.getInspectorId() == null) {
      throw failure(
          HttpStatus.CONFLICT, "INSPECTOR_NOT_ASSIGNED", "No inspector assigned to receive payout");
    }
    UserAccess.PayoutBankDetails bankDetails =
        users.findInspectorPayoutBankDetails(occurrence.getInspectorId()).orElse(null);
    if (bankDetails == null || !bankDetails.isComplete()) {
      throw failure(
          HttpStatus.CONFLICT,
          "INSPECTOR_PAYOUT_BANK_INFO_REQUIRED",
          "Inspector must enter payout bank account details before Manager can settle payout");
    }
    try {
      occurrence.recordPayout(input.payoutReference(), manager.id());
    } catch (IllegalStateException ex) {
      throw failure(HttpStatus.CONFLICT, "PAYOUT_RECORD_INVALID", ex.getMessage());
    }
    occurrences.saveAndFlush(occurrence);
    if (events != null) {
      events.publishEvent(
          new InspectorPayoutSettledEvent(
              occurrence.getId(),
              request.getId(),
              occurrence.getInspectionId(),
              occurrence.getInspectorId(),
              request.getAssignedManagerId(),
              input.payoutReference(),
              occurrence.getRemainingInspectorNetPayout(),
              "MANUAL_WIRE",
              false,
              Instant.now()));
    }
    return toResponse(request);
  }

  /** Retrieves role-scoped financial details for an occurrence. */
  @Transactional(readOnly = true)
  public OccurrenceFinanceResponse getOccurrenceFinance(
      UUID actorId, UUID requestId, UUID occurrenceId) {
    UserAccess.ActiveUser actor = requireActor(actorId, null);
    ServiceRequest request = requireRequestForActor(actor, requestId);
    if (actor.hasRole(Roles.MANAGER)) {
      requireAssignedManager(request, actor);
    }
    ServiceRequestOccurrence occurrence =
        occurrences
            .findById(occurrenceId)
            .filter(value -> requestId.equals(value.getRequestId()))
            .orElseThrow(
                () ->
                    failure(HttpStatus.NOT_FOUND, "OCCURRENCE_NOT_FOUND", "Occurrence not found"));

    UserAccess.PayoutBankDetails bankDetails =
        occurrence.getInspectorId() != null
            ? users.findInspectorPayoutBankDetails(occurrence.getInspectorId()).orElse(null)
            : null;
    if (actor.hasRole(Roles.MANAGER)) {
      return OccurrenceFinanceResponse.forManager(occurrence, requireReceivables(), bankDetails);
    }
    if (actor.hasRole(Roles.CUSTOMER)
        && actor.ownerCustomerId() != null
        && actor.ownerCustomerId().equals(request.getOwnerCustomerId())) {
      return OccurrenceFinanceResponse.forCustomer(occurrence);
    }
    if (actor.hasRole(Roles.INSPECTOR) && actor.id().equals(occurrence.getInspectorId())) {
      return OccurrenceFinanceResponse.forInspector(occurrence, bankDetails);
    }
    throw forbidden();
  }

  @Transactional(readOnly = true)
  public FinanceHistoryPageResponse customerFinanceHistory(UUID actorId, int page, int size) {
    UserAccess.ActiveUser actor = requireActor(actorId, Mf2ActorPolicy.ActorKind.CUSTOMER);
    return financeHistoryPage(actor, page, size, true);
  }

  @Transactional(readOnly = true)
  public FinanceHistoryPageResponse inspectorFinanceHistory(UUID actorId, int page, int size) {
    UserAccess.ActiveUser actor = requireActor(actorId, Mf2ActorPolicy.ActorKind.INSPECTOR);
    return financeHistoryPage(actor, page, size, false);
  }

  private FinanceHistoryPageResponse financeHistoryPage(
      UserAccess.ActiveUser actor, int page, int size, boolean customer) {
    if (page < 0 || size < 1 || size > 100) {
      throw failure(
          HttpStatus.BAD_REQUEST, "INVALID_PAGE", "Page must be nonnegative and size 1-100");
    }
    var pageable = org.springframework.data.domain.PageRequest.of(page, size);
    var result =
        customer
            ? occurrences.pagePaidByCustomer(actor.ownerCustomerId(), pageable)
            : occurrences.pagePaidByInspector(actor.id(), pageable);
    List<FinanceHistoryItemResponse> content =
        result.getContent().stream()
            .map(
                occurrence -> {
                  ServiceRequest request =
                      requests.findById(occurrence.getRequestId()).orElse(null);
                  UUID assetId = request != null ? request.getAssetId() : null;
                  String assetName =
                      assetId != null
                          ? assets.findById(assetId).map(Asset::getName).orElse(null)
                          : null;
                  UUID counterpartyId =
                      customer ? occurrence.getInspectorId() : actor.ownerCustomerId();
                  String counterpartyName =
                      counterpartyId != null
                          ? users
                              .findActiveUser(counterpartyId)
                              .map(UserAccess.ActiveUser::fullName)
                              .orElse(null)
                          : null;
                  if (!customer && request != null) {
                    counterpartyId = request.getOwnerCustomerId();
                    counterpartyName =
                        users
                            .findActiveUser(counterpartyId)
                            .map(UserAccess.ActiveUser::fullName)
                            .orElse(null);
                  }
                  return customer
                      ? FinanceHistoryItemResponse.customer(
                          occurrence,
                          request != null ? request.getDescription() : null,
                          assetId,
                          assetName,
                          counterpartyId,
                          counterpartyName)
                      : FinanceHistoryItemResponse.inspector(
                          occurrence,
                          request != null ? request.getDescription() : null,
                          assetId,
                          assetName,
                          counterpartyId,
                          counterpartyName);
                })
            .toList();
    return FinanceHistoryPageResponse.of(content, result.getTotalElements(), page, size);
  }

  @Transactional(readOnly = true)
  public OccurrenceFinanceResponse customerFinanceHistoryDetail(UUID actorId, UUID occurrenceId) {
    UserAccess.ActiveUser actor = requireActor(actorId, Mf2ActorPolicy.ActorKind.CUSTOMER);
    ServiceRequestOccurrence occurrence =
        occurrences
            .findPaidByCustomer(actor.ownerCustomerId(), occurrenceId)
            .orElseThrow(
                () -> failure(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment not found"));
    return OccurrenceFinanceResponse.forCustomer(occurrence);
  }

  @Transactional(readOnly = true)
  public OccurrenceFinanceResponse inspectorFinanceHistoryDetail(UUID actorId, UUID occurrenceId) {
    UserAccess.ActiveUser actor = requireActor(actorId, Mf2ActorPolicy.ActorKind.INSPECTOR);
    ServiceRequestOccurrence occurrence =
        occurrences
            .findPaidByInspector(actor.id(), occurrenceId)
            .orElseThrow(
                () -> failure(HttpStatus.NOT_FOUND, "PAYOUT_NOT_FOUND", "Payout not found"));
    return OccurrenceFinanceResponse.forInspector(occurrence);
  }

  /**
   * Manager view of platform revenue inflows and earned commissions, or payout-trackable records.
   */
  @Transactional(readOnly = true)
  public ManagerFinanceHistoryResponse managerFinanceHistory(UUID actorId, int page, int size) {
    return managerFinanceHistory(actorId, page, size, "REVENUE");
  }

  /**
   * Manager view scoped by {@code REVENUE} (customer paid occurrences) or {@code PAYOUTS}
   * (occurrences whose payout is eligible/settled or customer-paid).
   */
  @Transactional(readOnly = true)
  public ManagerFinanceHistoryResponse managerFinanceHistory(
      UUID actorId, int page, int size, String scope) {
    requireActor(actorId, Mf2ActorPolicy.ActorKind.MANAGER);
    if (page < 0 || size < 1 || size > 100) {
      throw failure(
          HttpStatus.BAD_REQUEST, "INVALID_PAGE", "Page must be nonnegative and size 1-100");
    }
    boolean payoutsScope = "PAYOUTS".equalsIgnoreCase(scope);
    List<ServiceRequestOccurrence> allRecords =
        payoutsScope
            ? occurrences.findAllPayoutTrackableForManager()
            : occurrences.findAllPaidForManager();
    ManagerFinanceHistoryResponse.ManagerFinanceSummary summary =
        ManagerFinanceHistoryResponse.summarize(allRecords);

    var pageable = org.springframework.data.domain.PageRequest.of(page, size);
    var pageResult =
        payoutsScope
            ? occurrences.pagePayoutTrackableForManager(pageable)
            : occurrences.pagePaidForManager(pageable);

    java.util.Map<UUID, ServiceRequest> requestCache = new java.util.HashMap<>();
    java.util.Map<UUID, String> assetNameCache = new java.util.HashMap<>();
    Map<UUID, Optional<UserAccess.ActiveUser>> customerCache = new HashMap<>();
    Map<UUID, String> inspectorNameCache = new HashMap<>();

    List<ManagerFinanceHistoryResponse.ManagerFinanceHistoryItem> content =
        pageResult.getContent().stream()
            .map(
                occurrence -> {
                  ServiceRequest request =
                      requestCache.computeIfAbsent(
                          occurrence.getRequestId(), id -> requests.findById(id).orElse(null));
                  UUID assetId = request != null ? request.getAssetId() : null;
                  String assetName =
                      assetId != null
                          ? assetNameCache.computeIfAbsent(
                              assetId, id -> assets.findById(id).map(Asset::getName).orElse(null))
                          : null;
                  UUID customerId = request != null ? request.getOwnerCustomerId() : null;
                  UserAccess.ActiveUser customer =
                      customerId != null
                          ? customerCache
                              .computeIfAbsent(customerId, users::findActiveUser)
                              .orElse(null)
                          : null;
                  UUID inspectorId = occurrence.getInspectorId();
                  String inspectorName =
                      inspectorId != null
                          ? inspectorNameCache.computeIfAbsent(
                              inspectorId,
                              id ->
                                  users
                                      .findActiveUser(id)
                                      .map(UserAccess.ActiveUser::fullName)
                                      .orElse(null))
                          : null;
                  return ManagerFinanceHistoryResponse.ManagerFinanceHistoryItem.from(
                      occurrence,
                      request != null ? request.getDescription() : null,
                      assetId,
                      assetName,
                      customerId,
                      customer != null ? customer.fullName() : null,
                      inspectorId,
                      inspectorName);
                })
            .toList();

    Map<UUID, String> customerAvatarUrls = new HashMap<>();
    if (!payoutsScope) {
      customerCache.forEach(
          (id, customer) ->
              customer
                  .map(UserAccess.ActiveUser::avatarUrl)
                  .filter(url -> !url.isBlank())
                  .ifPresent(url -> customerAvatarUrls.put(id, url)));
    }
    return new ManagerFinanceHistoryResponse(
        summary, content, customerAvatarUrls, pageResult.getTotalElements(), page, size);
  }

  /** Manager financial breakdown detail for a single revenue-paid or payout-eligible occurrence. */
  @Transactional(readOnly = true)
  public OccurrenceFinanceResponse managerFinanceHistoryDetail(UUID actorId, UUID occurrenceId) {
    requireActor(actorId, Mf2ActorPolicy.ActorKind.MANAGER);
    ServiceRequestOccurrence occurrence =
        occurrences
            .findFinanceHistoryByIdForManager(occurrenceId)
            .orElseThrow(
                () -> failure(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment not found"));
    UserAccess.PayoutBankDetails bankDetails =
        occurrence.getInspectorId() != null
            ? users.findInspectorPayoutBankDetails(occurrence.getInspectorId()).orElse(null)
            : null;
    return OccurrenceFinanceResponse.forManager(occurrence, requireReceivables(), bankDetails);
  }

  /** Customer creates a Sepay VietQR payment code for an occurrence awaiting payment. */
  @Transactional
  public SepayPaymentLinkResponse createSepayPaymentLink(
      UUID actorId, UUID requestId, UUID occurrenceId) {
    UserAccess.ActiveUser actor = requireActor(actorId, null);
    ServiceRequest request = requireRequestForActor(actor, requestId);
    if (actor.hasRole(Roles.ADMIN)) {
      throw forbidden();
    }
    if (actor.hasRole(Roles.MANAGER)) {
      requireAssignedManager(request, actor);
    } else if (!actor.hasRole(Roles.CUSTOMER)
        || actor.ownerCustomerId() == null
        || !actor.ownerCustomerId().equals(request.getOwnerCustomerId())) {
      throw forbidden();
    }
    ServiceRequestOccurrence occurrence =
        occurrences
            .findWithLockById(occurrenceId)
            .filter(value -> requestId.equals(value.getRequestId()))
            .orElseThrow(
                () ->
                    failure(HttpStatus.NOT_FOUND, "OCCURRENCE_NOT_FOUND", "Occurrence not found"));

    if (occurrence.getPaymentStatus() == OccurrencePaymentStatus.PAID) {
      // Already settled: nothing to pay, so return the recorded order without a fresh QR.
      long existingOrderCode =
          occurrence.getGatewayOrderCode() != null ? occurrence.getGatewayOrderCode() : 0L;
      return new SepayPaymentLinkResponse(
          existingOrderCode,
          null,
          "SDI" + existingOrderCode,
          occurrence.getTotalPrice() != null ? occurrence.getTotalPrice().longValue() : 0,
          String.valueOf(existingOrderCode));
    }

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
          "Sepay online checkout is only supported for VND contracts. Currency is "
              + occurrence.getCurrency());
    }
    long amount = toGatewayAmountVnd(occurrence.getTotalPrice());
    long orderCode;
    if (occurrence.getGatewayOrderCode() != null && occurrence.getGatewayOrderCode() > 0) {
      if ("VNPAY".equalsIgnoreCase(occurrence.getPaymentMethod())) {
        // Customer is switching from VNPay to VietQR/Sepay; release the unpaid VNPay order
        // immediately so switching works seamlessly without a manual reset.
        occurrence.clearUnpaidPaymentOrder(occurrence.getGatewayOrderCode(), "VNPAY");
        orderCode = generateUniqueGatewayOrderCode();
      } else if (!"SEPAY".equalsIgnoreCase(occurrence.getPaymentMethod())) {
        throw failure(
            HttpStatus.CONFLICT,
            "PAYMENT_ORDER_ALREADY_OPEN",
            "An unpaid order exists with another gateway. Resolve it before switching payment methods.");
      } else {
        orderCode = occurrence.getGatewayOrderCode();
      }
    } else {
      orderCode = generateUniqueGatewayOrderCode();
    }

    String description = "SDI OCC " + occurrence.getOccurrenceIndex();
    if (description.length() > 25) {
      description = description.substring(0, 25);
    }

    SepayGateway.SepayPaymentLink link =
        sepayGateway.createPaymentLink(orderCode, amount, description, null, null, null);

    occurrence.assignSepayOrder(orderCode, link.paymentLinkId());
    occurrences.saveAndFlush(occurrence);

    return new SepayPaymentLinkResponse(
        orderCode, link.checkoutUrl(), link.qrCode(), amount, link.paymentLinkId());
  }

  /** Webhook applies verified Sepay online payment to an occurrence. */
  @Transactional
  public void applySepayWebhookPayment(long orderCode, long amount, String reference) {
    ServiceRequestOccurrence occurrence =
        occurrences
            .findWithLockByGatewayOrderCode(orderCode)
            .orElseThrow(
                () ->
                    failure(
                        HttpStatus.NOT_FOUND,
                        "OCCURRENCE_NOT_FOUND",
                        "Occurrence with orderCode " + orderCode + " not found"));

    if (occurrence.getPaymentStatus() == OccurrencePaymentStatus.PAID) {
      return; // Idempotent no-op
    }

    if (occurrence.getReportAcceptedAt() == null
        || occurrence.getPaymentStatus() != OccurrencePaymentStatus.PAYMENT_DUE
        || !"SEPAY".equalsIgnoreCase(occurrence.getPaymentMethod())) {
      throw failure(
          HttpStatus.CONFLICT, "PAYMENT_NOT_PAYABLE", "Occurrence is not awaiting a Sepay payment");
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
        (reference != null && !reference.isBlank()) ? reference.trim() : ("SEPAY-" + orderCode);
    occurrence.confirmOnlinePayment(paymentRef, occurrence.getTotalPrice(), "SEPAY");
    occurrences.saveAndFlush(occurrence);
    publishPaymentSettled(occurrence);
  }

  /** Customer creates a signed VNPay checkout URL for an occurrence awaiting payment. */
  @Transactional
  public VnPayPaymentLinkResponse createVnPayPaymentLink(
      UUID actorId, UUID requestId, UUID occurrenceId, String clientIp) {
    return createVnPayPaymentLink(actorId, requestId, occurrenceId, clientIp, false);
  }

  /**
   * Customer creates a signed VNPay checkout URL, optionally routing the browser return callback to
   * the mobile deep-link handler.
   */
  @Transactional
  public VnPayPaymentLinkResponse createVnPayPaymentLink(
      UUID actorId, UUID requestId, UUID occurrenceId, String clientIp, boolean mobileClient) {
    UserAccess.ActiveUser actor = requireActor(actorId, null);
    ServiceRequest request = requireRequestForActor(actor, requestId);
    if (actor.hasRole(Roles.ADMIN)) {
      throw forbidden();
    }
    if (actor.hasRole(Roles.MANAGER)) {
      requireAssignedManager(request, actor);
    } else if (!actor.hasRole(Roles.CUSTOMER)
        || actor.ownerCustomerId() == null
        || !actor.ownerCustomerId().equals(request.getOwnerCustomerId())) {
      throw forbidden();
    }
    ServiceRequestOccurrence occurrence =
        occurrences
            .findWithLockById(occurrenceId)
            .filter(value -> requestId.equals(value.getRequestId()))
            .orElseThrow(
                () ->
                    failure(HttpStatus.NOT_FOUND, "OCCURRENCE_NOT_FOUND", "Occurrence not found"));

    if (occurrence.getPaymentStatus() == OccurrencePaymentStatus.PAID) {
      long existingOrderCode =
          occurrence.getGatewayOrderCode() != null ? occurrence.getGatewayOrderCode() : 0L;
      long paidAmount =
          occurrence.getTotalPrice() != null ? occurrence.getTotalPrice().longValue() : 0L;
      return new VnPayPaymentLinkResponse(
          existingOrderCode, null, paidAmount, String.valueOf(existingOrderCode));
    }

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
          "VNPay online checkout is only supported for VND contracts. Currency is "
              + occurrence.getCurrency());
    }
    long amount = toGatewayAmountVnd(occurrence.getTotalPrice());
    if (occurrence.getGatewayOrderCode() != null && occurrence.getGatewayOrderCode() > 0) {
      String existingMethod = occurrence.getPaymentMethod();
      if ("SEPAY".equalsIgnoreCase(existingMethod) || "VNPAY".equalsIgnoreCase(existingMethod)) {
        // Release the prior unpaid online checkout (whether switching from Sepay or retrying VNPay)
        // so opening a fresh VNPay checkout always works smoothly without a manual reset.
        occurrence.clearUnpaidPaymentOrder(occurrence.getGatewayOrderCode(), existingMethod);
      } else {
        throw failure(
            HttpStatus.CONFLICT,
            "PAYMENT_ORDER_ALREADY_OPEN",
            "An unpaid order exists with another gateway. Resolve it before switching payment methods.");
      }
    }
    long orderCode = generateUniqueGatewayOrderCode();

    String description =
        "Thanh toan dich vu kiem tra SDI OCC "
            + occurrence.getOccurrenceIndex()
            + " ma "
            + orderCode;
    String returnUrl =
        occurrence.getInspectionId() != null
            ? vnPayGateway.resolveReturnUrl(
                occurrence.getInspectionId().toString(), orderCode, mobileClient)
            : null;

    VnPayGateway.VnPayPaymentLink link =
        vnPayGateway.createPaymentUrl(orderCode, amount, description, clientIp, returnUrl);

    occurrence.assignVnPayOrder(orderCode);
    occurrences.saveAndFlush(occurrence);

    return new VnPayPaymentLinkResponse(
        link.orderCode(), link.checkoutUrl(), link.amount(), link.txnRef());
  }

  /**
   * Releases an unpaid VNPay order after VNPay reported a terminal failure (customer cancelled or
   * the checkout timed out) so the customer can start a new payment. Never touches a paid order.
   *
   * @return true when an open VNPay order was released
   */
  @Transactional
  public boolean releaseVnPayOrderAfterFailure(long orderCode) {
    Optional<ServiceRequestOccurrence> opt = occurrences.findWithLockByGatewayOrderCode(orderCode);
    if (opt.isEmpty()) {
      return false;
    }
    ServiceRequestOccurrence occurrence = opt.get();
    if (occurrence.getPaymentStatus() != OccurrencePaymentStatus.PAYMENT_DUE
        || !"VNPAY".equalsIgnoreCase(occurrence.getPaymentMethod())) {
      return false;
    }
    occurrence.clearUnpaidPaymentOrder(orderCode, "VNPAY");
    occurrences.saveAndFlush(occurrence);
    return true;
  }

  private static final java.security.SecureRandom ORDER_CODE_RANDOM =
      new java.security.SecureRandom();

  private long generateUniqueGatewayOrderCode() {
    for (int attempt = 0; attempt < 5; attempt++) {
      // 10-digit numeric code combining time and randomness to avoid same-millisecond collisions.
      long timePart = (System.currentTimeMillis() % 1_000_000L) * 1_000L;
      long randomPart = ORDER_CODE_RANDOM.nextInt(1_000);
      long candidate = 1_000_000_000L + timePart + randomPart;
      if (occurrences.findByGatewayOrderCode(candidate).isEmpty()
          && occurrences.findByPayoutOrderCode(candidate).isEmpty()) {
        return candidate;
      }
    }
    throw failure(
        HttpStatus.SERVICE_UNAVAILABLE,
        "ORDER_CODE_GENERATION_FAILED",
        "Could not allocate a unique payment order code; please retry");
  }

  private long generateUniquePayoutOrderCode() {
    for (int attempt = 0; attempt < 5; attempt++) {
      long timePart = (System.currentTimeMillis() % 1_000_000L) * 1_000L;
      long randomPart = ORDER_CODE_RANDOM.nextInt(1_000);
      long candidate = 2_000_000_000L + timePart + randomPart;
      if (occurrences.findByPayoutOrderCode(candidate).isEmpty()
          && occurrences.findByGatewayOrderCode(candidate).isEmpty()) {
        return candidate;
      }
    }
    throw failure(
        HttpStatus.SERVICE_UNAVAILABLE,
        "ORDER_CODE_GENERATION_FAILED",
        "Could not allocate a unique payout order code; please retry");
  }

  private static long toPayoutAmountVnd(java.math.BigDecimal netPayout) {
    if (netPayout == null || netPayout.compareTo(java.math.BigDecimal.ZERO) <= 0) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "PAYOUT_AMOUNT_INVALID",
          "Inspector net payout amount must be greater than zero");
    }
    return netPayout.setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
  }

  private static long toGatewayAmountVnd(java.math.BigDecimal totalPrice) {
    if (totalPrice == null || totalPrice.compareTo(java.math.BigDecimal.ZERO) <= 0) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "PAYMENT_AMOUNT_INVALID",
          "Occurrence total price must be greater than zero");
    }
    try {
      return totalPrice.stripTrailingZeros().longValueExact();
    } catch (ArithmeticException ex) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "PAYMENT_AMOUNT_INVALID",
          "Online VND payment requires a whole-number amount without fractional digits: "
              + totalPrice);
    }
  }

  public enum VnPayApplyStatus {
    CONFIRMED,
    ALREADY_CONFIRMED,
    AMOUNT_MISMATCH,
    NOT_PAYABLE,
    NOT_FOUND
  }

  public record VnPayApplyResult(VnPayApplyStatus status, String inspectionId) {}

  /** Applies verified VNPay Return/IPN payment notification to an occurrence. */
  @Transactional
  public VnPayApplyResult applyVnPayPayment(long orderCode, long amountVnd, String transactionNo) {
    Optional<ServiceRequestOccurrence> opt = occurrences.findWithLockByGatewayOrderCode(orderCode);
    if (opt.isEmpty()) {
      return new VnPayApplyResult(VnPayApplyStatus.NOT_FOUND, null);
    }
    ServiceRequestOccurrence occurrence = opt.get();
    String inspectionId =
        occurrence.getInspectionId() != null ? occurrence.getInspectionId().toString() : null;

    if (occurrence.getTotalPrice() == null
        || occurrence.getTotalPrice().compareTo(java.math.BigDecimal.valueOf(amountVnd)) != 0) {
      return new VnPayApplyResult(VnPayApplyStatus.AMOUNT_MISMATCH, inspectionId);
    }

    if (occurrence.getPaymentStatus() == OccurrencePaymentStatus.PAID) {
      return new VnPayApplyResult(VnPayApplyStatus.ALREADY_CONFIRMED, inspectionId);
    }

    if (occurrence.getReportAcceptedAt() == null
        || occurrence.getPaymentStatus() != OccurrencePaymentStatus.PAYMENT_DUE
        || !"VNPAY".equalsIgnoreCase(occurrence.getPaymentMethod())) {
      return new VnPayApplyResult(VnPayApplyStatus.NOT_PAYABLE, inspectionId);
    }

    String paymentRef =
        (transactionNo != null && !transactionNo.isBlank())
            ? ("VNPAY-" + transactionNo.trim())
            : ("VNPAY-" + orderCode);
    occurrence.confirmOnlinePayment(paymentRef, occurrence.getTotalPrice(), "VNPAY");
    occurrences.saveAndFlush(occurrence);
    publishPaymentSettled(occurrence);
    return new VnPayApplyResult(VnPayApplyStatus.CONFIRMED, inspectionId);
  }

  @Transactional(readOnly = true)
  public Optional<String> findInspectionIdByOrderCode(long orderCode) {
    return occurrences
        .findByGatewayOrderCode(orderCode)
        .map(ServiceRequestOccurrence::getInspectionId)
        .filter(java.util.Objects::nonNull)
        .map(UUID::toString);
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
  @Transactional(readOnly = true)
  public boolean isAcceptedOrPaid(UUID inspectionId) {
    if (inspectionId == null) {
      return false;
    }
    return occurrences
        .findByInspectionId(inspectionId)
        .map(
            occurrence ->
                occurrence.getReportAcceptedAt() != null
                    || occurrence.getPaymentStatus() == OccurrencePaymentStatus.PAID)
        .orElse(false);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean requiresPayoutBeforePublish(UUID inspectionId) {
    if (inspectionId == null) {
      return false;
    }
    return occurrences
        .findByInspectionId(inspectionId)
        .map(occ -> occ.getPayoutStatus() != ServiceRequestOccurrence.OccurrencePayoutStatus.PAID)
        .orElse(false);
  }

  @Override
  @Transactional
  public void onReportApproved(UUID inspectionId) {
    onReportPublished(inspectionId);
  }

  @Override
  @Transactional
  public void onReportPublished(UUID inspectionId) {
    if (inspectionId == null) {
      return;
    }
    occurrences
        .findWithLockByInspectionId(inspectionId)
        .ifPresent(
            occurrence -> {
              if (occurrence.getPayoutStatus()
                      == ServiceRequestOccurrence.OccurrencePayoutStatus.PENDING
                  && !occurrence.isPayoutHeldForRefund()) {
                occurrence.markPayoutEligible();
                occurrences.saveAndFlush(occurrence);
              }
            });
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
  public InspectionReportAcceptancePort.SepayCheckoutDetails createSepayPaymentLink(
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

    SepayPaymentLinkResponse resp =
        createSepayPaymentLink(actorId, request.getId(), occurrence.getId());
    return new InspectionReportAcceptancePort.SepayCheckoutDetails(
        resp.orderCode(), resp.amount(), resp.paymentLinkId(), resp.checkoutUrl(), resp.qrCode());
  }

  /** Manager creates a Sepay VietQR payout code targeting the assigned Inspector's bank account. */
  @Transactional
  public SepayPaymentLinkResponse createSepayPayoutLink(
      UUID actorId, UUID requestId, UUID occurrenceId) {
    UserAccess.ActiveUser manager = requireActor(actorId, Mf2ActorPolicy.ActorKind.MANAGER);
    requireRequestForManagerLocked(manager, requestId);
    ServiceRequestOccurrence occurrence =
        occurrences
            .findWithLockById(occurrenceId)
            .filter(value -> requestId.equals(value.getRequestId()))
            .orElseThrow(
                () ->
                    failure(HttpStatus.NOT_FOUND, "OCCURRENCE_NOT_FOUND", "Occurrence not found"));
    if (occurrence.isPayoutHeldForRefund()) {
      throw failure(
          HttpStatus.CONFLICT,
          "PAYOUT_HELD_FOR_REFUND",
          "Inspector payout is held while a customer refund is recorded and reconciled");
    }
    if (occurrence.getPayoutStatus() == ServiceRequestOccurrence.OccurrencePayoutStatus.PAID) {
      long existingCode =
          occurrence.getPayoutOrderCode() != null ? occurrence.getPayoutOrderCode() : 0L;
      long paidAmount =
          occurrence.getRemainingInspectorNetPayout() != null
                  && occurrence
                          .getRemainingInspectorNetPayout()
                          .compareTo(java.math.BigDecimal.ZERO)
                      > 0
              ? toPayoutAmountVnd(occurrence.getRemainingInspectorNetPayout())
              : 0L;
      return new SepayPaymentLinkResponse(
          existingCode, null, "SDIPAY" + existingCode, paidAmount, String.valueOf(existingCode));
    }
    if (occurrence.getPayoutStatus() == ServiceRequestOccurrence.OccurrencePayoutStatus.PENDING) {
      if (occurrence.getInspectionId() != null
          && reportAccess.isPresent()
          && reportAccess.get().hasApprovedOrPublishedReport(occurrence.getInspectionId())) {
        occurrence.markPayoutEligible();
      } else {
        throw failure(
            HttpStatus.CONFLICT,
            "PAYOUT_NOT_ELIGIBLE",
            "Inspector payout requires an approved inspection report");
      }
    }
    if (occurrence.getInspectorId() == null) {
      throw failure(
          HttpStatus.CONFLICT, "INSPECTOR_NOT_ASSIGNED", "No inspector assigned to receive payout");
    }
    UserAccess.PayoutBankDetails bankDetails =
        users.findInspectorPayoutBankDetails(occurrence.getInspectorId()).orElse(null);
    if (bankDetails == null || !bankDetails.isComplete()) {
      throw failure(
          HttpStatus.CONFLICT,
          "INSPECTOR_PAYOUT_BANK_INFO_REQUIRED",
          "Inspector must enter payout bank account details before Manager can settle payout");
    }
    if (occurrence.getCurrency() != null && !"VND".equalsIgnoreCase(occurrence.getCurrency())) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "CURRENCY_UNSUPPORTED",
          "Sepay online payout is only supported for VND contracts. Currency is "
              + occurrence.getCurrency());
    }
    long amount = toPayoutAmountVnd(occurrence.getRemainingInspectorNetPayout());
    long payoutOrderCode =
        occurrence.getPayoutOrderCode() != null && occurrence.getPayoutOrderCode() > 0
            ? occurrence.getPayoutOrderCode()
            : generateUniquePayoutOrderCode();

    SepayGateway.SepayPaymentLink link =
        sepayGateway.createPayoutLink(
            payoutOrderCode,
            amount,
            bankDetails.accountNumber(),
            bankDetails.bankName(),
            bankDetails.accountHolder());

    occurrence.assignSepayPayoutOrder(payoutOrderCode);
    occurrences.saveAndFlush(occurrence);
    return new SepayPaymentLinkResponse(
        payoutOrderCode, link.checkoutUrl(), link.qrCode(), amount, link.paymentLinkId());
  }

  @Override
  @Transactional
  public InspectionReportAcceptancePort.SepayCheckoutDetails createSepayPayoutLink(
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
    SepayPaymentLinkResponse resp =
        createSepayPayoutLink(actorId, occurrence.getRequestId(), occurrence.getId());
    return new InspectionReportAcceptancePort.SepayCheckoutDetails(
        resp.orderCode(), resp.amount(), resp.paymentLinkId(), resp.checkoutUrl(), resp.qrCode());
  }

  /** Outgoing webhook settles Inspector payout automatically when transfer matches SDIPAY<code>. */
  @Transactional
  public void applySepayWebhookPayout(long payoutOrderCode, long amount, String reference) {
    ServiceRequestOccurrence occurrence =
        occurrences
            .findWithLockByPayoutOrderCode(payoutOrderCode)
            .orElseThrow(
                () ->
                    failure(
                        HttpStatus.NOT_FOUND,
                        "OCCURRENCE_NOT_FOUND",
                        "Occurrence with payoutOrderCode " + payoutOrderCode + " not found"));

    if (occurrence.getPayoutStatus() == ServiceRequestOccurrence.OccurrencePayoutStatus.PAID) {
      return; // Idempotent no-op
    }
    if (occurrence.isPayoutHeldForRefund()) {
      throw failure(
          HttpStatus.CONFLICT,
          "PAYOUT_HELD_FOR_REFUND",
          "Inspector payout is held while a customer refund is recorded and reconciled");
    }
    if (occurrence.getPayoutStatus() == ServiceRequestOccurrence.OccurrencePayoutStatus.PENDING) {
      if (occurrence.getInspectionId() != null
          && reportAccess.isPresent()
          && reportAccess.get().hasApprovedOrPublishedReport(occurrence.getInspectionId())) {
        occurrence.markPayoutEligible();
      } else {
        throw failure(
            HttpStatus.CONFLICT,
            "PAYOUT_NOT_ELIGIBLE",
            "Inspector payout requires an approved inspection report");
      }
    }
    if (occurrence.getInspectorId() == null) {
      throw failure(
          HttpStatus.CONFLICT, "INSPECTOR_NOT_ASSIGNED", "No inspector assigned to receive payout");
    }

    long expectedAmount = toPayoutAmountVnd(occurrence.getRemainingInspectorNetPayout());
    if (amount != expectedAmount) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "PAYOUT_AMOUNT_MISMATCH",
          "Transfer amount "
              + amount
              + " does not match expected inspector net payout "
              + expectedAmount);
    }

    UUID attributedManagerId =
        requests
            .findById(occurrence.getRequestId())
            .map(ServiceRequest::getAssignedManagerId)
            .orElse(null);
    String payoutRef =
        (reference != null && !reference.isBlank())
            ? reference.trim()
            : ("SEPAY-OUT-" + payoutOrderCode);
    occurrence.confirmSepayPayout(payoutRef, attributedManagerId);
    occurrences.saveAndFlush(occurrence);
    if (events != null) {
      events.publishEvent(
          new InspectorPayoutSettledEvent(
              occurrence.getId(),
              occurrence.getRequestId(),
              occurrence.getInspectionId(),
              occurrence.getInspectorId(),
              attributedManagerId,
              payoutRef,
              occurrence.getRemainingInspectorNetPayout(),
              "SEPAY",
              true,
              Instant.now()));
    }
  }

  @Override
  @Transactional
  public InspectionReportAcceptancePort.VnPayCheckoutDetails createVnPayPaymentLink(
      UUID actorId, UUID inspectionId, String clientIp) {
    return createVnPayPaymentLink(actorId, inspectionId, clientIp, false);
  }

  @Override
  @Transactional
  public InspectionReportAcceptancePort.VnPayCheckoutDetails createVnPayPaymentLink(
      UUID actorId, UUID inspectionId, String clientIp, boolean mobileClient) {
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

    VnPayPaymentLinkResponse resp =
        createVnPayPaymentLink(
            actorId, request.getId(), occurrence.getId(), clientIp, mobileClient);
    return new InspectionReportAcceptancePort.VnPayCheckoutDetails(
        resp.orderCode(), resp.amount(), resp.txnRef(), resp.checkoutUrl());
  }

  @Override
  @Transactional
  public void cancelUnpaidPaymentOrder(UUID actorId, UUID inspectionId) {
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
    cancelUnpaidPaymentOrder(actorId, occurrence.getRequestId(), occurrence.getId());
  }

  @Override
  @Transactional(readOnly = true)
  public InspectionReportAcceptancePort.PaymentStatusDetails getPaymentStatus(
      UUID actorId, UUID inspectionId) {
    if (inspectionId == null) {
      return new InspectionReportAcceptancePort.PaymentStatusDetails(
          false, false, "NOT_APPLICABLE", null, null, null);
    }
    ServiceRequestOccurrence occurrence = occurrences.findByInspectionId(inspectionId).orElse(null);
    if (occurrence == null) {
      return new InspectionReportAcceptancePort.PaymentStatusDetails(
          false, false, "NOT_APPLICABLE", null, null, null);
    }
    boolean accepted = occurrence.getReportAcceptedAt() != null;
    boolean isPaid = occurrence.getPaymentStatus() == OccurrencePaymentStatus.PAID;
    String status = occurrence.getPaymentStatus().name();
    Long orderCode = occurrence.getGatewayOrderCode();
    Long amount =
        occurrence.getTotalPrice() != null ? occurrence.getTotalPrice().longValue() : null;
    String checkoutUrl = null;
    if (orderCode != null
        && orderCode > 0
        && "SEPAY".equalsIgnoreCase(occurrence.getPaymentMethod())) {
      SepayGateway.SepayPaymentLink link =
          sepayGateway.createPaymentLink(
              orderCode,
              amount != null ? amount : 0L,
              "SDI OCC " + occurrence.getOccurrenceIndex(),
              null,
              null,
              null);
      checkoutUrl = link.checkoutUrl();
    }
    UserAccess.PayoutBankDetails bankDetails = null;
    if (actorId != null && occurrence.getInspectorId() != null) {
      boolean includeBankDetails =
          users
              .findUser(actorId)
              .map(
                  actor ->
                      actor.hasRole(Roles.MANAGER)
                          || actor.hasRole(Roles.ADMIN)
                          || actor.hasRole(Roles.INSPECTOR))
              .orElse(false);
      if (includeBankDetails) {
        bankDetails =
            users.findInspectorPayoutBankDetails(occurrence.getInspectorId()).orElse(null);
      }
    }
    return toPaymentStatusDetails(occurrence, checkoutUrl, null, bankDetails);
  }

  @Override
  @Transactional(readOnly = true)
  public java.util.Map<UUID, InspectionReportAcceptancePort.PaymentStatusDetails>
      getPaymentStatuses(java.util.Collection<UUID> inspectionIds) {
    if (inspectionIds == null || inspectionIds.isEmpty()) {
      return java.util.Map.of();
    }
    List<ServiceRequestOccurrence> matched = occurrences.findByInspectionIdIn(inspectionIds);
    List<UUID> requestIds =
        matched.stream()
            .map(ServiceRequestOccurrence::getRequestId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
    java.util.Map<UUID, ServiceRequest> requestMap = new java.util.HashMap<>();
    for (ServiceRequest req : requests.findAllById(requestIds)) {
      requestMap.put(req.getId(), req);
    }
    java.util.Map<UUID, InspectionReportAcceptancePort.PaymentStatusDetails> byInspection =
        new java.util.HashMap<>();
    for (ServiceRequestOccurrence occurrence : matched) {
      if (occurrence.getInspectionId() == null) {
        continue;
      }
      ServiceRequest req = requestMap.get(occurrence.getRequestId());
      String desc = req != null ? req.getDescription() : null;
      byInspection.put(
          occurrence.getInspectionId(), toPaymentStatusDetails(occurrence, null, desc));
    }
    return byInspection;
  }

  private InspectionReportAcceptancePort.PaymentStatusDetails toPaymentStatusDetails(
      ServiceRequestOccurrence occurrence, String checkoutUrl) {
    String desc = null;
    if (occurrence.getRequestId() != null) {
      desc =
          requests
              .findById(occurrence.getRequestId())
              .map(ServiceRequest::getDescription)
              .orElse(null);
    }
    return toPaymentStatusDetails(occurrence, checkoutUrl, desc);
  }

  private InspectionReportAcceptancePort.PaymentStatusDetails toPaymentStatusDetails(
      ServiceRequestOccurrence occurrence, String checkoutUrl, String requestDescription) {
    return toPaymentStatusDetails(occurrence, checkoutUrl, requestDescription, null);
  }

  private InspectionReportAcceptancePort.PaymentStatusDetails toPaymentStatusDetails(
      ServiceRequestOccurrence occurrence,
      String checkoutUrl,
      String requestDescription,
      UserAccess.PayoutBankDetails bankDetails) {
    boolean accepted = occurrence.getReportAcceptedAt() != null;
    boolean isPaid = occurrence.getPaymentStatus() == OccurrencePaymentStatus.PAID;
    String status = occurrence.getPaymentStatus().name();
    Long orderCode = occurrence.getGatewayOrderCode();
    Long amount =
        occurrence.getTotalPrice() != null ? occurrence.getTotalPrice().longValue() : null;

    java.math.BigDecimal serviceBase = occurrence.getServiceBaseAmount();
    java.math.BigDecimal vatRate = occurrence.getVatRate();
    java.math.BigDecimal vatAmount = occurrence.getVatAmount();
    java.math.BigDecimal commRate = occurrence.getCommissionRate();
    java.math.BigDecimal commAmount = occurrence.getCommissionAmount();
    java.math.BigDecimal grossPayout = occurrence.getInspectorGrossPayout();
    java.math.BigDecimal tncnRate = occurrence.getTncnRate();
    java.math.BigDecimal tncnAmount = occurrence.getTncnAmount();
    java.math.BigDecimal netPayout = occurrence.getInspectorNetPayout();

    if (occurrence.getTotalPrice() != null
        && occurrence.getTotalPrice().signum() > 0
        && commRate == null
        && financeSettingsService != null) {
      Optional<InspectionFinanceSettings> current = financeSettingsService.findCurrentSettings();
      if (current.isPresent() && current.get().isFullyConfigured()) {
        InspectionFinanceSettings s = current.get();
        InspectionFinanceCalculator.FinanceCalculation calc =
            InspectionFinanceCalculator.calculate(
                occurrence.getTotalPrice(), s.getVatRate(), s.getCommissionRate(), s.getTncnRate());
        serviceBase = calc.serviceBaseAmount();
        vatRate = calc.vatRate();
        vatAmount = calc.vatAmount();
        commRate = calc.commissionRate();
        commAmount = calc.commissionAmount();
        grossPayout = calc.inspectorGrossPayout();
        tncnRate = calc.tncnRate();
        tncnAmount = calc.tncnAmount();
        netPayout = calc.inspectorNetPayout();
      }
    }

    return new InspectionReportAcceptancePort.PaymentStatusDetails(
        accepted,
        isPaid,
        status,
        orderCode,
        amount,
        checkoutUrl,
        serviceBase,
        vatRate,
        vatAmount,
        commRate,
        commAmount,
        grossPayout,
        tncnRate,
        tncnAmount,
        netPayout,
        occurrence.getPayoutStatus() != null ? occurrence.getPayoutStatus().name() : null,
        occurrence.getPayoutReference(),
        requestDescription,
        occurrence.getPaymentMethod(),
        occurrence.getPaymentDueAt(),
        occurrence.getPaymentConfirmedAt(),
        bankDetails != null ? bankDetails.bankName() : null,
        bankDetails != null ? bankDetails.accountNumber() : null,
        bankDetails != null ? bankDetails.accountHolder() : null,
        bankDetails != null ? bankDetails.bankBranch() : null);
  }

  private void materializeOccurrences(ServiceRequest request, ServiceRequestQuote quote) {
    if (!occurrences.findByRequestIdOrderByOccurrenceIndexAsc(request.getId()).isEmpty()) {
      return;
    }
    if (quote == null) {
      quote = quotes.findByRequestId(request.getId()).orElse(null);
    }
    if (quote == null) {
      return;
    }

    if (request.getRequestType() == RequestType.RECURRING) {
      validateRecurringQuoteSchedule(request, quote.getFirstOccurrenceStartAt());
    }
    validatePartnerEligibilityForQuote(request, quote);

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
      // An end-date recurring term stays on the customer-selected end date. A one-time quote may
      // move later and a count-based term shifts with its first date, so only the end-date case is
      // bounded by the original requested window.
      boolean boundedByCustomerEndDate =
          request.getRequestType() == RequestType.RECURRING && request.getOccurrenceCount() == null;
      if (boundedByCustomerEndDate
          && (date.isAfter(request.getRequestedUntil())
              || (request.getRecurrenceEndDate() != null
                  && date.isAfter(request.getRecurrenceEndDate())))) {
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
          occurrence.snapshotFinance(
              quote.getEffectivePrice(), s.getVatRate(), s.getCommissionRate(), s.getTncnRate());
        } else {
          occurrence.snapshotFinance(quote.getEffectivePrice(), null, null, null);
        }
      }
      occurrences.save(occurrence);
    }
    occurrences.flush();
  }

  private void validateRecurringQuoteSchedule(ServiceRequest request, Instant firstStartAt) {
    ZoneId zone = ZoneId.of(request.getTimezone());
    LocalDate firstDate = firstStartAt.atZone(zone).toLocalDate();
    if (firstDate.isBefore(request.getRequestedFrom())) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "RECURRENCE_INVALID",
          "The first quoted occurrence cannot be earlier than the requested start date");
    }
    LocalDate occurrenceEnd;
    if (request.getOccurrenceCount() != null) {
      // The customer bought a fixed number of visits. Moving the first date shifts the whole term.
      occurrenceEnd = recurrenceDate(request, firstDate, request.getOccurrenceCount() - 1);
    } else {
      // The customer chose an end date. It stays fixed; a later start must still fit two visits.
      occurrenceEnd = request.getRecurrenceEndDate();
      if (occurrenceEnd == null || occurrenceEnd.isAfter(request.getRequestedUntil())) {
        throw failure(
            HttpStatus.BAD_REQUEST,
            "RECURRENCE_INVALID",
            "The recurring schedule contract term has changed; create a new request with a valid end date");
      }
      if (firstDate.isAfter(occurrenceEnd)) {
        throw failure(
            HttpStatus.BAD_REQUEST,
            "RECURRENCE_INVALID",
            "The first quoted occurrence must be on or before the customer-selected end date");
      }
    }
    int count = 0;
    for (int index = 0; index <= 100; index++) {
      LocalDate date = recurrenceDate(request, firstDate, index);
      if (date.isAfter(occurrenceEnd)) break;
      count++;
    }
    if (count < 2) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "RECURRENCE_INVALID",
          "The revised schedule must keep at least two inspection occurrences");
    }
    if (count > 100) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "RECURRENCE_INVALID",
          "The recurring request cannot contain more than 100 inspection occurrences");
    }
  }

  private void validatePartnerEligibilityForQuote(
      ServiceRequest request, ServiceRequestQuote quote) {
    // Quotes no longer carry a partner pair; eligibility for a concrete Inspector/drone is now
    // enforced by assignOccurrence and by the platform inspection assignment port.
    UUID inspectorId = quote.getInspectorId();
    UUID droneId = quote.getDroneId();
    if (inspectorId == null || droneId == null) {
      return;
    }
    List<Instant> starts = proposedOccurrenceStarts(request, quote.getFirstOccurrenceStartAt());
    if (!partners.isEligibleInspectorForAllDates(inspectorId, starts)) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "INVALID_PARTNER_INSPECTOR",
          "The selected Inspector's pilot license must remain valid for every scheduled occurrence");
    }
    if (!partners.isEligibleDroneForAllDates(droneId, inspectorId, starts)) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "INVALID_PARTNER_DRONE",
          "The selected drone and Inspector must remain eligible for every scheduled occurrence");
    }
  }

  private List<Instant> proposedOccurrenceStarts(ServiceRequest request, Instant quotedStart) {
    if (request.getRequestType() == RequestType.ONE_TIME) {
      return List.of(quotedStart);
    }

    ZoneId zone = ZoneId.of(request.getTimezone());
    LocalDate firstDate = quotedStart.atZone(zone).toLocalDate();
    LocalTime firstTime = quotedStart.atZone(zone).toLocalTime();
    LocalDate lastDate;
    if (request.getOccurrenceCount() != null) {
      // A count-based term shifts with the proposed first date.
      lastDate = recurrenceDate(request, firstDate, request.getOccurrenceCount() - 1);
    } else {
      // An end-date term stays fixed on the customer-selected end date.
      lastDate = request.getRecurrenceEndDate();
      if (lastDate == null || lastDate.isAfter(request.getRequestedUntil())) {
        throw failure(
            HttpStatus.BAD_REQUEST,
            "RECURRENCE_INVALID",
            "The recurring schedule contract term has changed; create a new request with a valid end date");
      }
      if (firstDate.isAfter(lastDate)) {
        throw failure(
            HttpStatus.BAD_REQUEST,
            "RECURRENCE_INVALID",
            "The first quoted occurrence must be on or before the customer-selected end date");
      }
    }

    List<Instant> starts = new java.util.ArrayList<>();
    for (int index = 0; index < 100; index++) {
      LocalDate date = recurrenceDate(request, firstDate, index);
      if (date.isAfter(lastDate)) {
        break;
      }
      starts.add(date.atTime(firstTime).atZone(zone).toInstant());
    }
    if (starts.isEmpty() || starts.size() > 100) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "RECURRENCE_INVALID",
          "The recurring request must contain between 2 and 100 inspection occurrences");
    }
    return List.copyOf(starts);
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
    return recurrenceDate(
        request.getRecurrenceUnit(), request.getRecurrenceInterval(), firstDate, occurrenceIndex);
  }

  private LocalDate recurrenceDate(
      String unit, Integer interval, LocalDate firstDate, int occurrenceIndex) {
    if (unit == null || interval == null || firstDate == null || occurrenceIndex < 0) {
      throw failure(HttpStatus.BAD_REQUEST, "RECURRENCE_INVALID", "Recurrence cadence is invalid");
    }
    int offset = Math.multiplyExact(occurrenceIndex, interval);
    return switch (unit) {
      case "DAY" -> firstDate.plusDays(offset);
      case "WEEK" -> firstDate.plusWeeks(offset);
      case "MONTH" -> firstDate.plusMonths(offset);
      default ->
          throw failure(
              HttpStatus.BAD_REQUEST, "RECURRENCE_INVALID", "Recurrence cadence is invalid");
    };
  }

  private static List<String> stringList(Object value) {
    if (!(value instanceof List<?> values)) return List.of();
    return values.stream().filter(String.class::isInstance).map(String.class::cast).toList();
  }

  private void requireCandidateEligible(
      PartnerCandidateEvaluation.Result candidate, HttpStatus failureStatus) {
    if (candidate == null) {
      throw failure(
          failureStatus,
          "PARTNER_PAIR_NEEDS_VERIFICATION",
          "Partner eligibility and required capabilities could not be evaluated");
    }
    if (candidate.capabilityFit() == PartnerCandidateEvaluation.Status.MISMATCH) {
      throw failure(
          failureStatus,
          "PARTNER_CAPABILITY_MISMATCH",
          "Drone does not declare all required capabilities");
    }
    if (candidate.capabilityFit() == PartnerCandidateEvaluation.Status.UNKNOWN) {
      throw failure(
          failureStatus,
          "PARTNER_CAPABILITY_UNKNOWN",
          "Required capabilities or altitude cannot be verified for this drone");
    }
    if (candidate.eligibility() != PartnerCandidateEvaluation.Status.PASS) {
      throw failure(
          failureStatus,
          "PARTNER_PAIR_INELIGIBLE",
          "Selected partner pair fails eligibility checks");
    }
  }

  /**
   * The Manager may move the first inspection later than the customer's requested date. The quote
   * still has to use the customer's daily time window, and an end-date recurring quote may not run
   * past the customer-selected end date.
   */
  private void requireQuoteFirstOccurrenceWindow(
      ServiceRequest request, Instant start, Instant end) {
    if (start == null || end == null || !end.isAfter(start)) {
      throw failure(HttpStatus.BAD_REQUEST, "INVALID_DATES", "Occurrence end must be after start");
    }
    ZoneId zone = ZoneId.of(request.getTimezone());
    LocalDate date = start.atZone(zone).toLocalDate();
    LocalDate endDate = end.minusNanos(1).atZone(zone).toLocalDate();
    if (!date.equals(endDate)) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "OUTSIDE_REQUESTED_WINDOW",
          "The first occurrence must start and end on the same day");
    }
    if (date.isBefore(request.getRequestedFrom())) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "OUTSIDE_REQUESTED_WINDOW",
          "The first occurrence cannot be earlier than the customer-requested date");
    }
    if (request.getRequestType() == RequestType.RECURRING
        && request.getOccurrenceCount() == null
        && (request.getRecurrenceEndDate() == null
            || date.isAfter(request.getRecurrenceEndDate()))) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "OUTSIDE_REQUESTED_WINDOW",
          "The first occurrence must be on or before the customer-selected end date");
    }
    requireDailyTimeWindow(request, start, end, zone);
  }

  /**
   * Dispatch may adjust the time inside an accepted occurrence but not move it outside the term the
   * Customer accepted. A count-based recurring term shifts with the quoted first date.
   */
  private void requireQuotedOccurrenceWindow(
      ServiceRequest request, Instant acceptedFirstStartAt, Instant start, Instant end) {
    if (start == null || end == null || !end.isAfter(start)) {
      throw failure(HttpStatus.BAD_REQUEST, "INVALID_DATES", "Occurrence end must be after start");
    }
    ZoneId zone = ZoneId.of(request.getTimezone());
    LocalDate date = start.atZone(zone).toLocalDate();
    LocalDate endDate = end.minusNanos(1).atZone(zone).toLocalDate();
    if (!date.equals(endDate)) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "OUTSIDE_REQUESTED_WINDOW",
          "An occurrence must start and end on the same day");
    }
    LocalDate firstDate = acceptedFirstStartAt.atZone(zone).toLocalDate();
    LocalDate lastDate;
    if (request.getRequestType() == RequestType.ONE_TIME) {
      lastDate = firstDate;
    } else if (request.getOccurrenceCount() != null) {
      lastDate = recurrenceDate(request, firstDate, request.getOccurrenceCount() - 1);
    } else {
      lastDate =
          request.getRecurrenceEndDate() != null
              ? request.getRecurrenceEndDate()
              : request.getRequestedUntil();
    }
    if (date.isBefore(firstDate) || date.isAfter(lastDate)) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "OUTSIDE_REQUESTED_WINDOW",
          "Occurrence must stay inside the accepted quote schedule");
    }
    requireDailyTimeWindow(request, start, end, zone);
  }

  private void requireDailyTimeWindow(
      ServiceRequest request, Instant start, Instant end, ZoneId zone) {
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

  private void recordQuoteAudit(
      UserAccess.ActiveUser manager,
      ServiceRequest request,
      ServiceRequestQuote quote,
      java.math.BigDecimal previousPrice,
      ServiceRequest.ServiceRequestStatus previousStatus) {
    if (jdbcTemplate == null) {
      return;
    }
    jdbcTemplate.update(
        """
        INSERT INTO audit_events
          (actor_user_id, action, aggregate_type, aggregate_id,
           before_status, after_status, aggregate_version, safe_metadata, created_at)
        VALUES (?, 'MF2_QUOTE_SAVED', 'SERVICE_REQUEST', ?, ?, ?, ?,
          jsonb_build_object('quoteId', ?::text, 'scopeSnapshot', ?::text,
            'firstOccurrenceStartAt', ?::text, 'firstOccurrenceEndAt', ?::text,
            'inspectorId', ?::text, 'droneId', ?::text,
            'pricePerOccurrenceVnd', ?::text, 'previousPricePerOccurrenceVnd', ?::text,
            'updatedAt', ?::text), CURRENT_TIMESTAMP)
        """,
        manager.id(),
        request.getId(),
        previousStatus.name(),
        request.getStatus().name(),
        quote.getRowVersion(),
        quote.getId().toString(),
        quote.getScopeSnapshot(),
        quote.getFirstOccurrenceStartAt().toString(),
        quote.getFirstOccurrenceEndAt().toString(),
        quote.getInspectorId() == null ? null : quote.getInspectorId().toString(),
        quote.getDroneId() == null ? null : quote.getDroneId().toString(),
        quote.getPricePerOccurrenceVnd().toPlainString(),
        previousPrice == null ? null : previousPrice.toPlainString(),
        quote.getUpdatedAt().toString());
  }

  private void recordAcceptedQuoteAudit(
      UserAccess.ActiveUser customer, ServiceRequest request, ServiceRequestQuote quote) {
    if (jdbcTemplate == null) {
      return;
    }
    jdbcTemplate.update(
        """
        INSERT INTO audit_events
          (actor_user_id, action, aggregate_type, aggregate_id,
           before_status, after_status, aggregate_version, safe_metadata, created_at)
        VALUES (?, 'MF2_QUOTE_ACCEPTED', 'SERVICE_REQUEST', ?, 'QUOTED', 'ACCEPTED', ?,
          jsonb_build_object('quoteId', ?::text, 'acceptedAt', ?::text,
            'scopeSnapshot', ?::text, 'firstOccurrenceStartAt', ?::text,
            'firstOccurrenceEndAt', ?::text, 'inspectorId', ?::text,
            'droneId', ?::text, 'pricePerOccurrenceVnd', ?::text), CURRENT_TIMESTAMP)
        """,
        customer.id(),
        request.getId(),
        quote.getRowVersion(),
        quote.getId().toString(),
        request.getAcceptedAt() == null ? null : request.getAcceptedAt().toString(),
        quote.getScopeSnapshot(),
        quote.getFirstOccurrenceStartAt().toString(),
        quote.getFirstOccurrenceEndAt().toString(),
        quote.getInspectorId() == null ? null : quote.getInspectorId().toString(),
        quote.getDroneId() == null ? null : quote.getDroneId().toString(),
        quote.getPricePerOccurrenceVnd().toPlainString());
  }

  private ServiceRequestResponse toAdminAssignmentResponse(ServiceRequest request) {
    return ServiceRequestResponse.forAssignmentRegistry(request);
  }

  private ServiceRequestResponse toResponse(ServiceRequest request) {
    return toResponse(request, false, null, false, null);
  }

  private ServiceRequestResponse toResponse(
      ServiceRequest request,
      boolean includeManagerContact,
      UserAccess.ActiveUser managerActor,
      boolean includeManagerMessages,
      UserAccess.ActiveUser ownerCustomer) {
    Asset asset =
        assets != null && request.getAssetId() != null
            ? assets.findById(request.getAssetId()).orElse(null)
            : null;
    return toResponse(
        request, includeManagerContact, managerActor, includeManagerMessages, ownerCustomer, asset);
  }

  private ServiceRequestResponse toResponse(
      ServiceRequest request,
      boolean includeManagerContact,
      UserAccess.ActiveUser managerActor,
      boolean includeManagerMessages,
      UserAccess.ActiveUser ownerCustomer,
      Asset asset) {
    boolean managerHasNotClaimed = managerActor != null && request.getAssignedManagerId() == null;
    ServiceRequestQuoteResponse quote =
        managerHasNotClaimed
            ? null
            : quotes
                .findByRequestId(request.getId())
                .map(ServiceRequestQuoteResponse::from)
                .orElse(null);
    ServiceManagerContactResponse managerContact = null;
    if (includeManagerContact && request.getAssignedManagerId() != null) {
      managerContact =
          users
              .findActiveServiceManager(request.getAssignedManagerId())
              .map(ServiceManagerContactResponse::from)
              .orElse(null);
    }
    boolean canClaim =
        managerActor != null
            && !managerActor.hasRole(Roles.ADMIN)
            && request.getAssignedManagerId() == null;
    List<ServiceRequestOccurrenceResponse> occurrenceResponses = List.of();
    if (!managerHasNotClaimed) {
      List<ServiceRequestOccurrence> requestOccurrences =
          occurrences.findByRequestIdOrderByOccurrenceIndexAsc(request.getId());
      Map<UUID, String> droneLabels = loadDroneLabels(requestOccurrences);
      Map<UUID, String> inspectorNames = new HashMap<>();
      occurrenceResponses =
          requestOccurrences.stream()
              .map(
                  occ ->
                      toOccurrenceResponse(
                          occ, request, droneLabels, inspectorNames, managerActor != null))
              .toList();
    }
    return ServiceRequestResponse.from(
        request,
        ownerCustomer == null ? null : ownerCustomer.fullName(),
        ownerCustomer != null
                && managerActor != null
                && managerActor.id().equals(request.getAssignedManagerId())
            ? ownerCustomer.avatarUrl()
            : null,
        asset != null ? asset.getName() : null,
        asset != null ? asset.getCode() : null,
        asset != null ? asset.getLocationText() : null,
        quote,
        managerContact,
        occurrenceResponses,
        managerActor != null && (managerHasNotClaimed || !includeManagerMessages)
            ? List.of()
            : messages.findByRequestIdOrderByCreatedAtAscIdAsc(request.getId()).stream()
                .map(this::toMessageResponse)
                .toList(),
        canClaim);
  }

  private Map<UUID, Asset> loadAssetMap(Collection<ServiceRequest> requestList) {
    if (assets == null || requestList == null || requestList.isEmpty()) {
      return Map.of();
    }
    Set<UUID> assetIds = new HashSet<>();
    for (ServiceRequest req : requestList) {
      if (req.getAssetId() != null) {
        assetIds.add(req.getAssetId());
      }
    }
    if (assetIds.isEmpty()) {
      return Map.of();
    }
    Map<UUID, Asset> map = new HashMap<>();
    for (Asset asset : assets.findAllById(assetIds)) {
      map.put(asset.getId(), asset);
    }
    return map;
  }

  private Map<UUID, UserAccess.ActiveUser> loadRequestCustomers(
      Collection<ServiceRequest> requestList) {
    if (users == null || requestList == null || requestList.isEmpty()) {
      return Map.of();
    }
    Map<UUID, UserAccess.ActiveUser> customers = new HashMap<>();
    for (ServiceRequest req : requestList) {
      UUID customerId = req.getOwnerCustomerId();
      if (customerId != null && !customers.containsKey(customerId)) {
        customers.put(customerId, users.findActiveUser(customerId).orElse(null));
      }
    }
    return customers;
  }

  private Map<UUID, String> loadDroneLabels(List<ServiceRequestOccurrence> requestOccurrences) {
    if (partners == null || requestOccurrences == null || requestOccurrences.isEmpty()) {
      return Map.of();
    }
    Set<UUID> droneIds = new HashSet<>();
    for (ServiceRequestOccurrence occ : requestOccurrences) {
      if (occ.getDroneId() != null) {
        droneIds.add(occ.getDroneId());
      }
    }
    Map<UUID, String> labels = droneIds.isEmpty() ? Map.of() : partners.findDroneLabels(droneIds);
    return labels == null ? Map.of() : labels;
  }

  /**
   * Adds the Inspector's dispatch answer, so step 4 can show "waiting for the Inspector" or
   * "reassignment required" instead of assuming every dispatch is confirmed.
   */
  private ServiceRequestOccurrenceResponse toOccurrenceResponse(
      ServiceRequestOccurrence occurrence,
      ServiceRequest request,
      Map<UUID, String> droneLabels,
      Map<UUID, String> inspectorNames,
      boolean includeInspectorBankDetails) {
    Mf2InspectionAssignment.AssignmentAcceptance acceptance =
        occurrence.getInspectionId() == null
            ? null
            : assignment
                .findAssignmentState(occurrence.getInspectionId())
                .filter(state -> state.assignmentStatus() != null)
                .orElse(null);
    int timeoutHours =
        operationsSettings
            .map(PlatformOperationsPolicyPort::getAssignmentAcceptanceTimeoutHours)
            .orElse(PlatformOperationsPolicyPort.DEFAULT_ASSIGNMENT_ACCEPTANCE_TIMEOUT_HOURS);
    Instant deadlineAt =
        acceptance != null
                && "PENDING_ACCEPTANCE".equals(acceptance.assignmentStatus())
                && acceptance.assignmentRequestedAt() != null
            ? acceptance.assignmentRequestedAt().plus(Duration.ofHours(timeoutHours))
            : null;
    String lastTimeoutInspectorName =
        occurrence.getLastTimeoutInspectorId() == null
            ? null
            : users
                .findActiveUser(occurrence.getLastTimeoutInspectorId())
                .map(UserAccess.ActiveUser::fullName)
                .orElse(null);
    String inspectorName = null;
    if (occurrence.getInspectorId() != null && users != null) {
      UUID inspectorId = occurrence.getInspectorId();
      if (!inspectorNames.containsKey(inspectorId)) {
        inspectorNames.put(
            inspectorId,
            users.findActiveUser(inspectorId).map(UserAccess.ActiveUser::fullName).orElse(null));
      }
      inspectorName = inspectorNames.get(inspectorId);
    }
    String droneLabel =
        occurrence.getDroneId() == null ? null : droneLabels.get(occurrence.getDroneId());
    UserAccess.PayoutBankDetails bankDetails =
        includeInspectorBankDetails && occurrence.getInspectorId() != null
            ? users.findInspectorPayoutBankDetails(occurrence.getInspectorId()).orElse(null)
            : null;
    return ServiceRequestOccurrenceResponse.from(occurrence, request)
        .withPayoutBankDetails(bankDetails)
        .withAssignmentAcceptance(
            acceptance == null ? null : acceptance.assignmentStatus(),
            acceptance != null && acceptance.reassignmentRequired(),
            acceptance == null ? null : acceptance.declineReason(),
            deadlineAt,
            lastTimeoutInspectorName)
        .withAssignedPartnerSummary(inspectorName, droneLabel);
  }

  private ServiceRequestMessageResponse toMessageResponse(ServiceRequestMessage message) {
    Optional<UserAccess.ActiveUser> author = users.findActiveUser(message.getAuthorUserId());
    String authorName = author.map(UserAccess.ActiveUser::fullName).orElse(null);
    String authorRole = author.map(u -> u.roles().stream().findFirst().orElse(null)).orElse(null);
    return ServiceRequestMessageResponse.from(message, authorName, authorRole);
  }

  private ServiceRequest requireRequestForActor(UserAccess.ActiveUser actor, UUID requestId) {
    if (actorPolicy.actorKind(actor.roles()) == Mf2ActorPolicy.ActorKind.CUSTOMER) {
      return requireCustomerRequest(actor, requestId);
    }
    if (actorPolicy.actorKind(actor.roles()) == Mf2ActorPolicy.ActorKind.MANAGER) {
      ServiceRequest request = requireRequestForManager(actor, requestId);
      if (!actor.hasRole(Roles.ADMIN)
          && request.getAssignedManagerId() != null
          && !request.getAssignedManagerId().equals(actor.id())) {
        throw failure(HttpStatus.NOT_FOUND, REQUEST_NOT_FOUND, REQUEST_NOT_FOUND_DETAIL);
      }
      return request;
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
        .findByIdAndOwnerCustomerId(requestId, customer.ownerCustomerId())
        .orElseThrow(
            () -> failure(HttpStatus.NOT_FOUND, REQUEST_NOT_FOUND, REQUEST_NOT_FOUND_DETAIL));
  }

  private ServiceRequest requireCustomerRequestLocked(
      UserAccess.ActiveUser customer, UUID requestId) {
    return requests
        .findWithLockByIdAndOwnerCustomerId(requestId, customer.ownerCustomerId())
        .orElseThrow(
            () -> failure(HttpStatus.NOT_FOUND, REQUEST_NOT_FOUND, REQUEST_NOT_FOUND_DETAIL));
  }

  private ServiceRequest requireAdminRequestLocked(UserAccess.ActiveUser admin, UUID requestId) {
    if (!admin.hasRole(Roles.ADMIN)) {
      throw forbidden();
    }
    return requests
        .findWithLockById(requestId)
        .orElseThrow(
            () -> failure(HttpStatus.NOT_FOUND, REQUEST_NOT_FOUND, REQUEST_NOT_FOUND_DETAIL));
  }

  private ServiceRequest requireAdminRequest(UserAccess.ActiveUser admin, UUID requestId) {
    if (!admin.hasRole(Roles.ADMIN)) {
      throw forbidden();
    }
    return requests
        .findById(requestId)
        .orElseThrow(
            () -> failure(HttpStatus.NOT_FOUND, REQUEST_NOT_FOUND, REQUEST_NOT_FOUND_DETAIL));
  }

  private ServiceRequest requireRequestForManager(UserAccess.ActiveUser manager, UUID requestId) {
    ServiceRequest request =
        requests
            .findById(requestId)
            .orElseThrow(
                () -> failure(HttpStatus.NOT_FOUND, REQUEST_NOT_FOUND, REQUEST_NOT_FOUND_DETAIL));
    if (request.getAssignedManagerId() != null
        && !request.getAssignedManagerId().equals(manager.id())) {
      throw failure(HttpStatus.NOT_FOUND, REQUEST_NOT_FOUND, REQUEST_NOT_FOUND_DETAIL);
    }
    return request;
  }

  private ServiceRequest requireRequestForManagerLocked(
      UserAccess.ActiveUser manager, UUID requestId) {
    ServiceRequest request =
        requests
            .findWithLockById(requestId)
            .orElseThrow(
                () -> failure(HttpStatus.NOT_FOUND, REQUEST_NOT_FOUND, REQUEST_NOT_FOUND_DETAIL));
    requireAssignedManager(request, manager);
    return request;
  }

  private void requireAssignedManager(ServiceRequest request, UserAccess.ActiveUser manager) {
    if (request.getAssignedManagerId() == null) {
      throw failure(
          HttpStatus.CONFLICT, "REQUEST_NOT_CLAIMED", "Claim this request before managing it");
    }
    if (!request.getAssignedManagerId().equals(manager.id())) {
      throw forbidden();
    }
  }

  private UserAccess.ActiveUser requireAdmin(UUID actorId) {
    UserAccess.ActiveUser actor = requireActor(actorId, null);
    if (!actor.hasRole(Roles.ADMIN)) {
      throw forbidden();
    }
    return actor;
  }

  private ServiceRequestResponse withAssignmentOptions(
      UserAccess.ActiveUser actor, ServiceRequestResponse response) {
    return new ServiceRequestResponse(
        response.id(),
        response.ownerCustomerId(),
        response.ownerCustomerName(),
        response.ownerCustomerAvatarUrl(),
        response.assetId(),
        response.assetName(),
        response.assetCode(),
        response.assetLocationText(),
        response.description(),
        response.requestedFrom(),
        response.requestedUntil(),
        response.timezone(),
        response.windowStart(),
        response.windowEnd(),
        response.requestType(),
        response.recurrenceUnit(),
        response.recurrenceInterval(),
        response.occurrenceCount(),
        response.recurrenceEndDate(),
        response.status(),
        response.acceptedQuoteId(),
        response.acceptedAt(),
        response.acceptedByUserId(),
        response.acceptedTermsSnapshot(),
        response.contractFileName(),
        response.contractContentType(),
        response.contractSizeBytes(),
        response.contractUploadedAt(),
        response.createdAt(),
        response.currentQuote(),
        response.assignedManagerId(),
        response.managerAssignedAt(),
        response.managerAssignedByUserId(),
        response.serviceManager(),
        response.occurrences(),
        response.messages(),
        response.sourceInspectionId(),
        response.inspectionItems(),
        response.canClaim());
  }

  private void recordManagerAssignmentAudit(
      UUID actorId,
      ServiceRequest request,
      UUID previousManagerId,
      UUID nextManagerId,
      String action) {
    if (jdbcTemplate == null) {
      return;
    }
    jdbcTemplate.update(
        """
        INSERT INTO audit_events
          (actor_user_id, action, aggregate_type, aggregate_id,
           before_status, after_status, aggregate_version, safe_metadata, created_at)
        VALUES (?, ?, 'SERVICE_REQUEST', ?, ?, ?, ?,
          jsonb_build_object('previousManagerId', ?::text, 'assignedManagerId', ?::text,
            'managerAssignedAt', ?::text), CURRENT_TIMESTAMP)
        """,
        actorId,
        "MF2_MANAGER_" + action,
        request.getId(),
        request.getStatus().name(),
        request.getStatus().name(),
        request.getRowVersion(),
        previousManagerId == null ? null : previousManagerId.toString(),
        nextManagerId == null ? null : nextManagerId.toString(),
        request.getManagerAssignedAt() == null ? null : request.getManagerAssignedAt().toString());
  }

  private UserAccess.ActiveUser requireActor(UUID actorId, Mf2ActorPolicy.ActorKind expected) {
    UserAccess.ActiveUser actor = users.findActiveUser(actorId).orElseThrow(this::forbidden);
    if (actorPolicy.actorKind(actor.roles()) == Mf2ActorPolicy.ActorKind.NONE
        && !actor.hasRole(Roles.INSPECTOR)
        && !actor.hasRole(Roles.ADMIN)) {
      throw forbidden();
    }
    if (expected != null && actorPolicy.actorKind(actor.roles()) != expected) {
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

  private static String sanitizeMessageAttachmentFileName(String name) {
    String sanitized =
        name == null || name.isBlank() ? "attachment" : name.replaceAll("[\\\\/\\r\\n]", "_");
    return sanitized.length() > 255 ? sanitized.substring(0, 255) : sanitized;
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

  public record SignedContractFile(
      String fileName, String contentType, Long sizeBytes, InputStream stream) {}

  public record DownloadedContract(
      String fileName, String contentType, Long sizeBytes, InputStream stream) {}

  private static final class DisabledSepayGatewayDummy implements SepayGateway {
    @Override
    public SepayPaymentLink createPaymentLink(
        long orderCode,
        long amount,
        String description,
        String accountNumber,
        String bankName,
        String accountHolder) {
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "PAYMENT_GATEWAY_DISABLED",
          "Online payments via Sepay are not enabled. Use corporate wire transfer instead.");
    }

    @Override
    public boolean verifyWebhookApiKey(String receivedApiKey) {
      return false;
    }
  }

  private static final class DisabledVnPayGatewayDummy implements VnPayGateway {
    @Override
    public VnPayPaymentLink createPaymentUrl(
        long orderCode, long amount, String description, String clientIp, String returnUrl) {
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "PAYMENT_GATEWAY_DISABLED",
          "Online payments via VNPay are not enabled. Use Sepay or corporate wire transfer instead.");
    }

    @Override
    public boolean verifySecureHash(
        java.util.Map<String, String> params, String receivedSecureHash) {
      return false;
    }
  }
}
