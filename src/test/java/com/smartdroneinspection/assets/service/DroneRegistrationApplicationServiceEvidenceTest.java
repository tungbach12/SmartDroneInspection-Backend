package com.smartdroneinspection.assets.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.DroneRegistrationApplication;
import com.smartdroneinspection.assets.domain.enums.DroneServiceability;
import com.smartdroneinspection.assets.repository.DroneDocumentConfigRepository;
import com.smartdroneinspection.assets.repository.DroneRegistrationApplicationRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.AuthException;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.UserAccess;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DroneRegistrationApplicationServiceEvidenceTest {

  private static final UUID INSPECTOR_ID = UUID.randomUUID();
  private static final UUID OTHER_INSPECTOR_ID = UUID.randomUUID();
  private static final UUID ADMIN_ID = UUID.randomUUID();
  private static final UUID APPLICATION_ID = UUID.randomUUID();
  private static final UUID DRONE_ID = UUID.randomUUID();

  private DroneRegistrationApplicationRepository applications;
  private DroneRepository drones;
  private UserAccess userAccess;
  private EvidenceObjectStore objectStore;
  private DroneRegistrationApplicationService service;

  @BeforeEach
  void setUp() {
    applications = mock(DroneRegistrationApplicationRepository.class);
    drones = mock(DroneRepository.class);
    userAccess = mock(UserAccess.class);
    objectStore = mock(EvidenceObjectStore.class);
    service =
        new DroneRegistrationApplicationService(
            applications,
            drones,
            userAccess,
            Optional.of(objectStore),
            mock(DroneEvidenceUploadService.class),
            mock(DroneDocumentConfigRepository.class));

    when(userAccess.findActiveUser(INSPECTOR_ID))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(
                    INSPECTOR_ID,
                    Set.of(Roles.INSPECTOR),
                    "Pilot",
                    "pilot@test.local",
                    "LIC-1",
                    "CLASS_A",
                    Instant.now().plusSeconds(86400),
                    "license.pdf")));
    when(userAccess.findActiveUser(ADMIN_ID))
        .thenReturn(Optional.of(new UserAccess.ActiveUser(ADMIN_ID, Set.of(Roles.ADMIN), "Admin")));
    when(userAccess.findActiveUser(OTHER_INSPECTOR_ID))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(
                    OTHER_INSPECTOR_ID,
                    Set.of(Roles.INSPECTOR),
                    "Other Pilot",
                    "other@test.local",
                    "LIC-2",
                    "CLASS_A",
                    Instant.now().plusSeconds(86400),
                    "license-2.pdf")));
    when(userAccess.findUser(INSPECTOR_ID))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(
                    INSPECTOR_ID,
                    Set.of(Roles.INSPECTOR),
                    "Pilot",
                    "pilot@test.local",
                    "LIC-1",
                    "CLASS_A",
                    Instant.now().plusSeconds(86400),
                    "license.pdf")));
    when(userAccess.findUser(ADMIN_ID))
        .thenReturn(Optional.of(new UserAccess.ActiveUser(ADMIN_ID, Set.of(Roles.ADMIN), "Admin")));
  }

  @Test
  void ownerCanOpenRegistrationAndImageEvidence() throws Exception {
    DroneRegistrationApplication application = application();
    String imageKey = evidenceKey("DRONE_IMAGE");
    application.getImageFileKeys().add(imageKey);
    when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
    when(objectStore.open(application.getRegistrationFileKey()))
        .thenReturn(new ByteArrayInputStream(new byte[] {1}));
    when(objectStore.open(imageKey)).thenReturn(new ByteArrayInputStream(new byte[] {2}));

    var registration = service.openEvidence(INSPECTOR_ID, APPLICATION_ID, "REGISTRATION");
    var image = service.openEvidence(INSPECTOR_ID, APPLICATION_ID, "DRONE_IMAGE_0");

    assertThat(registration.contentType()).isEqualTo("application/pdf");
    assertThat(registration.stream().readAllBytes()).containsExactly(1);
    assertThat(image.stream().readAllBytes()).containsExactly(2);
  }

  @Test
  void reviewerCanOpenEvidenceAndUnknownTypeIsNotFound() throws Exception {
    DroneRegistrationApplication application = application();
    application.getImageFileKeys().add(evidenceKey("DRONE_IMAGE"));
    when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
    when(objectStore.open(application.getRegistrationFileKey()))
        .thenReturn(new ByteArrayInputStream(new byte[] {1}));

    assertThat(service.openEvidence(ADMIN_ID, APPLICATION_ID, "REGISTRATION").contentType())
        .isEqualTo("application/pdf");
    assertBusinessCode(
        () -> service.openEvidence(INSPECTOR_ID, APPLICATION_ID, "UNKNOWN"), "NOT_FOUND");
  }

  @Test
  void openEvidenceRejectsForeignCallerAndMissingApplication() {
    DroneRegistrationApplication application = application();
    when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
    UUID outsider = UUID.randomUUID();
    when(userAccess.findActiveUser(outsider)).thenReturn(Optional.empty());

    assertAuthCode(
        () -> service.openEvidence(outsider, APPLICATION_ID, "REGISTRATION"), "REVIEWER_REQUIRED");
    when(applications.findById(APPLICATION_ID)).thenReturn(Optional.empty());
    assertBusinessCode(
        () -> service.openEvidence(INSPECTOR_ID, APPLICATION_ID, "REGISTRATION"),
        "APPLICATION_NOT_FOUND");
  }

  @Test
  void missingStorageIsReportedForEvidence() {
    DroneRegistrationApplication application = application();
    when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
    service =
        new DroneRegistrationApplicationService(
            applications,
            drones,
            userAccess,
            Optional.empty(),
            mock(DroneEvidenceUploadService.class),
            mock(DroneDocumentConfigRepository.class));

    assertBusinessCode(
        () -> service.openEvidence(INSPECTOR_ID, APPLICATION_ID, "REGISTRATION"),
        "STORAGE_UNAVAILABLE");
  }

  @Test
  void opensDroneImagesForOwnerReviewerAndValidatesIndex() throws Exception {
    Drone drone = drone();
    when(drones.findById(DRONE_ID)).thenReturn(Optional.of(drone));
    String imageKey = evidenceKey("DRONE_IMAGE");
    ReflectionTestUtils.setField(drone, "imageFileKeys", List.of(imageKey));
    when(objectStore.open(imageKey)).thenReturn(new ByteArrayInputStream(new byte[] {3}));

    assertThat(service.openDroneImage(INSPECTOR_ID, DRONE_ID, 0).stream().readAllBytes())
        .containsExactly(3);
    assertBusinessCode(() -> service.openDroneImage(ADMIN_ID, DRONE_ID, 1), "NOT_FOUND");

    UUID outsider = UUID.randomUUID();
    when(userAccess.findActiveUser(outsider)).thenReturn(Optional.empty());
    assertAuthCode(() -> service.openDroneImage(outsider, DRONE_ID, 0), "REVIEWER_REQUIRED");
  }

  @Test
  void droneImageRejectsMissingDroneAndInvalidKey() {
    when(drones.findById(DRONE_ID)).thenReturn(Optional.empty());
    assertBusinessCode(() -> service.openDroneImage(INSPECTOR_ID, DRONE_ID, 0), "NOT_FOUND");

    Drone drone = drone();
    ReflectionTestUtils.setField(drone, "imageFileKeys", List.of("not-an-evidence-key"));
    when(drones.findById(DRONE_ID)).thenReturn(Optional.of(drone));
    assertBusinessCode(() -> service.openDroneImage(INSPECTOR_ID, DRONE_ID, 0), "NOT_FOUND");
  }

  @Test
  void historyIncludesReviewerOfDecidedApplications() {
    DroneRegistrationApplication application = application();
    application.approve(ADMIN_ID, DRONE_ID);
    when(applications.findAllByStatusInOrderByReviewedAtDesc(List.of("APPROVED", "REJECTED")))
        .thenReturn(List.of(application));

    var history = service.history(ADMIN_ID);

    assertThat(history).hasSize(1);
    assertThat(history.getFirst().reviewerFullName()).isEqualTo("Admin");
  }

  @Test
  void ownerCanSoftDeleteDroneAndRetiredDroneIsIdempotent() {
    Drone drone = drone();
    when(drones.findById(DRONE_ID)).thenReturn(Optional.of(drone));

    service.deleteMyDrone(INSPECTOR_ID, DRONE_ID);
    assertThat(drone.getServiceability()).isEqualTo(DroneServiceability.RETIRED);
    verify(drones).saveAndFlush(drone);

    service.deleteMyDrone(INSPECTOR_ID, DRONE_ID);
    verify(drones, org.mockito.Mockito.times(1)).saveAndFlush(drone);
  }

  @Test
  void deleteDroneRejectsForeignOwnerAndMissingDrone() {
    Drone drone = drone();
    when(drones.findById(DRONE_ID)).thenReturn(Optional.of(drone));
    assertBusinessCode(() -> service.deleteMyDrone(OTHER_INSPECTOR_ID, DRONE_ID), "FORBIDDEN");

    when(drones.findById(DRONE_ID)).thenReturn(Optional.empty());
    assertBusinessCode(() -> service.deleteMyDrone(INSPECTOR_ID, DRONE_ID), "DRONE_NOT_FOUND");
  }

  @Test
  void ownerCanChangeServiceabilityButNotAfterRetirement() {
    Drone drone = drone();
    when(drones.findById(DRONE_ID)).thenReturn(Optional.of(drone));
    when(drones.saveAndFlush(drone)).thenReturn(drone);

    var response =
        service.updateMyDroneServiceability(
            INSPECTOR_ID, DRONE_ID, DroneServiceability.MAINTENANCE);

    assertThat(response.serviceability()).isEqualTo("MAINTENANCE");

    drone.changeServiceability(DroneServiceability.RETIRED);
    assertBusinessCode(
        () ->
            service.updateMyDroneServiceability(INSPECTOR_ID, DRONE_ID, DroneServiceability.ACTIVE),
        "INVALID_STATE");
  }

  @Test
  void serviceabilityRejectsForeignOwnerAndMissingDrone() {
    Drone drone = drone();
    when(drones.findById(DRONE_ID)).thenReturn(Optional.of(drone));
    assertBusinessCode(
        () ->
            service.updateMyDroneServiceability(
                OTHER_INSPECTOR_ID, DRONE_ID, DroneServiceability.ACTIVE),
        "FORBIDDEN");

    when(drones.findById(DRONE_ID)).thenReturn(Optional.empty());
    assertBusinessCode(
        () ->
            service.updateMyDroneServiceability(INSPECTOR_ID, DRONE_ID, DroneServiceability.ACTIVE),
        "DRONE_NOT_FOUND");
  }

  @Test
  void inspectorDroneListFiltersRetiredDrones() {
    Drone active = drone();
    Drone retired = drone();
    retired.changeServiceability(DroneServiceability.RETIRED);
    when(drones.findAllByInspectorOwnerId(INSPECTOR_ID)).thenReturn(List.of(active, retired));

    var result = service.inspectorDrones(INSPECTOR_ID);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().id()).isEqualTo(DRONE_ID);
  }

  @Test
  void rejectsInactiveInspectorsAndUnauthorizedReviewers() {
    UUID inactive = UUID.randomUUID();
    when(userAccess.findActiveUser(inactive)).thenReturn(Optional.empty());
    assertAuthCode(() -> service.mine(inactive), "INSPECTOR_PARTNER_REQUIRED");
    assertAuthCode(() -> service.pending(inactive), "REVIEWER_REQUIRED");

    UserAccess.ActiveUser customer =
        new UserAccess.ActiveUser(inactive, Set.of(Roles.CUSTOMER), "Customer");
    when(userAccess.findActiveUser(inactive)).thenReturn(Optional.of(customer));
    assertAuthCode(() -> service.mine(inactive), "INSPECTOR_PARTNER_REQUIRED");
    assertAuthCode(() -> service.pending(inactive), "REVIEWER_REQUIRED");
  }

  private static DroneRegistrationApplication application() {
    DroneRegistrationApplication application =
        new DroneRegistrationApplication(
            INSPECTOR_ID,
            "SN-APP",
            "M350",
            "DJI",
            "REG-APP",
            evidenceKey("REGISTRATION"),
            Instant.now().plusSeconds(86400),
            evidenceKey("OWNERSHIP"),
            evidenceKey("INSPECTION"),
            null,
            null,
            null,
            false,
            false,
            List.of());
    ReflectionTestUtils.setField(application, "id", APPLICATION_ID);
    return application;
  }

  private static Drone drone() {
    Drone drone =
        Drone.createInspectorOwned(
            INSPECTOR_ID,
            "SN-DRONE",
            "M350",
            "DJI",
            "REG-DRONE",
            evidenceKey("REGISTRATION"),
            Instant.now().plusSeconds(86400));
    ReflectionTestUtils.setField(drone, "id", DRONE_ID);
    return drone;
  }

  private static String evidenceKey(String type) {
    return "drone-registration-applications/"
        + APPLICATION_ID
        + "/evidence/"
        + type
        + "/"
        + UUID.randomUUID()
        + ".pdf";
  }

  private static void assertBusinessCode(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, String code) {
    assertThatThrownBy(callable)
        .isInstanceOfSatisfying(
            BusinessException.class, exception -> assertThat(exception.code()).isEqualTo(code));
  }

  private static void assertAuthCode(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, String code) {
    assertThatThrownBy(callable)
        .isInstanceOfSatisfying(
            AuthException.class, exception -> assertThat(exception.code()).isEqualTo(code));
  }
}
