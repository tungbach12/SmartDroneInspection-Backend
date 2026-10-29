package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.spi.Mf2InspectionAssignment;
import com.smartdroneinspection.inspections.spi.ReinspectionInspectionAccess;
import com.smartdroneinspection.inspections.spi.ReinspectionInspectionAssignment;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReinspectionInspectionAdapter implements ReinspectionInspectionAccess {

  private final Mf2InspectionAssignment mf2Assignment;

  public ReinspectionInspectionAdapter(Mf2InspectionAssignment mf2Assignment) {
    this.mf2Assignment = mf2Assignment;
  }

  @Override
  @Transactional
  public UUID createAssignedInspection(ReinspectionInspectionAssignment assignment) {
    return mf2Assignment.assign(
        assignment.organizationId(),
        assignment.assetId(),
        assignment.inspectorId(),
        assignment.droneId(),
        assignment.plannedStartAt(),
        assignment.plannedEndAt(),
        assignment.managerUserId());
  }
}
