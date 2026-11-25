package com.smartdroneinspection.notifications.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.smartdroneinspection.inspections.events.FieldSessionCompletedEvent;
import com.smartdroneinspection.inspections.events.InspectionAcceptedEvent;
import com.smartdroneinspection.inspections.events.InspectionAssignedEvent;
import com.smartdroneinspection.inspections.events.InspectionCancelledEvent;
import com.smartdroneinspection.inspections.events.ReportApprovedForPayoutEvent;
import com.smartdroneinspection.inspections.events.ReportClarificationRequestedEvent;
import com.smartdroneinspection.inspections.events.ReportPublishedEvent;
import com.smartdroneinspection.inspections.events.ReportReturnedEvent;
import com.smartdroneinspection.inspections.events.ReportSubmittedEvent;
import com.smartdroneinspection.inspections.events.ReportWithdrawnEvent;
import com.smartdroneinspection.inspections.events.VideoProcessingFinishedEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InspectionNotificationListenerTest {

  private InspectionNotificationService notificationService;
  private InspectionNotificationListener listener;

  @BeforeEach
  void setUp() {
    notificationService = mock(InspectionNotificationService.class);
    listener = new InspectionNotificationListener(notificationService);
  }

  @Test
  void onInspectionAssignedDelegatesToService() {
    UUID inspectionId = UUID.randomUUID();
    UUID ownerCustomerId = UUID.randomUUID();
    UUID inspectorId = UUID.randomUUID();
    Instant plannedStart = Instant.now();

    InspectionAssignedEvent event =
        new InspectionAssignedEvent(inspectionId, ownerCustomerId, inspectorId, plannedStart);

    listener.onInspectionAssigned(event);

    verify(notificationService)
        .recordInspectionAssignment(inspectionId, ownerCustomerId, inspectorId, plannedStart);
  }

  @Test
  void onFieldSessionAbortedDelegatesToService() {
    UUID inspectionId = UUID.randomUUID();
    UUID ownerCustomerId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    UUID inspectorId = UUID.randomUUID();
    String reason = "Heavy thunderstorm";

    com.smartdroneinspection.inspections.events.FieldSessionAbortedEvent event =
        new com.smartdroneinspection.inspections.events.FieldSessionAbortedEvent(
            inspectionId, ownerCustomerId, sessionId, inspectorId, reason, Instant.now());

    listener.onFieldSessionAborted(event);

    verify(notificationService)
        .recordFieldSessionAborted(inspectionId, ownerCustomerId, inspectorId, reason);
  }

  @Test
  void onReportSubmittedDelegatesToService() {
    UUID inspectionId = UUID.randomUUID();
    UUID reportVersionId = UUID.randomUUID();
    UUID submittedByUserId = UUID.randomUUID();
    Instant submittedAt = Instant.now();
    ReportSubmittedEvent event =
        new ReportSubmittedEvent(inspectionId, reportVersionId, 2, submittedByUserId, submittedAt);

    listener.onReportSubmitted(event);

    verify(notificationService)
        .recordReportSubmitted(inspectionId, reportVersionId, 2, submittedByUserId, submittedAt);
  }

  @Test
  void onReportReturnedDelegatesToService() {
    UUID inspectionId = UUID.randomUUID();
    UUID reportVersionId = UUID.randomUUID();
    UUID inspectorId = UUID.randomUUID();
    UUID returnedByUserId = UUID.randomUUID();
    Instant returnedAt = Instant.now();
    ReportReturnedEvent event =
        new ReportReturnedEvent(
            inspectionId,
            reportVersionId,
            1,
            inspectorId,
            returnedByUserId,
            "Add evidence",
            returnedAt);

    listener.onReportReturned(event);

    verify(notificationService)
        .recordReportReturned(
            inspectionId, reportVersionId, 1, inspectorId, "Add evidence", returnedAt);
  }

  @Test
  void onReportPublishedDelegatesToService() {
    UUID inspectionId = UUID.randomUUID();
    UUID reportVersionId = UUID.randomUUID();
    UUID ownerCustomerId = UUID.randomUUID();
    ReportPublishedEvent event =
        new ReportPublishedEvent(
            reportVersionId,
            reportVersionId,
            inspectionId,
            ownerCustomerId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            List.of(),
            Instant.now());

    listener.onReportPublished(event);

    verify(notificationService)
        .recordReportPublished(inspectionId, reportVersionId, ownerCustomerId);
  }

  @Test
  void onInspectionAcceptedCancelledCompletedReportAndVideoEventsDelegateToService() {
    UUID inspectionId = UUID.randomUUID();
    UUID ownerCustomerId = UUID.randomUUID();
    UUID inspectorId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    UUID reportVersionId = UUID.randomUUID();
    UUID evidenceId = UUID.randomUUID();
    Instant now = Instant.now();

    listener.onInspectionAccepted(
        new InspectionAcceptedEvent(inspectionId, ownerCustomerId, inspectorId, now, now));
    verify(notificationService)
        .recordInspectionAccepted(inspectionId, ownerCustomerId, inspectorId, now);

    listener.onInspectionCancelled(
        new InspectionCancelledEvent(
            inspectionId, ownerCustomerId, inspectorId, "Customer cancelled", now));
    verify(notificationService)
        .recordInspectionCancelled(
            inspectionId, ownerCustomerId, inspectorId, "Customer cancelled", now);

    listener.onFieldSessionCompleted(
        new FieldSessionCompletedEvent(
            inspectionId,
            sessionId,
            ownerCustomerId,
            inspectorId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            now,
            now,
            30L,
            "ROOF",
            "Clear",
            "Roof flight",
            "Site A",
            "Building",
            "M350",
            "SN-01"));
    verify(notificationService)
        .recordFieldSessionCompleted(inspectionId, sessionId, ownerCustomerId, "Roof flight", now);

    listener.onReportApprovedForPayout(
        new ReportApprovedForPayoutEvent(
            inspectionId, reportVersionId, 2, inspectorId, UUID.randomUUID(), now));
    verify(notificationService)
        .recordReportApprovedForPayout(inspectionId, reportVersionId, 2, inspectorId, now);

    listener.onReportWithdrawn(
        new ReportWithdrawnEvent(inspectionId, reportVersionId, 2, inspectorId, now));
    verify(notificationService).recordReportWithdrawn(inspectionId, reportVersionId, 2, now);

    listener.onReportClarificationRequested(
        new ReportClarificationRequestedEvent(
            inspectionId,
            reportVersionId,
            2,
            ownerCustomerId,
            inspectorId,
            "Need closer shot",
            1,
            now));
    verify(notificationService)
        .recordReportClarificationRequested(
            inspectionId, reportVersionId, 2, "Need closer shot", 1, now);

    listener.onVideoProcessingFinished(
        new VideoProcessingFinishedEvent(
            evidenceId, inspectionId, inspectorId, "flight.mp4", false, "codec error", now));
    verify(notificationService)
        .recordVideoProcessingFinished(
            evidenceId, inspectionId, inspectorId, "flight.mp4", false, "codec error", now);
  }
}
