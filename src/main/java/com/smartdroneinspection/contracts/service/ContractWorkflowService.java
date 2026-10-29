package com.smartdroneinspection.contracts.service;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.enums.AssetStatus;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.contracts.api.dto.request.AssignOccurrenceRequest;
import com.smartdroneinspection.contracts.api.dto.request.CreateServiceRequest;
import com.smartdroneinspection.contracts.api.dto.request.IssueServiceQuoteRequest;
import com.smartdroneinspection.contracts.api.dto.response.ServiceRequestMessageResponse;
import com.smartdroneinspection.contracts.api.dto.response.ServiceRequestOccurrenceResponse;
import com.smartdroneinspection.contracts.api.dto.response.ServiceRequestQuoteResponse;
import com.smartdroneinspection.contracts.api.dto.response.ServiceRequestResponse;
import com.smartdroneinspection.contracts.domain.ServiceRequest;
import com.smartdroneinspection.contracts.domain.ServiceRequest.RequestType;
import com.smartdroneinspection.contracts.domain.ServiceRequestMessage;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence;
import com.smartdroneinspection.contracts.domain.ServiceRequestQuote;
import com.smartdroneinspection.contracts.repository.ServiceRequestMessageRepository;
import com.smartdroneinspection.contracts.repository.ServiceRequestOccurrenceRepository;
import com.smartdroneinspection.contracts.repository.ServiceRequestQuoteRepository;
import com.smartdroneinspection.contracts.repository.ServiceRequestRepository;
import com.smartdroneinspection.inspections.spi.Mf2InspectionAssignment;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.UserAccess;
import java.io.ByteArrayInputStream;
import java.io.IOException;
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
public class ContractWorkflowService {

  static final long MAX_CONTRACT_SIZE_BYTES = 10L * 1024 * 1024;
  private static final String REQUEST_NOT_FOUND = "REQUEST_NOT_FOUND";
  private static final String REQUEST_NOT_FOUND_DETAIL = "Service request not found";
  private static final Set<String> ALLOWED_CONTRACT_TYPES =
      Set.of("application/pdf", "image/jpeg", "image/png");

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
              input.pricePerOccurrenceUsd(),
              manager.id());
      request.markQuoted();
    } catch (IllegalArgumentException | IllegalStateException exception) {
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
    } catch (IllegalStateException exception) {
      throw failure(HttpStatus.CONFLICT, "QUOTE_ACCEPTANCE_INVALID", exception.getMessage());
    }
    requests.saveAndFlush(request);
    return toResponse(request);
  }

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
          "Only PDF, JPEG, and PNG files are allowed");
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
    EvidenceObjectStore store =
        objectStore.orElseThrow(
            () ->
                failure(
                    HttpStatus.BAD_GATEWAY,
                    "STORAGE_UNAVAILABLE",
                    "Contract storage is not configured"));
    String fileName = sanitizeFileName(file.getOriginalFilename());
    String objectKey = "contracts/requests/" + requestId + "/" + UUID.randomUUID();
    try {
      store.put(objectKey, contentType, content.length, new ByteArrayInputStream(content));
    } catch (IOException exception) {
      throw failure(HttpStatus.BAD_GATEWAY, "STORAGE_FAILED", "Failed to store signed contract");
    }
    try {
      request.recordSignedContract(
          objectKey, fileName, contentType, content.length, sha256(content), manager.id());
      requests.saveAndFlush(request);
      materializeOccurrences(request);
    } catch (RuntimeException exception) {
      try {
        store.delete(objectKey);
      } catch (IOException ignored) {
        // Best-effort cleanup when the database transaction cannot commit metadata.
      }
      throw exception;
    }
    return toResponse(request);
  }

  @Transactional
  public ServiceRequestResponse assignOccurrence(
      UUID actorId, UUID requestId, UUID occurrenceId, AssignOccurrenceRequest input) {
    UserAccess.ActiveUser manager = requireActor(actorId, Mf2ActorPolicy.ActorKind.MANAGER);
    ServiceRequest request = requireRequestForManagerLocked(manager, requestId);
    if (request.getStatus() != ServiceRequest.ServiceRequestStatus.CONTRACT_SIGNED
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
    occurrence.assign(input.inspectorId(), input.droneId(), inspectionId, manager.id());
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

  private void materializeOccurrences(ServiceRequest request) {
    if (!occurrences.findByRequestIdOrderByOccurrenceIndexAsc(request.getId()).isEmpty()) {
      return;
    }
    ServiceRequestQuote quote =
        quotes
            .findByRequestIdAndVersionNo(request.getId(), request.getAcceptedQuoteVersion())
            .orElseThrow(
                () ->
                    failure(
                        HttpStatus.CONFLICT, "QUOTE_NOT_FOUND", "Accepted quote was not found"));
    int count = request.getRequestType() == RequestType.ONE_TIME ? 1 : 0;
    if (request.getRequestType() == RequestType.RECURRING && request.getOccurrenceCount() != null) {
      count = request.getOccurrenceCount();
    } else if (request.getRequestType() == RequestType.RECURRING) {
      count = estimateOccurrenceCount(request, quote);
    }
    for (int index = 0; index < count; index++) {
      Instant start =
          index == 0 ? quote.getFirstOccurrenceStartAt() : occurrenceAt(request, quote, index);
      Instant end =
          start.plus(
              Duration.between(quote.getFirstOccurrenceStartAt(), quote.getFirstOccurrenceEndAt()));
      occurrences.save(new ServiceRequestOccurrence(request.getId(), index + 1, start, end));
    }
    occurrences.flush();
  }

  private int estimateOccurrenceCount(ServiceRequest request, ServiceRequestQuote quote) {
    int count = 0;
    LocalDate date = request.getRequestedFrom();
    LocalDate until = request.getRecurrenceEndDate();
    ZoneId zone = ZoneId.of(request.getTimezone());
    while (!date.isAfter(until) && count < 100) {
      Instant candidate = date.atTime(request.getWindowStart()).atZone(zone).toInstant();
      if (!candidate.isBefore(quote.getFirstOccurrenceStartAt())) {
        count++;
      }
      date = advance(date, request.getRecurrenceUnit(), request.getRecurrenceInterval());
    }
    if (count < 1) {
      throw failure(
          HttpStatus.BAD_REQUEST, "RECURRENCE_INVALID", "Recurring range contains no occurrences");
    }
    return count;
  }

  private Instant occurrenceAt(ServiceRequest request, ServiceRequestQuote quote, int index) {
    ZoneId zone = ZoneId.of(request.getTimezone());
    LocalDate date = request.getRequestedFrom();
    int remaining = index;
    while (remaining > 0) {
      date = advance(date, request.getRecurrenceUnit(), request.getRecurrenceInterval());
      remaining--;
    }
    Instant result = date.atTime(request.getWindowStart()).atZone(zone).toInstant();
    if (request.getRecurrenceEndDate() != null && date.isAfter(request.getRecurrenceEndDate())) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "RECURRENCE_INVALID",
          "Occurrence falls after recurrence end date");
    }
    if (result.isBefore(quote.getFirstOccurrenceStartAt())) {
      return occurrenceAt(request, quote, index + 1);
    }
    return result;
  }

  private LocalDate advance(LocalDate date, String unit, int interval) {
    return switch (unit) {
      case "DAY" -> date.plusDays(interval);
      case "WEEK" -> date.plusWeeks(interval);
      case "MONTH" -> date.plusMonths(interval);
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
    if (actorPolicy.actorKind(manager.roles(), manager.organizationId())
        != Mf2ActorPolicy.ActorKind.MANAGER) {
      throw forbidden();
    }
    return requests
        .findById(requestId)
        .orElseThrow(
            () -> failure(HttpStatus.NOT_FOUND, REQUEST_NOT_FOUND, REQUEST_NOT_FOUND_DETAIL));
  }

  private ServiceRequest requireRequestForManagerLocked(
      UserAccess.ActiveUser manager, UUID requestId) {
    if (actorPolicy.actorKind(manager.roles(), manager.organizationId())
        != Mf2ActorPolicy.ActorKind.MANAGER) {
      throw forbidden();
    }
    return requests
        .findWithLockById(requestId)
        .orElseThrow(
            () -> failure(HttpStatus.NOT_FOUND, REQUEST_NOT_FOUND, REQUEST_NOT_FOUND_DETAIL));
  }

  private UserAccess.ActiveUser requireActor(UUID actorId, Mf2ActorPolicy.ActorKind expected) {
    UserAccess.ActiveUser actor = users.findActiveUser(actorId).orElseThrow(this::forbidden);
    if (actorPolicy.actorKind(actor.roles(), actor.organizationId())
            == Mf2ActorPolicy.ActorKind.NONE
        || (expected != null
            && actorPolicy.actorKind(actor.roles(), actor.organizationId()) != expected)) {
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
}
