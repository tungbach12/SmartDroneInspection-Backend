package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.InspectionReportVersion;
import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionReportVersionRepository
    extends JpaRepository<InspectionReportVersion, UUID> {

  Optional<InspectionReportVersion> findByInspectionIdAndVersionNo(
      UUID inspectionId, int versionNo);

  Optional<InspectionReportVersion> findFirstByInspectionIdOrderByVersionNoDesc(UUID inspectionId);

  List<InspectionReportVersion> findByInspectionIdOrderByVersionNoDesc(UUID inspectionId);

  List<InspectionReportVersion> findByInspectionIdInOrderByVersionNoDesc(List<UUID> inspectionIds);

  Optional<InspectionReportVersion> findFirstByInspectionIdAndStatus(
      UUID inspectionId, ReportStatus status);

  List<InspectionReportVersion> findByInspectionIdAndStatus(UUID inspectionId, ReportStatus status);
}
