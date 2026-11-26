package com.smartdroneinspection.inspections.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.inspections.domain.FieldSession;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.repository.FieldSessionFlightRepository;
import com.smartdroneinspection.inspections.repository.FieldSessionRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.spi.InspectionReportAccess;
import com.smartdroneinspection.inspections.spi.SmartDroneHubPort;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InspectionDroneHubFlightLogTest {
  private final UUID actorId = UUID.randomUUID();
  private final UUID inspectionId = UUID.randomUUID();
  private final UUID sessionId = UUID.randomUUID();
  private final UUID organizationId = UUID.randomUUID();
  private final UUID assetId = UUID.randomUUID();
  private final InspectionRepository inspections = mock(InspectionRepository.class);
  private final FieldSessionRepository sessions = mock(FieldSessionRepository.class);
  private final AssetRepository assets = mock(AssetRepository.class);
  private final UserAccess userAccess = mock(UserAccess.class);
  private final SmartDroneHubPort hub = mock(SmartDroneHubPort.class);
  private final FieldSessionFlightRepository flights = mock(FieldSessionFlightRepository.class);
  private final InspectionReportAccess reportAccess = mock(InspectionReportAccess.class);
  private final Inspection inspection = mock(Inspection.class);
  private final FieldSession session =
      new FieldSession(
          inspectionId, FieldSession.IN_PROGRESS, Instant.parse("2026-11-10T10:00:00Z"));
  private final InspectionDroneHubService service =
      new InspectionDroneHubService(
          inspections, sessions, assets, userAccess, hub, null, new FlightLogParser());

  @BeforeEach
  void setup() {
    when(userAccess.findActiveUser(actorId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(
                    actorId,
                    Set.of("INSPECTOR"),
                    "INSPECTOR",
                    "Inspector",
                    null,
                    null,
                    null,
                    null,
                    null)));
    when(inspections.findAssignedByInspector(inspectionId, actorId))
        .thenReturn(Optional.of(inspection));
    when(inspection.isPartnerAssignment()).thenReturn(false);
    when(inspection.hasFlightPermit()).thenReturn(true);
    when(inspection.getFlightPermitVerificationStatus())
        .thenReturn(
            com.smartdroneinspection.inspections.domain.enums.FlightPermitVerificationStatus
                .APPROVED);
    when(inspection.getStatus()).thenReturn(InspectionStatus.IN_PROGRESS);
    when(inspection.getAssetId()).thenReturn(assetId);
    when(inspection.getOwnerCustomerId()).thenReturn(actorId);
    when(assets.findByIdAndOwnerCustomerId(assetId, actorId)).thenReturn(Optional.empty());
    when(sessions.findByIdAndInspectionId(sessionId, inspectionId))
        .thenReturn(Optional.of(session));
  }

  @Test
  void onlyOwningCustomerCanReadFlightDataAfterPublication() {
    InspectionDroneHubService service =
        new InspectionDroneHubService(
            inspections,
            sessions,
            assets,
            userAccess,
            hub,
            null,
            new FlightLogParser(),
            flights,
            reportAccess);
    UUID customer = UUID.randomUUID();
    when(userAccess.findActiveUser(customer))
        .thenReturn(
            Optional.of(new UserAccess.ActiveUser(customer, Set.of("CUSTOMER"), "Customer")));
    when(inspections.findById(inspectionId)).thenReturn(Optional.of(inspection));
    when(inspection.getOwnerCustomerId()).thenReturn(customer);
    session.recordFlightReport(
        "IMPORTED_LOG_CSV", 12, 10.0, 25.0, null, null, "LANDED", "Saved report", "[]");

    assertFalse(service.canReadFlightData(customer, inspectionId));
    assertTrue(service.listFlightsForInspection(customer, inspectionId).isEmpty());
    assertTrue(service.listFlightsForSession(customer, inspectionId, sessionId).isEmpty());
    verify(flights, never())
        .findByInspectionIdOrderByFlightDateAscStartedAtAscFlightNumberAsc(any());
    verify(flights, never()).findByFieldSessionIdOrderByFlightNumberAsc(any());
    assertEquals(
        "REPORT_NOT_PUBLISHED",
        assertThrows(
                BusinessException.class,
                () -> service.flightReport(customer, inspectionId, sessionId))
            .code());

    when(reportAccess.hasPublishedReport(inspectionId)).thenReturn(true);
    assertTrue(service.canReadFlightData(customer, inspectionId));
    assertEquals(12, service.flightReport(customer, inspectionId, sessionId).durationSeconds());
    assertEquals(
        "DRONEHUB_SCOPE_DENIED",
        assertThrows(
                BusinessException.class, () -> service.telemetry(customer, inspectionId, sessionId))
            .code());

    when(inspection.getOwnerCustomerId()).thenReturn(UUID.randomUUID());
    assertEquals(
        "DRONEHUB_SCOPE_DENIED",
        assertThrows(
                BusinessException.class,
                () -> service.flightReport(customer, inspectionId, sessionId))
            .code());
  }

  @Test
  void persistsImportedTraceAndServesItWithoutHub() {
    String csv =
        "timestamp,latitude,longitude,altitude_m\n"
            + "2026-11-10T10:00:00Z,21.0285,105.8542,10\n"
            + "2026-11-10T10:00:05Z,21.0286,105.8543,20\n";
    var imported =
        service.importFlightLog(
            actorId,
            inspectionId,
            sessionId,
            "flight.csv",
            new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
    assertEquals("IMPORTED_LOG_CSV", imported.telemetrySource());
    assertEquals(2, imported.flightTrace().size());
    assertEquals(5, imported.durationSeconds());
    assertNotNull(imported.distanceFlownMeters());
    assertTrue(imported.distanceFlownMeters() > 0);
    assertEquals("UNKNOWN", imported.finalState());
    assertEquals(Instant.parse("2026-11-10T10:00:00Z"), imported.startedAt());
    assertEquals(Instant.parse("2026-11-10T10:00:05Z"), imported.endedAt());
    assertTrue(session.hasFlightReport());
    session.complete("Survey done");
    var persisted = service.flightReport(actorId, inspectionId, sessionId);
    assertEquals("IMPORTED_LOG_CSV", persisted.telemetrySource());
    assertEquals(2, persisted.flightTrace().size());
    verify(hub, never()).flightReport(any(), any(), any());
  }

  @Test
  void keepsDurationAndTimestampsNullWhenImportedLogHasNoTimeAndWarnsWhenFarFromAsset() {
    Asset farAsset = mock(Asset.class);
    when(farAsset.getLatitude()).thenReturn(new BigDecimal("10.7769"));
    when(farAsset.getLongitude()).thenReturn(new BigDecimal("106.7009"));
    when(assets.findByIdAndOwnerCustomerId(assetId, actorId)).thenReturn(Optional.of(farAsset));

    String kml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<kml xmlns=\"http://www.opengis.net/kml/2.2\"><Document><Placemark><LineString>"
            + "<coordinates>105.8542,21.0285,10 105.8543,21.0286,20</coordinates>"
            + "</LineString></Placemark></Document></kml>";
    var imported =
        service.importFlightLog(
            actorId,
            inspectionId,
            sessionId,
            "path.kml",
            new ByteArrayInputStream(kml.getBytes(StandardCharsets.UTF_8)));
    assertEquals("IMPORTED_LOG_KML", imported.telemetrySource());
    assertNull(imported.durationSeconds());
    assertNull(imported.startedAt());
    assertNull(imported.endedAt());
    assertEquals("UNKNOWN", imported.finalState());
    assertTrue(imported.note().contains("WARN: First GPS point is"));
    assertTrue(session.hasFlightReport());
  }

  @Test
  void persistsManualDeclarationWithoutInventingTrace() {
    var report =
        service.declareManualFlight(
            actorId,
            inspectionId,
            sessionId,
            "NO_MAVLINK_SUPPORT",
            15,
            50d,
            "Drone flown using manufacturer controller.");
    assertEquals("MANUAL_DECLARATION", report.telemetrySource());
    assertEquals(900, report.durationSeconds());
    assertNull(report.distanceFlownMeters());
    assertNull(report.startedAt());
    assertNull(report.endedAt());
    assertEquals("UNKNOWN", report.finalState());
    assertTrue(report.flightTrace().isEmpty());
    assertTrue(report.note().contains("manufacturer controller"));
    session.complete("Survey done");
    assertEquals(
        "MANUAL_DECLARATION",
        service.flightReport(actorId, inspectionId, sessionId).telemetrySource());
    verify(hub, never()).flightReport(any(), any(), any());
  }

  @Test
  void rejectsInvalidManualDeclarationUnsupportedLogAndUnauthorizedInspector() {
    assertThrows(
        BusinessException.class,
        () ->
            service.declareManualFlight(
                actorId, inspectionId, sessionId, "OTHER", 15, null, "Test"));
    assertThrows(
        BusinessException.class,
        () ->
            service.declareManualFlight(
                actorId, inspectionId, sessionId, "NO_MAVLINK_SUPPORT", 0, null, ""));
    assertThrows(
        BusinessException.class,
        () ->
            service.importFlightLog(
                actorId,
                inspectionId,
                sessionId,
                "log.txt",
                new ByteArrayInputStream("hi".getBytes(StandardCharsets.UTF_8))));
    UUID outsider = UUID.randomUUID();
    when(userAccess.findActiveUser(outsider))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(
                    outsider,
                    Set.of("CUSTOMER"),
                    "CUSTOMER",
                    "Outsider",
                    null,
                    null,
                    null,
                    null,
                    null)));
    assertThrows(
        BusinessException.class,
        () ->
            service.declareManualFlight(
                outsider, inspectionId, sessionId, "NO_MAVLINK_SUPPORT", 15, null, "Test"));
    assertFalse(session.hasFlightReport());
  }

  @Test
  void allowsReplacingFlightLogInFieldCompletedBeforeReviewSubmissionAndRejectsAfterReview() {
    String srt =
        "1\n00:00:00,000 --> 00:00:01,000\n2026-11-10T10:00:00Z [latitude: 21.0285] [longitude: 105.8542]\n";
    service.importFlightLog(
        actorId,
        inspectionId,
        sessionId,
        "wrong.srt",
        new ByteArrayInputStream(srt.getBytes(StandardCharsets.UTF_8)));

    session.complete("Survey done");
    when(inspection.getStatus()).thenReturn(InspectionStatus.FIELD_COMPLETED);
    when(inspection.isFlightPermitApproved()).thenReturn(true);

    String csv =
        "datetime(utc),latitude,longitude,height_above_takeoff(meters),speed(m/s),compass_heading(degrees),battery_percent\n"
            + "2026-11-10 10:00:00,10.762622,106.660172,15,5.0,90,95\n"
            + "2026-11-10 10:00:10,10.762722,106.660272,20,5.5,95,93\n";
    var replaced =
        service.importFlightLog(
            actorId,
            inspectionId,
            sessionId,
            "corrected.csv",
            new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

    assertEquals("IMPORTED_LOG_CSV", replaced.telemetrySource());
    assertEquals(2, replaced.flightTrace().size());
    assertEquals(10, replaced.durationSeconds());

    when(inspection.getStatus()).thenReturn(InspectionStatus.UNDER_REVIEW);
    BusinessException locked =
        assertThrows(
            BusinessException.class,
            () ->
                service.importFlightLog(
                    actorId,
                    inspectionId,
                    sessionId,
                    "late.csv",
                    new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))));
    assertEquals("FIELD_SESSION_NOT_IN_PROGRESS", locked.code());
  }

  @Test
  void completeFlightPersistsLiveReportWhileSessionIsOpenAndMakesFlightReportReadable() {
    var hubReport =
        new SmartDroneHubPort.FlightReport(
            inspectionId,
            sessionId,
            "MISSION_PLANNER_MAVLINK",
            Instant.parse("2026-11-18T08:00:00Z"),
            Instant.parse("2026-11-18T08:03:00Z"),
            180,
            420.5,
            35.0,
            96,
            82,
            "LANDED",
            "Live MAVLink flight completed.",
            List.of(
                new SmartDroneHubPort.FlightTracePoint(
                    10.8755,
                    106.8008,
                    20.0,
                    4.0,
                    90.0,
                    96,
                    "AUTO",
                    1,
                    Instant.parse("2026-11-18T08:00:10Z"))),
            null);
    when(hub.flightReport(eq(inspectionId), eq(sessionId), any())).thenReturn(hubReport);

    var completed = service.completeFlight(actorId, inspectionId, sessionId);

    assertEquals("MISSION_PLANNER_MAVLINK", completed.telemetrySource());
    assertEquals(180, completed.durationSeconds());
    assertEquals(1, completed.flightTrace().size());
    assertTrue(session.isOpen());
    assertTrue(session.hasPersistedFlightReport());

    var fetchedWhileOpen = service.flightReport(actorId, inspectionId, sessionId);
    assertEquals("MISSION_PLANNER_MAVLINK", fetchedWhileOpen.telemetrySource());
    assertEquals(420.5, fetchedWhileOpen.distanceFlownMeters());
    verify(hub, times(1)).flightReport(eq(inspectionId), eq(sessionId), any());
  }
}
