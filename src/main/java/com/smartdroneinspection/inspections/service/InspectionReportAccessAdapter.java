package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
import com.smartdroneinspection.inspections.repository.InspectionReportRepository;
import com.smartdroneinspection.inspections.spi.InspectionReportAccess;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InspectionReportAccessAdapter implements InspectionReportAccess {

  private final InspectionReportRepository versions;

  public InspectionReportAccessAdapter(InspectionReportRepository versions) {
    this.versions = versions;
  }

  @Override
  public boolean isReportPublished(UUID inspectionId, UUID reportVersionId) {
    if (inspectionId == null || reportVersionId == null) {
      return false;
    }
    return versions
        .findById(reportVersionId)
        .filter(report -> inspectionId.equals(report.getInspectionId()))
        .filter(report -> report.getStatus() == ReportStatus.PUBLISHED)
        .isPresent();
  }
}
