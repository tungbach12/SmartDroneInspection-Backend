package com.smartdroneinspection.assets.service;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.InspectionSchedule;
import com.smartdroneinspection.assets.domain.enums.InspectionScheduleStatus;
import com.smartdroneinspection.assets.events.InspectionScheduleDue;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.InspectionScheduleRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes one {@link InspectionScheduleDue} event per due schedule cycle.
 *
 * <p>Idempotency: {@code markGenerated} advances {@code nextDueAt} and records {@code
 * lastGeneratedDueCycle} in the same transaction as the publish, so a replayed run cannot publish
 * the same cycle twice.
 */
@Component
public class InspectionScheduleDuePublisher {

  private static final Logger LOG = LoggerFactory.getLogger(InspectionScheduleDuePublisher.class);
  private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  private static final int BATCH_SIZE = 50;

  private final InspectionScheduleRepository schedules;
  private final AssetRepository assets;
  private final ApplicationEventPublisher events;
  private final ObjectProvider<InspectionScheduleDuePublisher> self;

  public InspectionScheduleDuePublisher(
      InspectionScheduleRepository schedules,
      AssetRepository assets,
      ApplicationEventPublisher events,
      ObjectProvider<InspectionScheduleDuePublisher> self) {
    this.schedules = schedules;
    this.assets = assets;
    this.events = events;
    this.self = self;
  }

  @Scheduled(fixedDelayString = "PT1M")
  public void scheduledPublish() {
    // publish through the proxy: a plain this.publishDueSchedules() call would bypass
    // the @Transactional advice and fail with "No active transaction"
    int published = self.getObject().publishDueSchedules();
    if (published > 0) {
      LOG.info("Published {} inspection schedule due events", published);
    }
  }

  @Transactional
  public int publishDueSchedules() {
    Instant now = Instant.now();
    List<InspectionSchedule> due =
        schedules.findDueForUpdate(
            InspectionScheduleStatus.ACTIVE, now, PageRequest.of(0, BATCH_SIZE));
    int published = 0;
    for (InspectionSchedule schedule : due) {
      Asset asset = assets.findById(schedule.getAssetId()).orElse(null);
      if (asset == null) {
        continue;
      }
      LocalDate dueCycle = schedule.getNextDueAt().atZone(BUSINESS_ZONE).toLocalDate();
      events.publishEvent(
          new InspectionScheduleDue(
              asset.getOrganizationId(),
              asset.getId(),
              schedule.getId(),
              schedule.getChecklistTemplateId(),
              dueCycle));
      schedule.markGenerated(
          dueCycle,
          ScheduleProposalService.plusFrequency(
              now, schedule.getFrequencyUnit().name(), schedule.getFrequencyInterval()));
      schedules.saveAndFlush(schedule);
      published++;
    }
    return published;
  }
}
