package com.smartdroneinspection.assets.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.assets.api.dto.request.ReviewDroneRegistrationApplicationRequest;
import com.smartdroneinspection.assets.api.dto.request.SubmitDroneRegistrationApplicationRequest;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.DroneRegistrationApplication;
import com.smartdroneinspection.assets.repository.DroneRegistrationApplicationRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.AuthException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.UserAccess;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DroneRegistrationApplicationServiceTest {

  private DroneRegistrationApplicationRepository applications;
  private DroneRepository drones;
  private UserAccess userAccess;
  private DroneRegistrationApplicationService service;

  private final UUID inspectorId = UUID.randomUUID();
  private final UUID adminId = UUID.randomUUID();
  private final UUID applicationId = UUID.randomUUID();
  private final UUID droneId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    applications = mock(DroneRegistrationApplicationRepository.class);
    drones = mock(DroneRepository.class);
    userAccess = mock(UserAccess.class);
    service =
        new DroneRegistrationApplicationService(
            applications,
            drones,
            userAccess,
            Optional.of(mock(EvidenceObjectStore.class)),
            mock(DroneEvidenceUploadService.class));

    when(userAccess.findActiveUser(inspectorId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(
                    inspectorId,
                    Set.of(Roles.INSPECTOR),
                    null,
                    "INSPECTOR_PARTNER",
                    "Pilot",
                    "pilot@test.local",
                    "LIC-123",
                    "CLASS_A",
                    Instant.now().plusSeconds(86400),
                    "file-key")));
    when(userAccess.findActiveUser(adminId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(
                    adminId,
                    Set.of(Roles.ADMIN),
                    null,
                    "PLATFORM",
                    "Admin",
                    "admin@test.local",
                    null,
                    null,
                    null,
                    null)));
  }

  @Test
  void submitValidApplicationSavesAndAudits() {
    SubmitDroneRegistrationApplicationRequest request =
        new SubmitDroneRegistrationApplicationRequest(
            "SN-001",
            "Matrice 350",
            "DJI",
            "REG-999",
            "reg-key",
            Instant.now().plusSeconds(86400),
            "owner-key",
            "insp-key");
    DroneEvidenceUploadService evidenceUploads = mock(DroneEvidenceUploadService.class);
    service =
        new DroneRegistrationApplicationService(
            applications,
            drones,
            userAccess,
            Optional.of(mock(EvidenceObjectStore.class)),
            evidenceUploads);
    org.mockito.Mockito.doNothing()
        .when(evidenceUploads)
        .validateReference(inspectorId, "REGISTRATION", "reg-key");
    org.mockito.Mockito.doNothing()
        .when(evidenceUploads)
        .validateReference(inspectorId, "OWNERSHIP", "owner-key");
    org.mockito.Mockito.doNothing()
        .when(evidenceUploads)
        .validateReference(inspectorId, "INSPECTION", "insp-key");

    when(drones.existsBySerialNumberIgnoreCase("SN-001")).thenReturn(false);
    when(applications.existsBySerialNumberIgnoreCaseAndStatus("SN-001", "PENDING"))
        .thenReturn(false);
    when(applications.saveAndFlush(any(DroneRegistrationApplication.class)))
        .thenAnswer(
            inv -> {
              DroneRegistrationApplication app = inv.getArgument(0);
              ReflectionTestUtils.setField(app, "id", applicationId);
              return app;
            });

    var response = service.submit(inspectorId, request, "127.0.0.1", "agent", "corr-1");
    assertThat(response.serialNumber()).isEqualTo("SN-001");
    verify(userAccess)
        .recordSecurityAudit(
            eq(inspectorId),
            eq(applicationId),
            eq("DRONE_REGISTRATION_SUBMITTED"),
            eq("SUCCESS"),
            any(),
            any(),
            any());
  }

  @Test
  void submitRejectsExpiredRegistrationOrDuplicateSerial() {
    SubmitDroneRegistrationApplicationRequest expired =
        new SubmitDroneRegistrationApplicationRequest(
            "SN-002", "M350", "DJI", "REG-1", "reg", Instant.now().minusSeconds(10), "own", "insp");
    assertThatThrownBy(() -> service.submit(inspectorId, expired, "ip", "agent", "corr"))
        .isInstanceOf(AuthException.class)
        .hasMessageContaining("Drone registration must be current");

    SubmitDroneRegistrationApplicationRequest valid =
        new SubmitDroneRegistrationApplicationRequest(
            "SN-003",
            "M350",
            "DJI",
            "REG-2",
            "reg",
            Instant.now().plusSeconds(1000),
            "own",
            "insp");
    when(drones.existsBySerialNumberIgnoreCase("SN-003")).thenReturn(true);
    assertThatThrownBy(() -> service.submit(inspectorId, valid, "ip", "agent", "corr"))
        .isInstanceOf(AuthException.class)
        .hasMessageContaining("already registered");

    when(drones.existsBySerialNumberIgnoreCase("SN-003")).thenReturn(false);
    when(applications.existsBySerialNumberIgnoreCaseAndStatus("SN-003", "PENDING"))
        .thenReturn(true);
    assertThatThrownBy(() -> service.submit(inspectorId, valid, "ip", "agent", "corr"))
        .isInstanceOf(AuthException.class)
        .hasMessageContaining("already registered");
  }

  @Test
  void mineAndInspectorDronesReturnMappedData() {
    DroneRegistrationApplication app = sampleApplication();
    when(applications.findAllByInspectorUserIdOrderByCreatedAtDesc(inspectorId))
        .thenReturn(List.of(app));
    assertThat(service.mine(inspectorId)).hasSize(1);

    Drone drone =
        Drone.createInspectorOwned(
            inspectorId, "SN-001", "M350", "DJI", "REG-1", "file", Instant.now().plusSeconds(1000));
    ReflectionTestUtils.setField(drone, "id", droneId);
    when(drones.findAllByInspectorOwnerId(inspectorId)).thenReturn(List.of(drone));
    assertThat(service.inspectorDrones(inspectorId)).hasSize(1);
  }

  @Test
  void pendingRequiresAdminAuthority() {
    DroneRegistrationApplication app = sampleApplication();
    when(applications.findAllByStatusOrderByCreatedAtAsc("PENDING")).thenReturn(List.of(app));

    assertThat(service.pending(adminId)).hasSize(1);

    assertThatThrownBy(() -> service.pending(inspectorId))
        .isInstanceOf(AuthException.class)
        .hasMessageContaining("Platform Admin access is required");
  }

  @Test
  void approveApplicationCreatesDroneAndUpdatesApplication() {
    DroneRegistrationApplication app = sampleApplication();
    when(applications.findByIdAndStatus(applicationId, "PENDING")).thenReturn(Optional.of(app));
    when(drones.existsBySerialNumberIgnoreCase(app.getSerialNumber())).thenReturn(false);
    when(drones.saveAndFlush(any(Drone.class)))
        .thenAnswer(
            inv -> {
              Drone d = inv.getArgument(0);
              ReflectionTestUtils.setField(d, "id", droneId);
              return d;
            });
    when(applications.saveAndFlush(any(DroneRegistrationApplication.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    var response = service.approve(adminId, applicationId, "ip", "agent", "corr");
    assertThat(response.status()).isEqualTo("APPROVED");
    assertThat(response.droneId()).isEqualTo(droneId);
    verify(userAccess)
        .recordSecurityAudit(
            eq(adminId),
            eq(applicationId),
            eq("DRONE_REGISTRATION_APPROVED"),
            eq("SUCCESS"),
            any(),
            any(),
            any());
  }

  @Test
  void approveApplicationRejectsWhenRegistrationExpiredBeforeApproval() {
    DroneRegistrationApplication app =
        new DroneRegistrationApplication(
            inspectorId,
            "SN-EXP",
            "M350",
            "DJI",
            "REG",
            "file",
            Instant.now().minusSeconds(10),
            "own",
            "insp");
    ReflectionTestUtils.setField(app, "id", applicationId);
    when(applications.findByIdAndStatus(applicationId, "PENDING")).thenReturn(Optional.of(app));

    assertThatThrownBy(() -> service.approve(adminId, applicationId, "ip", "agent", "corr"))
        .isInstanceOf(AuthException.class)
        .hasMessageContaining("expired before approval");
  }

  @Test
  void rejectApplicationUpdatesStatusAndRecordsAudit() {
    DroneRegistrationApplication app = sampleApplication();
    when(applications.findByIdAndStatus(applicationId, "PENDING")).thenReturn(Optional.of(app));
    when(applications.saveAndFlush(any(DroneRegistrationApplication.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    var response =
        service.reject(
            adminId,
            applicationId,
            new ReviewDroneRegistrationApplicationRequest("Invalid documentation"),
            "ip",
            "agent",
            "corr");
    assertThat(response.status()).isEqualTo("REJECTED");
    assertThat(response.reviewNote()).isEqualTo("Invalid documentation");
    verify(userAccess)
        .recordSecurityAudit(
            eq(adminId),
            eq(applicationId),
            eq("DRONE_REGISTRATION_REJECTED"),
            eq("SUCCESS"),
            any(),
            any(),
            any());
  }

  private DroneRegistrationApplication sampleApplication() {
    DroneRegistrationApplication app =
        new DroneRegistrationApplication(
            inspectorId,
            "SN-APP",
            "M350",
            "DJI",
            "REG-APP",
            "reg-file",
            Instant.now().plusSeconds(86400),
            "owner-file",
            "insp-file");
    ReflectionTestUtils.setField(app, "id", applicationId);
    return app;
  }
}
