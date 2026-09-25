package com.smartdroneinspection.assets;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AssetApiIntegrationTest {

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
        webAppContextSetup(webApplicationContext)
            .apply(
                org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                    .springSecurity())
            .build();
    fixture = new AssetTestFixture(categories, templates, assets, users, jdbcTemplate).create();
  }

  @Test
  void clientCreatesAssetInPendingReviewState() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/assets")
                .with(client(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"code\":\"BR-01\",\"name\":\"North bridge\",\"description\":\"Main span\","
                        + "\"categoryId\":\""
                        + fixture.categoryId()
                        + "\",\"locationText\":\"District 1\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"));
  }

  @Test
  void duplicateCodeInSameOrganizationConflictsAndOtherOrganizationSeesNothing() throws Exception {
    String body =
        "{\"code\":\"BR-DUP\",\"name\":\"Bridge\",\"categoryId\":\""
            + fixture.categoryId()
            + "\",\"locationText\":\"District 1\"}";
    mockMvc
        .perform(
            post("/api/v1/assets")
                .with(client(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post("/api/v1/assets")
                .with(client(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DUPLICATE_CODE"));
    mockMvc
        .perform(get("/api/v1/assets").with(client(fixture.otherClientId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalCount").value(0));
  }

  @Test
  void searchFiltersAssetsWithinTheAuthenticatedOrganization() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/assets")
                .with(client(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"code\":\"BR-SOUTH\",\"name\":\"South bridge\",\"categoryId\":\""
                        + fixture.categoryId()
                        + "\",\"locationText\":\"District 3\"}"))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/v1/assets?search=south").with(client(fixture.clientId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalCount").value(1))
        .andExpect(jsonPath("$.data.items[0].code").value("BR-SOUTH"));
  }

  @Test
  void crossOrganizationReadAndUpdateAreDenied() throws Exception {
    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/assets")
                    .with(client(fixture.clientId()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"code\":\"BR-X\",\"name\":\"Bridge\",\"categoryId\":\""
                            + fixture.categoryId()
                            + "\",\"locationText\":\"District 1\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    UUID assetId =
        UUID.fromString(
            com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.data.id"));
    mockMvc
        .perform(get("/api/v1/assets/{id}", assetId).with(client(fixture.otherClientId())))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            put("/api/v1/assets/{id}", assetId)
                .with(client(fixture.otherClientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Hijacked\",\"locationText\":\"Elsewhere\"}"))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            put("/api/v1/assets/{id}", assetId)
                .with(client(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Renamed\",\"locationText\":\"District 2\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("Renamed"));
  }

  @Test
  void managerCannotCreateAssets() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/assets")
                .with(manager(fixture.managerId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"code\":\"NO\",\"name\":\"Nope\",\"categoryId\":\""
                        + fixture.categoryId()
                        + "\",\"locationText\":\"X\"}"))
        .andExpect(status().isForbidden());
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor client(UUID id) {
    return jwt()
        .jwt(token -> token.subject(id.toString()))
        .authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CLIENT"));
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor manager(UUID id) {
    return jwt()
        .jwt(token -> token.subject(id.toString()))
        .authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                "ROLE_SERVICE_MANAGER"));
  }
}
