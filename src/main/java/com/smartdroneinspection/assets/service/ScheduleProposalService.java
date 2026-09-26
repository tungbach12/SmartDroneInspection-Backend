package com.smartdroneinspection.assets.service;

import com.smartdroneinspection.assets.api.dto.request.ReviewProposalRequest;
import com.smartdroneinspection.assets.api.dto.response.ScheduleProposalResponse;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.InspectionSchedule;
import com.smartdroneinspection.assets.domain.ScheduleProposal;
import com.smartdroneinspection.assets.domain.enums.InspectionScheduleStatus;
import com.smartdroneinspection.assets.domain.enums.ScheduleProposalStatus;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.InspectionScheduleRepository;
import com.smartdroneinspection.assets.repository.ScheduleProposalRepository;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleProposalService {

  private final ScheduleProposalRepository proposals;
  private final AssetRepository assets;
  private final InspectionScheduleRepository schedules;
  private final UserAccess userAccess;

  public ScheduleProposalService(
      ScheduleProposalRepository proposals,
      AssetRepository assets,
      InspectionScheduleRepository schedules,
      UserAccess userAccess) {
    this.proposals = proposals;
    this.assets = assets;
    this.schedules = schedules;
    this.userAccess = userAccess;
  }

  @Transactional(readOnly = true)
  public List<ScheduleProposalResponse> listForClient(UUID actorId, UUID assetId) {
    Asset asset = requireOwnedAsset(actorId, assetId);
    return proposals
        .findByAssetIdAndStatus(asset.getId(), ScheduleProposalStatus.MANAGER_APPROVED)
        .stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<ScheduleProposalResponse> listForManager(UUID assetId) {
    requireAssetExists(assetId);
    return proposals.findByAssetIdOrderByCreatedAtAsc(assetId).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public ScheduleProposalResponse review(
      UUID managerId, UUID proposalId, ReviewProposalRequest request) {
    ScheduleProposal proposal =
        proposals.findWithLockById(proposalId).orElseThrow(() -> notFound(proposalId));
    if ("APPROVE".equals(request.action())) {
      if ((request.frequencyUnit() == null) != (request.frequencyInterval() == null)) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_FAILED",
            "frequencyUnit and frequencyInterval must be provided together");
      }
      if (request.frequencyUnit() != null && request.frequencyInterval() != null) {
        proposal.managerAdjust(request.frequencyUnit(), request.frequencyInterval());
      }
      proposal.managerApprove(request.note(), managerId);
    } else if ("REJECT".equals(request.action())) {
      proposal.managerReject(request.note(), managerId);
    } else {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Unknown review action");
    }
    return toResponse(proposals.saveAndFlush(proposal));
  }

  @Transactional
  public ScheduleProposalResponse select(UUID clientId, UUID proposalId) {
    ScheduleProposal proposal =
        proposals.findWithLockById(proposalId).orElseThrow(() -> notFound(proposalId));
    Asset asset = requireOwnedAsset(clientId, proposal.getAssetId());
    assets.findWithLockById(asset.getId()).orElseThrow(() -> notFound(asset.getId()));

    boolean alreadyScheduled =
        schedules.findByAssetIdOrderByNextDueAt(asset.getId()).stream()
            .anyMatch(s -> s.getStatus() == InspectionScheduleStatus.ACTIVE);
    if (alreadyScheduled) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "INVALID_STATE", "Asset already has an active schedule");
    }

    proposal.clientSelect(clientId);
    proposals.saveAndFlush(proposal);
    for (ScheduleProposal sibling :
        proposals.findByAssetIdAndStatus(
            proposal.getAssetId(), ScheduleProposalStatus.MANAGER_APPROVED)) {
      sibling.supersede();
      proposals.save(sibling);
    }

    InspectionSchedule schedule =
        InspectionSchedule.fromSelectedProposal(
            proposal.getAssetId(),
            proposal.getChecklistTemplateId(),
            proposal.getFrequencyUnit(),
            proposal.getFrequencyInterval(),
            plusFrequency(
                Instant.now(), proposal.getFrequencyUnit(), proposal.getFrequencyInterval()),
            clientId);
    schedules.saveAndFlush(schedule);
    return toResponse(proposal);
  }

  public static Instant plusFrequency(Instant from, String unit, int interval) {
    return switch (unit) {
      case "DAY" -> from.plus(interval, ChronoUnit.DAYS);
      case "WEEK" -> from.plus(interval * 7L, ChronoUnit.DAYS);
      case "MONTH" -> from.plus(interval * 30L, ChronoUnit.DAYS);
      case "YEAR" -> from.plus(interval * 365L, ChronoUnit.DAYS);
      default -> throw new IllegalArgumentException("Unsupported frequency unit: " + unit);
    };
  }

  private Asset requireOwnedAsset(UUID actorId, UUID assetId) {
    UserAccess.ActiveUser actor =
        userAccess
            .findActiveUser(actorId)
            .orElseThrow(
                () ->
                    new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "User is not active"));
    if (actor.organizationId() == null) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "No organization scope");
    }
    return assets
        .findByIdAndOrganizationId(assetId, actor.organizationId())
        .orElseThrow(() -> notFound(assetId));
  }

  private void requireAssetExists(UUID assetId) {
    assets.findById(assetId).orElseThrow(() -> notFound(assetId));
  }

  private BusinessException notFound(UUID id) {
    return new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Proposal not found: " + id);
  }

  private ScheduleProposalResponse toResponse(ScheduleProposal proposal) {
    return new ScheduleProposalResponse(
        proposal.getId(),
        proposal.getAssetId(),
        proposal.getChecklistTemplateId(),
        proposal.getFrequencyUnit(),
        proposal.getFrequencyInterval(),
        proposal.getStatus().name(),
        proposal.getManagerNote());
  }
}
