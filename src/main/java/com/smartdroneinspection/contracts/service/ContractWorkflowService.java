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
import com.smartdroneinspection.contracts.api.dto.response.PartnerCandidateResponse;
import com.smartdroneinspection.contracts.api.dto.response.SepayPaymentLinkResponse;
import com.smartdroneinspection.contracts.api.dto.response.ServiceManagerContactResponse;
import com.smartdroneinspection.contracts.api.dto.response.ServiceRequestMessageResponse;
import com.smartdroneinspection.contracts.api.dto.response.ServiceRequestOccurrenceResponse;
import com.smartdroneinspection.contracts.api.dto.response.ServiceRequestQuoteResponse;
import com.smartdroneinspection.contracts.api.dto.response.ServiceRequestResponse;
import com.smartdroneinspection.contracts.api.dto.response.VnPayPaymentLinkResponse;
import com.smartdroneinspection.contracts.domain.InspectionFinanceSettings;
import com.smartdroneinspection.contracts.domain.ServiceRequest;
import com.smartdroneinspection.contracts.domain.ServiceRequest.RequestType;
import com.smartdroneinspection.contracts.domain.ServiceRequestMessage;
import com.smartdroneinspection.contracts.domain.ServiceRequestMessageAttachment;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence.OccurrencePaymentStatus;
import com.smartdroneinspection.contracts.domain.ServiceRequestQuote;
import com.smartdroneinspection.contracts.events.PaymentSettledEvent;
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
import java.time.format.DateTimeParseException;
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
    Asset asset =
        assets
            .findByIdAndOwnerCustomerId(input.assetId(), actor.ownerCustomerId())
            .filter(value -> value.getStatus() != AssetStatus.RETIRED)
            .orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "ASSET_NOT_FOUND", "Asset not found"));
    if (input.sourceInspectionId() != null) {
      assignment.validateReinspectionSource(
          actor.ownerCustomerId(), asset.getId(), input.sourceInspectionId());
    } else {
      boolean hasActiveRequest =
          requests.findByOwnerCustomerIdAndAssetId(actor.ownerCustomerId(), asset.getId()).stream()
              .anyMatch(this::isActiveRequest);
      if (hasActiveRequest) {
        throw failure(
            HttpStatus.CONFLICT,
            "DUPLICATE_ACTIVE_REQUEST",
            "This asset already has an active service request in progress");
      }
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
    LocalDate minimumStartDate = customerToday.plusDays(20);
    if (input.requestedFrom() == null || input.requestedFrom().isBefore(minimumStartDate)) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "REQUEST_TOO_SOON",
          "The first inspection date must be at least 20 days from today to allow contract and flight-permit processing");
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
    return toResponse(requests.saveAndFlush(request));
  }

  @Transactional(readOnly = true)
  public List<ServiceRequestResponse> list(UUID actorId) {
    UserAccess.ActiveUser actor = requireActor(actorId, null);
    if (actorPolicy.actorKind(actor.roles()) == Mf2ActorPolicy.ActorKind.CUSTOMER) {
      return requests.findByOwnerCustomerIdOrderByCreatedAtDesc(actor.ownerCustomerId()).stream()
          .map(request -> toResponse(request, true, null, true))
          .toList();
    }
    if (actor.hasRole(Roles.ADMIN)) {
      return requests.findAllByOrderByCreatedAtDesc().stream()
          .map(this::toAdminAssignmentResponse)
          .toList();
    }
    if (actorPolicy.actorKind(actor.roles()) == Mf2ActorPolicy.ActorKind.MANAGER) {
      return requests.findManagerQueue(actor.id()).stream()
          .map(
              request ->
                  withAssignmentOptions(
                      actor,
                      toResponse(
                          request,
                          false,
                          actor,
                          request.getAssignedManagerId() != null
                              && request.getAssignedManagerId().equals(actor.id()))))
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
    boolean managerOwnsRequest = isManager && request.getAssignedManagerId() != null;
    // Only the Manager detail needs the customer label for its persistent asset overview, so the
    // lookup stays off the list path and does not turn it into an N+1 query.
    String ownerCustomerName =
        isManager
            ? users
                .findActiveUser(request.getOwnerCustomerId())
                .map(UserAccess.ActiveUser::fullName)
                .orElse(null)
            : null;
    ServiceRequestResponse response =
        toResponse(
            request,
            includeManagerProfile,
            isManager ? actor : null,
            !isManager || managerOwnsRequest,
            ownerCustomerName);
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
    return withAssignmentOptions(manager, toResponse(request, false, manager, true));
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
    request.reassignManager(newManager.id(), admin.id(), Instant.now());
    requests.saveAndFlush(request);
    recordManagerAssignmentAudit(
        admin.id(),
        request,
        previousManagerId,
        newManager.id(),
        previousManagerId == null ? "ASSIGNED" : "REASSIGNED");
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
    request.unassignManager(admin.id(), Instant.now());
    requests.saveAndFlush(request);
    recordManagerAssignmentAudit(admin.id(), request, previousManagerId, null, "UNASSIGNED");
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
    requests.saveAndFlush(request);
    recordQuoteAudit(manager, request, quote, previousPrice, previousStatus);
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
    requireQuotedOccurrenceWindow(
        request,
        acceptedFirstOccurrenceStart(acceptedSnapshot),
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
    UUID inspectionId =
        request.getSourceInspectionId() != null
            ? assignment.assign(
                request.getOwnerCustomerId(),
                request.getAssetId(),
                input.inspectorId(),
                input.droneId(),
                input.plannedStartAt(),
                input.plannedEndAt(),
                manager.id(),
                request.getSourceInspectionId())
            : assignment.assign(
                request.getOwnerCustomerId(),
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
    try {
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

  /** Manager cancels the saved unpaid gateway order before switching gateway or retrying. */
  @Transactional
  public ServiceRequestResponse cancelUnpaidPaymentOrder(
      UUID actorId, UUID requestId, UUID occurrenceId) {
    UserAccess.ActiveUser manager = requireActor(actorId, Mf2ActorPolicy.ActorKind.MANAGER);
    ServiceRequest request = requireRequestForManagerLocked(manager, requestId);
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
    if ("SEPAY".equalsIgnoreCase(method)) {
      // Sepay has no cancel API; the unpaid order can only be cleared manually by the manager.
      // No gateway-side confirmation is required.
    } else if ("VNPAY".equalsIgnoreCase(method)) {
      // VNPay has no cancel API. Only an order past its checkout lifetime is certain to be
      // unpayable, so a live order stays blocked to avoid double settlement.
      if (!occurrence.isGatewayOrderExpired(Instant.now(), vnPayOrderLifetime())) {
        throw failure(
            HttpStatus.CONFLICT,
            "PAYMENT_ORDER_UNRESOLVED",
            "VNPay order is still payable; wait for it to expire or reconcile with the gateway before clearing the order");
      }
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
    if (occurrence.getReportAcceptedAt() == null
        || occurrence.getPaymentStatus() != OccurrencePaymentStatus.PAID) {
      throw failure(
          HttpStatus.CONFLICT,
          "PAYOUT_NOT_ELIGIBLE",
          "Inspector payout requires both customer report acceptance and confirmed customer payment");
    }
    if (occurrence.isPayoutHeldForRefund()) {
      throw failure(
          HttpStatus.CONFLICT,
          "PAYOUT_HELD_FOR_REFUND",
          "Inspector payout is held while a customer refund is recorded and reconciled");
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

    if (actor.hasRole(Roles.MANAGER)) {
      return OccurrenceFinanceResponse.forManager(occurrence, requireReceivables());
    }
    if (actor.hasRole(Roles.CUSTOMER)
        && actor.ownerCustomerId() != null
        && actor.ownerCustomerId().equals(request.getOwnerCustomerId())) {
      return OccurrenceFinanceResponse.forCustomer(occurrence);
    }
    if (actor.hasRole(Roles.INSPECTOR) && actor.id().equals(occurrence.getInspectorId())) {
      return OccurrenceFinanceResponse.forInspector(occurrence);
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
   * Manager view of all platform revenue inflows and earned commissions across paid occurrences.
   */
  @Transactional(readOnly = true)
  public ManagerFinanceHistoryResponse managerFinanceHistory(UUID actorId, int page, int size) {
    requireActor(actorId, Mf2ActorPolicy.ActorKind.MANAGER);
    if (page < 0 || size < 1 || size > 100) {
      throw failure(
          HttpStatus.BAD_REQUEST, "INVALID_PAGE", "Page must be nonnegative and size 1-100");
    }
    List<ServiceRequestOccurrence> allPaid = occurrences.findAllPaidForManager();
    ManagerFinanceHistoryResponse.ManagerFinanceSummary summary =
        ManagerFinanceHistoryResponse.summarize(allPaid);

    var pageable = org.springframework.data.domain.PageRequest.of(page, size);
    var pageResult = occurrences.pagePaidForManager(pageable);

    java.util.Map<UUID, ServiceRequest> requestCache = new java.util.HashMap<>();
    java.util.Map<UUID, String> assetNameCache = new java.util.HashMap<>();
    java.util.Map<UUID, String> userNameCache = new java.util.HashMap<>();

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
                  String customerName =
                      customerId != null
                          ? userNameCache.computeIfAbsent(
                              customerId,
                              id ->
                                  users
                                      .findActiveUser(id)
                                      .map(UserAccess.ActiveUser::fullName)
                                      .orElse(null))
                          : null;
                  UUID inspectorId = occurrence.getInspectorId();
                  String inspectorName =
                      inspectorId != null
                          ? userNameCache.computeIfAbsent(
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
                      customerName,
                      inspectorId,
                      inspectorName);
                })
            .toList();

    return new ManagerFinanceHistoryResponse(
        summary, content, pageResult.getTotalElements(), page, size);
  }

  /** Manager financial breakdown detail for a single paid occurrence. */
  @Transactional(readOnly = true)
  public OccurrenceFinanceResponse managerFinanceHistoryDetail(UUID actorId, UUID occurrenceId) {
    requireActor(actorId, Mf2ActorPolicy.ActorKind.MANAGER);
    ServiceRequestOccurrence occurrence =
        occurrences
            .findPaidByIdForManager(occurrenceId)
            .orElseThrow(
                () -> failure(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment not found"));
    return OccurrenceFinanceResponse.forManager(occurrence, requireReceivables());
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
      if (!"SEPAY".equalsIgnoreCase(occurrence.getPaymentMethod())) {
        throw failure(
            HttpStatus.CONFLICT,
            "PAYMENT_ORDER_ALREADY_OPEN",
            "An unpaid order exists with another gateway. Resolve it before switching payment methods.");
      }
      orderCode = occurrence.getGatewayOrderCode();
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
      if (!"VNPAY".equalsIgnoreCase(occurrence.getPaymentMethod())) {
        throw failure(
            HttpStatus.CONFLICT,
            "PAYMENT_ORDER_ALREADY_OPEN",
            "An unpaid order exists with another gateway. Resolve it before switching payment methods.");
      }
      if (!occurrence.isGatewayOrderExpired(Instant.now(), vnPayOrderLifetime())) {
        throw failure(
            HttpStatus.CONFLICT,
            "PAYMENT_ORDER_UNRESOLVED",
            "The existing VNPay order is still payable; complete it or wait for it to expire before retrying.");
      }
      // The previous checkout can no longer be paid at VNPay: release it so the customer can retry.
      occurrence.clearUnpaidPaymentOrder(occurrence.getGatewayOrderCode(), "VNPAY");
    }
    long orderCode = generateUniqueGatewayOrderCode();

    String description =
        "Thanh toan dich vu kiem tra SDI OCC "
            + occurrence.getOccurrenceIndex()
            + " ma "
            + orderCode;
    String returnUrl =
        occurrence.getInspectionId() != null
            ? vnPayGateway.resolveReturnUrl(occurrence.getInspectionId().toString(), orderCode)
            : null;

    VnPayGateway.VnPayPaymentLink link =
        vnPayGateway.createPaymentUrl(orderCode, amount, description, clientIp, returnUrl);

    occurrence.assignVnPayOrder(orderCode);
    occurrences.saveAndFlush(occurrence);

    return new VnPayPaymentLinkResponse(
        link.orderCode(), link.checkoutUrl(), link.amount(), link.txnRef());
  }

  /** Checkout lifetime plus a grace period for a payment landing right at the VNPay deadline. */
  private java.time.Duration vnPayOrderLifetime() {
    int minutes = vnPayGateway.orderLifetimeMinutes();
    return java.time.Duration.ofMinutes((minutes > 0 ? minutes : 15) + 5L);
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
      if (occurrences.findByGatewayOrderCode(candidate).isEmpty()) {
        return candidate;
      }
    }
    throw failure(
        HttpStatus.SERVICE_UNAVAILABLE,
        "ORDER_CODE_GENERATION_FAILED",
        "Could not allocate a unique payment order code; please retry");
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

  @Override
  @Transactional
  public InspectionReportAcceptancePort.VnPayCheckoutDetails createVnPayPaymentLink(
      UUID actorId, UUID inspectionId, String clientIp) {
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
        createVnPayPaymentLink(actorId, request.getId(), occurrence.getId(), clientIp);
    return new InspectionReportAcceptancePort.VnPayCheckoutDetails(
        resp.orderCode(), resp.amount(), resp.txnRef(), resp.checkoutUrl());
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
    return toPaymentStatusDetails(occurrence, checkoutUrl);
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
        occurrence.getPaymentDueAt());
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

  private Instant acceptedFirstOccurrenceStart(Map<String, Object> acceptedSnapshot) {
    Object rawStart = acceptedSnapshot.get("firstOccurrenceStartAt");
    if (rawStart instanceof Instant instant) {
      return instant;
    }
    if (rawStart == null) {
      throw failure(
          HttpStatus.CONFLICT,
          "ACCEPTED_TERMS_MISSING",
          "The accepted quote schedule is required before dispatch");
    }
    try {
      return Instant.parse(rawStart.toString());
    } catch (DateTimeParseException exception) {
      throw failure(
          HttpStatus.CONFLICT,
          "ACCEPTED_TERMS_MISSING",
          "The accepted quote schedule is required before dispatch");
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
      boolean includeManagerMessages) {
    return toResponse(request, includeManagerContact, managerActor, includeManagerMessages, null);
  }

  private ServiceRequestResponse toResponse(
      ServiceRequest request,
      boolean includeManagerContact,
      UserAccess.ActiveUser managerActor,
      boolean includeManagerMessages,
      String ownerCustomerName) {
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
    return ServiceRequestResponse.from(
        request,
        ownerCustomerName,
        quote,
        managerContact,
        managerHasNotClaimed
            ? List.of()
            : occurrences.findByRequestIdOrderByOccurrenceIndexAsc(request.getId()).stream()
                .map(occ -> toOccurrenceResponse(occ, request))
                .toList(),
        managerActor != null && (managerHasNotClaimed || !includeManagerMessages)
            ? List.of()
            : messages.findByRequestIdOrderByCreatedAtAscIdAsc(request.getId()).stream()
                .map(this::toMessageResponse)
                .toList(),
        canClaim);
  }

  /**
   * Adds the Inspector's dispatch answer, so step 4 can show "waiting for the Inspector" or
   * "reassignment required" instead of assuming every dispatch is confirmed.
   */
  private ServiceRequestOccurrenceResponse toOccurrenceResponse(
      ServiceRequestOccurrence occurrence, ServiceRequest request) {
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
    return ServiceRequestOccurrenceResponse.from(occurrence, request)
        .withAssignmentAcceptance(
            acceptance == null ? null : acceptance.assignmentStatus(),
            acceptance != null && acceptance.reassignmentRequired(),
            acceptance == null ? null : acceptance.declineReason(),
            deadlineAt,
            lastTimeoutInspectorName);
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
        response.assetId(),
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

  private boolean isActiveRequest(ServiceRequest existing) {
    ServiceRequest.ServiceRequestStatus status = existing.getStatus();
    if (status == ServiceRequest.ServiceRequestStatus.CANCELLED) {
      return false;
    }
    if (status != ServiceRequest.ServiceRequestStatus.ASSIGNED) {
      return true;
    }
    List<ServiceRequestOccurrence> requestOccurrences =
        occurrences.findByRequestIdOrderByOccurrenceIndexAsc(existing.getId());
    if (requestOccurrences.isEmpty()) {
      return true;
    }
    return requestOccurrences.stream()
        .anyMatch(
            occurrence ->
                occurrence.getStatus() != ServiceRequestOccurrence.OccurrenceStatus.CANCELLED
                    && occurrence.getReportAcceptedAt() == null);
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
