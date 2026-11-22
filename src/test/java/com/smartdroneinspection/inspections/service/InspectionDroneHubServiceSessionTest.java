package com.smartdroneinspection.inspections.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.inspections.api.dto.response.EvidenceResponse;
import com.smartdroneinspection.inspections.domain.FieldSession;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.enums.FlightPermitVerificationStatus;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.repository.FieldSessionRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.spi.SmartDroneHubPort;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InspectionDroneHubServiceSessionTest {

  private final UUID actorId = UUID.randomUUID();
  private final UUID inspectionId = UUID.randomUUID();
  private final UUID sessionId = UUID.randomUUID();
  private final UUID assetId = UUID.randomUUID();

  private final InspectionRepository inspections = mock(InspectionRepository.class);
  private final FieldSessionRepository sessions = mock(FieldSessionRepository.class);
  private final AssetRepository assets = mock(AssetRepository.class);
  private final UserAccess userAccess = mock(UserAccess.class);
  private final SmartDroneHubPort hub = mock(SmartDroneHubPort.class);
  private final EvidenceService evidenceService = mock(EvidenceService.class);
  private final Inspection inspection = mock(Inspection.class);

  private FieldSession session;
  private InspectionDroneHubService service;

  @BeforeEach
  void setUp() {
    session =
        new FieldSession(
            inspectionId, FieldSession.IN_PROGRESS, Instant.parse("2026-11-10T10:00:00Z"));
    service =
        new InspectionDroneHubService(
            inspections, sessions, assets, userAccess, hub, evidenceService, new FlightLogParser());
    when(userAccess.findActiveUser(actorId))
        .thenReturn(
            Optional.of(new UserAccess.ActiveUser(actorId, Set.of(Roles.INSPECTOR), "Inspector")));
    when(inspections.findAssignedByInspector(inspectionId, actorId))
        .thenReturn(Optional.of(inspection));
    when(inspection.isPartnerAssignment()).thenReturn(false);
    when(inspection.hasFlightPermit()).thenReturn(true);
    when(inspection.getFlightPermitVerificationStatus())
        .thenReturn(FlightPermitVerificationStatus.APPROVED);
    when(inspection.getStatus()).thenReturn(InspectionStatus.IN_PROGRESS);
    when(inspection.getAssetId()).thenReturn(assetId);
    when(inspection.getOwnerCustomerId()).thenReturn(actorId);
    when(sessions.findByIdAndInspectionId(sessionId, inspectionId))
        .thenReturn(Optional.of(session));
  }

  @Test
  void startsMissionWithAssetAndNormalizedTelemetrySource() {
    Asset asset = mock(Asset.class);
    when(asset.getName()).thenReturn("Bridge");
    when(asset.getLatitude()).thenReturn(new BigDecimal("21.0"));
    when(asset.getLongitude()).thenReturn(new BigDecimal("105.0"));
    when(asset.getInspectionItems()).thenReturn(List.of("Deck"));
    when(assets.findByIdAndOwnerCustomerId(assetId, actorId)).thenReturn(Optional.of(asset));
    SmartDroneHubPort.Mission mission = mock(SmartDroneHubPort.Mission.class);
    when(hub.createMission(any())).thenReturn(mission);

    var result = service.start(actorId, inspectionId, sessionId, " MAVLINK ");

    assertThat(result).isSameAs(mission);
    verify(hub)
        .createMission(
            new SmartDroneHubPort.MissionRequest(
                inspectionId,
                sessionId,
                "Bridge",
                new BigDecimal("21.0"),
                new BigDecimal("105.0"),
                List.of("Deck"),
                "MAVLINK"));
  }

  @Test
  void startRejectsInactiveSessionsAndMissingAssets() {
    session = new FieldSession(inspectionId, FieldSession.FIELD_COMPLETED, Instant.now());
    when(sessions.findByIdAndInspectionId(sessionId, inspectionId))
        .thenReturn(Optional.of(session));

    assertBusinessCode(
        () -> service.start(actorId, inspectionId, sessionId, null),
        "FIELD_SESSION_NOT_IN_PROGRESS");

    session = new FieldSession(inspectionId, FieldSession.IN_PROGRESS, Instant.now());
    when(sessions.findByIdAndInspectionId(sessionId, inspectionId))
        .thenReturn(Optional.of(session));
    when(assets.findByIdAndOwnerCustomerId(assetId, actorId)).thenReturn(Optional.empty());
    assertBusinessCode(
        () -> service.start(actorId, inspectionId, sessionId, null), "INSPECTION_NOT_FOUND");
  }

  @Test
  void telemetryRequiresOpenSessionAndDelegatesToHub() {
    SmartDroneHubPort.Telemetry telemetry = mock(SmartDroneHubPort.Telemetry.class);
    when(hub.missionTelemetry(inspectionId, sessionId)).thenReturn(telemetry);

    assertThat(service.telemetry(actorId, inspectionId, sessionId)).isSameAs(telemetry);

    session = new FieldSession(inspectionId, FieldSession.FIELD_COMPLETED, Instant.now());
    when(sessions.findByIdAndInspectionId(sessionId, inspectionId))
        .thenReturn(Optional.of(session));
    assertBusinessCode(
        () -> service.telemetry(actorId, inspectionId, sessionId), "FIELD_SESSION_NOT_IN_PROGRESS");
  }

  @Test
  void flightReportRequiresCompletedSessionWhenNoReportExists() {
    assertBusinessCode(
        () -> service.flightReport(actorId, inspectionId, sessionId),
        "FIELD_SESSION_NOT_COMPLETED");
  }

  @Test
  void flightReportReturnsPersistedImportedReportWithoutCallingHub() {
    session.recordFlightReport(
        "IMPORTED_LOG_CSV", 120, 100.0, 50.0, 90, 80, "LANDED", "Imported", "[]");
    session.complete("done");
    when(sessions.findByIdAndInspectionId(sessionId, inspectionId))
        .thenReturn(Optional.of(session));

    var report = service.flightReport(actorId, inspectionId, sessionId);

    assertThat(report.telemetrySource()).isEqualTo("IMPORTED_LOG_CSV");
    assertThat(report.flightTrace()).isEmpty();
  }

  @Test
  void flightReportFetchesFromHubWhenSessionHasNoPersistedReport() {
    session.complete("done");
    when(sessions.findByIdAndInspectionId(sessionId, inspectionId))
        .thenReturn(Optional.of(session));
    SmartDroneHubPort.FlightReport hubReport = mock(SmartDroneHubPort.FlightReport.class);
    when(hubReport.flightTrace()).thenReturn(List.of());
    when(hubReport.telemetrySource()).thenReturn("HUB");
    when(hubReport.startedAt()).thenReturn(Instant.parse("2026-11-10T10:00:00Z"));
    when(hubReport.endedAt()).thenReturn(Instant.parse("2026-11-10T10:10:00Z"));
    when(hubReport.durationSeconds()).thenReturn(600);
    when(hubReport.distanceFlownMeters()).thenReturn(150.0);
    when(hubReport.maxAltitudeMeters()).thenReturn(60.0);
    when(hubReport.startBatteryPercent()).thenReturn(90);
    when(hubReport.endBatteryPercent()).thenReturn(70);
    when(hubReport.finalState()).thenReturn("LANDED");
    when(hubReport.note()).thenReturn("hub note");
    when(hub.flightReport(eq(inspectionId), eq(sessionId), any())).thenReturn(hubReport);

    var report = service.flightReport(actorId, inspectionId, sessionId);

    assertThat(report.telemetrySource()).isEqualTo("HUB");
    verify(sessions).saveAndFlush(session);
  }

  @Test
  void syncFlightVideoReusesExistingEvidence() {
    UUID evidenceId = UUID.randomUUID();
    session.recordFlightVideoEvidenceId(evidenceId);
    EvidenceResponse existing = mock(EvidenceResponse.class);
    when(existing.id()).thenReturn(evidenceId);
    when(existing.isDeleted()).thenReturn(false);
    when(evidenceService.list(actorId, inspectionId)).thenReturn(List.of(existing));

    assertThat(service.syncFlightVideo(actorId, inspectionId, sessionId)).isSameAs(existing);
  }

  @Test
  void syncFlightVideoStoresHubRecordingAndPersistsEvidenceId() {
    SmartDroneHubPort.CapturedMedia recording =
        new SmartDroneHubPort.CapturedMedia(
            "flight.mp4",
            "video/mp4",
            new byte[] {1},
            Instant.now(),
            new BigDecimal("21.0"),
            new BigDecimal("105.0"),
            "Bridge");
    when(hub.missionRecording(inspectionId, sessionId)).thenReturn(recording);
    EvidenceResponse saved = mock(EvidenceResponse.class);
    UUID savedId = UUID.randomUUID();
    when(saved.id()).thenReturn(savedId);
    when(evidenceService.storeCapturedMedia(
            eq(actorId),
            eq(inspectionId),
            eq(sessionId),
            eq("flight.mp4"),
            eq("video/mp4"),
            any(),
            any(),
            any(),
            any(),
            eq("Bridge")))
        .thenReturn(saved);

    var result = service.syncFlightVideo(actorId, inspectionId, sessionId);

    assertThat(result).isSameAs(saved);
    assertThat(session.getFlightVideoEvidenceId()).isEqualTo(savedId);
    verify(sessions).saveAndFlush(session);
  }

  @Test
  void syncFlightVideoRequiresEvidenceService() {
    InspectionDroneHubService withoutEvidence =
        new InspectionDroneHubService(inspections, sessions, assets, userAccess, hub, null, null);

    assertBusinessCode(
        () -> withoutEvidence.syncFlightVideo(actorId, inspectionId, sessionId),
        "EVIDENCE_STORAGE_UNAVAILABLE");
  }

  @Test
  void captureSnapshotStoresHubMedia() {
    SmartDroneHubPort.CapturedMedia snapshot =
        new SmartDroneHubPort.CapturedMedia(
            "snap.jpg", "image/jpeg", new byte[] {2}, Instant.now(), null, null, null);
    when(hub.captureSnapshot(inspectionId, sessionId)).thenReturn(snapshot);
    EvidenceResponse saved = mock(EvidenceResponse.class);
    when(evidenceService.storeCapturedMedia(
            eq(actorId),
            eq(inspectionId),
            eq(sessionId),
            eq("snap.jpg"),
            eq("image/jpeg"),
            any(),
            any(),
            any(),
            any(),
            any()))
        .thenReturn(saved);

    assertThat(service.captureSnapshot(actorId, inspectionId, sessionId)).isSameAs(saved);
  }

  @Test
  void captureSnapshotRequiresEvidenceService() {
    InspectionDroneHubService withoutEvidence =
        new InspectionDroneHubService(inspections, sessions, assets, userAccess, hub, null, null);

    assertBusinessCode(
        () -> withoutEvidence.captureSnapshot(actorId, inspectionId, sessionId),
        "EVIDENCE_STORAGE_UNAVAILABLE");
  }

  private static void assertBusinessCode(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, String code) {
    assertThatThrownBy(callable)
        .isInstanceOfSatisfying(
            BusinessException.class, exception -> assertThat(exception.code()).isEqualTo(code));
  }
}
