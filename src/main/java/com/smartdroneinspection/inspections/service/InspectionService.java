package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.ChecklistItem;
import com.smartdroneinspection.assets.domain.ChecklistTemplate;
import com.smartdroneinspection.assets.domain.enums.ChecklistTemplateStatus;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.inspectionrequests.domain.InspectionAssignment;
import com.smartdroneinspection.inspectionrequests.domain.InspectionRequest;
import com.smartdroneinspection.inspectionrequests.domain.InspectionServiceOrder;
import com.smartdroneinspection.inspectionrequests.domain.enums.InspectionAssignmentStatus;
import com.smartdroneinspection.inspectionrequests.repository.InspectionAssignmentRepository;
import com.smartdroneinspection.inspectionrequests.repository.InspectionRequestRepository;
import com.smartdroneinspection.inspectionrequests.repository.InspectionServiceOrderRepository;
import com.smartdroneinspection.inspections.api.dto.request.ChecklistResponseRequest;
import com.smartdroneinspection.inspections.api.dto.response.ChecklistResponseResponse;
import com.smartdroneinspection.inspections.api.dto.response.InspectionAssignmentResponse;
import com.smartdroneinspection.inspections.api.dto.response.InspectionChecklistItemResponse;
import com.smartdroneinspection.inspections.api.dto.response.StartInspectionResponse;
import com.smartdroneinspection.inspections.domain.ChecklistResponse;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.repository.ChecklistResponseRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class InspectionService {

  private final InspectionAssignmentRepository assignments;
  private final InspectionServiceOrderRepository serviceOrders;
  private final InspectionRequestRepository requests;
  private final AssetRepository assets;
  private final ChecklistTemplateRepository checklistTemplates;
  private final InspectionRepository inspections;
  private final ChecklistResponseRepository responses;
  private final UserAccess users;
  private final ObjectMapper objectMapper;

  public InspectionService(
      InspectionAssignmentRepository assignments,
      InspectionServiceOrderRepository serviceOrders,
      InspectionRequestRepository requests,
      AssetRepository assets,
      ChecklistTemplateRepository checklistTemplates,
      InspectionRepository inspections,
      ChecklistResponseRepository responses,
      UserAccess users,
      ObjectMapper objectMapper) {
    this.assignments = assignments;
    this.serviceOrders = serviceOrders;
    this.requests = requests;
    this.assets = assets;
    this.checklistTemplates = checklistTemplates;
    this.inspections = inspections;
    this.responses = responses;
    this.users = users;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public List<InspectionAssignmentResponse> listAcceptedAssignments(UUID inspectorId) {
    requireActiveInspector(inspectorId);
    return assignments
        .findByInspectorUserIdAndStatusOrderByDeadlineAsc(
            inspectorId, InspectionAssignmentStatus.ACCEPTED)
        .stream()
        .map(this::toAssignmentResponse)
        .toList();
  }

  @Transactional
  public StartInspectionResponse start(UUID inspectorId, UUID assignmentId) {
    requireActiveInspector(inspectorId);
    InspectionAssignment assignment =
        assignments
            .findForUpdateByIdAndInspectorUserId(assignmentId, inspectorId)
            .orElseThrow(this::scopeDenied);
    if (assignment.getStatus() != InspectionAssignmentStatus.ACCEPTED) {
      throw stateConflict("Only accepted assignments can start an inspection.");
    }

    var existing = inspections.findByAcceptedAssignmentId(assignmentId);
    if (existing.isPresent()) {
      Inspection inspection = existing.get();
      if (!inspection.getAuthorUserId().equals(inspectorId)) {
        throw scopeDenied();
      }
      if (inspection.getStatus() == InspectionStatus.READY_FOR_INSPECTION) {
        inspection.start();
        inspections.saveAndFlush(inspection);
      } else if (inspection.getStatus() != InspectionStatus.IN_PROGRESS) {
        throw stateConflict("This inspection can no longer be reopened.");
      }
      return toStartResponse(inspection);
    }

    AssignmentContext context = resolveAssignment(assignment);
    Inspection inspection =
        new Inspection(
            context.serviceOrder().getId(),
            assignment.getId(),
            context.asset().getId(),
            inspectorId,
            context.template().getId());
    inspection.start();
    inspections.saveAndFlush(inspection);
    return toStartResponse(inspection);
  }

  @Transactional
  public ChecklistResponseResponse saveChecklistResponse(
      UUID inspectorId, UUID inspectionId, UUID checklistItemId, ChecklistResponseRequest request) {
    requireActiveInspector(inspectorId);
    Inspection inspection =
        inspections
            .findForUpdateByIdAndAuthorUserId(inspectionId, inspectorId)
            .orElseThrow(this::scopeDenied);
    InspectionAssignment assignment =
        assignments
            .findByIdAndInspectorUserId(inspection.getAcceptedAssignmentId(), inspectorId)
            .orElseThrow(this::scopeDenied);
    if (assignment.getStatus() != InspectionAssignmentStatus.ACCEPTED) {
      throw scopeDenied();
    }
    if (inspection.getStatus() != InspectionStatus.IN_PROGRESS) {
      throw stateConflict("Checklist responses can only be saved for an in-progress inspection.");
    }

    ChecklistTemplate template =
        checklistTemplates
            .findDetailedById(inspection.getChecklistTemplateId())
            .orElseThrow(this::inspectionNotFound);
    ChecklistItem item =
        template.getItems().stream()
            .filter(candidate -> candidate.getId().equals(checklistItemId))
            .findFirst()
            .orElseThrow(this::invalidChecklistResponse);
    validateResponse(item, request.responseValue());
    String responseJson = serialize(request.responseValue());

    ChecklistResponse response =
        responses
            .findByInspectionIdAndChecklistItemId(inspectionId, checklistItemId)
            .orElseGet(
                () ->
                    new ChecklistResponse(
                        inspectionId, checklistItemId, responseJson, request.notes(), inspectorId));
    if (response.getId() != null) {
      response.update(responseJson, request.notes(), inspectorId);
    }
    responses.saveAndFlush(response);
    return toChecklistResponse(response);
  }

  @Transactional(readOnly = true)
  public List<InspectionChecklistItemResponse> listChecklist(UUID inspectorId, UUID inspectionId) {
    requireActiveInspector(inspectorId);
    Inspection inspection =
        inspections
            .findByIdAndAuthorUserId(inspectionId, inspectorId)
            .orElseThrow(this::scopeDenied);
    InspectionAssignment assignment =
        assignments
            .findByIdAndInspectorUserId(inspection.getAcceptedAssignmentId(), inspectorId)
            .filter(value -> value.getStatus() == InspectionAssignmentStatus.ACCEPTED)
            .orElseThrow(this::scopeDenied);
    if (!assignment.getInspectorUserId().equals(inspectorId)) {
      throw scopeDenied();
    }
    if (inspection.getStatus() != InspectionStatus.IN_PROGRESS) {
      throw stateConflict("Checklist responses are available only for an in-progress inspection.");
    }
    ChecklistTemplate template =
        checklistTemplates
            .findDetailedById(inspection.getChecklistTemplateId())
            .orElseThrow(this::inspectionNotFound);
    java.util.Map<UUID, ChecklistResponse> savedResponses =
        responses.findByInspectionId(inspectionId).stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    ChecklistResponse::getChecklistItemId, response -> response));
    return template.getItems().stream()
        .map(
            item -> {
              ChecklistResponse response = savedResponses.get(item.getId());
              return new InspectionChecklistItemResponse(
                  item.getId(),
                  item.getItemCode(),
                  item.getSectionName(),
                  item.getPrompt(),
                  item.getResponseType().name(),
                  item.isRequired(),
                  item.getDisplayOrder(),
                  item.getGuidance(),
                  item.getValidationConfig(),
                  response == null ? null : parseResponse(response.getResponseValue()),
                  response == null ? null : response.getNotes(),
                  response == null ? null : response.getCompletedAt());
            })
        .toList();
  }

  private InspectionAssignmentResponse toAssignmentResponse(InspectionAssignment assignment) {
    InspectionServiceOrder order =
        serviceOrders
            .findById(assignment.getServiceOrderId())
            .orElseThrow(this::inspectionNotFound);
    InspectionRequest request =
        requests.findById(order.getInspectionRequestId()).orElseThrow(this::inspectionNotFound);
    assets
        .findByIdAndOrganizationId(request.getAssetId(), request.getOrganizationId())
        .orElseThrow(this::inspectionNotFound);
    UUID inspectionId =
        inspections
            .findByAcceptedAssignmentId(assignment.getId())
            .map(Inspection::getId)
            .orElse(null);
    return new InspectionAssignmentResponse(
        assignment.getId(),
        order.getId(),
        request.getAssetId(),
        assignment.getDeadline(),
        assignment.getStatus(),
        inspectionId);
  }

  private AssignmentContext resolveAssignment(InspectionAssignment assignment) {
    InspectionServiceOrder order =
        serviceOrders
            .findById(assignment.getServiceOrderId())
            .orElseThrow(this::inspectionNotFound);
    InspectionRequest request =
        requests.findById(order.getInspectionRequestId()).orElseThrow(this::inspectionNotFound);
    var asset =
        assets
            .findByIdAndOrganizationId(request.getAssetId(), request.getOrganizationId())
            .orElseThrow(this::inspectionNotFound);
    ChecklistTemplate template =
        checklistTemplates
            .findDetailedById(request.getChecklistTemplateId())
            .orElseThrow(this::inspectionNotFound);
    if (template.getStatus() != ChecklistTemplateStatus.ACTIVE) {
      throw stateConflict("The inspection checklist template is not active.");
    }
    return new AssignmentContext(order, asset, template);
  }

  private void requireActiveInspector(UUID userId) {
    users
        .findActiveUser(userId)
        .filter(user -> user.hasRole(Roles.INSPECTOR))
        .orElseThrow(this::scopeDenied);
  }

  private void validateResponse(ChecklistItem item, JsonNode responseValue) {
    if (responseValue == null || !responseValue.isObject()) {
      throw invalidChecklistResponse();
    }
    JsonNode value = responseValue.get("value");
    if (value == null || value.isNull()) {
      throw invalidChecklistResponse();
    }
    if (item.isRequired() && value.isTextual() && value.asText().isBlank()) {
      throw invalidChecklistResponse();
    }

    switch (item.getResponseType()) {
      case PASS_FAIL -> requireTextValue(value, "PASS", "FAIL");
      case TEXT -> requireTextValue(value);
      case NUMBER -> {
        if (!value.isNumber()) {
          throw invalidChecklistResponse();
        }
      }
      case BOOLEAN -> {
        if (!value.isBoolean()) {
          throw invalidChecklistResponse();
        }
      }
      case CHOICE -> {
        requireTextValue(value);
        validateConfiguredChoice(item, value.asText());
      }
    }
  }

  private void requireTextValue(JsonNode value, String... allowedValues) {
    if (!value.isTextual() || value.asText().isBlank()) {
      throw invalidChecklistResponse();
    }
    if (allowedValues.length > 0) {
      String normalized = value.asText().trim().toUpperCase(Locale.ROOT);
      boolean allowed =
          java.util.Arrays.stream(allowedValues)
              .anyMatch(candidate -> candidate.equals(normalized));
      if (!allowed) {
        throw invalidChecklistResponse();
      }
    }
  }

  private void validateConfiguredChoice(ChecklistItem item, String value) {
    String config = item.getValidationConfig();
    if (config == null || config.isBlank()) {
      return;
    }
    try {
      JsonNode choices = objectMapper.readTree(config).path("choices");
      if (choices.isArray()
          && java.util.stream.StreamSupport.stream(choices.spliterator(), false)
              .noneMatch(choice -> choice.isTextual() && choice.asText().equals(value))) {
        throw invalidChecklistResponse();
      }
    } catch (JacksonException exception) {
      throw invalidChecklistResponse();
    }
  }

  private String serialize(JsonNode value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JacksonException exception) {
      throw invalidChecklistResponse();
    }
  }

  private JsonNode parseResponse(String value) {
    try {
      return objectMapper.readTree(value);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Stored checklist response is not valid JSON", exception);
    }
  }

  private StartInspectionResponse toStartResponse(Inspection inspection) {
    return new StartInspectionResponse(
        inspection.getId(),
        inspection.getAcceptedAssignmentId(),
        inspection.getServiceOrderId(),
        inspection.getAssetId(),
        inspection.getChecklistTemplateId(),
        inspection.getStatus(),
        inspection.getStartedAt());
  }

  private ChecklistResponseResponse toChecklistResponse(ChecklistResponse response) {
    try {
      return new ChecklistResponseResponse(
          response.getId(),
          response.getInspectionId(),
          response.getChecklistItemId(),
          objectMapper.readTree(response.getResponseValue()),
          response.getNotes(),
          response.getCompletedByUserId(),
          response.getCompletedAt());
    } catch (JacksonException exception) {
      throw new IllegalStateException("Stored checklist response is not valid JSON", exception);
    }
  }

  private BusinessException scopeDenied() {
    return new BusinessException(
        HttpStatus.FORBIDDEN,
        "INSPECTION_SCOPE_DENIED",
        "The inspection is not available to this Inspector.");
  }

  private BusinessException inspectionNotFound() {
    return new BusinessException(
        HttpStatus.NOT_FOUND, "INSPECTION_NOT_FOUND", "Inspection resource was not found.");
  }

  private BusinessException stateConflict(String message) {
    return new BusinessException(HttpStatus.CONFLICT, "INSPECTION_STATE_CONFLICT", message);
  }

  private BusinessException invalidChecklistResponse() {
    return new BusinessException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "CHECKLIST_RESPONSE_INVALID",
        "The checklist response is invalid for this item.");
  }

  private record AssignmentContext(
      InspectionServiceOrder serviceOrder, Asset asset, ChecklistTemplate template) {}
}
