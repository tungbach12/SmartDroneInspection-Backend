package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.InspectionAssignment;
import com.smartdroneinspection.inspections.domain.InspectionAssignmentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionAssignmentRepository extends JpaRepository<InspectionAssignment, UUID> {

  List<InspectionAssignment> findByInspectorUserIdAndStatusOrderByDeadlineAsc(
      UUID inspectorUserId, InspectionAssignmentStatus status);

  Optional<InspectionAssignment> findByIdAndInspectorUserId(UUID id, UUID inspectorUserId);

  List<InspectionAssignment> findByServiceOrderIdOrderByCreatedAtDesc(UUID serviceOrderId);
}
