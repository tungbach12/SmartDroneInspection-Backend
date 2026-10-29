package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.spi.ReinspectionInspectionAccess;
import com.smartdroneinspection.inspections.spi.ReinspectionInspectionAssignment;
import com.smartdroneinspection.shared.exception.BusinessException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReinspectionInspectionAdapter implements ReinspectionInspectionAccess {

  private final InspectionCreationService creationService;
  private final InspectionRepository inspections;
  private final AssetRepository assets;

  public ReinspectionInspectionAdapter(
      InspectionCreationService creationService,
      InspectionRepository inspections,
      AssetRepository assets) {
    this.creationService = creationService;
    this.inspections = inspections;
    this.assets = assets;
  }

  @Override
  @Transactional
  public UUID createAssignedInspection(ReinspectionInspectionAssignment assignment) {
    Asset asset =
        assets
            .findByIdAndOrganizationId(assignment.assetId(), assignment.organizationId())
            .orElseThrow(
                () -> new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Asset not found"));
    Inspection inspection =
        creationService.dispatchReinspectionInternal(
            assignment.organizationId(),
            assignment.assetId(),
            assignment.inspectorId(),
            assignment.droneId(),
            assignment.managerUserId(),
            assignment.plannedStartAt(),
            assignment.plannedEndAt());
    return inspection.getId();
  }
}
