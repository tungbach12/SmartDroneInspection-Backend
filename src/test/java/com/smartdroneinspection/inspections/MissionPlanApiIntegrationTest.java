package com.smartdroneinspection.inspections;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.domain.AssetCategory;
import com.smartdroneinspection.assets.domain.ChecklistTemplate;
import com.smartdroneinspection.assets.domain.enums.ChecklistResponseType;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.inspectionrequests.domain.InspectionQuotation;
import com.smartdroneinspection.inspectionrequests.domain.InspectionRequest;
import com.smartdroneinspection.inspectionrequests.domain.InspectionServiceOrder;
import com.smartdroneinspection.inspectionrequests.domain.enums.InspectionRequestPriority;
import com.smartdroneinspection.inspectionrequests.repository.InspectionQuotationRepository;
import com.smartdroneinspection.inspectionrequests.repository.InspectionRequestRepository;
import com.smartdroneinspection.inspectionrequests.repository.InspectionServiceOrderRepository;
import com.smartdroneinspection.inspections.domain.DroneMissionPlan;
import com.smartdroneinspection.inspections.repository.DroneMissionPlanRepository;
import com.smartdroneinspection.users.domain.Organization;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.OrganizationRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import java.math.BigDecimal;
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
class MissionPlanApiIntegrationTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired DroneMissionPlanRepository missionPlanRepository;
  @Autowired InspectionServiceOrderRepository serviceOrderRepository;
  @Autowired InspectionQuotationRepository quotationRepository;
  @Autowired InspectionRequestRepository requestRepository;
  @Autowired OrganizationRepository organizationRepository;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired UserRepository userRepository;
  @Autowired AssetCategoryRepository categoryRepository;
  @Autowired ChecklistTemplateRepository templateRepository;
  @Autowired AssetRepository assetRepository;

  private MockMvc mockMvc;
  private FixtureData fixture;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    fixture = createFixture();
  }

  @Test
  void fullMissionPlanningWorkflowCompletesThroughApproval() throws Exception {
    String createPayload = String.format("{\"serviceOrderId\":\"%s\"}", fixture.serviceOrderId);
    String createResponse =
        mockMvc
            .perform(
                post("/api/v1/mission-plans")
                    .with(manager(fixture.providerManagerId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createPayload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.versionNumber").value(1))
            .andReturn()
            .getResponse()
            .getContentAsString();

    UUID planId =
        UUID.fromString(
            tools.jackson.databind.json.JsonMapper.builder()
                .build()
                .readTree(createResponse)
                .get("data")
                .get("id")
                .asText());

    String sowPayload =
        """
        {
          "cameraModel": "DJI Zenmuse H20T",
          "droneRegistrationId": "VN-UAV-2026-88",
          "pilotUserId": "%s",
          "targetGsdMmPerPixel": 1.5,
          "plannedAglM": 50.0,
          "forwardOverlapPercent": 75.0,
          "sideOverlapPercent": 65.0,
          "sensorWidthMm": 6.4,
          "focalLengthMm": 24.0,
          "imageWidthPx": 4000
        }
        """
            .formatted(fixture.inspectorId);
    mockMvc
        .perform(
            put("/api/v1/mission-plans/" + planId + "/sow")
                .with(manager(fixture.providerManagerId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(sowPayload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.cameraModel").value("DJI Zenmuse H20T"))
        .andExpect(jsonPath("$.data.targetGsdMmPerPixel").value(1.5));

    String shotPayload =
        """
        {
          "sequenceNumber": 1,
          "componentReference": "Tip of blade A",
          "captureInstructions": "Manual flight, no waypoint"
        }
        """;
    mockMvc
        .perform(
            post("/api/v1/mission-plans/" + planId + "/shot-items")
                .with(manager(fixture.providerManagerId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(shotPayload))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.shotItems").isArray());

    String airspacePayload = "{ \"status\": \"BLOCKED\", \"notes\": \"training corridor\" }";
    mockMvc
        .perform(
            post("/api/v1/mission-plans/" + planId + "/airspace-check")
                .with(manager(fixture.providerManagerId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(airspacePayload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.airspaceCheckStatus").value("BLOCKED"));

    mockMvc
        .perform(
            post("/api/v1/mission-plans/" + planId + "/approve")
                .with(manager(fixture.providerManagerId)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("AIRSPACE_CLEARANCE_REQUIRED"));

    String permitPayload = "{ \"flightPermitReference\": \"PERMIT-TC-BTTM-2026-091\" }";
    mockMvc
        .perform(
            post("/api/v1/mission-plans/" + planId + "/verify-permit")
                .with(manager(fixture.providerManagerId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(permitPayload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.airspaceCheckStatus").value("CLEARED"))
        .andExpect(jsonPath("$.data.flightPermitReference").value("PERMIT-TC-BTTM-2026-091"));

    mockMvc
        .perform(
            post("/api/v1/mission-plans/" + planId + "/submit")
                .with(manager(fixture.providerManagerId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("SUBMITTED"));

    mockMvc
        .perform(
            post("/api/v1/mission-plans/" + planId + "/approve")
                .with(manager(fixture.providerManagerId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("APPROVED"))
        .andExpect(jsonPath("$.data.approvedByUserId").value(fixture.providerManagerId.toString()));

    // MF2-06: the parent order moves to READY_FOR_FLIGHT in the same transaction.
    com.smartdroneinspection.inspectionrequests.domain.InspectionServiceOrder order =
        serviceOrderRepository.findById(fixture.serviceOrderId).orElseThrow();
    org.assertj.core.api.Assertions.assertThat(order.getStatus())
        .isEqualTo(
            com.smartdroneinspection.inspectionrequests.domain.enums.InspectionOrderStatus
                .READY_FOR_FLIGHT);
  }

  @Test
  void crossProviderMissionAccessIsDenied_BR03_N6() throws Exception {
    String createPayload = String.format("{\"serviceOrderId\":\"%s\"}", fixture.serviceOrderId);
    mockMvc
        .perform(
            post("/api/v1/mission-plans")
                .with(manager(fixture.rivalManagerId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CROSS_PROVIDER_ACCESS_DENIED"));
  }

  @Test
  void clientFromOtherOrganizationCannotReadPlan() throws Exception {
    String createPayload = String.format("{\"serviceOrderId\":\"%s\"}", fixture.serviceOrderId);
    String createResponse =
        mockMvc
            .perform(
                post("/api/v1/mission-plans")
                    .with(manager(fixture.providerManagerId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createPayload))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID planId =
        UUID.fromString(
            tools.jackson.databind.json.JsonMapper.builder()
                .build()
                .readTree(createResponse)
                .get("data")
                .get("id")
                .asText());

    mockMvc
        .perform(get("/api/v1/mission-plans/" + planId).with(client(fixture.rivalClientId)))
        .andExpect(status().isForbidden());
  }

  @Test
  void approvingDuplicateApprovedPlanConflicts() throws Exception {
    // First plan approved via service; a second plan for the same order cannot also be APPROVED
    // without violating uq_drone_mission_plans_approved.
    DroneMissionPlan plan1 =
        missionPlanRepository.saveAndFlush(
            new DroneMissionPlan(
                fixture.serviceOrderId, fixture.providerId, 1, null, fixture.providerManagerId));
    plan1.setEquipmentAndSow(
        "DJI",
        "DRONE-1",
        fixture.inspectorId,
        new BigDecimal("1.5"),
        new BigDecimal("50"),
        new BigDecimal("75"),
        new BigDecimal("65"),
        new BigDecimal("6.4"),
        new BigDecimal("24"),
        4000);
    plan1.addShotItem(
        new com.smartdroneinspection.inspections.domain.MissionShotItem(
            1, "blade", null, null, new BigDecimal("50"), null, null, null, null));
    plan1.recordAirspacePreCheck(
        com.smartdroneinspection.inspections.domain.enums.AirspaceCheckStatus.CLEARED);
    plan1.approve(fixture.providerManagerId);
    missionPlanRepository.saveAndFlush(plan1);

    DroneMissionPlan plan2 =
        missionPlanRepository.saveAndFlush(
            new DroneMissionPlan(
                fixture.serviceOrderId, fixture.providerId, 2, null, fixture.providerManagerId));
    plan2.setEquipmentAndSow(
        "DJI",
        "DRONE-1",
        fixture.inspectorId,
        new BigDecimal("1.5"),
        new BigDecimal("50"),
        new BigDecimal("75"),
        new BigDecimal("65"),
        new BigDecimal("6.4"),
        new BigDecimal("24"),
        4000);
    plan2.addShotItem(
        new com.smartdroneinspection.inspections.domain.MissionShotItem(
            1, "blade", null, null, new BigDecimal("50"), null, null, null, null));
    plan2.recordAirspacePreCheck(
        com.smartdroneinspection.inspections.domain.enums.AirspaceCheckStatus.CLEARED);
    plan2.submit();

    mockMvc
        .perform(
            post("/api/v1/mission-plans/" + plan2.getId() + "/approve")
                .with(manager(fixture.providerManagerId)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DUPLICATE_APPROVED_PLAN"));
  }

  @Test
  void inspectorCannotApprove_RoleForbidden() throws Exception {
    String createPayload = String.format("{\"serviceOrderId\":\"%s\"}", fixture.serviceOrderId);
    String createResponse =
        mockMvc
            .perform(
                post("/api/v1/mission-plans")
                    .with(manager(fixture.providerManagerId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createPayload))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID planId =
        UUID.fromString(
            tools.jackson.databind.json.JsonMapper.builder()
                .build()
                .readTree(createResponse)
                .get("data")
                .get("id")
                .asText());

    mockMvc
        .perform(
            post("/api/v1/mission-plans/" + planId + "/approve")
                .with(inspector(fixture.inspectorId)))
        .andExpect(status().isForbidden());
  }

  private RequestPostProcessor manager(UUID id) {
    return jwt()
        .jwt(token -> token.subject(id.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_PROVIDER_MANAGER"));
  }

  private RequestPostProcessor inspector(UUID id) {
    return jwt()
        .jwt(token -> token.subject(id.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_INSPECTOR"));
  }

  private RequestPostProcessor client(UUID id) {
    return jwt()
        .jwt(token -> token.subject(id.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_CLIENT"));
  }

  private FixtureData createFixture() {
    Organization clientOrg =
        organizationRepository.saveAndFlush(
            new Organization(
                "Client Org " + UUID.randomUUID(),
                "CLI-" + UUID.randomUUID().toString().substring(0, 8),
                "Client description"));

    Organization rivalOrg =
        organizationRepository.saveAndFlush(
            new Organization(
                "Rival Org " + UUID.randomUUID(),
                "RIV-" + UUID.randomUUID().toString().substring(0, 8),
                "Rival"));

    UUID providerId = insertProvider("Skyline " + UUID.randomUUID(), "p@skyline.test");
    UUID rivalProviderId = insertProvider("Rival " + UUID.randomUUID(), "p@rival.test");

    User manager =
        saveUser("mgr", ActorZone.SERVICE_WORKFORCE, null, providerId, UserRole.PROVIDER_MANAGER);
    User rivalManager =
        saveUser(
            "rival", ActorZone.SERVICE_WORKFORCE, null, rivalProviderId, UserRole.PROVIDER_MANAGER);
    User inspector =
        saveUser("insp", ActorZone.SERVICE_WORKFORCE, null, providerId, UserRole.INSPECTOR);
    User client =
        saveUser(
            "client", ActorZone.CUSTOMER_ORGANIZATION, clientOrg.getId(), null, UserRole.CLIENT);
    User rivalClient =
        saveUser(
            "rival-client",
            ActorZone.CUSTOMER_ORGANIZATION,
            rivalOrg.getId(),
            null,
            UserRole.CLIENT);

    AssetCategory category =
        categoryRepository.saveAndFlush(
            new AssetCategory("cat-" + UUID.randomUUID(), "Towers", "Telecommunication", true));
    ChecklistTemplate template =
        new ChecklistTemplate(
            "tmpl-" + UUID.randomUUID(),
            1,
            category.getId(),
            "Tower Checklist",
            "Desc",
            manager.getId());
    template.addItem(
        "item1", "Rust Check", "Check rust", ChecklistResponseType.PASS_FAIL, true, 0, null, null);
    template.publish();
    template = templateRepository.saveAndFlush(template);

    com.smartdroneinspection.assets.domain.Asset asset =
        assetRepository.saveAndFlush(
            new com.smartdroneinspection.assets.domain.Asset(
                clientOrg.getId(),
                category.getId(),
                "asset-" + UUID.randomUUID(),
                "Tower Alpha",
                "Main tower",
                "Hanoi",
                null,
                null,
                "Municipal asset",
                manager.getId()));

    InspectionRequest request =
        requestRepository.saveAndFlush(
            InspectionRequest.adHoc(
                clientOrg.getId(),
                asset.getId(),
                template.getId(),
                manager.getId(),
                "Tower inspect",
                InspectionRequestPriority.NORMAL,
                null,
                null,
                null,
                null,
                null,
                null));

    InspectionQuotation quotation =
        new InspectionQuotation(
            UUID.randomUUID(),
            request.getId(),
            1,
            null,
            manager.getId(),
            "USD",
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(10),
            BigDecimal.valueOf(110),
            "{\"items\":[]}",
            "{\"scope\":\"tower\"}",
            BigDecimal.valueOf(2),
            "NET 30");
    quotation.setProviderId(providerId);
    quotation.send();
    quotation.approve(manager.getId());
    quotation = quotationRepository.saveAndFlush(quotation);

    InspectionServiceOrder order =
        InspectionServiceOrder.fromApprovedQuotation(
            quotation,
            "SO-" + UUID.randomUUID(),
            manager.getId(),
            "{\"report\":\"Final inspection report\"}");
    order = serviceOrderRepository.saveAndFlush(order);

    return new FixtureData(
        order.getId(),
        providerId,
        manager.getId(),
        rivalManager.getId(),
        inspector.getId(),
        client.getId(),
        rivalClient.getId());
  }

  private User saveUser(
      String label, ActorZone zone, UUID organizationId, UUID providerId, UserRole role) {
    User user =
        new User(
            label + UUID.randomUUID() + "@test.com",
            label,
            "hashed",
            UserStatus.ACTIVE,
            zone,
            organizationId,
            providerId);
    user.addRole(role);
    return userRepository.saveAndFlush(user);
  }

  private UUID insertProvider(String name, String email) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO provider_organizations (id, name, legal_name, tax_code, business_license_no, status) VALUES (?,?,?,?,?, 'VERIFIED')",
        id,
        name,
        name + " Ltd",
        "TAX-" + UUID.randomUUID().toString().substring(0, 8),
        "BL-1");
    return id;
  }

  record FixtureData(
      UUID serviceOrderId,
      UUID providerId,
      UUID providerManagerId,
      UUID rivalManagerId,
      UUID inspectorId,
      UUID clientId,
      UUID rivalClientId) {}
}
