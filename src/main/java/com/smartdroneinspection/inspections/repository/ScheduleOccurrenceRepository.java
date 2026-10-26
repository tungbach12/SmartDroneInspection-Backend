package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.ScheduleOccurrence;
import com.smartdroneinspection.inspections.domain.enums.OccurrenceStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleOccurrenceRepository extends JpaRepository<ScheduleOccurrence, UUID> {

  Optional<ScheduleOccurrence> findByIdAndOrganizationId(UUID id, UUID organizationId);

  Optional<ScheduleOccurrence> findByScheduleIdAndOccurrenceIndex(
      UUID scheduleId, long occurrenceIndex);

  Optional<ScheduleOccurrence> findByScheduleIdAndOccurrenceKey(
      UUID scheduleId, String occurrenceKey);

  List<ScheduleOccurrence> findByScheduleIdOrderByOccurrenceIndexAsc(UUID scheduleId);

  long countByScheduleIdAndStatus(UUID scheduleId, OccurrenceStatus status);

  @Query(
      "select o from ScheduleOccurrence o "
          + "where o.status = com.smartdroneinspection.inspections.domain.enums.OccurrenceStatus.GENERATED "
          + "and o.reminderSentAt is null "
          + "and o.plannedStartAt > :now and o.plannedStartAt <= :windowEnd "
          + "order by o.plannedStartAt asc")
  List<ScheduleOccurrence> findDueForReminder(
      @Param("now") Instant now, @Param("windowEnd") Instant windowEnd, Pageable pageable);
}
