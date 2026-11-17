package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.AssetTestFixture;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.inspections.domain.Evidence;
import com.smartdroneinspection.inspections.domain.Finding;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.enums.DefectResolutionStatus;
import com.smartdroneinspection.inspections.domain.enums.FindingSeverity;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.domain.enums.InspectionType;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.repository.FindingRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.repository.ReinspectionDefectVerificationRepository;
import com.smartdroneinspection.inspections.spi.Mf2InspectionAssignment;
import com.smartdroneinspection.users.domain.InspectorProfile;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.InspectorProfileRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ReinspectionWorkflowIntegrationTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired UserRepository users;
  @Autowired InspectorProfileRepository profiles;
  @Autowired AssetCategoryRepository categories;
  @Autowired AssetRepository assets;
  @Autowired DroneRepository drones;
  @Autowired InspectionRepository inspections;
  @Autowired EvidenceRepository evidences;
  @Autowired FindingRepository findings;
  @Autowired ReinspectionDefectVerificationRepository verifications;
  @Autowired Mf2InspectionAssignment mf2Assignment;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private MockMvc mockMvc;

  private UUID customerId;
  private UUID managerId;
  private UUID inspectorId;
  private UUID assetId;
  private UUID droneId;
  private UUID sourceInspectionId;
  private UUID activeFinding1Id;
  private UUID activeFinding2Id;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();

    AssetTestFixture.Data fixture = new AssetTestFixture(categories, assets, users).create();
    customerId = fixture.clientId();
    managerId = createUser(UserRole.MANAGER, "reinsp-mgr");
    inspectorId = createPartnerInspector("reinsp-insp", "CLASS_B");

    assetId =
        assets
            .saveAndFlush(
                new Asset(
                    customerId,
                    fixture.categoryId(),
                    "AST-RI-" + UUID.randomUUID(),
                    "North Facade Tower",
                    "High-rise concrete tower",
                    "{\"label\":\"District 1\"}",
                    null,
                    null,
                    null,
                    customerId))
            .getId();
    droneId = createPartnerDrone(inspectorId, "DRN-RI");

    Instant start = Instant.now().minus(5, ChronoUnit.DAYS);
    Instant end = start.plus(2, ChronoUnit.HOURS);
    Inspection source = new Inspection(customerId, assetId, inspectorId, droneId, start, end);
    source.markPartnerAssignment();
    source.recordFlightPermit("permits/source.pdf", "source.pdf", Instant.now());
    source.approveFlightPermit(managerId, "Approved", Instant.now());
    source.moveTo(InspectionStatus.IN_PROGRESS);
    source.completeFieldSession();
    source.confirmFindings();
    source.markReportPublished();
    source = inspections.saveAndFlush(source);
    sourceInspectionId = source.getId();

    Evidence srcEv =
        evidences.saveAndFlush(
            new Evidence(
                sourceInspectionId,
                null,
                "before-facade.jpg",
                "image/jpeg",
                12345L,
                "inspections/before-facade.jpg",
                Instant.now().minus(5, ChronoUnit.DAYS),
                new BigDecimal("10.776889"),
                new BigDecimal("106.700806"),
                "East Facade 12F",
                Instant.now()));

    Finding f1 =
        new Finding(
            sourceInspectionId,
            srcEv.getId(),
            "F-1",
            "Concrete crack",
            FindingSeverity.HIGH,
            "Hairline diagonal crack at panel joint · Seal joint and re-verify",
            null,
            "manual",
            null);
    f1.confirmByInspector();
    activeFinding1Id = findings.saveAndFlush(f1).getId();

    Finding f2 =
        new Finding(
            sourceInspectionId,
            srcEv.getId(),
            "F-2",
            "Coating spalling",
            FindingSeverity.MEDIUM,
            "Localized waterproof peeling near roof parapet · Reapply waterproof membrane",
            null,
            "manual",
            null);
    f2.confirmByInspector();
    activeFinding2Id = findings.saveAndFlush(f2).getId();

    Finding deletedFinding =
        new Finding(
            sourceInspectionId,
            srcEv.getId(),
            "F-3",
            "False positive reflection",
            FindingSeverity.LOW,
            "Glare artifact",
            null,
            "yolo",
            new BigDecimal("0.42"));
    deletedFinding.delete();
    findings.saveAndFlush(deletedFinding);
  }

  @Test
  void quickReinspectionSeedsTargetDefectsEnforcesGuardsAndUpdatesBeforeAfterVerification()
      throws Exception {
    // Customer cannot override assigned partner crew directly
    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/reinspections", sourceInspectionId)
                .with(principal(customerId, "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "inspectorId", inspectorId.toString(), "droneId", droneId.toString()))))
        .andExpect(status().isForbidden());

    // Customer creates a quick re-inspection -> inherits partner crew & seeds 2 confirmed defects
    MvcResult createRes =
        mockMvc
            .perform(
                post("/api/v1/inspections/{id}/reinspections", sourceInspectionId)
                    .with(principal(customerId, "CUSTOMER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.inspectionType").value("RE_INSPECTION"))
            .andExpect(jsonPath("$.data.sourceInspectionId").value(sourceInspectionId.toString()))
            .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
            .andReturn();

    JsonNode createJson = objectMapper.readTree(createRes.getResponse().getContentAsString());
    UUID reinspectionId = UUID.fromString(createJson.get("data").get("id").asText());

    MvcResult verifRes =
        mockMvc
            .perform(
                get("/api/v1/inspections/{id}/reinspection-verifications", reinspectionId)
                    .with(principal(inspectorId, "INSPECTOR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].sourceFindingId").value(activeFinding1Id.toString()))
            .andExpect(jsonPath("$.data[0].resolutionStatus").value("UNRESOLVED"))
            .andExpect(jsonPath("$.data[1].sourceFindingId").value(activeFinding2Id.toString()))
            .andReturn();

    JsonNode verifList =
        objectMapper.readTree(verifRes.getResponse().getContentAsString()).get("data");
    UUID verif1Id = UUID.fromString(verifList.get(0).get("id").asText());
    UUID verif2Id = UUID.fromString(verifList.get(1).get("id").asText());

    // Updating verification before field session completes -> 409 CONFLICT
    mockMvc
        .perform(
            put(
                    "/api/v1/inspections/{id}/reinspection-verifications/{vid}",
                    reinspectionId,
                    verif1Id)
                .with(principal(inspectorId, "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "resolutionStatus",
                            "INCONCLUSIVE",
                            "inspectorNotes",
                            "Attempt before flight"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("FIELD_SESSION_NOT_COMPLETED"));

    // Advance re-inspection to FIELD_COMPLETED and upload fresh verification evidence
    Inspection reinspection = inspections.findById(reinspectionId).orElseThrow();
    reinspection.moveTo(InspectionStatus.IN_PROGRESS);
    reinspection.completeFieldSession();
    inspections.saveAndFlush(reinspection);

    Evidence afterEv =
        evidences.saveAndFlush(
            new Evidence(
                reinspectionId,
                null,
                "after-facade.jpg",
                "image/jpeg",
                22334L,
                "inspections/after-facade.jpg",
                Instant.now(),
                new BigDecimal("10.776891"),
                new BigDecimal("106.700809"),
                "East Facade 12F (Re-capture)",
                Instant.now()));

    // RESOLVED without fresh capture -> 400 BAD_REQUEST
    mockMvc
        .perform(
            put(
                    "/api/v1/inspections/{id}/reinspection-verifications/{vid}",
                    reinspectionId,
                    verif1Id)
                .with(principal(inspectorId, "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("resolutionStatus", "RESOLVED"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("EVIDENCE_REQUIRED_FOR_RESOLVED"));

    // Record RESOLVED on defect 1 with fresh capture & UNRESOLVED on defect 2 with notes
    mockMvc
        .perform(
            put(
                    "/api/v1/inspections/{id}/reinspection-verifications/{vid}",
                    reinspectionId,
                    verif1Id)
                .with(principal(inspectorId, "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "resolutionStatus",
                            "RESOLVED",
                            "newEvidenceId",
                            afterEv.getId().toString(),
                            "inspectorNotes",
                            "Sealed flush, no fissure remaining"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.resolutionStatus").value("RESOLVED"))
        .andExpect(jsonPath("$.data.newEvidenceId").value(afterEv.getId().toString()));

    mockMvc
        .perform(
            put(
                    "/api/v1/inspections/{id}/reinspection-verifications/{vid}",
                    reinspectionId,
                    verif2Id)
                .with(principal(inspectorId, "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "resolutionStatus",
                            "UNRESOLVED",
                            "inspectorNotes",
                            "Membrane peeling still visible at corner"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.resolutionStatus").value("UNRESOLVED"));

    Inspection refreshed = inspections.findById(reinspectionId).orElseThrow();
    assertThat(refreshed.hasFindingsConfirmation()).isTrue();
    assertThat(verifications.findByReinspectionIdOrderByCreatedAtAsc(reinspectionId))
        .extracting(v -> v.getResolutionStatus())
        .containsExactly(DefectResolutionStatus.RESOLVED, DefectResolutionStatus.UNRESOLVED);
  }

  @Test
  void mf2ServiceRequestWithSourceInspectionDispatchesReinspectionAndSeedsVerifications()
      throws Exception {
    LocalDate startDate = LocalDate.now().plusDays(25);
    mockMvc
        .perform(
            post("/api/v1/contracts/requests")
                .with(principal(customerId, "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "assetId",
                            assetId.toString(),
                            "description",
                            "Post-remediation verification flight via MF2 contract",
                            "requestedFrom",
                            startDate.toString(),
                            "timezone",
                            "Asia/Ho_Chi_Minh",
                            "requestType",
                            "ONE_TIME",
                            "sourceInspectionId",
                            sourceInspectionId.toString()))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.sourceInspectionId").value(sourceInspectionId.toString()));

    Instant windowStart = Instant.now().plus(25, ChronoUnit.DAYS);
    Instant windowEnd = windowStart.plus(2, ChronoUnit.HOURS);
    UUID dispatchedId =
        mf2Assignment.assign(
            customerId,
            assetId,
            inspectorId,
            droneId,
            windowStart,
            windowEnd,
            managerId,
            sourceInspectionId);

    Inspection dispatched = inspections.findById(dispatchedId).orElseThrow();
    assertThat(dispatched.getInspectionType()).isEqualTo(InspectionType.RE_INSPECTION);
    assertThat(dispatched.getSourceInspectionId()).isEqualTo(sourceInspectionId);
    assertThat(verifications.findByReinspectionIdOrderByCreatedAtAsc(dispatchedId)).hasSize(2);
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

  private UUID createPartnerDrone(UUID ownerInspectorId, String serialPrefix) {
    Drone drone =
        Drone.createInspectorOwned(
            ownerInspectorId,
            serialPrefix + "-" + UUID.randomUUID(),
            "Mavic 3 Enterprise",
            "DJI",
            "REG-" + serialPrefix,
            "registrations/test.pdf",
            Instant.now().plus(180, ChronoUnit.DAYS));
    return drones.saveAndFlush(drone).getId();
  }

  private static RequestPostProcessor principal(UUID subjectId, String role) {
    return jwt()
        .jwt(token -> token.subject(subjectId.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_" + role));
  }
}
