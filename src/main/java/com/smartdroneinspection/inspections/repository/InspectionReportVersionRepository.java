package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.InspectionReportVersion;
import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionReportVersionRepository
    extends JpaRepository<InspectionReportVersion, UUID> {

  Optional<InspectionReportVersion> findByInspectionReportIdAndVersionNo(
      UUID inspectionReportId, int versionNo);

  List<InspectionReportVersion> findByInspectionReportIdOrderByVersionNoDesc(
      UUID inspectionReportId);

  Optional<InspectionReportVersion> findFirstByInspectionReportIdAndStatus(
      UUID inspectionReportId, ReportStatus status);

  List<InspectionReportVersion> findByInspectionReportIdAndStatus(
      UUID inspectionReportId, ReportStatus status);
}
