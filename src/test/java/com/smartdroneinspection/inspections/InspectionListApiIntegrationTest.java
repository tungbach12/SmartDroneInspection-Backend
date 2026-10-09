package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.inspections.domain.InspectionReport;
import com.smartdroneinspection.inspections.repository.InspectionReportRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.context.WebApplicationContext;

/**
 * The inspection collection: who may see which rows, and how paging behaves.
 *
 * <p>Role alone is never the answer. Each case pins a different scope axis — organization,
 * assignment, and the platform plane — so a regression that widens one is visible.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class InspectionListApiIntegrationTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired InspectionRepository inspections;
  @Autowired InspectionReportRepository reports;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbcTemplate;

  private InspectionTestFixture.Data fixture;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        webAppContextSetup(webApplicationContext)
            .apply(
                org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                    .springSecurity())
            .build();
    fixture = new InspectionTestFixture(users, inspections, jdbcTemplate).create();
  }

  @Test
  void anInspectorSeesOnlyInspectionsAssignedToThem() throws Exception {
    UUID unassigned = createInspection(fixture.organizationId(), fixture.otherInspectorId());

    mockMvc
        .perform(get("/api/v1/inspections").with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalCount").value(1))
        .andExpect(jsonPath("$.data.items[0].id").value(fixture.inspectionId().toString()));

    // The same organization holds an inspection this Inspector was not assigned to; role alone
    // must not expose it.
    assertThat(unassigned).isNotEqualTo(fixture.inspectionId());
  }

  @Test
  void anOrganizationAdminSeesEveryInspectionInTheirOrganizationOnly() throws Exception {
    createInspection(fixture.otherOrganizationId(), fixture.otherInspectorId());

    mockMvc
        .perform(get("/api/v1/inspections").with(principal(fixture.reviewerId(), "ORG_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalCount").value(1))
        .andExpect(jsonPath("$.data.items[0].id").value(fixture.inspectionId().toString()));
  }

  @Test
  void anOrganizationAdminFromAnotherOrganizationSeesNothing() throws Exception {
    mockMvc
        .perform(get("/api/v1/inspections").with(principal(fixture.outsiderId(), "ORG_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalCount").value(0))
        .andExpect(jsonPath("$.data.items").isEmpty());
  }

  @Test
  void aPlatformAdminReadsAcrossOrganizations() throws Exception {
    UUID adminId = createPlatformAdmin();
    UUID foreignInspection = createInspection(fixture.otherOrganizationId(), null);

    // The suite shares one container, so an absolute total would count other tests' fixtures.
    // What matters is that ADMIN sees both tenants, which an organization-scoped caller cannot.
    mockMvc
        .perform(get("/api/v1/inspections").with(principal(adminId, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.data.items[?(@.id == '%s')]".formatted(fixture.inspectionId())).exists())
        .andExpect(jsonPath("$.data.items[?(@.id == '%s')]".formatted(foreignInspection)).exists());

    mockMvc
        .perform(get("/api/v1/inspections").with(principal(fixture.reviewerId(), "ORG_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.data.items[?(@.id == '%s')]".formatted(fixture.inspectionId())).exists())
        .andExpect(
            jsonPath("$.data.items[?(@.id == '%s')]".formatted(foreignInspection)).doesNotExist());
  }

  @Test
  void aMaintenanceEngineerHasNoInspectionList() throws Exception {
    UUID engineerId = createMaintenanceEngineer();

    mockMvc
        .perform(get("/api/v1/inspections").with(principal(engineerId, "MAINTENANCE_ENGINEER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void pagingIsBoundedAndReportsTheRequestedWindow() throws Exception {
    for (int index = 0; index < 4; index++) {
      createInspection(fixture.organizationId(), fixture.inspectorId());
    }

    mockMvc
        .perform(
            get("/api/v1/inspections")
                .param("page", "1")
                .param("pageSize", "2")
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.page").value(1))
        .andExpect(jsonPath("$.data.pageSize").value(2))
        .andExpect(jsonPath("$.data.totalCount").value(5))
        .andExpect(jsonPath("$.data.totalPages").value(3))
        .andExpect(jsonPath("$.data.items.length()").value(2));

    // The last page holds the remainder, so the five rows are accounted for across pages.
    mockMvc
        .perform(
            get("/api/v1/inspections")
                .param("page", "3")
                .param("pageSize", "2")
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()").value(1));
  }

  @Test
  void anOutOfRangePageSizeIsClampedRatherThanRejected() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/inspections")
                .param("pageSize", "5000")
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.pageSize").value(100));
  }

  @Test
  void theReportListReturnsOnlyInspectionsThatCarryAReport() throws Exception {
    UUID withoutReport = createInspection(fixture.organizationId(), fixture.inspectorId());
    createReport(fixture.inspectionId(), fixture.inspectorId());

    mockMvc
        .perform(
            get("/api/v1/inspections/with-reports")
                .with(principal(fixture.reviewerId(), "ORG_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalCount").value(1))
        .andExpect(jsonPath("$.data.items[0].id").value(fixture.inspectionId().toString()));

    assertThat(withoutReport).isNotEqualTo(fixture.inspectionId());
  }

  @Test
  void aListRowCarriesItsLatestReportStatusAndVersion() throws Exception {
    createReport(fixture.inspectionId(), fixture.inspectorId());

    mockMvc
        .perform(get("/api/v1/inspections").with(principal(fixture.reviewerId(), "ORG_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].reportStatus").value("DRAFT"))
        .andExpect(jsonPath("$.data.items[0].reportVersionNo").value(0))
        .andExpect(jsonPath("$.data.items[0].reportId").isNotEmpty());
  }

  @Test
  void anInspectorOnlySeesReportsForTheirOwnInspections() throws Exception {
    UUID otherInspection = createInspection(fixture.organizationId(), fixture.otherInspectorId());
    createReport(otherInspection, fixture.otherInspectorId());
    createReport(fixture.inspectionId(), fixture.inspectorId());

    mockMvc
        .perform(
            get("/api/v1/inspections/with-reports")
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalCount").value(1))
        .andExpect(jsonPath("$.data.items[0].id").value(fixture.inspectionId().toString()));
  }

  @Test
  void anOrganizationAdminFromAnotherOrganizationSeesNoReports() throws Exception {
    createReport(fixture.inspectionId(), fixture.inspectorId());

    mockMvc
        .perform(
            get("/api/v1/inspections/with-reports")
                .with(principal(fixture.outsiderId(), "ORG_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalCount").value(0))
        .andExpect(jsonPath("$.data.items").isEmpty());
  }

  /**
   * The schema carries composite asset and inspector foreign keys, so an inspection can only be
   * created when both belong to its own organization. That constraint is itself part of why a
   * cross-tenant row cannot be forged by passing another organization's asset or inspector.
   *
   * @param inspectorId an inspector of {@code organizationId}, or null to create one there
   */
  private UUID createInspection(UUID organizationId, UUID inspectorId) {
    UUID assetId =
        fixture.organizationId().equals(organizationId)
            ? fixture.assetId()
            : createAssetIn(organizationId);
    UUID resolvedInspector =
        fixture.organizationId().equals(organizationId) && inspectorId != null
            ? inspectorId
            : createInspectorIn(organizationId);
    return inspections
        .saveAndFlush(
            new com.smartdroneinspection.inspections.domain.Inspection(
                organizationId,
                assetId,
                null,
                null,
                resolvedInspector,
                null,
                "list-" + UUID.randomUUID(),
                "Inspection created for list scope coverage",
                "{\"method\":\"visual\"}",
                "[\"main span\"]",
                "{\"criteria\":\"no visible structural damage\"}",
                null,
                null))
        .getId();
  }

  private UUID createInspectorIn(UUID organizationId) {
    User inspector =
        new User(
            "list-inspector-" + UUID.randomUUID() + "@example.test",
            "List inspector",
            null,
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            organizationId);
    inspector.addRole(UserRole.INSPECTOR);
    return users.saveAndFlush(inspector).getId();
  }

  private UUID createAssetIn(UUID organizationId) {
    UUID categoryId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO asset_categories (id, code, name, description, active)
        VALUES (?, ?, 'Bridge', 'Inspection list fixture category', TRUE)
        """,
        categoryId,
        ("CAT" + UUID.randomUUID()).toUpperCase(java.util.Locale.ROOT));
    UUID assetId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO assets (id, organization_id, category_id, asset_code, name, location, status,
                            created_at, updated_at, row_version)
        VALUES (?, ?, ?, ?, 'Main span', '{"label":"District 1"}', 'ACTIVE',
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
        """,
        assetId,
        organizationId,
        categoryId,
        ("AST" + UUID.randomUUID()).toUpperCase(java.util.Locale.ROOT));
    return assetId;
  }

  private InspectionReport createReport(UUID inspectionId, UUID authorId) {
    return reports.saveAndFlush(new InspectionReport(inspectionId, authorId));
  }

  private UUID createPlatformAdmin() {
    User admin =
        new User(
            "list-admin-" + UUID.randomUUID() + "@example.test",
            "List platform admin",
            null,
            UserStatus.ACTIVE,
            ActorZone.PLATFORM,
            null);
    admin.addRole(UserRole.ADMIN);
    return users.saveAndFlush(admin).getId();
  }

  private UUID createMaintenanceEngineer() {
    User engineer =
        new User(
            "list-engineer-" + UUID.randomUUID() + "@example.test",
            "List maintenance engineer",
            null,
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            fixture.organizationId());
    engineer.addRole(UserRole.MAINTENANCE_ENGINEER);
    return users.saveAndFlush(engineer).getId();
  }

  private static RequestPostProcessor principal(UUID userId, String role) {
    return jwt()
        .jwt(
            jwt -> jwt.subject(userId.toString()).claim("roles", java.util.List.of("ROLE_" + role)))
        .authorities(new SimpleGrantedAuthority("ROLE_" + role));
  }
}
