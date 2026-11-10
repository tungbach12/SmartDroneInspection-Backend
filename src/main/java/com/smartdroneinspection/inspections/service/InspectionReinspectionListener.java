package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.domain.Finding;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.ReinspectionDefectVerification;
import com.smartdroneinspection.inspections.repository.FindingRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.repository.ReinspectionDefectVerificationRepository;
import com.smartdroneinspection.inspections.spi.ReinspectionCreatedEvent;
import com.smartdroneinspection.inspections.spi.ReinspectionRequestedEvent;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** MF4-18: Listens to ReinspectionRequestedEvent and creates a linked verification inspection. */
@Component
public class InspectionReinspectionListener {

  private static final Logger log = LoggerFactory.getLogger(InspectionReinspectionListener.class);

  private final InspectionRepository inspections;
  private final FindingRepository findings;
  private final ReinspectionDefectVerificationRepository verifications;
  private final ApplicationEventPublisher events;

  public InspectionReinspectionListener(
      InspectionRepository inspections,
      FindingRepository findings,
      ReinspectionDefectVerificationRepository verifications,
      ApplicationEventPublisher events) {
    this.inspections = inspections;
    this.findings = findings;
    this.verifications = verifications;
    this.events = events;
  }

  @ApplicationModuleListener
  public void onReinspectionRequested(ReinspectionRequestedEvent event) {
    log.info(
        "Received reinspection request for work order {} and finding {}",
        event.workOrderId(),
        event.sourceFindingId());

    Optional<Finding> findingOpt = findings.findById(event.sourceFindingId());
    String defectLabel = findingOpt.map(Finding::getDefectLabel).orElse("Unspecified defect");
    String objective =
        "Re-inspection for Work Order "
            + event.workOrderId()
            + " ("
            + defectLabel
            + "): "
            + (event.technicalComments() != null
                ? event.technicalComments()
                : "Verification required");

    Inspection sourceInspection =
        findingOpt.flatMap(f -> inspections.findById(f.getInspectionId())).orElse(null);

    java.util.List<Inspection> priorReinspections =
        event.workOrderId() != null
            ? inspections.findByWorkOrderIdOrderByCreatedAtAsc(event.workOrderId())
            : java.util.List.of();
    int roundNumber = priorReinspections.size() + 1;

    Inspection reinspection =
        Inspection.createReinspection(
            event.organizationId(),
            event.assetId(),
            sourceInspection != null ? sourceInspection.getInspectorId() : null,
            sourceInspection != null ? sourceInspection.getDroneId() : null,
            event.requestedAt() != null ? event.requestedAt() : Instant.now(),
            event.workOrderId(),
            sourceInspection != null ? sourceInspection.getId() : null,
            event.sourceReportVersionId());

    reinspection = inspections.saveAndFlush(reinspection);

    ReinspectionDefectVerification verification =
        new ReinspectionDefectVerification(reinspection.getId(), event.sourceFindingId());

    StringBuilder notesBuilder = new StringBuilder();
    notesBuilder.append("[Lần kiểm tra lại #").append(roundNumber).append("] ");
    if (event.technicalComments() != null && !event.technicalComments().isBlank()) {
      notesBuilder.append("Yêu cầu kiểm tra: ").append(event.technicalComments().trim());
    } else {
      notesBuilder.append("Kiểm tra xác minh sau sửa chữa bảo trì.");
    }

    java.util.List<ReinspectionDefectVerification> priorVerifications =
        verifications.findBySourceFindingIdOrderByCreatedAtAsc(event.sourceFindingId());
    int priorIdx = 1;
    for (ReinspectionDefectVerification prev : priorVerifications) {
      if (prev.getInspectorNotes() != null && !prev.getInspectorNotes().isBlank()) {
        notesBuilder
            .append("\n• Kết quả lần #")
            .append(priorIdx)
            .append(" (")
            .append(prev.getResolutionStatus())
            .append("): ")
            .append(prev.getInspectorNotes().trim());
      }
      priorIdx++;
    }
    verification.setRepairProof(event.repairProofUrl(), notesBuilder.toString());
    verifications.saveAndFlush(verification);

    events.publishEvent(new ReinspectionCreatedEvent(event.workOrderId(), reinspection.getId()));

    log.info(
        "Created linked re-inspection {} (round #{}) in status {}",
        reinspection.getId(),
        roundNumber,
        reinspection.getStatus());
  }
}
