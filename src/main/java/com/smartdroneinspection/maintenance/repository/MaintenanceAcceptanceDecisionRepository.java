package com.smartdroneinspection.maintenance.repository;

import com.smartdroneinspection.maintenance.domain.MaintenanceAcceptanceDecision;
import com.smartdroneinspection.maintenance.domain.enums.AcceptanceDecisionKind;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceptance decisions are append-only: there is no update path, and a corrected decision is a new
 * row rather than an edit of an earlier one.
 */
public interface MaintenanceAcceptanceDecisionRepository
    extends JpaRepository<MaintenanceAcceptanceDecision, UUID> {

  List<MaintenanceAcceptanceDecision> findByWorkOrderIdOrderByDecidedAtDesc(UUID workOrderId);

  Optional<MaintenanceAcceptanceDecision> findFirstByWorkOrderIdOrderByDecidedAtDesc(
      UUID workOrderId);

  Optional<MaintenanceAcceptanceDecision> findByWorkOrderIdAndDecision(
      UUID workOrderId, AcceptanceDecisionKind decision);
}
