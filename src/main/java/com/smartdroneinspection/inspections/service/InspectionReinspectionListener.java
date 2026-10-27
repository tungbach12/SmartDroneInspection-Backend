package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.domain.Finding;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.repository.FindingRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.spi.ReinspectionRequestedEvent;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** MF4-18: Listens to ReinspectionRequestedEvent and creates a linked verification inspection. */
@Component
public class InspectionReinspectionListener {

  private static final Logger log = LoggerFactory.getLogger(InspectionReinspectionListener.class);

  private final InspectionRepository inspections;
  private final FindingRepository findings;

  public InspectionReinspectionListener(
      InspectionRepository inspections, FindingRepository findings) {
    this.inspections = inspections;
    this.findings = findings;
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

    Inspection reinspection =
        new Inspection(
            event.organizationId(),
            event.assetId(),
            sourceInspection != null ? sourceInspection.getInspectorId() : null,
            sourceInspection != null ? sourceInspection.getDroneId() : null,
            event.requestedAt() != null ? event.requestedAt() : Instant.now(),
            null);

    inspections.saveAndFlush(reinspection);
    log.info(
        "Created linked re-inspection {} in status {}",
        reinspection.getId(),
        reinspection.getStatus());
  }
}
