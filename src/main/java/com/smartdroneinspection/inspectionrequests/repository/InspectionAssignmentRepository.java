package com.smartdroneinspection.inspectionrequests.repository;

import com.smartdroneinspection.inspectionrequests.domain.InspectionAssignment;
import com.smartdroneinspection.inspectionrequests.domain.InspectionAssignmentStatus;
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
