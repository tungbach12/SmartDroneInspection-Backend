package com.smartdroneinspection.maintenance.repository;

import com.smartdroneinspection.maintenance.domain.ReinspectionRequest;
import com.smartdroneinspection.maintenance.domain.enums.ReinspectionRequestStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReinspectionRequestRepository extends JpaRepository<ReinspectionRequest, UUID> {

  Optional<ReinspectionRequest> findByIdAndOrganizationId(UUID id, UUID organizationId);

  Optional<ReinspectionRequest> findByIdAndManagerUserId(UUID id, UUID managerUserId);

  Optional<ReinspectionRequest> findByInspectionId(UUID inspectionId);

  List<ReinspectionRequest> findByStatusOrderByRequestedAtAsc(
      ReinspectionRequestStatus status, Pageable pageable);

  List<ReinspectionRequest> findByManagerUserIdOrderByRequestedAtDesc(UUID managerUserId);

  boolean existsByWorkOrderIdAndStatusIn(
      UUID workOrderId, List<ReinspectionRequestStatus> statuses);
}
