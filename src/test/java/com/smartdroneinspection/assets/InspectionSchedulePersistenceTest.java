package com.smartdroneinspection.assets;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.AssetCategory;
import com.smartdroneinspection.assets.domain.AssetStatus;
import com.smartdroneinspection.assets.domain.ChecklistResponseType;
import com.smartdroneinspection.assets.domain.ChecklistTemplate;
import com.smartdroneinspection.assets.domain.ChecklistTemplateStatus;
import com.smartdroneinspection.assets.domain.InspectionFrequencyUnit;
import com.smartdroneinspection.assets.domain.InspectionSchedule;
import com.smartdroneinspection.assets.domain.InspectionScheduleStatus;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.assets.repository.InspectionScheduleRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class InspectionSchedulePersistenceTest {

  @Autowired AssetCategoryRepository assetCategoryRepository;
  @Autowired AssetRepository assetRepository;
  @Autowired ChecklistTemplateRepository checklistTemplateRepository;
  @Autowired InspectionScheduleRepository inspectionScheduleRepository;
  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  void returnsOnlyOverdueActiveSchedulesWithDeterministicOrdering() {
    Fixture fixture = persistFixture();
    Instant now = Instant.parse("2026-10-01T00:00:00Z");

    InspectionSchedule overdueActive = newSchedule(fixture, now.minusSeconds(60));
    overdueActive.activate(AssetStatus.ACTIVE, ChecklistTemplateStatus.ACTIVE);
    InspectionSchedule futureActive = newSchedule(fixture, now.plusSeconds(60));
    futureActive.activate(AssetStatus.ACTIVE, ChecklistTemplateStatus.ACTIVE);
    InspectionSchedule overduePaused = newSchedule(fixture, now.minusSeconds(120));
    inspectionScheduleRepository.saveAllAndFlush(
        java.util.List.of(overdueActive, futureActive, overduePaused));

    assertThat(
            inspectionScheduleRepository.findDueForUpdate(
                InspectionScheduleStatus.ACTIVE, now, PageRequest.of(0, 50)))
        .extracting(InspectionSchedule::getId)
        .containsExactly(overdueActive.getId());
  }

  private InspectionSchedule newSchedule(Fixture fixture, Instant nextDueAt) {
    return new InspectionSchedule(
        fixture.assetId(),
        fixture.checklistTemplateId(),
        InspectionFrequencyUnit.MONTH,
        1,
        nextDueAt,
        fixture.userId());
  }

  private Fixture persistFixture() {
    UUID organizationId = persistOrganization();
    UUID userId = persistUser(organizationId);
    AssetCategory category =
        assetCategoryRepository.saveAndFlush(
            new AssetCategory("bridges", "Bridges", "Bridge infrastructure", true));
    Asset asset =
        assetRepository.saveAndFlush(
            new Asset(
                organizationId,
                category.getId(),
                "bridge-01",
                "River Bridge",
                null,
                "District 1",
                null,
                null,
                null,
                userId));
    ChecklistTemplate template =
        new ChecklistTemplate("bridge-basic", 1, category.getId(), "Bridge Basic", null, userId);
    template.addItem(
        "surface-crack",
        null,
        "Check visible surface cracks",
        ChecklistResponseType.PASS_FAIL,
        true,
        0,
        null,
        null);
    template.publish();
    checklistTemplateRepository.saveAndFlush(template);
    return new Fixture(asset.getId(), template.getId(), userId);
  }

  private UUID persistOrganization() {
    UUID organizationId = UUID.randomUUID();
    String suffix = organizationId.toString();
    jdbcTemplate.update(
        "INSERT INTO organizations (id, name, code) VALUES (?, ?, ?)",
        organizationId,
        "Organization " + suffix,
        "ORG-" + suffix);
    return organizationId;
  }

  private UUID persistUser(UUID organizationId) {
    UUID userId = UUID.randomUUID();
    String email = "schedule-user-" + userId + "@example.test";
    jdbcTemplate.update(
        """
        INSERT INTO users (
          id, email, normalized_email, full_name, status, actor_zone,
          organization_id, auth_version, failed_login_count, must_change_password
        ) VALUES (?, ?, ?, ?, 'ACTIVE', 'CUSTOMER_ORGANIZATION', ?, 0, 0, FALSE)
        """,
        userId,
        email,
        email,
        "Schedule User",
        organizationId);
    return userId;
  }

  private record Fixture(UUID assetId, UUID checklistTemplateId, UUID userId) {}
}
