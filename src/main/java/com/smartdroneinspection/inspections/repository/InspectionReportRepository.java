package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.InspectionReport;
import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionReportRepository extends JpaRepository<InspectionReport, UUID> {

  Optional<InspectionReport> findByInspectionIdAndVersionNo(UUID inspectionId, int versionNo);

  Optional<InspectionReport> findFirstByInspectionIdOrderByVersionNoDesc(UUID inspectionId);

  List<InspectionReport> findByInspectionIdOrderByVersionNoDesc(UUID inspectionId);

  List<InspectionReport> findByInspectionIdInOrderByVersionNoDesc(List<UUID> inspectionIds);

  Optional<InspectionReport> findFirstByInspectionIdAndStatus(
      UUID inspectionId, ReportStatus status);

  List<InspectionReport> findByInspectionIdAndStatus(UUID inspectionId, ReportStatus status);
}
