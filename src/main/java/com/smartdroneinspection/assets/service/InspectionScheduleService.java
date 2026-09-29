package com.smartdroneinspection.assets.service;

import com.smartdroneinspection.assets.api.dto.response.InspectionScheduleResponse;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.ChecklistTemplate;
import com.smartdroneinspection.assets.domain.InspectionSchedule;
import com.smartdroneinspection.assets.domain.enums.InspectionScheduleStatus;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.assets.repository.InspectionScheduleRepository;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspectionScheduleService {

  private final InspectionScheduleRepository schedules;
  private final AssetRepository assets;
  private final ChecklistTemplateRepository templates;
  private final UserAccess userAccess;

  public InspectionScheduleService(
      InspectionScheduleRepository schedules,
      AssetRepository assets,
      ChecklistTemplateRepository templates,
      UserAccess userAccess) {
    this.schedules = schedules;
    this.assets = assets;
    this.templates = templates;
    this.userAccess = userAccess;
  }

  @Transactional(readOnly = true)
  public List<InspectionScheduleResponse> listForClient(UUID actorId, UUID assetId) {
    requireOwnedAsset(actorId, assetId);
    return schedules.findByAssetIdOrderByNextDueAt(assetId).stream().map(this::toResponse).toList();
  }

  @Transactional
  public InspectionScheduleResponse pause(UUID actorId, UUID scheduleId) {
    InspectionSchedule schedule = requireOwnedSchedule(actorId, scheduleId);
    if (schedule.getStatus() != InspectionScheduleStatus.ACTIVE) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "INVALID_STATE", "Only active schedules can be paused");
    }
    schedule.pause();
    return toResponse(schedules.saveAndFlush(schedule));
  }

  @Transactional
  public InspectionScheduleResponse activate(UUID actorId, UUID scheduleId) {
    InspectionSchedule schedule = requireOwnedSchedule(actorId, scheduleId);
    if (schedule.getStatus() == InspectionScheduleStatus.ACTIVE) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "INVALID_STATE", "Schedule is already active");
    }
    Asset asset =
        assets
            .findById(schedule.getAssetId())
            .orElseThrow(
                () -> new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Asset not found"));
    ChecklistTemplate template =
        templates
            .findById(schedule.getChecklistTemplateId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Checklist not found"));
    try {
      schedule.activate(asset.getStatus(), template.getStatus());
    } catch (IllegalStateException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, "INVALID_STATE", ex.getMessage());
    }
    return toResponse(schedules.saveAndFlush(schedule));
  }

  private InspectionSchedule requireOwnedSchedule(UUID actorId, UUID scheduleId) {
    InspectionSchedule schedule =
        schedules
            .findById(scheduleId)
            .orElseThrow(
                () ->
                    new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Schedule not found"));
    requireOwnedAsset(actorId, schedule.getAssetId());
    return schedule;
  }

  private void requireOwnedAsset(UUID actorId, UUID assetId) {
    UserAccess.ActiveUser actor =
        userAccess
            .findActiveUser(actorId)
            .orElseThrow(
                () ->
                    new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "User is not active"));
    if (actor.organizationId() == null) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "No organization scope");
    }
    assets
        .findByIdAndOrganizationId(assetId, actor.organizationId())
        .orElseThrow(
            () -> new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Asset not found"));
  }

  private InspectionScheduleResponse toResponse(InspectionSchedule schedule) {
    return new InspectionScheduleResponse(
        schedule.getId(),
        schedule.getAssetId(),
        schedule.getChecklistTemplateId(),
        schedule.getFrequencyUnit().name(),
        schedule.getFrequencyInterval(),
        schedule.getNextDueAt(),
        schedule.getStatus().name());
  }
}
