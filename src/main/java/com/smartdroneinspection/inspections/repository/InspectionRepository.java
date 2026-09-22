package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.Inspection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionRepository extends JpaRepository<Inspection, UUID> {

  Optional<Inspection> findByAcceptedAssignmentId(UUID acceptedAssignmentId);
}
