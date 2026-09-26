package com.smartdroneinspection.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.CategoryFrequencySuggestion;
import com.smartdroneinspection.assets.domain.enums.ScheduleProposalStatus;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.CategoryFrequencySuggestionRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.assets.repository.ScheduleProposalRepository;
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
class AssetReviewApiIntegrationTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired AssetCategoryRepository categories;
  @Autowired ChecklistTemplateRepository templates;
  @Autowired AssetRepository assets;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired CategoryFrequencySuggestionRepository frequencies;
  @Autowired ScheduleProposalRepository proposals;

  private AssetTestFixture.Data fixture;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    fixture = new AssetTestFixture(categories, templates, assets, users, jdbcTemplate).create();
  }

  @Test
  void managerApprovalActivatesAssetAndGeneratesOneProposalPerSuggestion() throws Exception {
    frequencies.saveAndFlush(new CategoryFrequencySuggestion(fixture.categoryId(), "MONTH", 3, 0));
    frequencies.saveAndFlush(new CategoryFrequencySuggestion(fixture.categoryId(), "YEAR", 1, 1));
    UUID assetId = seedPendingAsset();

    mockMvc
        .perform(
            post("/api/v1/assets/{id}/review", assetId)
                .with(manager(fixture.managerId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"APPROVE\",\"note\":\"Standard cadence\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.proposalCount").value(2));

    assertThat(proposals.findByAssetIdOrderByCreatedAtAsc(assetId))
        .hasSize(2)
        .allSatisfy(p -> assertThat(p.getStatus()).isEqualTo(ScheduleProposalStatus.GENERATED));

    mockMvc
        .perform(
            post("/api/v1/assets/{id}/review", assetId)
                .with(manager(fixture.managerId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"APPROVE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.proposalCount").value(2));
    assertThat(proposals.findByAssetIdOrderByCreatedAtAsc(assetId)).hasSize(2);
  }

  @Test
  void approvalRequiresSuggestedFrequenciesAndRejectionLeavesNoProposals() throws Exception {
    UUID assetNoSuggestions = seedPendingAsset();
    mockMvc
        .perform(
            post("/api/v1/assets/{id}/review", assetNoSuggestions)
                .with(manager(fixture.managerId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"APPROVE\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("NO_SUGGESTED_FREQUENCIES"));

    UUID assetId = seedPendingAsset();
    mockMvc
        .perform(
            post("/api/v1/assets/{id}/review", assetId)
                .with(manager(fixture.managerId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"REJECT\",\"note\":\"Unlocatable\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("REJECTED"))
        .andExpect(jsonPath("$.data.proposalCount").value(0));
    assertThat(proposals.findByAssetIdOrderByCreatedAtAsc(assetId)).isEmpty();
  }

  @Test
  void onlyServiceManagerCanReview() throws Exception {
    UUID assetId = seedPendingAsset();
    mockMvc
        .perform(
            post("/api/v1/assets/{id}/review", assetId)
                .with(client(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"APPROVE\"}"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/api/v1/assets/{id}/review", assetId)
                .with(admin(fixture.adminId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"APPROVE\"}"))
        .andExpect(status().isForbidden());
  }

  private UUID seedPendingAsset() {
    return assets
        .saveAndFlush(
            Asset.clientCreate(
                fixture.organizationId(),
                fixture.categoryId(),
                "PA-" + UUID.randomUUID(),
                "Pending asset",
                null,
                "District 1",
                null,
                null,
                null,
                fixture.clientId()))
        .getId();
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

  private org.springframework.test.web.servlet.request.RequestPostProcessor admin(UUID id) {
    return jwt()
        .jwt(token -> token.subject(id.toString()))
        .authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"));
  }
}
