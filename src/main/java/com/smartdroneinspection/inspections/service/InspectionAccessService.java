package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.InspectionAccess;
import com.smartdroneinspection.inspections.domain.Evidence;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.InspectionReport;
import com.smartdroneinspection.inspections.domain.ReportVersion;
import com.smartdroneinspection.inspections.domain.VerifiedFinding;
import com.smartdroneinspection.inspections.domain.enums.EvidenceKind;
import com.smartdroneinspection.inspections.domain.enums.EvidenceSource;
import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
import com.smartdroneinspection.inspections.domain.enums.UploadStatus;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.repository.InspectionReportRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.repository.ReportVersionRepository;
import com.smartdroneinspection.inspections.repository.VerifiedFindingRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspectionAccessService implements InspectionAccess {

  private final ReportVersionRepository reportVersions;
  private final InspectionReportRepository reports;
  private final InspectionRepository inspections;
  private final VerifiedFindingRepository findings;
  private final EvidenceRepository evidenceRepository;

  public InspectionAccessService(
      ReportVersionRepository reportVersions,
      InspectionReportRepository reports,
      InspectionRepository inspections,
      VerifiedFindingRepository findings,
      EvidenceRepository evidenceRepository) {
    this.reportVersions = reportVersions;
    this.reports = reports;
    this.inspections = inspections;
    this.findings = findings;
    this.evidenceRepository = evidenceRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<AcceptedReportSnapshot> findAcceptedReportVersion(UUID reportVersionId) {
    Optional<ReportVersion> versionOpt = reportVersions.findById(reportVersionId);
    if (versionOpt.isEmpty() || versionOpt.get().getStatus() != ReportStatus.ACCEPTED) {
      return Optional.empty();
    }
    ReportVersion version = versionOpt.get();
    Optional<InspectionReport> reportOpt = reports.findById(version.getReportId());
    if (reportOpt.isEmpty()) {
      return Optional.empty();
    }
    InspectionReport report = reportOpt.get();
    Optional<Inspection> inspectionOpt = inspections.findById(report.getInspectionId());
    if (inspectionOpt.isEmpty()) {
      return Optional.empty();
    }
    Inspection inspection = inspectionOpt.get();
    Set<UUID> findingIds =
        findings.findByInspectionIdOrderByCreatedAtAsc(inspection.getId()).stream()
            .map(VerifiedFinding::getId)
            .collect(Collectors.toSet());

    return Optional.of(
        new AcceptedReportSnapshot(
            version.getId(),
            report.getId(),
            inspection.getId(),
            inspection.getAssetId(),
            findingIds));
  }

  @Override
  @Transactional
  public UUID createMaintenanceEvidence(
      UUID uploadedByUserId,
      String evidenceKind,
      String fileName,
      String contentType,
      long sizeBytes,
      String checksumSha256,
      String objectKey) {
    EvidenceKind kind = EvidenceKind.valueOf(evidenceKind);
    Evidence evidence =
        new Evidence(
            null,
            null,
            uploadedByUserId,
            kind,
            fileName,
            contentType,
            sizeBytes,
            checksumSha256,
            objectKey,
            EvidenceSource.MOBILE_UPLOAD,
            UploadStatus.AVAILABLE);
    return evidenceRepository.save(evidence).getId();
  }

  @Override
  @Transactional
  public void linkEvidenceToWorkLog(UUID evidenceId, UUID workLogId) {
    evidenceRepository.findById(evidenceId).ifPresent(e -> e.attachMaintenanceWorkLog(workLogId));
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsEvidence(UUID evidenceId) {
    return evidenceRepository.existsById(evidenceId);
  }
}
