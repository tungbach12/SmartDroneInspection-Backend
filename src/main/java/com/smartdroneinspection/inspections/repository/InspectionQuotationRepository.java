package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.InspectionQuotation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionQuotationRepository extends JpaRepository<InspectionQuotation, UUID> {

  Optional<InspectionQuotation> findByIdAndInspectionRequestId(UUID id, UUID inspectionRequestId);

  List<InspectionQuotation> findByQuotationSeriesIdOrderByVersionNumberDesc(UUID quotationSeriesId);

  Optional<InspectionQuotation> findByQuotationSeriesIdAndVersionNumber(
      UUID quotationSeriesId, int versionNumber);
}
