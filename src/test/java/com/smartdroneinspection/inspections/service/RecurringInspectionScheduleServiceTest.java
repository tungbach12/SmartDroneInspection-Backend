package com.smartdroneinspection.inspections.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.enums.AssetStatus;
import com.smartdroneinspection.assets.domain.enums.DroneServiceability;
import com.smartdroneinspection.assets.repository.AssetPairAssignmentRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.inspections.api.dto.request.CreateRecurringScheduleRequest;
import com.smartdroneinspection.inspections.api.dto.request.SkipOccurrenceRequest;
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
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RecurringInspectionScheduleServiceTest {

  private RecurringInspectionScheduleRepository scheduleRepository;
  private ScheduleOccurrenceRepository occurrenceRepository;
  private InspectionCreationService creationService;
  private AssetRepository assetRepository;
  private AssetPairAssignmentRepository assetPairAssignments;
  private DroneRepository droneRepository;
  private UserAccess userAccess;
  private SubscriptionEntitlement subscriptionEntitlement;

  private RecurringInspectionScheduleService service;

  private final UUID orgId = UUID.randomUUID();
  private final UUID managerId = UUID.randomUUID();
  private final UUID inspectorId = UUID.randomUUID();
  private final UUID droneId = UUID.randomUUID();
  private final UUID assetId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    scheduleRepository = mock(RecurringInspectionScheduleRepository.class);
    occurrenceRepository = mock(ScheduleOccurrenceRepository.class);
    creationService = mock(InspectionCreationService.class);
    assetRepository = mock(AssetRepository.class);
    assetPairAssignments = mock(AssetPairAssignmentRepository.class);
    droneRepository = mock(DroneRepository.class);
    userAccess = mock(UserAccess.class);
    subscriptionEntitlement = mock(SubscriptionEntitlement.class);

    service =
        new RecurringInspectionScheduleService(
            scheduleRepository,
            occurrenceRepository,
            creationService,
            assetRepository,
            assetPairAssignments,
            droneRepository,
            userAccess,
            subscriptionEntitlement);

    when(userAccess.findActiveUser(managerId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(
                    managerId,
                    Set.of(Roles.ORG_MANAGER),
                    orgId,
                    "Manager User",
                    "manager@test.com")));

    when(userAccess.findActiveUser(inspectorId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(
                    inspectorId,
                    Set.of(Roles.INSPECTOR),
                    orgId,
                    "Inspector User",
                    "inspector@test.com")));
  }

  @Test
  void createScheduleGeneratesImmediateOccurrenceAndDispatchesInspection() {
    Asset asset = mock(Asset.class);
    when(asset.getId()).thenReturn(assetId);
    when(asset.getStatus()).thenReturn(AssetStatus.ACTIVE);
    when(assetRepository.findByIdAndOrganizationId(assetId, orgId)).thenReturn(Optional.of(asset));

    Drone drone = mock(Drone.class);
    when(drone.getId()).thenReturn(droneId);
    when(drone.getServiceability()).thenReturn(DroneServiceability.ACTIVE);
    when(droneRepository.findByIdAndOrganizationId(droneId, orgId)).thenReturn(Optional.of(drone));

    LocalDateTime futureAnchor = LocalDateTime.now().plusDays(2);
    CreateRecurringScheduleRequest request =
        new CreateRecurringScheduleRequest(
            assetId,
            "Monthly bridge inspection",
            null,
            null,
            null,
            futureAnchor,
            "UTC",
            "MONTH",
            1,
            7200L,
            3,
            null,
            inspectorId,
            droneId);

    when(scheduleRepository.saveAndFlush(any(RecurringInspectionSchedule.class)))
        .thenAnswer(
            invocation -> {
              RecurringInspectionSchedule s = invocation.getArgument(0);
              if (s.getId() == null) {
                ReflectionTestUtils.setField(s, "id", UUID.randomUUID());
              }
              return s;
            });

    when(scheduleRepository.findById(any()))
        .thenAnswer(inv -> Optional.of(mockSchedule(futureAnchor)));

    Inspection mockInspection = mock(Inspection.class);
    when(mockInspection.getId()).thenReturn(UUID.randomUUID());
    when(creationService.dispatchInspectionInternal(
            eq(orgId),
            eq(assetId),
            eq(inspectorId),
            eq(droneId),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()))
        .thenReturn(mockInspection);

    RecurringScheduleResponse response = service.create(managerId, request);

    assertThat(response).isNotNull();
    verify(subscriptionEntitlement).requireActive(eq(orgId), any(Instant.class));
    verify(scheduleRepository, org.mockito.Mockito.atLeastOnce())
        .saveAndFlush(any(RecurringInspectionSchedule.class));
  }

  @Test
  void createScheduleRejectsNonManagerRole() {
    UUID nonManagerId = UUID.randomUUID();
    when(userAccess.findActiveUser(nonManagerId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(
                    nonManagerId,
                    Set.of(Roles.INSPECTOR),
                    orgId,
                    "Inspector",
                    "inspector@test.com")));

    CreateRecurringScheduleRequest request =
        new CreateRecurringScheduleRequest(
            assetId,
            "Monthly bridge inspection",
            null,
            null,
            null,
            LocalDateTime.now().plusDays(1),
            "UTC",
            "WEEK",
            1,
            3600L,
            3,
            null,
            inspectorId,
            droneId);

    assertThatThrownBy(() -> service.create(nonManagerId, request))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Only ORG_MANAGER can perform this action");
  }

  @Test
  void createScheduleRejectsWhenActiveScheduleAlreadyExistsForAsset() {
    LocalDateTime futureAnchor = LocalDateTime.now().plusDays(2).truncatedTo(ChronoUnit.HOURS);
    CreateRecurringScheduleRequest request =
        new CreateRecurringScheduleRequest(
            assetId,
            "Monthly bridge inspection",
            null,
            null,
            null,
            futureAnchor,
            "Asia/Ho_Chi_Minh",
            "MONTH",
            1,
            7200L,
            3,
            null,
            inspectorId,
            droneId);

    when(scheduleRepository.existsByOrganizationIdAndAssetIdAndStatus(orgId, assetId, "ACTIVE"))
        .thenReturn(true);
    Asset mockAsset = mock(Asset.class);
    when(mockAsset.getId()).thenReturn(assetId);
    when(mockAsset.getStatus()).thenReturn(AssetStatus.ACTIVE);
    when(assetRepository.findByIdAndOrganizationId(assetId, orgId))
        .thenReturn(Optional.of(mockAsset));

    assertThatThrownBy(() -> service.create(managerId, request))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex -> {
              BusinessException be = (BusinessException) ex;
              assertThat(be.code()).isEqualTo("ACTIVE_SCHEDULE_EXISTS");
            });
  }

  @Test
  void pauseAndResumeScheduleWithCatchupGeneration() {
    UUID scheduleId = UUID.randomUUID();
    RecurringInspectionSchedule schedule = mockSchedule(LocalDateTime.now().plusDays(5));
    when(scheduleRepository.findWithLockByIdAndOrganizationId(scheduleId, orgId))
        .thenReturn(Optional.of(schedule));
    when(scheduleRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

    RecurringScheduleResponse pauseResp = service.pause(managerId, scheduleId);
    assertThat(pauseResp.status()).isEqualTo("PAUSED");

    when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
    RecurringScheduleResponse resumeResp = service.resume(managerId, scheduleId);
    assertThat(resumeResp.status()).isEqualTo("ACTIVE");
  }

  @Test
  void endSchedulePreventsResume() {
    UUID scheduleId = UUID.randomUUID();
    RecurringInspectionSchedule schedule = mockSchedule(LocalDateTime.now().plusDays(5));
    when(scheduleRepository.findWithLockByIdAndOrganizationId(scheduleId, orgId))
        .thenReturn(Optional.of(schedule));
    when(scheduleRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

    RecurringScheduleResponse endResp = service.end(managerId, scheduleId);
    assertThat(endResp.status()).isEqualTo("ENDED");

    assertThatThrownBy(() -> service.resume(managerId, scheduleId))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void generateOccurrencesBlocksPastOccurrencesWithoutSilentlyAdvancing() {
    UUID scheduleId = UUID.randomUUID();
    // Anchor in the past
    LocalDateTime pastAnchor = LocalDateTime.now().minusDays(5);
    RecurringInspectionSchedule schedule =
        new RecurringInspectionSchedule(
            orgId,
            assetId,
            managerId,
            inspectorId,
            droneId,
            "Survey",
            null,
            null,
            null,
            pastAnchor,
            "UTC",
            "MONTH",
            1,
            3600L,
            3,
            null);
    ReflectionTestUtils.setField(schedule, "id", scheduleId);

    when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));
    when(occurrenceRepository.findByScheduleIdAndOccurrenceIndex(scheduleId, 0L))
        .thenReturn(Optional.empty());
    when(occurrenceRepository.findByScheduleIdAndOccurrenceKey(eq(scheduleId), any()))
        .thenReturn(Optional.empty());

    Instant now = Instant.now();
    Instant horizon = now.plus(14, ChronoUnit.DAYS);

    service.generateOccurrencesInternal(scheduleId, now, horizon);

    // Verify blocked occurrence was saved
    verify(occurrenceRepository)
        .saveAndFlush(
            org.mockito.ArgumentMatchers.argThat(
                occ ->
                    occ.getStatus() == OccurrenceStatus.BLOCKED
                        && occ.getBlockedReason().contains("in the past")));

    // Verify schedule did NOT advance and remains at occurrence 0
    assertThat(schedule.getNextOccurrence()).isEqualTo(0L);
    verify(creationService, never())
        .dispatchInspectionInternal(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void retryBlockedOccurrenceSucceedsWhenWindowIsInFuture() {
    UUID scheduleId = UUID.randomUUID();
    UUID occId = UUID.randomUUID();
    RecurringInspectionSchedule schedule = mockSchedule(LocalDateTime.now().plusDays(3));

    Instant futureStart = Instant.now().plus(2, ChronoUnit.DAYS);
    Instant futureEnd = futureStart.plusSeconds(3600);

    ScheduleOccurrence occ =
        new ScheduleOccurrence(
            scheduleId,
            orgId,
            0L,
            futureStart.toString(),
            futureStart,
            futureEnd,
            OccurrenceStatus.BLOCKED);
    occ.markBlocked("Temporary conflict");
    ReflectionTestUtils.setField(occ, "id", occId);

    when(scheduleRepository.findWithLockByIdAndOrganizationId(scheduleId, orgId))
        .thenReturn(Optional.of(schedule));
    when(occurrenceRepository.findByIdAndOrganizationId(occId, orgId)).thenReturn(Optional.of(occ));

    Inspection mockInsp = mock(Inspection.class);
    when(mockInsp.getId()).thenReturn(UUID.randomUUID());
    when(creationService.dispatchInspectionInternal(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(mockInsp);

    ScheduleOccurrenceResponse resp = service.retryOccurrence(managerId, scheduleId, occId);

    assertThat(resp.status()).isEqualTo("GENERATED");
    assertThat(resp.inspectionId()).isEqualTo(mockInsp.getId());
    assertThat(occ.getRetryCount()).isEqualTo(1);
    assertThat(schedule.getNextOccurrence()).isEqualTo(1L);
  }

  @Test
  void retryBlockedOccurrenceRejectsPastDueDate() {
    UUID scheduleId = UUID.randomUUID();
    UUID occId = UUID.randomUUID();
    RecurringInspectionSchedule schedule = mockSchedule(LocalDateTime.now().minusDays(2));

    Instant pastStart = Instant.now().minus(1, ChronoUnit.DAYS);
    Instant pastEnd = pastStart.plusSeconds(3600);

    ScheduleOccurrence occ =
        new ScheduleOccurrence(
            scheduleId,
            orgId,
            0L,
            pastStart.toString(),
            pastStart,
            pastEnd,
            OccurrenceStatus.BLOCKED);
    occ.markBlocked("Past window");

    when(scheduleRepository.findWithLockByIdAndOrganizationId(scheduleId, orgId))
        .thenReturn(Optional.of(schedule));
    when(occurrenceRepository.findByIdAndOrganizationId(occId, orgId)).thenReturn(Optional.of(occ));

    assertThatThrownBy(() -> service.retryOccurrence(managerId, scheduleId, occId))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(
            "Cannot retry an occurrence whose planned start time has already passed");
  }

  @Test
  void skipBlockedOccurrenceAdvancesScheduleWithReason() {
    UUID scheduleId = UUID.randomUUID();
    UUID occId = UUID.randomUUID();
    RecurringInspectionSchedule schedule = mockSchedule(LocalDateTime.now().minusDays(2));

    Instant start = Instant.now().minus(1, ChronoUnit.DAYS);
    ScheduleOccurrence occ =
        new ScheduleOccurrence(
            scheduleId,
            orgId,
            0L,
            start.toString(),
            start,
            start.plusSeconds(3600),
            OccurrenceStatus.BLOCKED);

    when(scheduleRepository.findWithLockByIdAndOrganizationId(scheduleId, orgId))
        .thenReturn(Optional.of(schedule));
    when(occurrenceRepository.findByIdAndOrganizationId(occId, orgId)).thenReturn(Optional.of(occ));

    ScheduleOccurrenceResponse resp =
        service.skipOccurrence(
            managerId, scheduleId, occId, new SkipOccurrenceRequest("Bad weather"));

    assertThat(resp.status()).isEqualTo("SKIPPED");
    assertThat(occ.getBlockedReason()).isEqualTo("Bad weather");
    assertThat(schedule.getNextOccurrence()).isEqualTo(1L);
  }

  private RecurringInspectionSchedule mockSchedule(LocalDateTime anchor) {
    RecurringInspectionSchedule s =
        new RecurringInspectionSchedule(
            orgId,
            assetId,
            managerId,
            inspectorId,
            droneId,
            "Visual check",
            null,
            null,
            null,
            anchor,
            "UTC",
            "MONTH",
            1,
            7200L,
            3,
            null);
    ReflectionTestUtils.setField(s, "id", UUID.randomUUID());
    return s;
  }
}
