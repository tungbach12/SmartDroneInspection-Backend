package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.FieldSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FieldSessionRepository extends JpaRepository<FieldSession, UUID> {

  List<FieldSession> findByInspectionIdOrderByCreatedAtAsc(UUID inspectionId);

  Optional<FieldSession> findByIdAndInspectionId(UUID id, UUID inspectionId);

  @Query(
      "select fs from FieldSession fs join Inspection i on fs.inspectionId = i.id "
          + "where fs.status = 'IN_PROGRESS' "
          + "and fs.hungNotifiedAt is null "
          + "and ((i.plannedEndAt is not null and i.plannedEndAt < :plannedEndCutoff) "
          + "or (i.plannedEndAt is null and fs.startedAt is not null and fs.startedAt < :startedAtCutoff))")
  List<FieldSession> findHungInProgressSessions(
      @Param("plannedEndCutoff") Instant plannedEndCutoff,
      @Param("startedAtCutoff") Instant startedAtCutoff);
}
