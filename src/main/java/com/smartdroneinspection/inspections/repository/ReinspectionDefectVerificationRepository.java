package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.ReinspectionDefectVerification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReinspectionDefectVerificationRepository
    extends JpaRepository<ReinspectionDefectVerification, UUID> {

  List<ReinspectionDefectVerification> findByReinspectionId(UUID reinspectionId);

  Optional<ReinspectionDefectVerification> findByReinspectionIdAndSourceFindingId(
      UUID reinspectionId, UUID sourceFindingId);
}
