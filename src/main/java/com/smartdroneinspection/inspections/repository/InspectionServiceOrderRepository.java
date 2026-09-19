package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.InspectionServiceOrder;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionServiceOrderRepository
    extends JpaRepository<InspectionServiceOrder, UUID> {

  Optional<InspectionServiceOrder> findByApprovedQuotationId(UUID approvedQuotationId);

  Optional<InspectionServiceOrder> findByIdAndInspectionRequestId(
      UUID id, UUID inspectionRequestId);
}
