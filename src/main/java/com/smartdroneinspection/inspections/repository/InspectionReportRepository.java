package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.InspectionReport;
import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InspectionReportRepository extends JpaRepository<InspectionReport, UUID> {

  Optional<InspectionReport> findByInspectionId(UUID inspectionId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select report from InspectionReport report where report.id = :reportId")
  Optional<InspectionReport> findForUpdateById(@Param("reportId") UUID reportId);

  List<InspectionReport> findByAuthorUserIdOrderByUpdatedAtDesc(UUID authorUserId);

  List<InspectionReport> findAllByOrderByUpdatedAtDesc();

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select report from InspectionReport report " + "where report.inspectionId = :inspectionId")
  Optional<InspectionReport> findForUpdateByInspectionId(@Param("inspectionId") UUID inspectionId);

  @Query(
      "select report from InspectionReport report, Inspection inspection, "
          + "InspectionServiceOrder serviceOrder, InspectionRequest request "
          + "where report.inspectionId = inspection.id "
          + "and inspection.serviceOrderId = serviceOrder.id "
          + "and serviceOrder.inspectionRequestId = request.id "
          + "and request.organizationId = :organizationId "
          + "and report.status in :statuses "
          + "order by report.updatedAt desc")
  List<InspectionReport> findVisibleToOrganization(
      @Param("organizationId") UUID organizationId,
      @Param("statuses") Collection<ReportStatus> statuses);
}
