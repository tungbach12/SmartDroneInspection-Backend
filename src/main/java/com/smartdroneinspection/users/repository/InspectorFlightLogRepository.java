package com.smartdroneinspection.users.repository;

import com.smartdroneinspection.users.domain.InspectorFlightLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectorFlightLogRepository extends JpaRepository<InspectorFlightLog, UUID> {
  List<InspectorFlightLog> findByInspectorUserIdOrderByFlightDateDesc(UUID inspectorUserId);

  boolean existsByFlightCode(String flightCode);
}
