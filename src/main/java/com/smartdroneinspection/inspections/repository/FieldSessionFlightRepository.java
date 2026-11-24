package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.FieldSessionFlight;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FieldSessionFlightRepository extends JpaRepository<FieldSessionFlight, UUID> {

  List<FieldSessionFlight> findByFieldSessionIdOrderByFlightNumberAsc(UUID fieldSessionId);

  List<FieldSessionFlight> findByInspectionIdOrderByFlightDateAscStartedAtAscFlightNumberAsc(
      UUID inspectionId);

  Optional<FieldSessionFlight> findByIdAndFieldSessionIdAndInspectionId(
      UUID id, UUID fieldSessionId, UUID inspectionId);

  Optional<FieldSessionFlight> findTopByFieldSessionIdOrderByFlightNumberDesc(UUID fieldSessionId);
}
