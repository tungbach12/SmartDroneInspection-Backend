package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.domain.FieldSession;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.InspectorReliabilityEvent;
import com.smartdroneinspection.inspections.domain.InspectorReliabilityEvent.EventType;
import com.smartdroneinspection.inspections.events.FieldSessionHungEvent;
import com.smartdroneinspection.inspections.events.InspectionOverdueEvent;
import com.smartdroneinspection.inspections.repository.FieldSessionRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.repository.InspectorReliabilityEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Periodically scans for scheduled inspections that passed their flight window without starting
 * (no-show) and active field sessions stuck IN_PROGRESS well beyond their window.
 */
@Service
public class InspectionLifecycleMonitor {

  private final InspectionRepository inspections;
  private final FieldSessionRepository sessions;
  private final InspectorReliabilityEventRepository reliabilityEvents;
  private final ApplicationEventPublisher eventPublisher;
  private final long overdueGraceMinutes;
  private final long hungSessionHours;

  public InspectionLifecycleMonitor(
      InspectionRepository inspections,
      FieldSessionRepository sessions,
      InspectorReliabilityEventRepository reliabilityEvents,
      ApplicationEventPublisher eventPublisher,
      @Value("${app.inspections.overdue-grace-minutes:30}") long overdueGraceMinutes,
      @Value("${app.inspections.hung-session-hours:4}") long hungSessionHours) {
    this.inspections = inspections;
    this.sessions = sessions;
    this.reliabilityEvents = reliabilityEvents;
    this.eventPublisher = eventPublisher;
    this.overdueGraceMinutes = Math.max(0, overdueGraceMinutes);
    this.hungSessionHours = Math.max(1, hungSessionHours);
  }

  @Scheduled(fixedDelayString = "${app.inspections.monitor-interval-ms:300000}")
  @Transactional
  public void scanAndNotify() {
    Instant now = Instant.now();
    detectOverdueInspections(now);
    detectHungFieldSessions(now);
  }

  void detectOverdueInspections(Instant now) {
    Instant overdueCutoff = now.minus(Duration.ofMinutes(overdueGraceMinutes));
    List<Inspection> overdue = inspections.findScheduledOverdueWithoutSession(overdueCutoff);
    for (Inspection inspection : overdue) {
      inspection.markOverdueNotified(now);
      inspections.save(inspection);
      if (inspection.getInspectorId() != null
          && !reliabilityEvents.existsByInspectionIdAndInspectorIdAndEventType(
              inspection.getId(), inspection.getInspectorId(), EventType.NO_SHOW)) {
        reliabilityEvents.save(
            new InspectorReliabilityEvent(
                inspection.getInspectorId(),
                inspection.getId(),
                null,
                EventType.NO_SHOW,
                "Scheduled flight window elapsed without starting a field session",
                now));
      }
      eventPublisher.publishEvent(
          new InspectionOverdueEvent(
              inspection.getId(),
              inspection.getOwnerCustomerId(),
              inspection.getInspectorId(),
              inspection.getPlannedEndAt(),
              now));
    }
  }

  void detectHungFieldSessions(Instant now) {
    Instant plannedEndCutoff = now.minus(Duration.ofHours(hungSessionHours));
    Instant startedAtCutoff = now.minus(Duration.ofHours(Math.max(8, hungSessionHours * 2)));
    List<FieldSession> hung =
        sessions.findHungInProgressSessions(plannedEndCutoff, startedAtCutoff);
    for (FieldSession session : hung) {
      session.markHungNotified(now);
      sessions.save(session);
      Inspection inspection = inspections.findById(session.getInspectionId()).orElse(null);
      eventPublisher.publishEvent(
          new FieldSessionHungEvent(
              session.getInspectionId(),
              inspection == null ? null : inspection.getOwnerCustomerId(),
              session.getId(),
              inspection == null ? null : inspection.getInspectorId(),
              session.getStartedAt(),
              inspection == null ? null : inspection.getPlannedEndAt(),
              now));
    }
  }
}
