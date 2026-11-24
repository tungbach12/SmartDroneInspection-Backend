package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.domain.InspectorReliabilityEvent.EventType;
import com.smartdroneinspection.inspections.domain.enums.RaterRole;
import com.smartdroneinspection.inspections.repository.InspectorRatingRepository;
import com.smartdroneinspection.inspections.repository.InspectorReliabilityEventRepository;
import com.smartdroneinspection.inspections.spi.PartnerReliabilityQuery;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PartnerReliabilityQueryAdapter implements PartnerReliabilityQuery {

  private final InspectorReliabilityEventRepository events;
  private final InspectorRatingRepository ratings;

  public PartnerReliabilityQueryAdapter(InspectorReliabilityEventRepository events) {
    this(events, null);
  }

  @Autowired
  public PartnerReliabilityQueryAdapter(
      InspectorReliabilityEventRepository events, InspectorRatingRepository ratings) {
    this.events = events;
    this.ratings = ratings;
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, ReliabilityCounts> countsByInspectorIds(Collection<UUID> inspectorIds) {
    if (inspectorIds == null || inspectorIds.isEmpty()) {
      return Map.of();
    }
    Map<UUID, long[]> buckets = new HashMap<>();
    for (UUID id : inspectorIds) {
      if (id != null) {
        buckets.put(id, new long[3]);
      }
    }
    for (var row : events.countByInspectorIds(buckets.keySet())) {
      long[] counts = buckets.get(row.getInspectorId());
      if (counts == null) {
        continue;
      }
      if (row.getEventType() == EventType.DECLINE) {
        counts[0] += row.getTotal();
      } else if (row.getEventType() == EventType.NO_SHOW) {
        counts[1] += row.getTotal();
      } else if (row.getEventType() == EventType.ABORT
          || row.getEventType() == EventType.FORCE_ABORT) {
        counts[2] += row.getTotal();
      }
    }

    // [totalCount, totalSum, customerCount, customerSum, managerCount, managerSum]
    Map<UUID, long[]> ratingBuckets = new HashMap<>();
    if (ratings != null && !buckets.isEmpty()) {
      for (var row : ratings.summarizeByInspectorIds(buckets.keySet())) {
        long[] acc = ratingBuckets.computeIfAbsent(row.getInspectorId(), ignored -> new long[6]);
        acc[0] += row.getRatingCount();
        acc[1] += row.getScoreSum();
        if (row.getRaterRole() == RaterRole.CUSTOMER) {
          acc[2] += row.getRatingCount();
          acc[3] += row.getScoreSum();
        } else if (row.getRaterRole() == RaterRole.MANAGER) {
          acc[4] += row.getRatingCount();
          acc[5] += row.getScoreSum();
        }
      }
    }

    Map<UUID, ReliabilityCounts> result = new HashMap<>();
    buckets.forEach(
        (id, counts) -> {
          long[] r = ratingBuckets.get(id);
          Double avg = r != null && r[0] > 0 ? round2((double) r[1] / r[0]) : null;
          long ratingCount = r != null ? r[0] : 0L;
          Double custAvg = r != null && r[2] > 0 ? round2((double) r[3] / r[2]) : null;
          Double mgrAvg = r != null && r[4] > 0 ? round2((double) r[5] / r[4]) : null;
          result.put(
              id,
              new ReliabilityCounts(
                  counts[0], counts[1], counts[2], avg, ratingCount, custAvg, mgrAvg));
        });
    return result;
  }

  private static double round2(double value) {
    return Math.round(value * 100.0) / 100.0;
  }
}
