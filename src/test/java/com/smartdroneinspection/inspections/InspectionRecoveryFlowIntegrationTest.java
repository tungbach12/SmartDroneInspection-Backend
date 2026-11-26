package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.AssetTestFixture;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.inspections.domain.FieldSession;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.InspectorReliabilityEvent.EventType;
import com.smartdroneinspection.inspections.repository.FieldSessionRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.repository.InspectorReliabilityEventRepository;
import com.smartdroneinspection.inspections.service.InspectionLifecycleMonitor;
import com.smartdroneinspection.users.domain.InspectorProfile;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.InspectorProfileRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class InspectionRecoveryFlowIntegrationTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired UserRepository users;
  @Autowired InspectorProfileRepository profiles;
  @Autowired AssetCategoryRepository categories;
  @Autowired AssetRepository assets;
  @Autowired DroneRepository drones;
  @Autowired InspectionRepository inspections;
  @Autowired FieldSessionRepository sessions;
  @Autowired InspectorReliabilityEventRepository reliabilityEvents;
  @Autowired InspectionLifecycleMonitor lifecycleMonitor;
  @Autowired JdbcTemplate jdbcTemplate;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private MockMvc mockMvc;

  private UUID customerId;
  private UUID managerId;
  private UUID inspector1Id;
  private UUID inspector2Id;
  private UUID assetId;
  private UUID drone1Id;
  private UUID drone2Id;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();

    AssetTestFixture.Data fixture = new AssetTestFixture(categories, assets, users).create();
    customerId = fixture.clientId();
    managerId = createUser(UserRole.MANAGER, "recovery-mgr");
    inspector1Id = createPartnerInspector("recovery-insp1", "CLASS_B");
    inspector2Id = createPartnerInspector("recovery-insp2", "CLASS_B");

    assetId =
        assets
            .saveAndFlush(
                new Asset(
                    customerId,
                    fixture.categoryId(),
                    "AST-REC-" + UUID.randomUUID(),
                    "Recovery Bridge",
                    "Bridge",
                    "{\"label\":\"District 2\"}",
                    null,
                    null,
                    null,
                    customerId))
            .getId();
    drone1Id = createPartnerDrone(inspector1Id, "DRN-1", new BigDecimal("1.5"));
    drone2Id = createPartnerDrone(inspector2Id, "DRN-2", new BigDecimal("1.5"));
  }

  @Test
  void inspectorDeclineReleasesConflictSlotAndBlocksStartUntilReassigned() throws Exception {
    Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
    Instant end = start.plus(3, ChronoUnit.HOURS);
    UUID inspectionId = createInspection(inspector1Id, drone1Id, start, end, true);

    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/decline", inspectionId)
                .with(principal(inspector1Id, "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("reason", "Equipment maintenance"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.reassignmentRequired").value(true))
        .andExpect(jsonPath("$.data.declineReason").value("Equipment maintenance"));

    assertThat(
            reliabilityEvents.existsByInspectionIdAndInspectorIdAndEventType(
                inspectionId, inspector1Id, EventType.DECLINE))
        .isTrue();

    // Schedule slot for inspector1 is now freed
    assertThat(inspections.hasPlatformPartnerScheduleConflict(inspector1Id, drone1Id, start, end))
        .isFalse();

    // Declined inspector cannot start session
    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/field-sessions", inspectionId)
                .with(principal(inspector1Id, "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("preFlightChecklistNote", "Check"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("REASSIGNMENT_REQUIRED"));

    // Evaluate reassign candidates includes declineCount=1 for inspector1
    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/reassign-candidates", inspectionId)
                .with(principal(managerId, "MANAGER")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.data[?(@.inspectorId=='" + inspector1Id + "')].declineCount")
                .value(org.hamcrest.Matchers.hasItem(1)));

    // Manager reassigns to inspector2 -> clears reassignmentRequired and resets acceptance gate
    mockMvc
        .perform(
            put("/api/v1/inspections/{id}/assignment", inspectionId)
                .with(principal(managerId, "MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "inspectorId",
                            inspector2Id.toString(),
                            "droneId",
                            drone2Id.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.inspectorId").value(inspector2Id.toString()))
        .andExpect(jsonPath("$.data.reassignmentRequired").value(false))
        .andExpect(jsonPath("$.data.assignmentStatus").value("PENDING_ACCEPTANCE"));

    // New inspector cannot start or upload permit until accepting the assignment
    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/field-sessions", inspectionId)
                .with(principal(inspector2Id, "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("preFlightChecklistNote", "Check"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ASSIGNMENT_NOT_ACCEPTED"));

    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/accept", inspectionId)
                .with(principal(inspector2Id, "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.assignmentStatus").value("ACCEPTED"))
        .andExpect(jsonPath("$.data.assignmentAcceptedAt").isNotEmpty());
  }

  @Test
  void managerCancellationClosesWithdrawnAssignmentWithoutErasingReasons() throws Exception {
    Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
    UUID inspectionId =
        createInspection(inspector1Id, drone1Id, start, start.plusSeconds(3600), true);

    mockMvc
        .perform(
            get("/api/v1/inspections/{id}/workflow-state", inspectionId)
                .with(principal(inspector1Id, "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.canWithdrawAssignment").value(true));

    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/decline", inspectionId)
                .with(principal(inspector1Id, "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", "Permit unavailable"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
        .andExpect(jsonPath("$.data.reassignmentRequired").value(true));

    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/cancellation", inspectionId)
                .with(principal(managerId, "MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", "Customer cancelled"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CANCELLED"))
        .andExpect(jsonPath("$.data.reassignmentRequired").value(false))
        .andExpect(jsonPath("$.data.declinedByInspectorId").value(inspector1Id.toString()))
        .andExpect(jsonPath("$.data.declineReason").value("Permit unavailable"))
        .andExpect(jsonPath("$.data.cancellationReason").value("Customer cancelled"));
  }

  @Test
  void inspectorCannotWithdrawAfterStartedSessionWasAborted() throws Exception {
    Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
    UUID inspectionId =
        createInspection(inspector1Id, drone1Id, start, start.plusSeconds(3600), true);
    FieldSession session = new FieldSession(inspectionId, FieldSession.IN_PROGRESS, Instant.now());
    session.abort("Weather", inspector1Id);
    sessions.saveAndFlush(session);

    mockMvc
        .perform(
            get("/api/v1/inspections/{id}/workflow-state", inspectionId)
                .with(principal(inspector1Id, "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.canWithdrawAssignment").value(false));

    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/decline", inspectionId)
                .with(principal(inspector1Id, "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", "Permit unavailable"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ASSIGNMENT_FIELD_WORK_STARTED"));
  }

  @Test
  void startRejectsExpiredWindowAndInsufficientLicenseClass() throws Exception {
    Instant pastStart = Instant.now().minus(5, ChronoUnit.HOURS);
    Instant pastEnd = Instant.now().minus(2, ChronoUnit.HOURS);
    UUID expiredInspectionId = createInspection(inspector1Id, drone1Id, pastStart, pastEnd, true);

    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/field-sessions", expiredInspectionId)
                .with(principal(inspector1Id, "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("preFlightChecklistNote", "Check"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SCHEDULE_WINDOW_EXPIRED"));

    UUID classAInspector = createPartnerInspector("recovery-class-a", "CLASS_A");
    UUID heavyDroneId = createPartnerDrone(classAInspector, "DRN-HEAVY", new BigDecimal("3.5"));
    Instant futureStart = Instant.now().plus(1, ChronoUnit.DAYS);
    UUID heavyInspectionId =
        createInspection(
            classAInspector,
            heavyDroneId,
            futureStart,
            futureStart.plus(2, ChronoUnit.HOURS),
            true);

    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/field-sessions", heavyInspectionId)
                .with(principal(classAInspector, "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("preFlightChecklistNote", "Check"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PILOT_LICENSE_CLASS_INSUFFICIENT"));
  }

  @Test
  void managerForceAbortsHungSessionAndMonitorRecordsOverdueAndHungOnce() throws Exception {
    Instant overdueStart = Instant.now().minus(4, ChronoUnit.HOURS);
    Instant overdueEnd = Instant.now().minus(2, ChronoUnit.HOURS);
    UUID overdueInspectionId =
        createInspection(inspector1Id, drone1Id, overdueStart, overdueEnd, true);

    // Run lifecycle monitor: detects overdue scheduled inspection once
    lifecycleMonitor.scanAndNotify();
    lifecycleMonitor.scanAndNotify();

    Inspection reloadedOverdue = inspections.findById(overdueInspectionId).orElseThrow();
    assertThat(reloadedOverdue.getOverdueNotifiedAt()).isNotNull();
    assertThat(
            reliabilityEvents.existsByInspectionIdAndInspectorIdAndEventType(
                overdueInspectionId, inspector1Id, EventType.NO_SHOW))
        .isTrue();

    // Active hung session
    Instant futureStart = Instant.now().plus(1, ChronoUnit.DAYS);
    UUID hungInspectionId =
        createInspection(
            inspector2Id, drone2Id, futureStart, futureStart.plus(2, ChronoUnit.HOURS), true);
    String startedBody =
        mockMvc
            .perform(
                post("/api/v1/inspections/{id}/field-sessions", hungInspectionId)
                    .with(principal(inspector2Id, "INSPECTOR"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(Map.of("preFlightChecklistNote", "Ready"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID sessionId = UUID.fromString(objectMapper.readTree(startedBody).at("/data/id").asText());

    // Push planned_end_at into the past beyond hung threshold (4h)
    jdbcTemplate.update(
        "UPDATE inspections SET planned_start_at = ?, planned_end_at = ? WHERE id = ?",
        java.sql.Timestamp.from(Instant.now().minus(10, ChronoUnit.HOURS)),
        java.sql.Timestamp.from(Instant.now().minus(6, ChronoUnit.HOURS)),
        hungInspectionId);

    lifecycleMonitor.scanAndNotify();
    FieldSession hungSession = sessions.findById(sessionId).orElseThrow();
    assertThat(hungSession.getHungNotifiedAt()).isNotNull();

    // Customer, Admin, and Inspector cannot force-abort.
    for (String role : new String[] {"CUSTOMER", "ADMIN", "INSPECTOR"}) {
      UUID actorId =
          switch (role) {
            case "CUSTOMER" -> customerId;
            case "ADMIN" -> createUser(UserRole.ADMIN, "recovery-admin");
            default -> inspector2Id;
          };
      mockMvc
          .perform(
              post(
                      "/api/v1/inspections/{id}/field-sessions/{sid}/force-abort",
                      hungInspectionId,
                      sessionId)
                  .with(principal(actorId, role))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(Map.of("reason", "Hung too long"))))
          .andExpect(status().isForbidden());
    }

    // Manager force-aborts -> returns inspection to SCHEDULED
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/field-sessions/{sid}/force-abort",
                    hungInspectionId,
                    sessionId)
                .with(principal(managerId, "MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", "Hung too long"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ABORTED"));

    assertThat(inspections.findById(hungInspectionId).orElseThrow().getStatus().name())
        .isEqualTo("SCHEDULED");
    assertThat(
            reliabilityEvents.existsByInspectionIdAndInspectorIdAndEventType(
                hungInspectionId, inspector2Id, EventType.FORCE_ABORT))
        .isTrue();
  }

  @Test
  void rescheduleAndReassignRejectScheduleConflictsAndCancelSavesReason() throws Exception {
    Instant slotAStart = Instant.now().plus(3, ChronoUnit.DAYS);
    Instant slotAEnd = slotAStart.plus(3, ChronoUnit.HOURS);
    Instant slotBStart = Instant.now().plus(5, ChronoUnit.DAYS);
    Instant slotBEnd = slotBStart.plus(3, ChronoUnit.HOURS);

    UUID inspA = createInspection(inspector1Id, drone1Id, slotAStart, slotAEnd, true);
    UUID inspB = createInspection(inspector2Id, drone2Id, slotBStart, slotBEnd, true);

    // Reassign inspB to inspector1 in slotB -> ok, then reschedule inspB into slotA -> conflict!
    mockMvc
        .perform(
            put("/api/v1/inspections/{id}/schedule", inspA)
                .with(principal(managerId, "MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "plannedStartAt",
                            slotAStart.plus(1, ChronoUnit.HOURS).toString(),
                            "plannedEndAt",
                            slotAEnd.plus(1, ChronoUnit.HOURS).toString()))))
        .andExpect(status().isOk());

    // Reassign inspB to inspector1 during slotB works, then try overlapping slotA
    UUID inspC = createInspection(inspector1Id, drone1Id, slotBStart, slotBEnd, true);
    mockMvc
        .perform(
            put("/api/v1/inspections/{id}/schedule", inspC)
                .with(principal(managerId, "MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "plannedStartAt",
                            slotAStart.plus(1, ChronoUnit.HOURS).toString(),
                            "plannedEndAt",
                            slotAEnd.plus(1, ChronoUnit.HOURS).toString()))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SCHEDULE_CONFLICT"));

    mockMvc
        .perform(
            put("/api/v1/inspections/{id}/assignment", inspB)
                .with(principal(managerId, "MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "inspectorId",
                            inspector1Id.toString(),
                            "droneId",
                            drone1Id.toString()))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SCHEDULE_CONFLICT"));

    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/cancellation", inspC)
                .with(principal(managerId, "MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", "Customer postponed"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CANCELLED"))
        .andExpect(jsonPath("$.data.cancellationReason").value("Customer postponed"));
  }

  private UUID createUser(UserRole role, String label) {
    User user =
        new User(label + "-" + UUID.randomUUID() + "@test.org", label, null, UserStatus.ACTIVE);
    user.addRole(role);
    return users.saveAndFlush(user).getId();
  }

  private UUID createPartnerInspector(String label, String licenseClass) {
    UUID id = createUser(UserRole.INSPECTOR, label);
    InspectorProfile profile = new InspectorProfile(id);
    profile.updatePilotLicense(
        "LIC-" + UUID.randomUUID().toString().substring(0, 6),
        licenseClass,
        Instant.now().plus(180, ChronoUnit.DAYS),
        "licenses/test.pdf");
    profiles.saveAndFlush(profile);
    return id;
  }

  private UUID createPartnerDrone(UUID ownerInspectorId, String serialPrefix, BigDecimal mtowKg) {
    String model =
        mtowKg.compareTo(new BigDecimal("2.0")) >= 0 ? "Matrice 350 RTK" : "Mavic 3 Enterprise";
    Drone drone =
        Drone.createInspectorOwned(
            ownerInspectorId,
            serialPrefix + "-" + UUID.randomUUID(),
            model,
            "DJI",
            "REG-" + serialPrefix,
            "registrations/test.pdf",
            Instant.now().plus(180, ChronoUnit.DAYS));
    return drones.saveAndFlush(drone).getId();
  }

  private UUID createInspection(
      UUID inspectorId, UUID droneId, Instant start, Instant end, boolean partner) {
    Inspection inspection = new Inspection(customerId, assetId, inspectorId, droneId, start, end);
    if (partner) {
      inspection.markPartnerAssignment();
    }
    inspection.recordFlightPermit("permits/permit.pdf", "permit.pdf", Instant.now());
    return inspections.saveAndFlush(inspection).getId();
  }

  private static RequestPostProcessor principal(UUID subjectId, String role) {
    return jwt()
        .jwt(token -> token.subject(subjectId.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_" + role));
  }
}
