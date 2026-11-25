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
import com.smartdroneinspection.assets.repository.DroneDocumentConfigRepository;
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
  private DroneDocumentConfigRepository documentConfigs;
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
    documentConfigs = mock(DroneDocumentConfigRepository.class);
    service =
        new DroneRegistrationApplicationService(
            applications,
            drones,
            userAccess,
            Optional.of(mock(EvidenceObjectStore.class)),
            mock(DroneEvidenceUploadService.class),
            documentConfigs);

    when(userAccess.findActiveUser(inspectorId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(
                    inspectorId,
                    Set.of(Roles.INSPECTOR),
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
            "insp-key",
            null,
            new java.math.BigDecimal("5000"),
            null,
            false,
            false,
            null,
            List.of("drone-img-key"),
            null);
    DroneEvidenceUploadService evidenceUploads = mock(DroneEvidenceUploadService.class);
    service =
        new DroneRegistrationApplicationService(
            applications,
            drones,
            userAccess,
            Optional.of(mock(EvidenceObjectStore.class)),
            evidenceUploads,
            documentConfigs);
    org.mockito.Mockito.doNothing()
        .when(evidenceUploads)
        .validateReference(inspectorId, "REGISTRATION", "reg-key");
    org.mockito.Mockito.doNothing()
        .when(evidenceUploads)
        .validateReference(inspectorId, "OWNERSHIP", "owner-key");
    org.mockito.Mockito.doNothing()
        .when(evidenceUploads)
        .validateReference(inspectorId, "INSPECTION", "insp-key");
    org.mockito.Mockito.doNothing()
        .when(evidenceUploads)
        .validateReference(inspectorId, "DRONE_IMAGE", "drone-img-key");

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
    assertThat(response.maxAltitudeMeters()).isEqualByComparingTo("5000");
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
            "SN-002",
            "M350",
            "DJI",
            "REG-1",
            "reg",
            Instant.now().minusSeconds(10),
            "own",
            "insp",
            null,
            null,
            false,
            false,
            null,
            List.of("img"));
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
            "insp",
            null,
            null,
            false,
            false,
            null,
            List.of("img"));
    Drone otherDrone =
        Drone.createInspectorOwned(
            UUID.randomUUID(),
            "SN-003",
            "M350",
            "DJI",
            "REG-2",
            "reg",
            Instant.now().plusSeconds(1000));
    when(drones.findBySerialNumberIgnoreCase("SN-003")).thenReturn(Optional.of(otherDrone));
    assertThatThrownBy(() -> service.submit(inspectorId, valid, "ip", "agent", "corr"))
        .isInstanceOf(AuthException.class)
        .hasMessageContaining("already registered");

    when(drones.findBySerialNumberIgnoreCase("SN-003")).thenReturn(Optional.empty());
    when(applications.existsBySerialNumberIgnoreCaseAndStatus("SN-003", "PENDING"))
        .thenReturn(true);
    assertThatThrownBy(() -> service.submit(inspectorId, valid, "ip", "agent", "corr"))
        .isInstanceOf(AuthException.class)
        .hasMessageContaining("already pending review");
  }

  @Test
  void submitAllowsUpdatingOwnExistingDrone() {
    SubmitDroneRegistrationApplicationRequest updateReq =
        new SubmitDroneRegistrationApplicationRequest(
            "SN-OWN",
            "M350 RTK",
            "DJI",
            "REG-OWN",
            "reg-key",
            Instant.now().plusSeconds(86400),
            "owner-key",
            null,
            null,
            null,
            false,
            false,
            null,
            List.of("drone-img-key"));
    DroneEvidenceUploadService evidenceUploads = mock(DroneEvidenceUploadService.class);
    service =
        new DroneRegistrationApplicationService(
            applications,
            drones,
            userAccess,
            Optional.of(mock(EvidenceObjectStore.class)),
            evidenceUploads,
            documentConfigs);
    Drone ownDrone =
        Drone.createInspectorOwned(
            inspectorId,
            "SN-OWN",
            "M350",
            "DJI",
            "REG-OLD",
            "reg-old",
            Instant.now().plusSeconds(1000));
    ReflectionTestUtils.setField(ownDrone, "id", droneId);
    when(drones.findBySerialNumberIgnoreCase("SN-OWN")).thenReturn(Optional.of(ownDrone));
    when(applications.existsBySerialNumberIgnoreCaseAndStatus("SN-OWN", "PENDING"))
        .thenReturn(false);
    when(applications.saveAndFlush(any(DroneRegistrationApplication.class)))
        .thenAnswer(
            inv -> {
              DroneRegistrationApplication app = inv.getArgument(0);
              ReflectionTestUtils.setField(app, "id", applicationId);
              return app;
            });

    var response = service.submit(inspectorId, updateReq, "ip", "agent", "corr");
    assertThat(response.serialNumber()).isEqualTo("SN-OWN");
  }

  @Test
  void submitRequiresAtLeastOneDroneImage() {
    SubmitDroneRegistrationApplicationRequest noImage =
        new SubmitDroneRegistrationApplicationRequest(
            "SN-NO-IMG",
            "M350",
            "DJI",
            "REG-NO-IMG",
            "reg",
            Instant.now().plusSeconds(1000),
            "own",
            "insp",
            null,
            null,
            false,
            false,
            null,
            List.of());
    assertThatThrownBy(() -> service.submit(inspectorId, noImage, "ip", "agent", "corr"))
        .isInstanceOf(AuthException.class)
        .hasMessageContaining("At least one drone image is required");
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
        .hasMessageContaining("Platform Reviewer access is required");
  }

  @Test
  void approveApplicationCreatesDroneAndUpdatesApplication() {
    DroneRegistrationApplication app = sampleApplication();
    ReflectionTestUtils.setField(app, "maxAltitudeMeters", new java.math.BigDecimal("6000"));
    when(applications.findByIdAndStatus(applicationId, "PENDING")).thenReturn(Optional.of(app));
    when(drones.existsBySerialNumberIgnoreCase(app.getSerialNumber())).thenReturn(false);
    when(drones.saveAndFlush(any(Drone.class)))
        .thenAnswer(
            inv -> {
              Drone d = inv.getArgument(0);
              assertThat(d.getMaxAltitudeMeters()).isEqualByComparingTo("6000");
              ReflectionTestUtils.setField(d, "id", droneId);
              return d;
            });
    when(applications.saveAndFlush(any(DroneRegistrationApplication.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    var response = service.approve(adminId, applicationId, "ip", "agent", "corr");
    assertThat(response.status()).isEqualTo("APPROVED");
    assertThat(response.droneId()).isEqualTo(droneId);
    assertThat(response.maxAltitudeMeters()).isEqualByComparingTo("6000");
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

  @Test
  void resubmitRejectedApplicationResetsStatusToPendingAndAudits() {
    DroneRegistrationApplication app = sampleApplication();
    app.reject(adminId, "Blurry inspection scan");
    when(applications.findById(applicationId)).thenReturn(Optional.of(app));
    when(applications.saveAndFlush(any(DroneRegistrationApplication.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    SubmitDroneRegistrationApplicationRequest request =
        new SubmitDroneRegistrationApplicationRequest(
            "SN-APP",
            "DJI Matrice 350 RTK [MTOW: 9.2 kg]",
            "DJI",
            "REG-APP-2",
            "reg-file",
            Instant.now().plusSeconds(172800),
            "owner-file",
            "insp-file-clear");

    var response =
        service.resubmit(inspectorId, applicationId, request, "127.0.0.1", "agent", "corr-2");
    assertThat(response.status()).isEqualTo("PENDING");
    assertThat(response.reviewNote()).isNull();
    assertThat(response.registrationNumber()).isEqualTo("REG-APP-2");
    verify(userAccess)
        .recordSecurityAudit(
            eq(inspectorId),
            eq(applicationId),
            eq("DRONE_REGISTRATION_RESUBMITTED"),
            eq("SUCCESS"),
            any(),
            any(),
            any());
  }

  @Test
  void resubmitWithCorrectedSerialNumberUpdatesSerialSuccessfully() {
    DroneRegistrationApplication app = sampleApplication();
    app.reject(adminId, "Wrong serial number in application");
    when(applications.findById(applicationId)).thenReturn(Optional.of(app));
    when(applications.saveAndFlush(any(DroneRegistrationApplication.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    when(drones.existsBySerialNumberIgnoreCase("SN-APP-CORRECTED")).thenReturn(false);
    when(applications.existsBySerialNumberIgnoreCaseAndStatusAndIdNot(
            "SN-APP-CORRECTED", "PENDING", applicationId))
        .thenReturn(false);

    SubmitDroneRegistrationApplicationRequest request =
        new SubmitDroneRegistrationApplicationRequest(
            "SN-APP-CORRECTED",
            "DJI Matrice 350 RTK",
            "DJI",
            "REG-APP-2",
            "reg-file",
            Instant.now().plusSeconds(172800),
            "owner-file",
            "insp-file");

    var response =
        service.resubmit(inspectorId, applicationId, request, "127.0.0.1", "agent", "corr-3");
    assertThat(response.status()).isEqualTo("PENDING");
    assertThat(response.serialNumber()).isEqualTo("SN-APP-CORRECTED");
  }

  @Test
  void resubmitRejectsDuplicateSerialOrChangingApprovedDroneSerial() {
    DroneRegistrationApplication app = sampleApplication();
    app.reject(adminId, "Typos");
    when(applications.findById(applicationId)).thenReturn(Optional.of(app));

    // Duplicate serial exists in drones
    when(drones.existsBySerialNumberIgnoreCase("SN-DUP")).thenReturn(true);
    SubmitDroneRegistrationApplicationRequest duplicateRequest =
        new SubmitDroneRegistrationApplicationRequest(
            "SN-DUP",
            "DJI M350",
            "DJI",
            "REG-APP",
            "reg-file",
            Instant.now().plusSeconds(172800),
            "owner-file",
            "insp-file");

    assertThatThrownBy(
            () ->
                service.resubmit(
                    inspectorId, applicationId, duplicateRequest, "127.0.0.1", "agent", "corr-4"))
        .isInstanceOf(AuthException.class)
        .hasMessageContaining("already registered");

    // Changing serial on an already approved drone (renewal) is rejected
    DroneRegistrationApplication approvedApp = sampleApplication();
    approvedApp.approve(adminId, droneId);
    when(applications.findById(applicationId)).thenReturn(Optional.of(approvedApp));

    SubmitDroneRegistrationApplicationRequest changeSerialOnApproved =
        new SubmitDroneRegistrationApplicationRequest(
            "SN-CHANGED",
            "DJI M350",
            "DJI",
            "REG-APP",
            "reg-file",
            Instant.now().plusSeconds(172800),
            "owner-file",
            "insp-file");

    assertThatThrownBy(
            () ->
                service.resubmit(
                    inspectorId,
                    applicationId,
                    changeSerialOnApproved,
                    "127.0.0.1",
                    "agent",
                    "corr-5"))
        .isInstanceOf(AuthException.class)
        .hasMessageContaining("existing approved drone cannot be changed");
  }

  @Test
  void renewApplicationSyncsModelAndRejectsRetiredDrone() {
    Drone existingDrone =
        Drone.createInspectorOwned(
            inspectorId,
            "SN-APP",
            "M350",
            "DJI",
            "REG-OLD",
            "reg-old",
            Instant.now().plusSeconds(3600));
    ReflectionTestUtils.setField(existingDrone, "id", droneId);

    DroneRegistrationApplication app = sampleApplication();
    app.approve(adminId, droneId);

    when(applications.findById(applicationId)).thenReturn(Optional.of(app));
    when(drones.findById(droneId)).thenReturn(Optional.of(existingDrone));
    when(applications.saveAndFlush(any(DroneRegistrationApplication.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(drones.saveAndFlush(any(Drone.class))).thenAnswer(inv -> inv.getArgument(0));

    Instant renewedExpiry = Instant.now().plusSeconds(864000);
    SubmitDroneRegistrationApplicationRequest renewRequest =
        new SubmitDroneRegistrationApplicationRequest(
            "SN-APP",
            "DJI Matrice 350 RTK [MTOW: 9.2 kg]",
            "DJI Enterprise",
            "REG-RENEWED",
            "reg-renewed",
            renewedExpiry,
            "owner-file",
            "insp-file");

    service.resubmit(inspectorId, applicationId, renewRequest, "127.0.0.1", "agent", "corr-6");
    when(applications.findByIdAndStatus(applicationId, "PENDING")).thenReturn(Optional.of(app));
    service.approve(adminId, applicationId, "127.0.0.1", "agent", "corr-7");

    assertThat(existingDrone.getModel()).isEqualTo("DJI Matrice 350 RTK [MTOW: 9.2 kg]");
    assertThat(existingDrone.getManufacturer()).isEqualTo("DJI Enterprise");
    assertThat(existingDrone.getRegistrationNumber()).isEqualTo("REG-RENEWED");

    existingDrone.changeServiceability(
        com.smartdroneinspection.assets.domain.enums.DroneServiceability.RETIRED);
    assertThatThrownBy(
            () ->
                service.resubmit(
                    inspectorId, applicationId, renewRequest, "127.0.0.1", "agent", "corr-8"))
        .isInstanceOf(AuthException.class)
        .hasMessageContaining("Retired drones cannot renew registration");
  }

  @Test
  void submitAndApprovePersistStructuredSpecsSeparatelyFromCleanModel() {
    when(applications.existsBySerialNumberIgnoreCaseAndStatus("SN-CLEAN", "PENDING"))
        .thenReturn(false);
    when(drones.findBySerialNumberIgnoreCase("SN-CLEAN")).thenReturn(Optional.empty());
    when(drones.existsBySerialNumberIgnoreCase("SN-CLEAN")).thenReturn(false);
    when(applications.saveAndFlush(any(DroneRegistrationApplication.class)))
        .thenAnswer(
            inv -> {
              DroneRegistrationApplication saved = inv.getArgument(0);
              ReflectionTestUtils.setField(saved, "id", applicationId);
              return saved;
            });
    when(drones.saveAndFlush(any(Drone.class)))
        .thenAnswer(
            inv -> {
              Drone saved = inv.getArgument(0);
              ReflectionTestUtils.setField(saved, "id", droneId);
              return saved;
            });

    SubmitDroneRegistrationApplicationRequest request =
        new SubmitDroneRegistrationApplicationRequest(
            "SN-CLEAN",
            "DJI Matrice 350 RTK",
            "DJI",
            "REG-CLEAN",
            "reg-file",
            Instant.now().plusSeconds(86400),
            "owner-file",
            null,
            new java.math.BigDecimal("12.0"),
            new java.math.BigDecimal("120.0"),
            "1x",
            false,
            false,
            null,
            List.of("drone-img-key"),
            List.of("RTK_POSITIONING"),
            new java.math.BigDecimal("6.47"),
            55,
            "20 MP");

    var submitted = service.submit(inspectorId, request, "127.0.0.1", "agent", "corr-clean-1");
    assertThat(submitted.model()).isEqualTo("DJI Matrice 350 RTK");
    assertThat(submitted.mtowKg()).isEqualByComparingTo("6.47");
    assertThat(submitted.maxFlightTimeMinutes()).isEqualTo(55);
    assertThat(submitted.cameraSensor()).isEqualTo("20 MP");
    assertThat(submitted.legalCategory()).isEqualTo("CATEGORY_3");

    DroneRegistrationApplication storedApp =
        new DroneRegistrationApplication(
            inspectorId,
            "SN-CLEAN",
            "DJI Matrice 350 RTK",
            "DJI",
            "REG-CLEAN",
            "reg-file",
            Instant.now().plusSeconds(86400),
            "owner-file",
            null,
            null,
            new java.math.BigDecimal("12.0"),
            new java.math.BigDecimal("120.0"),
            "1x",
            false,
            false,
            List.of("drone-img-key"),
            List.of("RTK_POSITIONING"));
    storedApp.applyStructuredSpecs(new java.math.BigDecimal("6.47"), 55, "20 MP");
    ReflectionTestUtils.setField(storedApp, "id", applicationId);

    when(applications.findByIdAndStatus(applicationId, "PENDING"))
        .thenReturn(Optional.of(storedApp));

    var approved = service.approve(adminId, applicationId, "127.0.0.1", "agent", "corr-clean-2");
    assertThat(approved.droneId()).isEqualTo(droneId);
    assertThat(approved.mtowKg()).isEqualByComparingTo("6.47");
    assertThat(approved.maxFlightTimeMinutes()).isEqualTo(55);
    assertThat(approved.cameraSensor()).isEqualTo("20 MP");

    var droneCaptor = org.mockito.ArgumentCaptor.forClass(Drone.class);
    verify(drones).saveAndFlush(droneCaptor.capture());
    Drone persistedDrone = droneCaptor.getValue();
    assertThat(persistedDrone.getModel()).isEqualTo("DJI Matrice 350 RTK");
    assertThat(persistedDrone.getMtowKg()).isEqualByComparingTo("6.47");
    assertThat(persistedDrone.getMaxFlightTimeMinutes()).isEqualTo(55);
    assertThat(persistedDrone.getCameraSensor()).isEqualTo("20 MP");
    assertThat(persistedDrone.resolveLegalCategory()).isEqualTo("CATEGORY_3");
  }

  @Test
  void listForReviewPaginatesFiltersByStatusTierSearchAndComputesCounts() {
    DroneRegistrationApplication heavyPending =
        new DroneRegistrationApplication(
            inspectorId,
            "SN-HVY",
            "Matrice 350 RTK",
            "DJI",
            "REG-HVY",
            "reg-file",
            Instant.now().plusSeconds(86400),
            "owner-file",
            null);
    heavyPending.applyStructuredSpecs(new java.math.BigDecimal("6.47"), 55, "20 MP");
    ReflectionTestUtils.setField(heavyPending, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(heavyPending, "createdAt", Instant.parse("2026-10-02T00:00:00Z"));

    DroneRegistrationApplication lightApproved =
        new DroneRegistrationApplication(
            inspectorId,
            "SN-LGT",
            "Mavic 3 Enterprise",
            "DJI",
            "REG-LGT",
            "reg-file",
            Instant.now().plusSeconds(86400),
            "owner-file",
            null);
    lightApproved.applyStructuredSpecs(new java.math.BigDecimal("0.92"), 45, "20 MP");
    lightApproved.approve(adminId, droneId);
    ReflectionTestUtils.setField(lightApproved, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(lightApproved, "createdAt", Instant.parse("2026-10-01T00:00:00Z"));

    DroneRegistrationApplication rejectedApp =
        new DroneRegistrationApplication(
            inspectorId,
            "SN-REJ",
            "Mini 4 Pro",
            "DJI",
            "REG-REJ",
            "reg-file",
            Instant.now().plusSeconds(86400),
            "owner-file",
            null);
    rejectedApp.applyStructuredSpecs(new java.math.BigDecimal("0.249"), 34, "48 MP");
    rejectedApp.reject(adminId, "Blurry scan");
    ReflectionTestUtils.setField(rejectedApp, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(rejectedApp, "createdAt", Instant.parse("2026-10-03T00:00:00Z"));

    when(applications.findAll()).thenReturn(List.of(heavyPending, lightApproved, rejectedApp));
    when(userAccess.findUser(inspectorId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(
                    inspectorId,
                    Set.of(Roles.INSPECTOR),
                    "Pilot",
                    "pilot@test.local",
                    "LIC-123",
                    "CLASS_A",
                    Instant.now().plusSeconds(86400),
                    "file-key")));
    when(userAccess.findUser(adminId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(
                    adminId,
                    Set.of(Roles.ADMIN),
                    "Admin",
                    "admin@test.local",
                    null,
                    null,
                    null,
                    null)));

    var page1 = service.listForReview(adminId, 1, 2, "ALL", "ALL", null, "NEWEST");
    assertThat(page1.totalAllCount()).isEqualTo(3);
    assertThat(page1.pendingCount()).isEqualTo(1);
    assertThat(page1.approvedCount()).isEqualTo(1);
    assertThat(page1.rejectedCount()).isEqualTo(1);
    assertThat(page1.totalCount()).isEqualTo(3);
    assertThat(page1.totalPages()).isEqualTo(2);
    assertThat(page1.items()).hasSize(2);
    assertThat(page1.items().get(0).serialNumber()).isEqualTo("SN-REJ");

    var mismatch =
        service.listForReview(adminId, 1, 10, "PENDING", "LICENSE_MISMATCH", "350", "NEWEST");
    assertThat(mismatch.totalCount()).isEqualTo(1);
    assertThat(mismatch.items())
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.serialNumber()).isEqualTo("SN-HVY");
              assertThat(item.inspectorPilotLicenseClass()).isEqualTo("CLASS_A");
            });
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
            "insp-file",
            null,
            null,
            null,
            false,
            false,
            List.of("drone-img-key"));
    ReflectionTestUtils.setField(app, "id", applicationId);
    return app;
  }
}
