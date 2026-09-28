package com.smartdroneinspection.assets;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.InspectionSchedule;
import com.smartdroneinspection.assets.events.InspectionScheduleDue;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.assets.repository.InspectionScheduleRepository;
import com.smartdroneinspection.assets.service.InspectionScheduleDuePublisher;
import com.smartdroneinspection.users.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import({TestcontainersConfiguration.class, PeriodicRequestHandoffTest.CaptureConfig.class})
@Transactional
class PeriodicRequestHandoffTest {

  @Autowired AssetCategoryRepository categories;
  @Autowired ChecklistTemplateRepository templates;
  @Autowired AssetRepository assets;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired InspectionScheduleRepository schedules;
  @Autowired InspectionScheduleDuePublisher publisher;
  @Autowired EventCapture capture;

  private AssetTestFixture.Data fixture;

  @BeforeEach
  void setUp() {
    capture.clear();
    fixture = new AssetTestFixture(categories, templates, assets, users, jdbcTemplate).create();
  }

  @Test
  void publishesExactlyOneEventPerDueScheduleCycleAndReplayIsSilent() {
    InspectionSchedule schedule = dueSchedule();
    LocalDate dueCycle =
        schedule.getNextDueAt().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate();

    assertThat(publisher.publishDueSchedules()).isEqualTo(1);
    assertThat(capture.events()).hasSize(1);

    InspectionScheduleDue event = capture.events().get(0);
    assertThat(event.assetId()).isEqualTo(schedule.getAssetId());
    assertThat(event.scheduleId()).isEqualTo(schedule.getId());
    assertThat(event.organizationId()).isEqualTo(fixture.organizationId());
    assertThat(event.checklistTemplateVersionId()).isEqualTo(fixture.checklistTemplateId());
    assertThat(event.dueCycle()).isEqualTo(dueCycle);

    // second run: schedule advanced, nothing due — replay publishes nothing
    assertThat(publisher.publishDueSchedules()).isZero();
    assertThat(capture.events()).hasSize(1);

    // identity guard on the domain: an older cycle cannot regenerate
    InspectionSchedule reloaded = schedules.findById(schedule.getId()).orElseThrow();
    assertThat(reloaded.getLastGeneratedDueCycle()).isEqualTo(dueCycle);
  }

  @Test
  void pausedAndInactiveSchedulesNeverPublish() {
    InspectionSchedule schedule = dueSchedule();
    schedule.pause();
    schedules.saveAndFlush(schedule);

    assertThat(publisher.publishDueSchedules()).isZero();
    assertThat(capture.events()).isEmpty();
  }

  @Test
  void scheduledTriggerPublishesThroughTheTransactionalProxy() {
    dueSchedule();

    publisher.scheduledPublish();

    assertThat(capture.events()).hasSize(1);
  }

  private InspectionSchedule dueSchedule() {
    Asset asset =
        assets.saveAndFlush(
            new Asset(
                fixture.organizationId(),
                fixture.categoryId(),
                "DUE-" + UUID.randomUUID(),
                "Due asset",
                null,
                "District 1",
                null,
                null,
                null,
                fixture.clientId()));
    return schedules.saveAndFlush(
        InspectionSchedule.fromSelectedProposal(
            asset.getId(),
            fixture.checklistTemplateId(),
            "DAY",
            1,
            Instant.now().minusSeconds(3600),
            fixture.clientId()));
  }

  @TestConfiguration
  static class CaptureConfig {

    @Bean
    EventCapture eventCapture() {
      return new EventCapture();
    }
  }

  static class EventCapture {

    private final List<InspectionScheduleDue> events = new CopyOnWriteArrayList<>();

    @EventListener
    public void onDue(InspectionScheduleDue event) {
      events.add(event);
    }

    List<InspectionScheduleDue> events() {
      return List.copyOf(events);
    }

    void clear() {
      events.clear();
    }
  }
}
