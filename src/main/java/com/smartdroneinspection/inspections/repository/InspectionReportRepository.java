package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.InspectionReport;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionReportRepository extends JpaRepository<InspectionReport, UUID> {

  Optional<InspectionReport> findByInspectionId(UUID inspectionId);

  List<InspectionReport> findByInspectionIdIn(List<UUID> inspectionIds);

  List<InspectionReport> findByAuthorUserIdOrderByUpdatedAtDesc(UUID authorUserId);
}
