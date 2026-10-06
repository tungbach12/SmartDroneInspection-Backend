package com.smartdroneinspection.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.CategoryFrequencySuggestion;
import com.smartdroneinspection.assets.domain.ScheduleProposal;
import com.smartdroneinspection.assets.events.InspectionScheduleDue;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.CategoryFrequencySuggestionRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.assets.repository.InspectionScheduleRepository;
import com.smartdroneinspection.assets.repository.ScheduleProposalRepository;
import com.smartdroneinspection.assets.service.InspectionScheduleDuePublisher;
import com.smartdroneinspection.users.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
@Transactional
class AssetWorkflowIntegrationTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired AssetCategoryRepository categories;
  @Autowired ChecklistTemplateRepository templates;
  @Autowired AssetRepository assets;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired CategoryFrequencySuggestionRepository frequencies;
  @Autowired ScheduleProposalRepository proposals;
  @Autowired InspectionScheduleRepository schedules;
  @Autowired InspectionScheduleDuePublisher publisher;
  @Autowired EntityManager entityManager;
  @Autowired ApplicationEvents applicationEvents;

  private AssetTestFixture.Data fixture;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    fixture = new AssetTestFixture(categories, templates, assets, users, jdbcTemplate).create();
  }

  @Test
  void fullWf1FlowFromAssetCreationToDueEvent() throws Exception {
    frequencies.saveAndFlush(new CategoryFrequencySuggestion(fixture.categoryId(), "MONTH", 3, 0));
    frequencies.saveAndFlush(new CategoryFrequencySuggestion(fixture.categoryId(), "YEAR", 1, 1));

    // 1. Client creates asset -> PENDING_REVIEW
    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/assets")
                    .with(client(fixture.clientId()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"code\":\"WF1-FULL\",\"name\":\"Full flow bridge\",\"categoryId\":\""
                            + fixture.categoryId()
                            + "\",\"locationText\":\"District 1\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
            .andReturn();
    UUID assetId = uuid(created, "$.data.id");

    // 2. Manager approves -> ACTIVE + 2 proposals
    mockMvc
        .perform(
            post("/api/v1/assets/{id}/review", assetId)
                .with(manager(fixture.managerId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"APPROVE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.proposalCount").value(2));

    // 3. Manager approves both proposals
    for (ScheduleProposal proposal : proposals.findByAssetIdOrderByCreatedAtAsc(assetId)) {
      mockMvc
          .perform(
              post("/api/v1/schedule-proposals/{id}/review", proposal.getId())
                  .with(manager(fixture.managerId()))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"action\":\"APPROVE\"}"))
          .andExpect(status().isOk());
    }

    // 4. Client selects one -> ACTIVE schedule, siblings SUPERSEDED
    List<ScheduleProposal> approved = proposals.findByAssetIdOrderByCreatedAtAsc(assetId);
    mockMvc
        .perform(
            post("/api/v1/schedule-proposals/{id}/select", approved.get(0).getId())
                .with(client(fixture.clientId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CLIENT_SELECTED"));

    assertThat(schedules.findByAssetIdOrderByNextDueAt(assetId)).hasSize(1);

    // 5. Due event publishes once with the frozen contract payload
    long eventsBefore = applicationEvents.stream(InspectionScheduleDue.class).count();
    jdbcTemplate.update(
        "UPDATE inspection_schedules SET next_due_at = now() - interval '1 hour' WHERE asset_id = ?",
        assetId);
    // the schedule is already managed with the pre-selection due date; drop it so the
    // forced-past row is re-read instead of served from the persistence context
    entityManager.clear();

    assertThat(publisher.publishDueSchedules()).isEqualTo(1);
    assertThat(publisher.publishDueSchedules()).isZero();
    assertThat(applicationEvents.stream(InspectionScheduleDue.class).count() - eventsBefore)
        .isEqualTo(1);

    InspectionScheduleDue due =
        applicationEvents.stream(InspectionScheduleDue.class)
            .reduce((first, second) -> second)
            .orElseThrow();
    assertThat(due.organizationId()).isEqualTo(fixture.organizationId());
    assertThat(due.assetId()).isEqualTo(assetId);
    assertThat(due.checklistTemplateVersionId()).isEqualTo(fixture.checklistTemplateId());
  }

  @Test
  void negativeScopeSweepAcrossEveryWf1Endpoint() throws Exception {
    UUID ownPending = seedPendingAsset();
    UUID otherAssetId =
        assets
            .saveAndFlush(
                Asset.clientCreate(
                    fixture.otherOrganizationId(),
                    fixture.categoryId(),
                    "OTH-" + UUID.randomUUID(),
                    "Other org asset",
                    null,
                    "District 9",
                    null,
                    null,
                    null,
                    fixture.otherClientId()))
            .getId();
    ScheduleProposal otherProposal =
        ScheduleProposal.generate(otherAssetId, fixture.checklistTemplateId(), "MONTH", 3);
    otherProposal.managerApprove(null, fixture.managerId());
    otherProposal = proposals.saveAndFlush(otherProposal);

    // CLIENT cannot review assets
    mockMvc
        .perform(
            post("/api/v1/assets/{id}/review", ownPending)
                .with(client(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"APPROVE\"}"))
        .andExpect(status().isForbidden());

    // MANAGER cannot create assets or manage the catalog
    mockMvc
        .perform(
            post("/api/v1/assets")
                .with(manager(fixture.managerId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"code\":\"M1\",\"name\":\"x\",\"categoryId\":\""
                        + fixture.categoryId()
                        + "\",\"locationText\":\"x\"}"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/api/v1/asset-categories")
                .with(manager(fixture.managerId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"c\",\"name\":\"c\"}"))
        .andExpect(status().isForbidden());

    // PLATFORM_ADMIN cannot select proposals (Client-only action)
    mockMvc
        .perform(
            post("/api/v1/schedule-proposals/{id}/select", otherProposal.getId())
                .with(admin(fixture.adminId())))
        .andExpect(status().isForbidden());

    // cross-org: another organization's asset stays invisible to our client
    mockMvc
        .perform(get("/api/v1/assets/{id}", otherAssetId).with(client(fixture.clientId())))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            get("/api/v1/assets/{id}/documents", otherAssetId).with(client(fixture.clientId())))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            post("/api/v1/schedule-proposals/{id}/select", otherProposal.getId())
                .with(client(fixture.clientId())))
        .andExpect(status().isNotFound());

    // Manager is a platform role (organizationId null), so review is platform-scoped
    mockMvc
        .perform(
            post("/api/v1/assets/{id}/review", otherAssetId)
                .with(manager(fixture.managerId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"REJECT\",\"note\":\"missing documentation\"}"))
        .andExpect(status().isOk());

    // rejecting an asset that never awaited review is a conflict, not a server error
    UUID alreadyActive =
        assets
            .saveAndFlush(
                new Asset(
                    fixture.organizationId(),
                    fixture.categoryId(),
                    "ACT-" + UUID.randomUUID(),
                    "Already active asset",
                    null,
                    "District 1",
                    null,
                    null,
                    null,
                    fixture.clientId()))
            .getId();
    mockMvc
        .perform(
            post("/api/v1/assets/{id}/review", alreadyActive)
                .with(manager(fixture.managerId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"REJECT\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_STATE"));

    // unauthenticated -> 401 problem detail
    mockMvc
        .perform(get("/api/v1/assets"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  void managerReviewQueueIsPlatformScopedAndClosedToOtherRoles() throws Exception {
    UUID ownPending = seedPendingAsset();
    assets.saveAndFlush(
        Asset.clientCreate(
            fixture.otherOrganizationId(),
            fixture.categoryId(),
            "OTHQ-" + UUID.randomUUID(),
            "Other org pending asset",
            null,
            "District 9",
            null,
            null,
            null,
            fixture.otherClientId()));

    // the queue spans organizations, so the manager sees foreign-organization assets
    mockMvc
        .perform(get("/api/v1/assets/pending-review").with(manager(fixture.managerId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalCount").value(2));

    // the client list stays organization-scoped
    mockMvc
        .perform(get("/api/v1/assets").with(client(fixture.clientId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[?(@.id=='" + ownPending + "')]").exists())
        .andExpect(jsonPath("$.data.totalCount").value(1));

    mockMvc
        .perform(get("/api/v1/assets/pending-review").with(client(fixture.clientId())))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/v1/assets/pending-review").with(admin(fixture.adminId())))
        .andExpect(status().isForbidden());
  }

  private UUID seedPendingAsset() {
    return assets
        .saveAndFlush(
            Asset.clientCreate(
                fixture.organizationId(),
                fixture.categoryId(),
                "OWN-" + UUID.randomUUID(),
                "Own pending asset",
                null,
                "District 1",
                null,
                null,
                null,
                fixture.clientId()))
        .getId();
  }

  private UUID uuid(MvcResult result, String expression) throws Exception {
    return UUID.fromString(
        JsonPath.read(result.getResponse().getContentAsString(), expression).toString());
  }

  private RequestPostProcessor client(UUID id) {
    return jwt().jwt(token -> token.subject(id.toString())).authorities(role("CLIENT"));
  }

  private RequestPostProcessor manager(UUID id) {
    return jwt().jwt(token -> token.subject(id.toString())).authorities(role("PROVIDER_MANAGER"));
  }

  private RequestPostProcessor admin(UUID id) {
    return jwt().jwt(token -> token.subject(id.toString())).authorities(role("PLATFORM_ADMIN"));
  }

  private SimpleGrantedAuthority role(String name) {
    return new SimpleGrantedAuthority("ROLE_" + name);
  }
}
