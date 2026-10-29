package com.smartdroneinspection.contracts.repository;

import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServiceRequestOccurrenceRepository
    extends JpaRepository<ServiceRequestOccurrence, UUID> {

  List<ServiceRequestOccurrence> findByRequestIdOrderByOccurrenceIndexAsc(UUID requestId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select occurrence from ServiceRequestOccurrence occurrence where occurrence.id = :id")
  Optional<ServiceRequestOccurrence> findWithLockById(@Param("id") UUID id);

  @Query(
      "select count(occurrence) > 0 from ServiceRequestOccurrence occurrence "
          + "where (occurrence.inspectorId = :inspectorId or occurrence.droneId = :droneId) "
          + "and occurrence.status = com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence.OccurrenceStatus.ASSIGNED "
          + "and occurrence.plannedStartAt < :end and occurrence.plannedEndAt > :start")
  boolean hasScheduleConflict(
      @Param("inspectorId") UUID inspectorId,
      @Param("droneId") UUID droneId,
      @Param("start") java.time.Instant start,
      @Param("end") java.time.Instant end);
}
