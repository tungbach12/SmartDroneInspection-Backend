package com.smartdroneinspection.notifications.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.smartdroneinspection.inspections.events.InspectionAssignedEvent;
import com.smartdroneinspection.inspections.events.ReportPublishedEvent;
import com.smartdroneinspection.inspections.events.ReportReturnedEvent;
import com.smartdroneinspection.inspections.events.ReportSubmittedEvent;
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
}
