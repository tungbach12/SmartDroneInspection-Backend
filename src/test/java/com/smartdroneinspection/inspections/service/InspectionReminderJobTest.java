package com.smartdroneinspection.inspections.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.ScheduleOccurrence;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.domain.enums.OccurrenceStatus;
import com.smartdroneinspection.inspections.events.InspectionReminderDueEvent;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.repository.ScheduleOccurrenceRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.users.UserAccess;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class InspectionReminderJobTest {

  private ScheduleOccurrenceRepository occurrenceRepository;
  private InspectionRepository inspectionRepository;
  private UserAccess userAccess;
  private ApplicationEventPublisher eventPublisher;

  private InspectionReminderJob reminderJob;

  private final UUID orgId = UUID.randomUUID();
  private final UUID inspectorId = UUID.randomUUID();
  private final UUID inspectionId = UUID.randomUUID();
  private final UUID occurrenceId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    occurrenceRepository = mock(ScheduleOccurrenceRepository.class);
    inspectionRepository = mock(InspectionRepository.class);
    userAccess = mock(UserAccess.class);
    eventPublisher = mock(ApplicationEventPublisher.class);

    reminderJob =
        new InspectionReminderJob(
            occurrenceRepository, inspectionRepository, userAccess, eventPublisher, true, 50, 3);

    when(userAccess.findActiveUser(inspectorId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(
                    inspectorId,
                    Set.of(Roles.INSPECTOR),
                    orgId,
                    "Inspector",
                    "inspector@test.com")));
  }

  @Test
  void emitsReminderForEligibleInspectionAndMarksDeduplicationTimestamp() {
    Instant now = Instant.now();
    Instant plannedStart = now.plus(2, ChronoUnit.DAYS);
    Instant plannedEnd = plannedStart.plusSeconds(3600);

    ScheduleOccurrence occ =
        new ScheduleOccurrence(
            UUID.randomUUID(),
            orgId,
            0L,
            plannedStart.toString(),
            plannedStart,
            plannedEnd,
            OccurrenceStatus.GENERATED);
    occ.markGenerated(inspectionId);
    ReflectionTestUtils.setField(occ, "id", occurrenceId);

    Inspection inspection = mock(Inspection.class);
    when(inspection.getId()).thenReturn(inspectionId);
    when(inspection.getOrganizationId()).thenReturn(orgId);
    when(inspection.getInspectorId()).thenReturn(inspectorId);
    when(inspection.getStatus()).thenReturn(InspectionStatus.SCHEDULED);
    when(inspection.getPlannedStartAt()).thenReturn(plannedStart);

    when(occurrenceRepository.findDueForReminder(eq(now), any(Instant.class), any(Pageable.class)))
        .thenReturn(List.of(occ));
    when(occurrenceRepository.findById(occurrenceId)).thenReturn(Optional.of(occ));
    when(inspectionRepository.findById(inspectionId)).thenReturn(Optional.of(inspection));

    int sent = reminderJob.runReminderCheck(now);

    assertThat(sent).isEqualTo(1);
    assertThat(occ.getReminderSentAt()).isEqualTo(now);
    verify(occurrenceRepository).saveAndFlush(occ);

    ArgumentCaptor<InspectionReminderDueEvent> captor =
        ArgumentCaptor.forClass(InspectionReminderDueEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());

    InspectionReminderDueEvent event = captor.getValue();
    assertThat(event.inspectionId()).isEqualTo(inspectionId);
    assertThat(event.organizationId()).isEqualTo(orgId);
    assertThat(event.inspectorId()).isEqualTo(inspectorId);
    assertThat(event.plannedStartAt()).isEqualTo(plannedStart);
  }

  @Test
  void doesNotEmitReminderIfInspectionCancelledOrCompleted() {
    Instant now = Instant.now();
    Instant plannedStart = now.plus(2, ChronoUnit.DAYS);

    ScheduleOccurrence occ =
        new ScheduleOccurrence(
            UUID.randomUUID(),
            orgId,
            0L,
            plannedStart.toString(),
            plannedStart,
            plannedStart.plusSeconds(3600),
            OccurrenceStatus.GENERATED);
    occ.markGenerated(inspectionId);
    ReflectionTestUtils.setField(occ, "id", occurrenceId);

    Inspection inspection = mock(Inspection.class);
    when(inspection.getId()).thenReturn(inspectionId);
    when(inspection.getStatus()).thenReturn(InspectionStatus.CANCELLED);

    when(occurrenceRepository.findDueForReminder(eq(now), any(Instant.class), any(Pageable.class)))
        .thenReturn(List.of(occ));
    when(occurrenceRepository.findById(occurrenceId)).thenReturn(Optional.of(occ));
    when(inspectionRepository.findById(inspectionId)).thenReturn(Optional.of(inspection));

    int sent = reminderJob.runReminderCheck(now);

    assertThat(sent).isEqualTo(0);
    verify(eventPublisher, never()).publishEvent(any());
    assertThat(occ.getReminderSentAt()).isNull();
  }

  @Test
  void doesNotEmitReminderIfPlannedStartHasPassed() {
    Instant now = Instant.now();
    Instant pastStart = now.minus(1, ChronoUnit.HOURS);

    ScheduleOccurrence occ =
        new ScheduleOccurrence(
            UUID.randomUUID(),
            orgId,
            0L,
            pastStart.toString(),
            pastStart,
            pastStart.plusSeconds(3600),
            OccurrenceStatus.GENERATED);
    occ.markGenerated(inspectionId);
    ReflectionTestUtils.setField(occ, "id", occurrenceId);

    Inspection inspection = mock(Inspection.class);
    when(inspection.getId()).thenReturn(inspectionId);
    when(inspection.getStatus()).thenReturn(InspectionStatus.SCHEDULED);
    when(inspection.getPlannedStartAt()).thenReturn(pastStart);

    when(occurrenceRepository.findDueForReminder(eq(now), any(Instant.class), any(Pageable.class)))
        .thenReturn(List.of(occ));
    when(occurrenceRepository.findById(occurrenceId)).thenReturn(Optional.of(occ));
    when(inspectionRepository.findById(inspectionId)).thenReturn(Optional.of(inspection));

    int sent = reminderJob.runReminderCheck(now);

    assertThat(sent).isEqualTo(0);
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  void emitsReminderForPartnerAssignmentWithOrganizationIndependentInspector() {
    Instant now = Instant.now();
    Instant plannedStart = now.plus(1, ChronoUnit.DAYS);
    ScheduleOccurrence occ =
        new ScheduleOccurrence(
            UUID.randomUUID(),
            orgId,
            0L,
            plannedStart.toString(),
            plannedStart,
            plannedStart.plusSeconds(3600),
            OccurrenceStatus.GENERATED);
    occ.markGenerated(inspectionId);
    ReflectionTestUtils.setField(occ, "id", occurrenceId);

    when(userAccess.findActiveUser(inspectorId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(
                    inspectorId,
                    Set.of(Roles.INSPECTOR),
                    null,
                    "INSPECTOR_PARTNER",
                    "Partner Inspector",
                    "partner@test.com",
                    "LICENSE-1",
                    "PART-107",
                    plannedStart.plus(30, ChronoUnit.DAYS),
                    null)));

    Inspection inspection = mock(Inspection.class);
    when(inspection.getId()).thenReturn(inspectionId);
    when(inspection.getOrganizationId()).thenReturn(orgId);
    when(inspection.getInspectorId()).thenReturn(inspectorId);
    when(inspection.isPartnerAssignment()).thenReturn(true);
    when(inspection.getStatus()).thenReturn(InspectionStatus.SCHEDULED);
    when(inspection.getPlannedStartAt()).thenReturn(plannedStart);

    when(occurrenceRepository.findById(occurrenceId)).thenReturn(Optional.of(occ));
    when(inspectionRepository.findById(inspectionId)).thenReturn(Optional.of(inspection));

    assertThat(reminderJob.processSingleReminder(occurrenceId, now)).isTrue();
    verify(eventPublisher).publishEvent(any(InspectionReminderDueEvent.class));
  }

  @Test
  void doesNotEmitReminderWhenInspectionIsNotScheduled() {
    Instant now = Instant.now();
    Instant plannedStart = now.plus(1, ChronoUnit.DAYS);
    ScheduleOccurrence occ =
        new ScheduleOccurrence(
            UUID.randomUUID(),
            orgId,
            0L,
            plannedStart.toString(),
            plannedStart,
            plannedStart.plusSeconds(3600),
            OccurrenceStatus.GENERATED);
    occ.markGenerated(inspectionId);
    ReflectionTestUtils.setField(occ, "id", occurrenceId);

    Inspection inspection = mock(Inspection.class);
    when(inspection.getId()).thenReturn(inspectionId);
    when(inspection.getOrganizationId()).thenReturn(orgId);
    when(inspection.getInspectorId()).thenReturn(inspectorId);
    when(inspection.getStatus()).thenReturn(InspectionStatus.IN_PROGRESS);
    when(inspection.getPlannedStartAt()).thenReturn(plannedStart);

    when(occurrenceRepository.findById(occurrenceId)).thenReturn(Optional.of(occ));
    when(inspectionRepository.findById(inspectionId)).thenReturn(Optional.of(inspection));

    assertThat(reminderJob.processSingleReminder(occurrenceId, now)).isFalse();
    verify(eventPublisher, never()).publishEvent(any(InspectionReminderDueEvent.class));
  }
}
