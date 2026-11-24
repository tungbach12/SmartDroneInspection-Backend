package com.smartdroneinspection.inspections.spi;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Advisory partner inspector reliability counters exposed to MF2 candidate evaluation. */
public interface PartnerReliabilityQuery {

  Map<UUID, ReliabilityCounts> countsByInspectorIds(Collection<UUID> inspectorIds);

  record ReliabilityCounts(
      long declineCount,
      long noShowCount,
      long abortCount,
      Double averageRating,
      long ratingCount,
      Double customerAverageRating,
      Double managerAverageRating) {
    public static final ReliabilityCounts ZERO =
        new ReliabilityCounts(0L, 0L, 0L, null, 0L, null, null);

    public ReliabilityCounts(long declineCount, long noShowCount, long abortCount) {
      this(declineCount, noShowCount, abortCount, null, 0L, null, null);
    }
  }
}
