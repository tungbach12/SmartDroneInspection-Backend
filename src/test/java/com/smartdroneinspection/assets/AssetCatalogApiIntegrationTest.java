package com.smartdroneinspection.assets;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AssetCatalogApiIntegrationTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired AssetCategoryRepository categories;
  @Autowired AssetRepository assets;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbcTemplate;

  private AssetTestFixture.Data fixture;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    fixture = new AssetTestFixture(categories, assets, users).create();
  }

  @Test
  void adminCreatesCategoryAndClientIsDenied() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/asset-categories")
                .with(admin(fixture.adminId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"tower\",\"name\":\"Tower\",\"description\":\"Comms tower\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.code").value("TOWER"));

    mockMvc
        .perform(
            post("/api/v1/asset-categories")
                .with(client(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"denied\",\"name\":\"Denied\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void duplicateCategoryCodeConflicts() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/asset-categories")
                .with(admin(fixture.adminId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"code\":\""
                        + categories.findById(fixture.categoryId()).orElseThrow().getCode()
                        + "\",\"name\":\"Bridge 2\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DUPLICATE_CODE"));
  }

  @Test
  void categoryNameBeyondDatabaseLimitIsRejectedAsValidationFailure() throws Exception {
    String tooLongName = "a".repeat(161);

    mockMvc
        .perform(
            post("/api/v1/asset-categories")
                .with(admin(fixture.adminId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"code\":\"long-name\",\"name\":\""
                        + tooLongName
                        + "\",\"description\":\"Overlong\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    mockMvc
        .perform(
            put("/api/v1/asset-categories/{categoryId}", fixture.categoryId())
                .with(admin(fixture.adminId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"code\":\"ignored\",\"name\":\""
                        + tooLongName
                        + "\",\"description\":\"Overlong\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void adminManagesPreFlightPostFlightAndCategoryChecklistTemplates() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/asset-categories/checklist-templates")
                .param("type", "PRE_FLIGHT")
                .with(client(fixture.clientId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].templateType").value("PRE_FLIGHT"));

    String createdBody =
        mockMvc
            .perform(
                post("/api/v1/asset-categories/checklist-templates")
                    .with(admin(fixture.adminId()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"templateType\":\"CATEGORY_INSPECTION_ITEM\",\"categoryId\":\""
                            + fixture.categoryId()
                            + "\",\"label\":\"Curtain wall silicone sealant\",\"sortOrder\":1}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.label").value("Curtain wall silicone sealant"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String templateId = com.jayway.jsonpath.JsonPath.read(createdBody, "$.data.id");

    mockMvc
        .perform(
            put("/api/v1/asset-categories/checklist-templates/{templateId}", templateId)
                .with(admin(fixture.adminId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"label\":\"Curtain wall & structural glazing\",\"sortOrder\":2,\"active\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.label").value("Curtain wall & structural glazing"));

    mockMvc
        .perform(
            delete("/api/v1/asset-categories/checklist-templates/{templateId}", templateId)
                .with(admin(fixture.adminId())))
        .andExpect(status().isNoContent());
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor admin(UUID id) {
    return jwt()
        .jwt(t -> t.subject(id.toString()))
        .authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"));
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor client(UUID id) {
    return jwt()
        .jwt(t -> t.subject(id.toString()))
        .authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                "ROLE_CUSTOMER"));
  }
}
