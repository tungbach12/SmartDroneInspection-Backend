package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.domain.InspectorReliabilityEvent.EventType;
import com.smartdroneinspection.inspections.repository.InspectorReliabilityEventRepository;
import com.smartdroneinspection.inspections.spi.PartnerReliabilityQuery;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PartnerReliabilityQueryAdapter implements PartnerReliabilityQuery {

  private final InspectorReliabilityEventRepository events;

  public PartnerReliabilityQueryAdapter(InspectorReliabilityEventRepository events) {
    this.events = events;
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
    Map<UUID, ReliabilityCounts> result = new HashMap<>();
    buckets.forEach(
        (id, counts) -> result.put(id, new ReliabilityCounts(counts[0], counts[1], counts[2])));
    return result;
  }
}
