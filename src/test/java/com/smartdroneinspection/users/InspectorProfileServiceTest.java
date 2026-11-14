package com.smartdroneinspection.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.users.api.dto.request.CreateInspectorFlightLogRequest;
import com.smartdroneinspection.users.api.dto.request.UpdateInspectorProfileRequest;
import com.smartdroneinspection.users.api.dto.response.InspectorFlightLogResponse;
import com.smartdroneinspection.users.api.dto.response.InspectorProfileResponse;
import com.smartdroneinspection.users.domain.InspectorFlightLog;
import com.smartdroneinspection.users.domain.InspectorProfile;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.events.PilotLicenseUpdatedEvent;
import com.smartdroneinspection.users.repository.InspectorFlightLogRepository;
import com.smartdroneinspection.users.repository.InspectorProfileRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import com.smartdroneinspection.users.service.InspectorPartnerLicenseDocumentService;
import com.smartdroneinspection.users.service.InspectorProfileService;
import com.smartdroneinspection.users.service.PilotLicenseClassService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class InspectorProfileServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private InspectorFlightLogRepository flightLogRepository;
  @Mock private InspectorProfileRepository profileRepository;
  @Mock private InspectorPartnerLicenseDocumentService licenseDocumentService;
  @Mock private PilotLicenseClassService pilotLicenseClassService;
  @Mock private ApplicationEventPublisher eventPublisher;

  private InspectorProfile profile;

  private InspectorProfileService service;
  private User inspector;
  private UUID inspectorId;
  private UUID actorId;

  @BeforeEach
  void setUp() {
    service =
        new InspectorProfileService(
            userRepository,
            flightLogRepository,
            profileRepository,
            licenseDocumentService,
            pilotLicenseClassService);
    service.setEventPublisher(eventPublisher);
    inspectorId = UUID.randomUUID();
    actorId = UUID.randomUUID();

    inspector =
        new User(
            "inspector@example.com",
            "Nguyen Van Bay",
            "0901234567",
            null,
            "hash",
            UserStatus.ACTIVE,
            ActorZone.INSPECTOR);
    inspector.addRole(UserRole.INSPECTOR);
    profile = new InspectorProfile(inspectorId);
    profile.updateFlightExperience(BigDecimal.valueOf(50), null, null, null);
    profile.updatePilotLicense(
        "VN-UAV-12345",
        "Hạng 2 (2-25kg)",
        LocalDate.of(2025, 1, 10),
        Instant.now().plus(120, ChronoUnit.DAYS),
        "Cục Tác chiến - BQP",
        "inspector-partner-applications/licenses/test.pdf");
  }

  @Test
  void getProfile_calculatesHoursAndRemainingDaysCorrectly() {
    when(userRepository.findById(inspectorId)).thenReturn(Optional.of(inspector));
    when(profileRepository.findById(inspectorId)).thenReturn(Optional.of(profile));

    InspectorFlightLog log1 =
        new InspectorFlightLog(
            inspectorId,
            "FL-001",
            LocalDate.now(),
            "Kiem tra pin mat troi",
            "DJI Matrice 350 RTK",
            "SN123",
            "Binh Thuan",
            90, // 1.5 hours
            "Solar",
            false,
            "Bay on dinh",
            actorId);
    InspectorFlightLog log2 =
        new InspectorFlightLog(
            inspectorId,
            "FL-002",
            LocalDate.now(),
            "Kiem tra cot dien 220kV",
            "DJI Matrice 350 RTK",
            "SN123",
            "Ninh Thuan",
            30, // 0.5 hours
            "Grid",
            false,
            "Hoan thanh tot",
            actorId);

    when(flightLogRepository.findByInspectorUserIdOrderByFlightDateDesc(inspector.getId()))
        .thenReturn(List.of(log1, log2));

    InspectorProfileResponse response = service.getProfile(inspectorId);

    assertThat(response.fullName()).isEqualTo("Nguyen Van Bay");
    assertThat(response.pilotLicenseNumber()).isEqualTo("VN-UAV-12345");
    assertThat(response.priorFlightHours()).isEqualByComparingTo(BigDecimal.valueOf(50));
    // 90 + 30 = 120 mins = 2.00 hours
    assertThat(response.companyFlightHours()).isEqualByComparingTo(BigDecimal.valueOf(2.00));
    assertThat(response.totalFlightHours()).isEqualByComparingTo(BigDecimal.valueOf(52.00));
    assertThat(response.companyFlightsCount()).isEqualTo(2);
    assertThat(response.hasLicenseScan()).isTrue();
    assertThat(response.licenseExpirationStatus()).isEqualTo("VALID");
    assertThat(response.licenseRemainingDays()).isGreaterThan(60);
  }

  @Test
  void updateProfile_updatesPersonalAndLicenseAndExperienceFields() {
    when(userRepository.findById(inspectorId)).thenReturn(Optional.of(inspector));
    when(profileRepository.findById(inspectorId)).thenReturn(Optional.of(profile));
    when(profileRepository.save(any(InspectorProfile.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(flightLogRepository.findByInspectorUserIdOrderByFlightDateDesc(inspector.getId()))
        .thenReturn(List.of());
    when(pilotLicenseClassService.requireActiveName("BVLOS Nang cao")).thenReturn("BVLOS Nang cao");

    Instant expiresAt = Instant.now().plus(400, ChronoUnit.DAYS);
    UpdateInspectorProfileRequest request =
        new UpdateInspectorProfileRequest(
            "Nguyen Van Bay Update",
            "0988776655",
            "012345678901",
            LocalDate.of(1992, 5, 20),
            "Quan 1, TP. HCM",
            "Phi cong xuat sac",
            "VN-UAV-99999",
            "BVLOS Nang cao",
            LocalDate.of(2025, 6, 1),
            expiresAt,
            "Cuc Hang khong Viet Nam",
            BigDecimal.valueOf(120),
            "DJI M350, Autel Max 4T",
            "Duong day dien cao the, Tuabin gio",
            "5 nam kinh nghiem khao sat ha tang nang luong");

    InspectorProfileResponse response = service.updateProfile(inspectorId, request, actorId);

    assertThat(response.fullName()).isEqualTo("Nguyen Van Bay Update");
    assertThat(response.phoneNumber()).isEqualTo("0988776655");
    assertThat(response.citizenId()).isEqualTo("012345678901");
    assertThat(response.dateOfBirth()).isEqualTo(LocalDate.of(1992, 5, 20));
    assertThat(response.address()).isEqualTo("Quan 1, TP. HCM");
    assertThat(response.pilotLicenseNumber()).isEqualTo("VN-UAV-99999");
    assertThat(response.pilotLicenseClass()).isEqualTo("BVLOS Nang cao");
    assertThat(response.pilotLicenseAuthority()).isEqualTo("Cuc Hang khong Viet Nam");
    assertThat(response.priorFlightHours()).isEqualByComparingTo(BigDecimal.valueOf(120));
    assertThat(response.certifiedDroneModels()).isEqualTo("DJI M350, Autel Max 4T");
    assertThat(response.specializations()).isEqualTo("Duong day dien cao the, Tuabin gio");
    assertThat(response.experienceSummary()).contains("5 nam kinh nghiem");
    verify(eventPublisher).publishEvent(new PilotLicenseUpdatedEvent(inspectorId, expiresAt));
  }

  @Test
  void uploadLicenseScan_updatesLicenseFileKey() {
    when(userRepository.findById(inspectorId)).thenReturn(Optional.of(inspector));
    when(profileRepository.findById(inspectorId)).thenReturn(Optional.of(profile));
    when(profileRepository.save(any(InspectorProfile.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    MockMultipartFile file =
        new MockMultipartFile(
            "file", "scan-license.pdf", "application/pdf", "%PDF-1.4 test".getBytes());
    String expectedKey = "inspector-partner-applications/licenses/" + UUID.randomUUID() + ".pdf";
    when(licenseDocumentService.upload(file))
        .thenReturn(new InspectorPartnerLicenseDocumentService.UploadResponse(expectedKey));

    InspectorProfileResponse response = service.uploadLicenseScan(inspectorId, file, actorId);

    assertThat(response.pilotLicenseFileKey()).isEqualTo(expectedKey);
    assertThat(response.hasLicenseScan()).isTrue();
  }

  @Test
  void recordFlightLog_persistsAndCalculatesHours() {
    when(userRepository.findById(inspectorId)).thenReturn(Optional.of(inspector));
    when(flightLogRepository.save(any(InspectorFlightLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    CreateInspectorFlightLogRequest request =
        new CreateInspectorFlightLogRequest(
            "FL-2026-99",
            LocalDate.of(2026, 10, 31),
            "Khao sat duong day truyen tai 500kV",
            "DJI Matrice 350 RTK",
            "1581F5GJD2203001",
            "Tram bien ap 500kV Pho Noi",
            75, // 1.25 hours
            "Truyen tai dien",
            false,
            "Ghi hinh nhiet va LiDAR thanh cong");

    InspectorFlightLogResponse response = service.recordFlightLog(inspectorId, request, actorId);

    assertThat(response.flightCode()).isEqualTo("FL-2026-99");
    assertThat(response.missionTitle()).isEqualTo("Khao sat duong day truyen tai 500kV");
    assertThat(response.flightDurationMinutes()).isEqualTo(75);
    assertThat(response.flightDurationHours()).isEqualTo(1.25);
    assertThat(response.safetyIncident()).isFalse();
    verify(flightLogRepository).save(any(InspectorFlightLog.class));
  }

  @Test
  void recordAutomaticFlightSession_createsFlightLogAndUpdatesCertifiedModelsAndSpecializations() {
    when(flightLogRepository.existsByFlightCode(any())).thenReturn(false);
    when(profileRepository.findById(inspectorId)).thenReturn(Optional.of(profile));
    when(profileRepository.save(any(InspectorProfile.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(flightLogRepository.save(any(InspectorFlightLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.recordAutomaticFlightSession(
        inspectorId,
        "FL-INSP-TEST-001",
        LocalDate.of(2026, 10, 31),
        "Tuabin gió WTG-01",
        "Autel EVO Max 4T",
        "AUTEL-SN-999",
        "Mũi Né, Bình Thuận",
        90,
        "Năng lượng tái tạo",
        "Hoàn thành chụp nhiệt 3 cánh tuabin");

    verify(flightLogRepository).save(any(InspectorFlightLog.class));
    assertThat(profile.getCertifiedDroneModels()).contains("Autel EVO Max 4T");
    assertThat(profile.getSpecializations()).contains("Năng lượng tái tạo");
  }

  @Test
  void updateProfile_preservesExistingLicenseFieldsWhenOmitted() {
    when(userRepository.findById(inspectorId)).thenReturn(Optional.of(inspector));
    when(profileRepository.findById(inspectorId)).thenReturn(Optional.of(profile));
    when(profileRepository.save(any(InspectorProfile.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(flightLogRepository.findByInspectorUserIdOrderByFlightDateDesc(inspector.getId()))
        .thenReturn(List.of());

    UpdateInspectorProfileRequest partialRequest =
        new UpdateInspectorProfileRequest(
            "Nguyen Van Bay",
            null,
            null,
            null,
            "Ha Noi",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    InspectorProfileResponse response = service.updateProfile(inspectorId, partialRequest, actorId);

    assertThat(response.address()).isEqualTo("Ha Noi");
    assertThat(response.phoneNumber()).isEqualTo("0901234567");
    assertThat(response.pilotLicenseNumber()).isEqualTo("VN-UAV-12345");
    assertThat(response.pilotLicenseClass()).isEqualTo("Hạng 2 (2-25kg)");
    assertThat(response.pilotLicenseIssuedDate()).isEqualTo(LocalDate.of(2025, 1, 10));
    assertThat(response.pilotLicenseExpiresAt()).isNotNull();
    assertThat(response.pilotLicenseAuthority()).isEqualTo("Cục Tác chiến - BQP");
    assertThat(response.priorFlightHours()).isEqualByComparingTo(BigDecimal.valueOf(50));
  }
}
