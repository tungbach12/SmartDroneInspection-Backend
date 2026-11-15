package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.InspectorReliabilityEvent;
import com.smartdroneinspection.inspections.domain.InspectorReliabilityEvent.EventType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InspectorReliabilityEventRepository
    extends JpaRepository<InspectorReliabilityEvent, UUID> {

  boolean existsByInspectionIdAndInspectorIdAndEventType(
      UUID inspectionId, UUID inspectorId, EventType eventType);

  @Query(
      "select e.inspectorId as inspectorId, e.eventType as eventType, count(e) as total "
          + "from InspectorReliabilityEvent e "
          + "where e.inspectorId in :inspectorIds "
          + "group by e.inspectorId, e.eventType")
  List<InspectorReliabilityCountProjection> countByInspectorIds(
      @Param("inspectorIds") Collection<UUID> inspectorIds);

  interface InspectorReliabilityCountProjection {
    UUID getInspectorId();

    EventType getEventType();

    long getTotal();
  }
}
