package com.smartdroneinspection.inspections.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.inspections.domain.FieldSession;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.repository.FieldSessionRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.spi.SmartDroneHubPort;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
    when(inspection.getStatus()).thenReturn(InspectionStatus.IN_PROGRESS);
    when(inspection.getAssetId()).thenReturn(assetId);
    when(inspection.getOwnerCustomerId()).thenReturn(actorId);
    when(assets.findByIdAndOwnerCustomerId(assetId, actorId)).thenReturn(Optional.empty());
    when(sessions.findByIdAndInspectionId(sessionId, inspectionId))
        .thenReturn(Optional.of(session));
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
}
