package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.FieldSession;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FieldSessionRepository extends JpaRepository<FieldSession, UUID> {

  List<FieldSession> findByInspectionIdOrderByCreatedAtAsc(UUID inspectionId);

  Optional<FieldSession> findByIdAndInspectionId(UUID id, UUID inspectionId);
}
