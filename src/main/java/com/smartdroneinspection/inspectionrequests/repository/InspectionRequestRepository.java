package com.smartdroneinspection.inspectionrequests.repository;

import com.smartdroneinspection.inspectionrequests.domain.InspectionRequest;
import com.smartdroneinspection.inspectionrequests.domain.InspectionRequestStatus;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionRequestRepository extends JpaRepository<InspectionRequest, UUID> {

  Optional<InspectionRequest> findByIdAndOrganizationId(UUID id, UUID organizationId);

  Optional<InspectionRequest> findByAssetIdAndScheduleIdAndDueCycle(
      UUID assetId, UUID scheduleId, LocalDate dueCycle);

  Page<InspectionRequest> findByOrganizationIdAndStatus(
      UUID organizationId, InspectionRequestStatus status, Pageable pageable);
}
