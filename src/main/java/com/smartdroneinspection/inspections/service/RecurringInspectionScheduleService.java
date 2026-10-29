package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.enums.AssetStatus;
import com.smartdroneinspection.assets.domain.enums.DroneServiceability;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.inspections.api.dto.request.CreateRecurringScheduleRequest;
import com.smartdroneinspection.inspections.api.dto.request.SkipOccurrenceRequest;
import com.smartdroneinspection.inspections.api.dto.response.RecurringScheduleDetailResponse;
import com.smartdroneinspection.inspections.api.dto.response.RecurringSchedulePageResponse;
import com.smartdroneinspection.inspections.api.dto.response.RecurringScheduleResponse;
import com.smartdroneinspection.inspections.api.dto.response.ScheduleOccurrenceResponse;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.RecurringInspectionSchedule;
import com.smartdroneinspection.inspections.domain.ScheduleOccurrence;
import com.smartdroneinspection.inspections.domain.enums.OccurrenceStatus;
import com.smartdroneinspection.inspections.repository.RecurringInspectionScheduleRepository;
import com.smartdroneinspection.inspections.repository.ScheduleOccurrenceRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.subscriptions.SubscriptionEntitlement;
import com.smartdroneinspection.users.UserAccess;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecurringInspectionScheduleService {

  private final RecurringInspectionScheduleRepository scheduleRepository;
  private final ScheduleOccurrenceRepository occurrenceRepository;
  private final InspectionCreationService creationService;
  private final AssetRepository assetRepository;
  private final DroneRepository droneRepository;
  private final UserAccess userAccess;
  private final SubscriptionEntitlement subscriptionEntitlement;

  public RecurringInspectionScheduleService(
      RecurringInspectionScheduleRepository scheduleRepository,
      ScheduleOccurrenceRepository occurrenceRepository,
      InspectionCreationService creationService,
      AssetRepository assetRepository,
      DroneRepository droneRepository,
      UserAccess userAccess,
      SubscriptionEntitlement subscriptionEntitlement) {
    this.scheduleRepository = scheduleRepository;
    this.occurrenceRepository = occurrenceRepository;
    this.creationService = creationService;
    this.assetRepository = assetRepository;
    this.droneRepository = droneRepository;
    this.userAccess = userAccess;
    this.subscriptionEntitlement = subscriptionEntitlement;
  }

  @Transactional
  public RecurringScheduleResponse create(UUID actorId, CreateRecurringScheduleRequest request) {
    UserAccess.ActiveUser actor = requireOrgAdmin(actorId);
    UUID organizationId = actor.organizationId();

    subscriptionEntitlement.requireActive(organizationId, Instant.now());

    Asset asset =
        assetRepository
            .findByIdAndOrganizationId(request.assetId(), organizationId)
            .orElseThrow(
                () -> new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Asset not found"));

    if (asset.getStatus() == AssetStatus.RETIRED) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "INVALID_STATE", "Cannot create a schedule for a retired asset.");
    }

    if (scheduleRepository.existsByOrganizationIdAndAssetIdAndStatus(
        organizationId, asset.getId(), "ACTIVE")) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "ACTIVE_SCHEDULE_EXISTS",
          "This asset already has an active recurring inspection schedule. End the existing schedule before creating a new one.");
    }

    if (request.inspectorId() == null || request.droneId() == null) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "ASSIGNMENT_REQUIRED",
          "Both inspector and drone must be provided.");
    }
    UserAccess.ActiveUser inspector =
        userAccess
            .findActiveUser(request.inspectorId())
            .filter(u -> organizationId.equals(u.organizationId()) && u.hasRole(Roles.INSPECTOR))
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_INSPECTOR",
                        "Assigned inspector must be active in the organization."));

    Drone drone =
        droneRepository
            .findByIdAndOrganizationId(request.droneId(), organizationId)
            .filter(d -> d.getServiceability() == DroneServiceability.ACTIVE)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_DRONE",
                        "Assigned drone must be active in the organization."));

    RecurringInspectionSchedule schedule;
    try {
      schedule =
          new RecurringInspectionSchedule(
              organizationId,
              asset.getId(),
              actor.id(),
              inspector.id(),
              drone.getId(),
              request.anchor(),
              request.timezone(),
              request.unit(),
              request.interval(),
              request.durationSeconds(),
              request.resolvedReminderDays(),
              request.endsAt());
    } catch (IllegalArgumentException | java.time.DateTimeException ex) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_SCHEDULE", ex.getMessage());
    }

    RecurringInspectionSchedule saved = scheduleRepository.saveAndFlush(schedule);

    // Trigger immediate generation for occurrences due within lookahead horizon
    Instant now = Instant.now();
    Instant horizon = now.plus(Math.max(saved.getReminderDays(), 7), ChronoUnit.DAYS);
    generateOccurrencesInternal(saved.getId(), now, horizon);

    RecurringInspectionSchedule refreshed =
        scheduleRepository.findById(saved.getId()).orElse(saved);
    return toResponse(refreshed);
  }

  @Transactional(readOnly = true)
  public RecurringScheduleDetailResponse get(UUID actorId, UUID scheduleId) {
    UserAccess.ActiveUser actor = requireActive(actorId);
    UUID organizationId = actor.organizationId();

    RecurringInspectionSchedule schedule =
        scheduleRepository
            .findByIdAndOrganizationId(scheduleId, organizationId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Inspection schedule not found"));

    List<ScheduleOccurrence> occurrences =
        occurrenceRepository.findByScheduleIdOrderByOccurrenceIndexAsc(scheduleId);
    List<ScheduleOccurrenceResponse> occurrenceResponses =
        occurrences.stream().map(this::toOccurrenceResponse).toList();

    return toDetailResponse(schedule, occurrenceResponses);
  }

  @Transactional(readOnly = true)
  public RecurringSchedulePageResponse list(
      UUID actorId, UUID assetId, String status, int page, int pageSize) {
    UserAccess.ActiveUser actor = requireActive(actorId);
    UUID organizationId = actor.organizationId();

    int zeroBasedPage = Math.max(0, page - 1);
    Pageable pageable =
        PageRequest.of(zeroBasedPage, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));

    Page<RecurringInspectionSchedule> result;
    if (assetId != null && status != null) {
      result =
          scheduleRepository.findByOrganizationIdAndAssetIdAndStatus(
              organizationId, assetId, status, pageable);
    } else if (assetId != null) {
      result = scheduleRepository.findByOrganizationIdAndAssetId(organizationId, assetId, pageable);
    } else if (status != null) {
      result = scheduleRepository.findByOrganizationIdAndStatus(organizationId, status, pageable);
    } else {
      result = scheduleRepository.findByOrganizationId(organizationId, pageable);
    }

    List<RecurringScheduleResponse> items =
        result.getContent().stream().map(this::toResponse).toList();
    return new RecurringSchedulePageResponse(
        items, page, pageSize, result.getTotalElements(), result.getTotalPages());
  }

  @Transactional
  public RecurringScheduleResponse pause(UUID actorId, UUID scheduleId) {
    UserAccess.ActiveUser actor = requireOrgAdmin(actorId);
    RecurringInspectionSchedule schedule =
        scheduleRepository
            .findWithLockByIdAndOrganizationId(scheduleId, actor.organizationId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Inspection schedule not found"));

    try {
      schedule.pause();
    } catch (IllegalStateException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, "INVALID_STATE", ex.getMessage());
    }

    RecurringInspectionSchedule saved = scheduleRepository.saveAndFlush(schedule);
    return toResponse(saved);
  }

  @Transactional
  public RecurringScheduleResponse resume(UUID actorId, UUID scheduleId) {
    UserAccess.ActiveUser actor = requireOrgAdmin(actorId);
    RecurringInspectionSchedule schedule =
        scheduleRepository
            .findWithLockByIdAndOrganizationId(scheduleId, actor.organizationId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Inspection schedule not found"));

    try {
      schedule.resume();
    } catch (IllegalStateException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, "INVALID_STATE", ex.getMessage());
    }

    RecurringInspectionSchedule saved = scheduleRepository.saveAndFlush(schedule);

    // When resumed, run generator with past-time protection
    Instant now = Instant.now();
    Instant horizon = now.plus(Math.max(saved.getReminderDays(), 7), ChronoUnit.DAYS);
    generateOccurrencesInternal(saved.getId(), now, horizon);

    RecurringInspectionSchedule refreshed =
        scheduleRepository.findById(saved.getId()).orElse(saved);
    return toResponse(refreshed);
  }

  @Transactional
  public RecurringScheduleResponse end(UUID actorId, UUID scheduleId) {
    UserAccess.ActiveUser actor = requireOrgAdmin(actorId);
    RecurringInspectionSchedule schedule =
        scheduleRepository
            .findWithLockByIdAndOrganizationId(scheduleId, actor.organizationId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Inspection schedule not found"));

    schedule.end();
    RecurringInspectionSchedule saved = scheduleRepository.saveAndFlush(schedule);
    return toResponse(saved);
  }

  @Transactional
  public ScheduleOccurrenceResponse retryOccurrence(
      UUID actorId, UUID scheduleId, UUID occurrenceId) {
    UserAccess.ActiveUser actor = requireOrgAdmin(actorId);
    UUID organizationId = actor.organizationId();

    RecurringInspectionSchedule schedule =
        scheduleRepository
            .findWithLockByIdAndOrganizationId(scheduleId, organizationId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Inspection schedule not found"));

    ScheduleOccurrence occurrence =
        occurrenceRepository
            .findByIdAndOrganizationId(occurrenceId, organizationId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Schedule occurrence not found"));

    if (!occurrence.getScheduleId().equals(scheduleId)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "INVALID_OCCURRENCE", "Occurrence does not belong to schedule.");
    }

    if (occurrence.getStatus() != OccurrenceStatus.BLOCKED) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "NOT_BLOCKED", "Only blocked occurrences can be retried.");
    }

    if (occurrence.getPlannedStartAt().isBefore(Instant.now())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "PAST_DUE",
          "Cannot retry an occurrence whose planned start time has already passed. Please skip this occurrence with a reason.");
    }

    occurrence.recordRetry();

    try {
      Inspection inspection =
          creationService.dispatchInspectionInternal(
              schedule.getOrganizationId(),
              schedule.getAssetId(),
              schedule.getInspectorId(),
              schedule.getDroneId(),
              occurrence.getPlannedStartAt(),
              occurrence.getPlannedEndAt(),
              schedule.getId());

      occurrence.markGenerated(inspection.getId());
      occurrenceRepository.saveAndFlush(occurrence);

      if (schedule.getNextOccurrence() == occurrence.getOccurrenceIndex()) {
        schedule.advance();
        scheduleRepository.saveAndFlush(schedule);
      }

      return toOccurrenceResponse(occurrence);
    } catch (Exception ex) {
      occurrence.markBlocked(ex.getMessage());
      occurrenceRepository.saveAndFlush(occurrence);
      if (ex instanceof BusinessException be) {
        throw be;
      }
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "DISPATCH_FAILED",
          ex.getMessage() != null ? ex.getMessage() : "Failed to dispatch inspection on retry.");
    }
  }

  @Transactional
  public ScheduleOccurrenceResponse skipOccurrence(
      UUID actorId, UUID scheduleId, UUID occurrenceId, SkipOccurrenceRequest request) {
    UserAccess.ActiveUser actor = requireOrgAdmin(actorId);
    UUID organizationId = actor.organizationId();

    RecurringInspectionSchedule schedule =
        scheduleRepository
            .findWithLockByIdAndOrganizationId(scheduleId, organizationId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Inspection schedule not found"));

    ScheduleOccurrence occurrence =
        occurrenceRepository
            .findByIdAndOrganizationId(occurrenceId, organizationId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Schedule occurrence not found"));

    if (!occurrence.getScheduleId().equals(scheduleId)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "INVALID_OCCURRENCE", "Occurrence does not belong to schedule.");
    }

    if (occurrence.getStatus() != OccurrenceStatus.BLOCKED) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "NOT_BLOCKED", "Only blocked occurrences can be skipped.");
    }

    occurrence.markSkipped(request.reason());
    occurrenceRepository.saveAndFlush(occurrence);

    if (schedule.getNextOccurrence() == occurrence.getOccurrenceIndex()) {
      schedule.advance();
      scheduleRepository.saveAndFlush(schedule);
    }

    return toOccurrenceResponse(occurrence);
  }

  @Transactional
  public void generateOccurrencesInternal(UUID scheduleId, Instant now, Instant horizon) {
    RecurringInspectionSchedule schedule = scheduleRepository.findById(scheduleId).orElse(null);
    if (schedule == null || !schedule.isActive()) {
      return;
    }

    // Check if the current nextOccurrence is blocked
    Optional<ScheduleOccurrence> currentOcc =
        occurrenceRepository.findByScheduleIdAndOccurrenceIndex(
            schedule.getId(), schedule.getNextOccurrence());
    if (currentOcc.isPresent()) {
      if (currentOcc.get().getStatus() == OccurrenceStatus.BLOCKED) {
        // Schedule cannot advance past a blocked occurrence automatically
        return;
      }
      if (currentOcc.get().getStatus() == OccurrenceStatus.GENERATED
          || currentOcc.get().getStatus() == OccurrenceStatus.SKIPPED) {
        schedule.advance();
      }
    }

    int generatedCount = 0;
    while (schedule.isActive() && generatedCount < 10) {
      Instant due = schedule.nextDueAt();
      if (schedule.getEndsAt() != null && due.isAfter(schedule.getEndsAt())) {
        schedule.end();
        break;
      }
      if (due.isAfter(horizon)) {
        break;
      }

      long occurrenceIndex = schedule.getNextOccurrence();
      String occurrenceKey = due.toString();
      Instant plannedEnd = due.plusSeconds(schedule.getDurationSeconds());

      Optional<ScheduleOccurrence> existingByKey =
          occurrenceRepository.findByScheduleIdAndOccurrenceKey(schedule.getId(), occurrenceKey);
      if (existingByKey.isPresent()) {
        if (existingByKey.get().getStatus() == OccurrenceStatus.BLOCKED) {
          break;
        }
        schedule.advance();
        continue;
      }

      // Check if due is in the past
      if (due.isBefore(now)) {
        ScheduleOccurrence blocked =
            new ScheduleOccurrence(
                schedule.getId(),
                schedule.getOrganizationId(),
                occurrenceIndex,
                occurrenceKey,
                due,
                plannedEnd,
                OccurrenceStatus.BLOCKED);
        blocked.markBlocked(
            "Occurrence planned start time is in the past (missed window while inactive/paused)");
        occurrenceRepository.saveAndFlush(blocked);
        // Do NOT advance schedule on blocked occurrence
        break;
      }

      // Try to dispatch inspection
      try {
        Inspection inspection =
            creationService.dispatchInspectionInternal(
                schedule.getOrganizationId(),
                schedule.getAssetId(),
                schedule.getInspectorId(),
                schedule.getDroneId(),
                due,
                plannedEnd,
                schedule.getId());

        ScheduleOccurrence occ =
            new ScheduleOccurrence(
                schedule.getId(),
                schedule.getOrganizationId(),
                occurrenceIndex,
                occurrenceKey,
                due,
                plannedEnd,
                OccurrenceStatus.GENERATED);
        occ.markGenerated(inspection.getId());
        occurrenceRepository.saveAndFlush(occ);

        schedule.advance();
        generatedCount++;
      } catch (Exception ex) {
        ScheduleOccurrence blocked =
            new ScheduleOccurrence(
                schedule.getId(),
                schedule.getOrganizationId(),
                occurrenceIndex,
                occurrenceKey,
                due,
                plannedEnd,
                OccurrenceStatus.BLOCKED);
        blocked.markBlocked(
            ex.getMessage() != null
                ? ex.getMessage()
                : "Resource or entitlement validation blocked generation");
        occurrenceRepository.saveAndFlush(blocked);
        // Do NOT advance schedule on blocked occurrence
        break;
      }
    }

    scheduleRepository.saveAndFlush(schedule);
  }

  private RecurringScheduleResponse toResponse(RecurringInspectionSchedule schedule) {
    Asset asset =
        assetRepository
            .findByIdAndOrganizationId(schedule.getAssetId(), schedule.getOrganizationId())
            .orElse(null);

    String inspectorName =
        userAccess
            .findActiveUser(schedule.getInspectorId())
            .map(UserAccess.ActiveUser::fullName)
            .orElse(null);

    String creatorName =
        userAccess
            .findActiveUser(schedule.getCreatedByUserId())
            .map(UserAccess.ActiveUser::fullName)
            .orElse(null);

    Drone drone =
        droneRepository
            .findByIdAndOrganizationId(schedule.getDroneId(), schedule.getOrganizationId())
            .orElse(null);

    long generatedCount =
        occurrenceRepository.countByScheduleIdAndStatus(
            schedule.getId(), OccurrenceStatus.GENERATED);
    long blockedCount =
        occurrenceRepository.countByScheduleIdAndStatus(schedule.getId(), OccurrenceStatus.BLOCKED);

    Instant nextDueAt = null;
    if (schedule.isActive()) {
      try {
        nextDueAt = schedule.nextDueAt();
        if (schedule.getEndsAt() != null && nextDueAt.isAfter(schedule.getEndsAt())) {
          nextDueAt = null;
        }
      } catch (Exception ignored) {
      }
    }

    return new RecurringScheduleResponse(
        schedule.getId(),
        schedule.getOrganizationId(),
        schedule.getAssetId(),
        asset == null ? null : asset.getName(),
        asset == null ? null : asset.getLocationText(),
        schedule.getCreatedByUserId(),
        creatorName,
        schedule.getInspectorId(),
        inspectorName,
        schedule.getDroneId(),
        drone == null ? null : drone.getSerialNumber(),
        drone == null ? null : drone.getModel(),
        schedule.getAnchorDate(),
        schedule.getTimezone(),
        schedule.getRecurrenceUnit(),
        schedule.getRecurrenceInterval(),
        schedule.getDurationSeconds(),
        schedule.getReminderDays(),
        schedule.getEndsAt(),
        schedule.getStatus(),
        schedule.getNextOccurrence(),
        nextDueAt,
        generatedCount,
        blockedCount,
        schedule.getCreatedAt(),
        schedule.getUpdatedAt());
  }

  private RecurringScheduleDetailResponse toDetailResponse(
      RecurringInspectionSchedule schedule, List<ScheduleOccurrenceResponse> occurrences) {
    RecurringScheduleResponse base = toResponse(schedule);
    return new RecurringScheduleDetailResponse(
        base.id(),
        base.organizationId(),
        base.assetId(),
        base.assetName(),
        base.assetLocationText(),
        base.createdByUserId(),
        base.createdByName(),
        base.inspectorId(),
        base.inspectorName(),
        base.droneId(),
        base.droneSerialNumber(),
        base.droneModel(),
        base.anchor(),
        base.timezone(),
        base.unit(),
        base.interval(),
        base.durationSeconds(),
        base.reminderDays(),
        base.endsAt(),
        base.status(),
        base.nextOccurrenceIndex(),
        base.nextDueAt(),
        base.totalGeneratedOccurrences(),
        base.totalBlockedOccurrences(),
        base.createdAt(),
        base.updatedAt(),
        occurrences);
  }

  private ScheduleOccurrenceResponse toOccurrenceResponse(ScheduleOccurrence occ) {
    return new ScheduleOccurrenceResponse(
        occ.getId(),
        occ.getScheduleId(),
        occ.getOccurrenceIndex(),
        occ.getOccurrenceKey(),
        occ.getPlannedStartAt(),
        occ.getPlannedEndAt(),
        occ.getStatus().name(),
        occ.getInspectionId(),
        occ.getBlockedReason(),
        occ.getRetryCount(),
        occ.getCreatedAt(),
        occ.getUpdatedAt());
  }

  private UserAccess.ActiveUser requireOrgAdmin(UUID actorId) {
    UserAccess.ActiveUser actor = requireActive(actorId);
    if (!actor.hasRole(Roles.CUSTOMER)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "Only CUSTOMER can perform this action.");
    }
    if (actor.organizationId() == null) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "Actor has no organization scope.");
    }
    return actor;
  }

  private UserAccess.ActiveUser requireActive(UUID actorId) {
    return userAccess
        .findActiveUser(actorId)
        .orElseThrow(
            () -> new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "User is not active."));
  }
}
