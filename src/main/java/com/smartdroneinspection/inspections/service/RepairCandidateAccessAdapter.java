package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.InspectionReport;
import com.smartdroneinspection.inspections.domain.InspectionReportVersion;
import com.smartdroneinspection.inspections.domain.VerifiedFinding;
import com.smartdroneinspection.inspections.domain.enums.FindingDecision;
import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
import com.smartdroneinspection.inspections.repository.InspectionReportRepository;
import com.smartdroneinspection.inspections.repository.InspectionReportVersionRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.repository.VerifiedFindingRepository;
import com.smartdroneinspection.inspections.spi.RepairCandidate;
import com.smartdroneinspection.inspections.spi.RepairCandidateAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MF3's public read adapter for the repair candidates that a published report hands to MF4.
 * Organization filtering begins at the inspection repository; no other module reads inspections'
 * tables directly.
 */
@Service
public class RepairCandidateAccessAdapter implements RepairCandidateAccess {

  private final InspectionRepository inspections;
  private final InspectionReportRepository reports;
  private final InspectionReportVersionRepository reportVersions;
  private final VerifiedFindingRepository findings;

  public RepairCandidateAccessAdapter(
      InspectionRepository inspections,
      InspectionReportRepository reports,
      InspectionReportVersionRepository reportVersions,
      VerifiedFindingRepository findings) {
    this.inspections = inspections;
    this.reports = reports;
    this.reportVersions = reportVersions;
    this.findings = findings;
  }

  @Override
  @Transactional(readOnly = true)
  public List<RepairCandidate> findRepairCandidates(UUID organizationId) {
    List<RepairCandidate> candidates = new ArrayList<>();
    List<VerifiedFinding> repairRequired =
        findings.findByRepairRequiredTrueAndDecisionIn(
            List.of(FindingDecision.CONFIRMED, FindingDecision.MODIFIED));

    for (VerifiedFinding finding : repairRequired) {
      candidateFor(organizationId, finding).ifPresent(candidates::add);
    }
    return List.copyOf(candidates);
  }

  @Override
  @Transactional(readOnly = true)
  public java.util.Optional<RepairCandidate> findRepairCandidate(
      UUID organizationId, UUID reportVersionId, UUID findingId) {
    return findings
        .findById(findingId)
        .filter(
            finding ->
                finding.isRepairRequired()
                    && (finding.getDecision() == FindingDecision.CONFIRMED
                        || finding.getDecision() == FindingDecision.MODIFIED))
        .flatMap(finding -> candidateFor(organizationId, finding))
        .filter(candidate -> candidate.reportVersionId().equals(reportVersionId));
  }

  private java.util.Optional<RepairCandidate> candidateFor(
      UUID organizationId, VerifiedFinding finding) {
    Inspection inspection =
        inspections
            .findByIdAndOrganizationId(finding.getInspectionId(), organizationId)
            .orElse(null);
    if (inspection == null) {
      return java.util.Optional.empty();
    }
    InspectionReport report = reports.findByInspectionId(inspection.getId()).orElse(null);
    if (report == null) {
      return java.util.Optional.empty();
    }
    InspectionReportVersion version =
        reportVersions
            .findFirstByInspectionReportIdAndStatus(report.getId(), ReportStatus.PUBLISHED)
            .orElse(null);
    if (version == null) {
      return java.util.Optional.empty();
    }
    return java.util.Optional.of(
        new RepairCandidate(
            organizationId,
            inspection.getAssetId(),
            inspection.getId(),
            report.getId(),
            version.getId(),
            finding.getId(),
            finding.getFindingCode(),
            finding.getDefectLabel(),
            finding.getDescription(),
            finding.getSeverity().name(),
            finding.getRecommendedAction(),
            version.getPublishedAt()));
  }
}
