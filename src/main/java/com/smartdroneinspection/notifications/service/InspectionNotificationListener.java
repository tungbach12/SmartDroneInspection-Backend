package com.smartdroneinspection.notifications.service;

import com.smartdroneinspection.inspections.events.FieldSessionAbortedEvent;
import com.smartdroneinspection.inspections.events.FieldSessionForceAbortedEvent;
import com.smartdroneinspection.inspections.events.FieldSessionHungEvent;
import com.smartdroneinspection.inspections.events.FlightPermitSubmittedEvent;
import com.smartdroneinspection.inspections.events.InspectionAssignedEvent;
import com.smartdroneinspection.inspections.events.InspectionAssignmentRevokedEvent;
import com.smartdroneinspection.inspections.events.InspectionAssignmentTimedOutEvent;
import com.smartdroneinspection.inspections.events.InspectionDeclinedEvent;
import com.smartdroneinspection.inspections.events.InspectionOverdueEvent;
import com.smartdroneinspection.inspections.events.InspectionRescheduledEvent;
import com.smartdroneinspection.inspections.events.ReportPublishedEvent;
import com.smartdroneinspection.inspections.events.ReportReturnedEvent;
import com.smartdroneinspection.inspections.events.ReportSubmittedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Records in-app notifications for inspection assignments and field-session aborts. */
@Component
public class InspectionNotificationListener {

  private static final Logger log = LoggerFactory.getLogger(InspectionNotificationListener.class);

  private final InspectionNotificationService inspectionNotificationService;

  public InspectionNotificationListener(
      InspectionNotificationService inspectionNotificationService) {
    this.inspectionNotificationService = inspectionNotificationService;
  }

  @ApplicationModuleListener
  public void onInspectionAssigned(InspectionAssignedEvent event) {
    log.info("Handling InspectionAssignedEvent for inspection {}", event.inspectionId());
    inspectionNotificationService.recordInspectionAssignment(
        event.inspectionId(), event.ownerCustomerId(), event.inspectorId(), event.plannedStartAt());
  }

  @ApplicationModuleListener
  public void onFieldSessionAborted(FieldSessionAbortedEvent event) {
    log.info("Handling FieldSessionAbortedEvent for inspection {}", event.inspectionId());
    inspectionNotificationService.recordFieldSessionAborted(
        event.inspectionId(), event.ownerCustomerId(), event.inspectorId(), event.abortReason());
  }

  @ApplicationModuleListener
  public void onInspectionDeclined(InspectionDeclinedEvent event) {
    log.info("Handling InspectionDeclinedEvent for inspection {}", event.inspectionId());
    inspectionNotificationService.recordInspectionDeclined(
        event.inspectionId(), event.inspectorId(), event.declineReason());
  }

  @ApplicationModuleListener
  public void onInspectionOverdue(InspectionOverdueEvent event) {
    log.info("Handling InspectionOverdueEvent for inspection {}", event.inspectionId());
    inspectionNotificationService.recordInspectionOverdue(
        event.inspectionId(), event.inspectorId(), event.plannedEndAt());
  }

  @ApplicationModuleListener
  public void onFieldSessionHung(FieldSessionHungEvent event) {
    log.info("Handling FieldSessionHungEvent for session {}", event.sessionId());
    inspectionNotificationService.recordFieldSessionHung(
        event.inspectionId(), event.sessionId(), event.startedAt());
  }

  @ApplicationModuleListener
  public void onFieldSessionForceAborted(FieldSessionForceAbortedEvent event) {
    log.info("Handling FieldSessionForceAbortedEvent for session {}", event.sessionId());
    inspectionNotificationService.recordFieldSessionForceAborted(
        event.inspectionId(),
        event.ownerCustomerId(),
        event.inspectorId(),
        event.sessionId(),
        event.reason());
  }

  @ApplicationModuleListener
  public void onInspectionRescheduled(InspectionRescheduledEvent event) {
    log.info("Handling InspectionRescheduledEvent for inspection {}", event.inspectionId());
    inspectionNotificationService.recordInspectionRescheduled(
        event.inspectionId(), event.inspectorId(), event.ownerCustomerId(), event.plannedStartAt());
  }

  @ApplicationModuleListener
  public void onInspectionAssignmentRevoked(InspectionAssignmentRevokedEvent event) {
    log.info("Handling InspectionAssignmentRevokedEvent for inspection {}", event.inspectionId());
    inspectionNotificationService.recordInspectionAssignmentRevoked(
        event.inspectionId(), event.previousInspectorId());
  }

  @ApplicationModuleListener
  public void onInspectionAssignmentTimedOut(InspectionAssignmentTimedOutEvent event) {
    log.info("Handling InspectionAssignmentTimedOutEvent for inspection {}", event.inspectionId());
    inspectionNotificationService.recordAssignmentAcceptanceTimeout(
        event.inspectionId(),
        event.inspectorId(),
        event.requestId(),
        event.assignedManagerId(),
        event.timeoutHours(),
        event.timedOutAt());
  }

  @ApplicationModuleListener
  public void onReportSubmitted(ReportSubmittedEvent event) {
    log.info("Handling ReportSubmittedEvent for report version {}", event.reportVersionId());
    inspectionNotificationService.recordReportSubmitted(
        event.inspectionId(),
        event.reportVersionId(),
        event.versionNo(),
        event.submittedByUserId(),
        event.submittedAt());
  }

  @ApplicationModuleListener
  public void onReportReturned(ReportReturnedEvent event) {
    log.info("Handling ReportReturnedEvent for report version {}", event.reportVersionId());
    inspectionNotificationService.recordReportReturned(
        event.inspectionId(),
        event.reportVersionId(),
        event.versionNo(),
        event.inspectorId(),
        event.reason(),
        event.returnedAt());
  }

  @ApplicationModuleListener
  public void onReportPublished(ReportPublishedEvent event) {
    log.info("Handling ReportPublishedEvent for report version {}", event.reportVersionId());
    inspectionNotificationService.recordReportPublished(
        event.inspectionId(), event.reportVersionId(), event.ownerCustomerId());
  }

  @ApplicationModuleListener
  public void onFlightPermitSubmitted(FlightPermitSubmittedEvent event) {
    log.info("Handling FlightPermitSubmittedEvent for inspection {}", event.inspectionId());
    inspectionNotificationService.recordFlightPermitSubmitted(
        event.inspectionId(), event.permitNumber(), event.issuingAuthority(), event.submittedAt());
  }
}
