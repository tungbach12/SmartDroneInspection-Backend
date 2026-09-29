package com.smartdroneinspection.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.ChecklistTemplate;
import com.smartdroneinspection.assets.domain.InspectionSchedule;
import com.smartdroneinspection.assets.domain.enums.InspectionFrequencyUnit;
import com.smartdroneinspection.assets.domain.enums.InspectionScheduleStatus;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.assets.repository.InspectionScheduleRepository;
import com.smartdroneinspection.assets.service.InspectionScheduleService;
import com.smartdroneinspection.assets.service.ScheduleProposalService;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class InspectionScheduleServiceTest {

  @Autowired AssetCategoryRepository categories;
  @Autowired ChecklistTemplateRepository templates;
  @Autowired AssetRepository assets;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired InspectionScheduleRepository schedules;
  @Autowired InspectionScheduleService service;

  private AssetTestFixture.Data fixture;

  @BeforeEach
  void setUp() {
    fixture = new AssetTestFixture(categories, templates, assets, users, jdbcTemplate).create();
  }

  @Test
  void onlyScheduledAssetsAreListedAndPauseActivateRoundTrip() {
    UUID assetId = seedActiveAsset();
    InspectionSchedule schedule = seedSchedule(assetId);

    assertThat(service.listForClient(fixture.clientId(), assetId)).hasSize(1);

    service.pause(fixture.clientId(), schedule.getId());
    assertThat(schedules.findById(schedule.getId()).orElseThrow().getStatus())
        .isEqualTo(InspectionScheduleStatus.PAUSED);

    service.activate(fixture.clientId(), schedule.getId());
    assertThat(schedules.findById(schedule.getId()).orElseThrow().getStatus())
        .isEqualTo(InspectionScheduleStatus.ACTIVE);
  }

  @Test
  void inactiveAssetCannotProduceAnActiveSchedule() {
    UUID assetId = seedPendingAsset();
    Asset asset = assets.findById(assetId).orElseThrow();
    ChecklistTemplate template = templates.findById(fixture.checklistTemplateId()).orElseThrow();

    assertThatThrownBy(
            () ->
                new InspectionSchedule(
                        assetId,
                        fixture.checklistTemplateId(),
                        InspectionFrequencyUnit.MONTH,
                        3,
                        ScheduleProposalService.plusFrequency(Instant.now(), "MONTH", 3),
                        fixture.clientId())
                    .activate(asset.getStatus(), template.getStatus()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void crossOrgScheduleOperationsAreDenied() {
    UUID assetId = seedActiveAsset();
    InspectionSchedule schedule = seedSchedule(assetId);

    assertThatThrownBy(() -> service.pause(fixture.otherClientId(), schedule.getId()))
        .isInstanceOf(BusinessException.class);
  }

  private UUID seedActiveAsset() {
    return assets
        .saveAndFlush(
            new Asset(
                fixture.organizationId(),
                fixture.categoryId(),
                "ACT-" + UUID.randomUUID(),
                "Active asset",
                null,
                "District 1",
                null,
                null,
                null,
                fixture.clientId()))
        .getId();
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

  private InspectionSchedule seedSchedule(UUID assetId) {
    return schedules.saveAndFlush(
        InspectionSchedule.fromSelectedProposal(
            assetId,
            fixture.checklistTemplateId(),
            "MONTH",
            3,
            ScheduleProposalService.plusFrequency(Instant.now(), "MONTH", 3),
            fixture.clientId()));
  }
}
