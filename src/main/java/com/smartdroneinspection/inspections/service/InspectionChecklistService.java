package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.assets.domain.ChecklistItem;
import com.smartdroneinspection.assets.domain.ChecklistTemplate;
import com.smartdroneinspection.assets.domain.enums.ChecklistTemplateStatus;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.inspections.api.dto.request.ChecklistResponseRequest;
import com.smartdroneinspection.inspections.api.dto.response.ChecklistResponseResponse;
import com.smartdroneinspection.inspections.api.dto.response.InspectionChecklistItemResponse;
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
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * MF2-11: the checklist the assigned Inspector records against an inspection.
 *
 * <p>The checklist is system-managed text, so the section, prompt and guidance are read in the
 * locale the request asked for, with English as the fallback whenever a Vietnamese version has not
 * been recorded. The Inspector's own answer and note are returned exactly as recorded: a note is
 * evidence of what was observed on site, not copy to be translated.
 *
 * <p>Which checklist applies is decided by the asset category, because the template is
 * platform-scoped rather than organization-scoped. An inspection whose category has no active
 * template has no checklist, and an empty checklist is the honest answer rather than a missing
 * resource.
 */
@Service
public class InspectionChecklistService {

  private final InspectionRepository inspections;
  private final ChecklistResponseRepository responses;
  private final ChecklistTemplateRepository templates;
  private final AssetRepository assets;
  private final UserAccess userAccess;
  private final ObjectMapper objectMapper;

  public InspectionChecklistService(
      InspectionRepository inspections,
      ChecklistResponseRepository responses,
      ChecklistTemplateRepository templates,
      AssetRepository assets,
      UserAccess userAccess,
      ObjectMapper objectMapper) {
    this.inspections = inspections;
    this.responses = responses;
    this.templates = templates;
    this.assets = assets;
    this.userAccess = userAccess;
    this.objectMapper = objectMapper;
  }

  /** The checklist for an inspection, in the requested locale. */
  @Transactional(readOnly = true)
  public List<InspectionChecklistItemResponse> listChecklist(
      UUID inspectorId, UUID inspectionId, Locale locale) {
    Inspection inspection = requireAssignedInspection(inspectorId, inspectionId);
    ChecklistTemplate template = activeTemplateFor(inspection);
    if (template == null) {
      return List.of();
    }
    Map<UUID, ChecklistResponse> saved =
        responses.findByInspectionId(inspectionId).stream()
            .collect(
                Collectors.toMap(
                    ChecklistResponse::getChecklistItemId,
                    Function.identity(),
                    (first, second) -> first));
    return template.getItems().stream()
        .map(
            item -> {
              ChecklistResponse response = saved.get(item.getId());
              return new InspectionChecklistItemResponse(
                  item.getId(),
                  item.getItemCode(),
                  item.localizedSectionName(locale),
                  item.localizedPrompt(locale),
                  item.getResponseType().name(),
                  item.isRequired(),
                  item.getDisplayOrder(),
                  item.localizedGuidance(locale),
                  item.getValidationConfig(),
                  response == null ? null : parseResponse(response.getResponseValue()),
                  response == null ? null : response.getNotes(),
                  response == null ? null : response.getCompletedAt());
            })
        .toList();
  }

  /** Records or replaces the Inspector's answer to one checklist item. */
  @Transactional
  public ChecklistResponseResponse saveResponse(
      UUID inspectorId, UUID inspectionId, UUID checklistItemId, ChecklistResponseRequest request) {
    Inspection inspection = requireAssignedInspection(inspectorId, inspectionId);
    if (inspection.getStatus() == InspectionStatus.REPORT_PUBLISHED
        || inspection.getStatus() == InspectionStatus.REPAIR_PENDING
        || inspection.getStatus() == InspectionStatus.COMPLETED) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "INSPECTION_STATE_CONFLICT",
          "Checklist responses are immutable after publication");
    }
    ChecklistTemplate template = activeTemplateFor(inspection);
    ChecklistItem item =
        template == null
            ? null
            : template.getItems().stream()
                .filter(candidate -> candidate.getId().equals(checklistItemId))
                .findFirst()
                .orElse(null);
    if (item == null) {
      throw invalidChecklistResponse();
    }
    validateResponse(item, request.responseValue());

    ChecklistResponse response =
        responses
            .findByInspectionIdAndChecklistItemId(inspectionId, checklistItemId)
            .orElseGet(
                () ->
                    new ChecklistResponse(
                        inspectionId,
                        checklistItemId,
                        serialize(request.responseValue()),
                        request.notes()));
    response.recordResponse(serialize(request.responseValue()), request.notes());
    responses.saveAndFlush(response);
    return new ChecklistResponseResponse(
        response.getId(),
        response.getInspectionId(),
        response.getChecklistItemId(),
        parseResponse(response.getResponseValue()),
        response.getNotes(),
        response.getCompletedAt());
  }

  /**
   * The active checklist for the inspection's asset category.
   *
   * <p>The newest active version wins, so a republished checklist takes effect for the next
   * inspection without rewriting the responses already recorded against the earlier version.
   */
  private ChecklistTemplate activeTemplateFor(Inspection inspection) {
    UUID categoryId =
        assets
            .findByIdAndOrganizationId(inspection.getAssetId(), inspection.getOrganizationId())
            .map(asset -> asset.getCategoryId())
            .orElse(null);
    if (categoryId == null) {
      return null;
    }
    return templates
        .findFirstByAssetCategoryIdAndStatusOrderByVersionNumberDesc(
            categoryId, ChecklistTemplateStatus.ACTIVE)
        .orElse(null);
  }

  private Inspection requireAssignedInspection(UUID inspectorId, UUID inspectionId) {
    UserAccess.ActiveUser actor =
        userAccess
            .findActiveUser(inspectorId)
            .filter(candidate -> candidate.hasRole(Roles.INSPECTOR))
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.FORBIDDEN,
                        "CHECKLIST_SCOPE_DENIED",
                        "Only an active Inspector may record checklist responses."));
    if (actor.organizationId() == null) {
      throw new BusinessException(
          HttpStatus.NOT_FOUND, "INSPECTION_NOT_FOUND", "The inspection was not found.");
    }
    return inspections
        .findByIdAndOrganizationIdAndInspectorId(inspectionId, actor.organizationId(), inspectorId)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND, "INSPECTION_NOT_FOUND", "The inspection was not found."));
  }

  private void validateResponse(ChecklistItem item, JsonNode responseValue) {
    if (responseValue == null || !responseValue.isObject()) {
      throw invalidChecklistResponse();
    }
    JsonNode value = responseValue.get("value");
    if (value == null || value.isNull()) {
      throw invalidChecklistResponse();
    }
    if (item.isRequired() && value.isString() && value.asString().isBlank()) {
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
        validateConfiguredChoice(item, value.asString());
      }
    }
  }

  private void requireTextValue(JsonNode value, String... allowedValues) {
    if (!value.isString() || value.asString().isBlank()) {
      throw invalidChecklistResponse();
    }
    if (allowedValues.length > 0) {
      String normalized = value.asString().trim().toUpperCase(Locale.ROOT);
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
              .noneMatch(choice -> choice.isString() && choice.asString().equals(value))) {
        throw invalidChecklistResponse();
      }
    } catch (tools.jackson.core.JacksonException exception) {
      throw invalidChecklistResponse();
    }
  }

  private String serialize(JsonNode value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (tools.jackson.core.JacksonException exception) {
      throw invalidChecklistResponse();
    }
  }

  private JsonNode parseResponse(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readTree(raw);
    } catch (tools.jackson.core.JacksonException exception) {
      return null;
    }
  }

  private static BusinessException invalidChecklistResponse() {
    return new BusinessException(
        HttpStatus.BAD_REQUEST,
        "CHECKLIST_RESPONSE_INVALID",
        "The checklist response does not match the item definition.");
  }
}
