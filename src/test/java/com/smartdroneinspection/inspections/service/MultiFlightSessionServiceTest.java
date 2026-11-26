package com.smartdroneinspection.inspections.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.inspections.api.dto.request.CreateFieldSessionFlightRequest;
import com.smartdroneinspection.inspections.domain.FieldSession;
import com.smartdroneinspection.inspections.domain.FieldSessionFlight;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.enums.FlightPermitVerificationStatus;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.repository.FieldSessionFlightRepository;
import com.smartdroneinspection.inspections.repository.FieldSessionRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.spi.SmartDroneHubPort;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.users.UserAccess;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MultiFlightSessionServiceTest {

  private final UUID actorId = UUID.randomUUID();
  private final UUID inspectionId = UUID.randomUUID();
  private final UUID sessionId = UUID.randomUUID();
  private final UUID assetId = UUID.randomUUID();

  private final InspectionRepository inspections = mock(InspectionRepository.class);
  private final FieldSessionRepository sessions = mock(FieldSessionRepository.class);
  private final FieldSessionFlightRepository flights = mock(FieldSessionFlightRepository.class);
  private final AssetRepository assets = mock(AssetRepository.class);
  private final UserAccess userAccess = mock(UserAccess.class);
  private final SmartDroneHubPort hub = mock(SmartDroneHubPort.class);

  private FieldSession session;
  private InspectionDroneHubService service;

  @BeforeEach
  void setUp() {
    session =
        new FieldSession(
            inspectionId, FieldSession.IN_PROGRESS, Instant.parse("2026-11-23T08:00:00Z"));
    // Assign deterministic session ID reflection for test double
    try {
      var field = FieldSession.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(session, sessionId);
    } catch (ReflectiveOperationException ignored) {
    }
    service =
        new InspectionDroneHubService(
            inspections,
            sessions,
            assets,
            userAccess,
            hub,
            null,
            new FlightLogParser(),
            flights,
            null);
    when(userAccess.findActiveUser(actorId))
        .thenReturn(
            Optional.of(new UserAccess.ActiveUser(actorId, Set.of(Roles.INSPECTOR), "Inspector")));
    Inspection inspection = mock(Inspection.class);
    when(inspections.findAssignedByInspector(inspectionId, actorId))
        .thenReturn(Optional.of(inspection));
    when(inspection.getId()).thenReturn(inspectionId);
    when(inspection.getStatus()).thenReturn(InspectionStatus.IN_PROGRESS);
    when(inspection.hasFlightPermit()).thenReturn(true);
    when(inspection.getFlightPermitVerificationStatus())
        .thenReturn(FlightPermitVerificationStatus.APPROVED);
    when(inspection.getAssetId()).thenReturn(assetId);
    when(inspection.getOwnerCustomerId()).thenReturn(actorId);
    when(sessions.findByIdAndInspectionId(sessionId, inspectionId))
        .thenReturn(Optional.of(session));
    Asset asset = mock(Asset.class);
    when(asset.getLatitude()).thenReturn(new BigDecimal("10.7717"));
    when(asset.getLongitude()).thenReturn(new BigDecimal("106.7044"));
    when(assets.findByIdAndOwnerCustomerId(assetId, actorId)).thenReturn(Optional.of(asset));
  }

  @Test
  void createsSequentialMultiDayFlightsWithoutOverwritingEarlierSorties() {
    UUID flight1Id = UUID.randomUUID();
    UUID flight2Id = UUID.randomUUID();
    FieldSessionFlight f1 =
        new FieldSessionFlight(
            sessionId, inspectionId, 1, LocalDate.of(2026, 11, 23), "Morning East Wing");
    FieldSessionFlight f2 =
        new FieldSessionFlight(
            sessionId, inspectionId, 2, LocalDate.of(2026, 11, 24), "Day 2 Roof Thermal");
    setFlightId(f1, flight1Id);
    setFlightId(f2, flight2Id);

    when(flights.findTopByFieldSessionIdOrderByFlightNumberDesc(sessionId))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(f1));
    AtomicInteger saves = new AtomicInteger();
    when(flights.saveAndFlush(any(FieldSessionFlight.class)))
        .thenAnswer(
            inv -> {
              FieldSessionFlight arg = inv.getArgument(0);
              if (arg.getId() == null) {
                setFlightId(arg, saves.incrementAndGet() == 1 ? flight1Id : flight2Id);
              }
              return arg;
            });

    Instant beforeRecording = Instant.now();
    var created1 =
        service.createFlight(
            actorId,
            inspectionId,
            sessionId,
            new CreateFieldSessionFlightRequest("Morning East Wing", LocalDate.of(2026, 11, 23)));
    var created2 =
        service.createFlight(
            actorId,
            inspectionId,
            sessionId,
            new CreateFieldSessionFlightRequest("Day 2 Roof Thermal", LocalDate.of(2026, 11, 24)));

    assertThat(created1.flightNumber()).isEqualTo(1);
    assertThat(created2.flightNumber()).isEqualTo(2);
    assertThat(created2.flightDate()).isEqualTo(LocalDate.of(2026, 11, 24));
    assertThat(created2.createdAt()).isBetween(beforeRecording, Instant.now());
    assertThat(created2.startedAt()).isNull();

    when(flights.findByIdAndFieldSessionIdAndInspectionId(flight2Id, sessionId, inspectionId))
        .thenReturn(Optional.of(f2));
    String csv =
        """
        timestamp,latitude,longitude,altitude
        2026-11-24T08:00:00Z,10.7717,106.7044,30
        2026-11-24T08:05:00Z,10.7720,106.7048,45
        """;
    var report =
        service.importFlightLog(
            actorId,
            inspectionId,
            sessionId,
            flight2Id,
            "day2-roof.csv",
            new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
            LocalDate.of(2026, 11, 24),
            "Day 2 Roof Thermal");

    assertThat(report.fieldSessionId()).isEqualTo(flight2Id);
    assertThat(report.durationSeconds()).isEqualTo(300);
    assertThat(f2.getFlightDate()).isEqualTo(LocalDate.of(2026, 11, 24));
    assertThat(f1.hasPersistedReport()).isFalse();
  }

  private static void setFlightId(FieldSessionFlight flight, UUID id) {
    try {
      var field = FieldSessionFlight.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(flight, id);
    } catch (ReflectiveOperationException ignored) {
    }
  }
}
