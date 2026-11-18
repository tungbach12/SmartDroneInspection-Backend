package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.events.InspectionAssignmentTimedOutEvent;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.shared.api.InspectionContractSyncPort;
import com.smartdroneinspection.shared.api.PlatformOperationsPolicyPort;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Periodically checks for SCHEDULED inspections still in PENDING_ACCEPTANCE beyond the
 * Admin-configured acceptance window and automatically cancels the assignment.
 */
@Service
public class InspectionAssignmentTimeoutMonitor {

  private final InspectionRepository inspections;
  private final ApplicationEventPublisher eventPublisher;
  private final Optional<InspectionContractSyncPort> contractSync;
  private final Optional<PlatformOperationsPolicyPort> operationsPolicy;

  @Autowired
  public InspectionAssignmentTimeoutMonitor(
      InspectionRepository inspections,
      ApplicationEventPublisher eventPublisher,
      Optional<InspectionContractSyncPort> contractSync,
      Optional<PlatformOperationsPolicyPort> operationsPolicy) {
    this.inspections = inspections;
    this.eventPublisher = eventPublisher;
    this.contractSync = contractSync != null ? contractSync : Optional.empty();
    this.operationsPolicy = operationsPolicy != null ? operationsPolicy : Optional.empty();
  }

  @Scheduled(fixedDelayString = "${app.inspections.assignment-timeout-interval-ms:60000}")
  @Transactional
  public void expireUnacceptedAssignments() {
    expireUnacceptedAssignments(Instant.now());
  }

  @Transactional
  public int expireUnacceptedAssignments(Instant now) {
    int timeoutHours =
        operationsPolicy
            .map(PlatformOperationsPolicyPort::getAssignmentAcceptanceTimeoutHours)
            .orElse(PlatformOperationsPolicyPort.DEFAULT_ASSIGNMENT_ACCEPTANCE_TIMEOUT_HOURS);
    Instant cutoff = now.minus(Duration.ofHours(timeoutHours));
    List<Inspection> candidates = inspections.findPendingAcceptanceExpiredBefore(cutoff);
    int cancelledCount = 0;
    for (Inspection candidate : candidates) {
      Inspection locked = inspections.findWithLockById(candidate.getId()).orElse(null);
      if (locked == null || !locked.cancelForAcceptanceTimeout(timeoutHours)) {
        continue;
      }
      inspections.saveAndFlush(locked);
      UUID inspectorId = locked.getInspectorId();
      Optional<InspectionContractSyncPort.TimeoutRevertResult> revertResult =
          contractSync.flatMap(
              port -> port.syncTimeoutRevertFromInspection(locked.getId(), inspectorId, now));
      UUID requestId =
          revertResult.map(InspectionContractSyncPort.TimeoutRevertResult::requestId).orElse(null);
      UUID assignedManagerId =
          revertResult
              .map(InspectionContractSyncPort.TimeoutRevertResult::assignedManagerId)
              .orElse(null);
      eventPublisher.publishEvent(
          new InspectionAssignmentTimedOutEvent(
              locked.getId(),
              locked.getOwnerCustomerId(),
              inspectorId,
              requestId,
              assignedManagerId,
              timeoutHours,
              now));
      cancelledCount += 1;
    }
    return cancelledCount;
  }
}
