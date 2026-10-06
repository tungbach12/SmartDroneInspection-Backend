package com.smartdroneinspection.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.InspectionSchedule;
import com.smartdroneinspection.assets.domain.ScheduleProposal;
import com.smartdroneinspection.assets.domain.enums.InspectionScheduleStatus;
import com.smartdroneinspection.assets.domain.enums.ScheduleProposalStatus;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.assets.repository.InspectionScheduleRepository;
import com.smartdroneinspection.assets.repository.ScheduleProposalRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ScheduleProposalApiIntegrationTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired AssetCategoryRepository categories;
  @Autowired ChecklistTemplateRepository templates;
  @Autowired AssetRepository assets;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired ScheduleProposalRepository proposals;
  @Autowired InspectionScheduleRepository schedules;

  private AssetTestFixture.Data fixture;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    fixture = new AssetTestFixture(categories, templates, assets, users, jdbcTemplate).create();
  }

  @Test
  void managerReviewsProposalAndClientOnlySeesApprovedOwnOrg() throws Exception {
    UUID assetId = seedPendingAsset();
    UUID proposalId = seedProposal(assetId, ScheduleProposalStatus.GENERATED);

    mockMvc
        .perform(
            get("/api/v1/schedule-proposals")
                .param("assetId", assetId.toString())
                .with(client(fixture.clientId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0));

    mockMvc
        .perform(
            post("/api/v1/schedule-proposals/{id}/review", proposalId)
                .with(manager(fixture.managerId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"action\":\"APPROVE\",\"frequencyUnit\":\"WEEK\","
                        + "\"frequencyInterval\":2}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("MANAGER_APPROVED"))
        .andExpect(jsonPath("$.data.frequencyInterval").value(2));

    mockMvc
        .perform(
            get("/api/v1/schedule-proposals")
                .param("assetId", assetId.toString())
                .with(client(fixture.clientId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(proposalId.toString()));

    mockMvc
        .perform(
            get("/api/v1/schedule-proposals")
                .param("assetId", assetId.toString())
                .with(client(fixture.otherClientId())))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void selectingCreatesOneScheduleSupersedesSiblingsAndIsNotRepeatable() throws Exception {
    UUID assetId = seedPendingAsset();
    approveAsset(assetId);
    UUID first = seedProposal(assetId, ScheduleProposalStatus.MANAGER_APPROVED, "MONTH", 3);
    UUID second = seedProposal(assetId, ScheduleProposalStatus.MANAGER_APPROVED, "YEAR", 1);

    mockMvc
        .perform(
            post("/api/v1/schedule-proposals/{id}/select", first).with(client(fixture.clientId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CLIENT_SELECTED"));

    assertThat(proposals.findById(second).orElseThrow().getStatus())
        .isEqualTo(ScheduleProposalStatus.SUPERSEDED);
    List<InspectionSchedule> createdSchedules = schedules.findByAssetIdOrderByNextDueAt(assetId);
    assertThat(createdSchedules).hasSize(1);
    assertThat(createdSchedules.get(0).getStatus()).isEqualTo(InspectionScheduleStatus.ACTIVE);

    mockMvc
        .perform(
            post("/api/v1/schedule-proposals/{id}/select", first).with(client(fixture.clientId())))
        .andExpect(status().isConflict());
    mockMvc
        .perform(
            post("/api/v1/schedule-proposals/{id}/select", second).with(client(fixture.clientId())))
        .andExpect(status().isConflict());

    UUID third = seedProposal(assetId, ScheduleProposalStatus.MANAGER_APPROVED, "WEEK", 2);
    mockMvc
        .perform(
            post("/api/v1/schedule-proposals/{id}/select", third).with(client(fixture.clientId())))
        .andExpect(status().isConflict());
  }

  @Test
  void crossOrgSelectionAndNonManagerReviewAreDenied() throws Exception {
    UUID otherAssetId =
        assets
            .saveAndFlush(
                new Asset(
                    fixture.otherOrganizationId(),
                    fixture.categoryId(),
                    "OTHER-" + UUID.randomUUID(),
                    "Other organization asset",
                    null,
                    "District 9",
                    null,
                    null,
                    null,
                    fixture.otherClientId()))
            .getId();
    UUID proposalId = seedProposal(otherAssetId, ScheduleProposalStatus.MANAGER_APPROVED);

    mockMvc
        .perform(
            post("/api/v1/schedule-proposals/{id}/select", proposalId)
                .with(client(fixture.clientId())))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            post("/api/v1/schedule-proposals/{id}/review", proposalId)
                .with(client(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"APPROVE\"}"))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/v1/schedule-proposals/{id}/review", proposalId)
                .with(manager(fixture.managerId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"MAYBE\"}"))
        .andExpect(status().isBadRequest());
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

  private void approveAsset(UUID assetId) {
    Asset asset = assets.findById(assetId).orElseThrow();
    asset.approveReview();
    assets.saveAndFlush(asset);
  }

  private UUID seedProposal(UUID assetId, ScheduleProposalStatus status) {
    return seedProposal(assetId, status, "MONTH", 3);
  }

  private UUID seedProposal(
      UUID assetId, ScheduleProposalStatus status, String frequencyUnit, int frequencyInterval) {
    ScheduleProposal proposal =
        ScheduleProposal.generate(
            assetId, fixture.checklistTemplateId(), frequencyUnit, frequencyInterval);
    if (status == ScheduleProposalStatus.MANAGER_APPROVED) {
      proposal.managerApprove(null, fixture.managerId());
    }
    return proposals.saveAndFlush(proposal).getId();
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
                "ROLE_PROVIDER_MANAGER"));
  }
}
