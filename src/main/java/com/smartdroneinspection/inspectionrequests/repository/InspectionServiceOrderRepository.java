package com.smartdroneinspection.inspectionrequests.repository;

import com.smartdroneinspection.inspectionrequests.domain.InspectionServiceOrder;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionServiceOrderRepository
    extends JpaRepository<InspectionServiceOrder, UUID> {

  Optional<InspectionServiceOrder> findByApprovedQuotationId(UUID approvedQuotationId);

  Optional<InspectionServiceOrder> findByIdAndInspectionRequestId(
      UUID id, UUID inspectionRequestId);
}
