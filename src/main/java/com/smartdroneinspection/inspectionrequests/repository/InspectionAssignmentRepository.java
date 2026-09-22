package com.smartdroneinspection.inspectionrequests.repository;

import com.smartdroneinspection.inspectionrequests.domain.InspectionAssignment;
import com.smartdroneinspection.inspectionrequests.domain.enums.InspectionAssignmentStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InspectionAssignmentRepository extends JpaRepository<InspectionAssignment, UUID> {

  List<InspectionAssignment> findByInspectorUserIdAndStatusOrderByDeadlineAsc(
      UUID inspectorUserId, InspectionAssignmentStatus status);

  Optional<InspectionAssignment> findByIdAndInspectorUserId(UUID id, UUID inspectorUserId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select assignment from InspectionAssignment assignment "
          + "where assignment.id = :assignmentId and assignment.inspectorUserId = :inspectorUserId")
  Optional<InspectionAssignment> findForUpdateByIdAndInspectorUserId(
      @Param("assignmentId") UUID assignmentId, @Param("inspectorUserId") UUID inspectorUserId);

  List<InspectionAssignment> findByServiceOrderIdOrderByCreatedAtDesc(UUID serviceOrderId);
}
