package com.smartdroneinspection.assets;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AssetCatalogApiIntegrationTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired AssetCategoryRepository categories;
  @Autowired ChecklistTemplateRepository templates;
  @Autowired AssetRepository assets;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbcTemplate;

  private AssetTestFixture.Data fixture;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    fixture = new AssetTestFixture(categories, templates, assets, users, jdbcTemplate).create();
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
  void adminManagesSuggestedFrequencies() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/asset-categories/{id}/suggested-frequencies", fixture.categoryId())
                .with(admin(fixture.adminId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"frequencyUnit\":\"MONTH\",\"frequencyInterval\":3}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.frequencyUnit").value("MONTH"));

    mockMvc
        .perform(
            post("/api/v1/asset-categories/{id}/suggested-frequencies", fixture.categoryId())
                .with(admin(fixture.adminId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"frequencyUnit\":\"MONTH\",\"frequencyInterval\":0}"))
        .andExpect(status().isBadRequest());

    MvcResult list =
        mockMvc
            .perform(
                get("/api/v1/asset-categories/{id}/suggested-frequencies", fixture.categoryId())
                    .with(admin(fixture.adminId())))
            .andExpect(status().isOk())
            .andReturn();
    org.assertj.core.api.Assertions.assertThat(list.getResponse().getContentAsString())
        .contains("\"frequencyUnit\":\"MONTH\"");
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
            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CLIENT"));
  }
}
