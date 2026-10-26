package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.AssetTestFixture;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.AssetCategory;
import com.smartdroneinspection.assets.domain.ChecklistTemplate;
import com.smartdroneinspection.assets.domain.enums.ChecklistResponseType;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
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

/**
 * The checklist surface the mobile inspection record screen reads and writes.
 *
 * <p>The system-managed part of a checklist (section, prompt, guidance) follows the requested
 * locale, while the Inspector's own notes stay in the language they were written in: a note is
 * evidence of what the Inspector saw, not a piece of system copy to be translated.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class InspectionChecklistApiIntegrationTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired UserRepository users;
  @Autowired AssetRepository assets;
  @Autowired AssetCategoryRepository categories;
  @Autowired ChecklistTemplateRepository templates;
  @Autowired JdbcTemplate jdbcTemplate;

  MockMvc mockMvc;
  UUID organizationId;
  UUID inspectorId;
  UUID assetId;
  UUID inspectionId;
  UUID checklistItemId;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    AssetTestFixture.Data fixture =
        new AssetTestFixture(categories, templates, assets, users, jdbcTemplate).create();
    organizationId = fixture.organizationId();
    inspectorId = createUser(organizationId, UserRole.INSPECTOR, "inspector");
    assetId =
        assets
            .saveAndFlush(
                new Asset(
                    organizationId,
                    fixture.categoryId(),
                    "ASSET-CHECKLIST-API-" + UUID.randomUUID(),
                    "Checklist API asset",
                    null,
                    "Inspection location",
                    null,
                    null,
                    null,
                    inspectorId))
            .getId();

    // A second, newer version of the same category's checklist carries the Vietnamese system
    // text, so the same template answers both locales.
    ChecklistTemplate bilingual =
        new ChecklistTemplate(
            "bridge-inspection-bilingual-" + organizationId,
            2,
            fixture.categoryId(),
            "Bridge inspection",
            "WF1 fixture checklist",
            "Kiểm tra cầu",
            "Danh sách kiểm tra cầu",
            fixture.managerId());
    var item =
        bilingual.addItem(
            "surface-condition",
            "Surface",
            "Check the visible surface condition",
            "Bề mặt",
            "Kiểm tra tình trạng bề mặt nhìn thấy",
            ChecklistResponseType.PASS_FAIL,
            true,
            0,
            "Look for cracks and spalling",
            "Quan sát vết nứt và bong tróc",
            null);
    bilingual.publish();
    templates.saveAndFlush(bilingual);
    checklistItemId = item.getId();

    inspectionId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO inspections (id, organization_id, asset_id, inspector_id, status, objective) "
            + "VALUES (?, ?, ?, ?, 'IN_PROGRESS', 'Inspect main span')",
        inspectionId,
        organizationId,
        assetId,
        inspectorId);
  }

  @Test
  void returnsVietnameseSystemTextWhenVietnameseIsRequested() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/inspections/{i}/checklist", inspectionId)
                .with(inspector())
                .header("Accept-Language", "vi"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].prompt").value("Kiểm tra tình trạng bề mặt nhìn thấy"))
        .andExpect(jsonPath("$.data[0].sectionName").value("Bề mặt"))
        .andExpect(jsonPath("$.data[0].guidance").value("Quan sát vết nứt và bong tróc"))
        .andExpect(jsonPath("$.data[0].responseType").value("PASS_FAIL"))
        .andExpect(jsonPath("$.data[0].required").value(true))
        .andExpect(jsonPath("$.data[0].itemId").value(checklistItemId.toString()));
  }

  @Test
  void returnsEnglishSystemTextByDefault() throws Exception {
    mockMvc
        .perform(get("/api/v1/inspections/{i}/checklist", inspectionId).with(inspector()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].prompt").value("Check the visible surface condition"))
        .andExpect(jsonPath("$.data[0].sectionName").value("Surface"))
        .andExpect(jsonPath("$.data[0].guidance").value("Look for cracks and spalling"));
  }

  @Test
  void savesAResponseAndKeepsTheInspectorNoteInItsOriginalLanguage() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/inspections/{i}/checklist-responses/{item}", inspectionId, checklistItemId)
                .with(inspector())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "responseValue",
                            Map.of("value", "PASS"),
                            "notes",
                            "Ghi chú tiếng Việt của kiểm tra viên"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.checklistItemId").value(checklistItemId.toString()))
        .andExpect(jsonPath("$.data.completedByUserId").doesNotExist())
        .andExpect(jsonPath("$.data.notes").value("Ghi chú tiếng Việt của kiểm tra viên"));

    mockMvc
        .perform(
            get("/api/v1/inspections/{i}/checklist", inspectionId)
                .with(inspector())
                .header("Accept-Language", "vi"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].prompt").value("Kiểm tra tình trạng bề mặt nhìn thấy"))
        .andExpect(jsonPath("$.data[0].responseValue.value").value("PASS"))
        .andExpect(jsonPath("$.data[0].notes").value("Ghi chú tiếng Việt của kiểm tra viên"));
  }

  @Test
  void refusesAResponseForAnItemOutsideTheInspectionChecklist() throws Exception {
    mockMvc
        .perform(
            put(
                    "/api/v1/inspections/{i}/checklist-responses/{item}",
                    inspectionId,
                    UUID.randomUUID())
                .with(inspector())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("responseValue", Map.of("value", "PASS")))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void refusesAResponseWhoseValueDoesNotMatchTheItemType() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/inspections/{i}/checklist-responses/{item}", inspectionId, checklistItemId)
                .with(inspector())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("responseValue", Map.of("value", "MAYBE")))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void anInspectorFromAnotherOrganizationSeesNoChecklist() throws Exception {
    UUID outsiderOrganizationId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO organizations (id, legal_name, display_name, registration_code, timezone, status) "
            + "VALUES (?, 'Outsider org', 'Outsider org', ?, 'Asia/Ho_Chi_Minh', 'ACTIVE')",
        outsiderOrganizationId,
        "ORG-" + outsiderOrganizationId);
    UUID outsiderId = createUser(outsiderOrganizationId, UserRole.INSPECTOR, "outsider");

    mockMvc
        .perform(get("/api/v1/inspections/{i}/checklist", inspectionId).with(inspector(outsiderId)))
        .andExpect(status().isNotFound());
  }

  @Test
  void anUnauthenticatedCallerIsRefused() throws Exception {
    mockMvc
        .perform(get("/api/v1/inspections/{i}/checklist", inspectionId))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            put(
                "/api/v1/inspections/{i}/checklist-responses/{item}",
                inspectionId,
                checklistItemId))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void aPublishedInspectionKeepsItsRecordedChecklistResponses() throws Exception {
    saveResponse("PASS", "Ghi chú trước khi xuất bản");
    jdbcTemplate.update(
        "UPDATE inspections SET status = 'REPORT_PUBLISHED' WHERE id = ?", inspectionId);

    mockMvc
        .perform(get("/api/v1/inspections/{i}/checklist", inspectionId).with(inspector()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].responseValue.value").value("PASS"))
        .andExpect(jsonPath("$.data[0].notes").value("Ghi chú trước khi xuất bản"));
  }

  private void saveResponse(String value, String notes) throws Exception {
    mockMvc
        .perform(
            put("/api/v1/inspections/{i}/checklist-responses/{item}", inspectionId, checklistItemId)
                .with(inspector())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("responseValue", Map.of("value", value), "notes", notes))))
        .andExpect(status().isOk());
  }

  private static String json(Map<String, Object> fields) throws Exception {
    return new ObjectMapper().writeValueAsString(fields);
  }

  private UUID createUser(UUID organizationId, UserRole role, String label) {
    User user =
        new User(
            label + "-" + UUID.randomUUID() + "@example.test",
            label,
            null,
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            organizationId);
    user.addRole(role);
    return users.saveAndFlush(user).getId();
  }

  private RequestPostProcessor inspector() {
    return inspector(inspectorId);
  }

  private RequestPostProcessor inspector(UUID userId) {
    return jwt()
        .jwt(jwt -> jwt.subject(userId.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_" + UserRole.INSPECTOR.name()));
  }

  @Test
  void theChecklistIsEmptyWhenTheAssetCategoryHasNoTemplate() throws Exception {
    UUID categoryWithoutTemplateId =
        categories
            .saveAndFlush(
                new AssetCategory(
                    "tunnel-" + organizationId, "Tunnel", "Tunnel infrastructure", true))
            .getId();
    UUID categorylessAssetId =
        assets
            .saveAndFlush(
                new Asset(
                    organizationId,
                    categoryWithoutTemplateId,
                    "ASSET-NO-TEMPLATE-" + UUID.randomUUID(),
                    "Asset without checklist template",
                    null,
                    "Inspection location",
                    null,
                    null,
                    null,
                    inspectorId))
            .getId();
    UUID inspectionWithoutTemplate = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO inspections (id, organization_id, asset_id, inspector_id, status, objective) "
            + "VALUES (?, ?, ?, ?, 'IN_PROGRESS', 'Inspect main span')",
        inspectionWithoutTemplate,
        organizationId,
        categorylessAssetId,
        inspectorId);

    mockMvc
        .perform(
            get("/api/v1/inspections/{i}/checklist", inspectionWithoutTemplate).with(inspector()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty());

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM checklist_responses WHERE inspection_id = ?",
                Long.class,
                inspectionWithoutTemplate))
        .isZero();
  }
}
