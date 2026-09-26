package com.smartdroneinspection.assets.service;

import com.smartdroneinspection.assets.api.dto.request.ReviewAssetRequest;
import com.smartdroneinspection.assets.api.dto.response.AssetReviewResponse;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.ChecklistTemplate;
import com.smartdroneinspection.assets.domain.ScheduleProposal;
import com.smartdroneinspection.assets.domain.enums.AssetStatus;
import com.smartdroneinspection.assets.domain.enums.ChecklistTemplateStatus;
import com.smartdroneinspection.assets.domain.enums.ScheduleProposalStatus;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.CategoryFrequencySuggestionRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.assets.repository.ScheduleProposalRepository;
import com.smartdroneinspection.shared.exception.BusinessException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetReviewService {

  private final AssetRepository assets;
  private final CategoryFrequencySuggestionRepository suggestions;
  private final ChecklistTemplateRepository templates;
  private final ScheduleProposalRepository proposals;

  public AssetReviewService(
      AssetRepository assets,
      CategoryFrequencySuggestionRepository suggestions,
      ChecklistTemplateRepository templates,
      ScheduleProposalRepository proposals) {
    this.assets = assets;
    this.suggestions = suggestions;
    this.templates = templates;
    this.proposals = proposals;
  }

  @Transactional
  public AssetReviewResponse review(UUID managerId, UUID assetId, ReviewAssetRequest request) {
    Asset asset =
        assets
            .findWithLockById(assetId)
            .orElseThrow(
                () -> new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Asset not found"));

    if ("REJECT".equals(request.action())) {
      asset.rejectReview();
      assets.saveAndFlush(asset);
      return new AssetReviewResponse(assetId, asset.getStatus().name(), 0);
    }
    if (!"APPROVE".equals(request.action())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Unknown review action");
    }

    if (asset.getStatus() == AssetStatus.ACTIVE) {
      return new AssetReviewResponse(
          assetId, asset.getStatus().name(), activeProposalCount(assetId));
    }
    if (asset.getStatus() != AssetStatus.PENDING_REVIEW) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "INVALID_STATE", "Asset is not awaiting review");
    }

    var categorySuggestions =
        suggestions.findByAssetCategoryIdOrderBySortOrderAsc(asset.getCategoryId());
    if (categorySuggestions.isEmpty()) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "NO_SUGGESTED_FREQUENCIES",
          "Category has no suggested frequencies; ask an Admin to configure them first");
    }
    ChecklistTemplate template =
        templates
            .findFirstByAssetCategoryIdAndStatusOrderByVersionNumberDesc(
                asset.getCategoryId(), ChecklistTemplateStatus.ACTIVE)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.CONFLICT,
                        "NO_ACTIVE_CHECKLIST",
                        "Category has no active checklist template"));

    asset.approveReview();
    assets.saveAndFlush(asset);
    for (var suggestion : categorySuggestions) {
      proposals.saveAndFlush(
          ScheduleProposal.generate(
              assetId,
              template.getId(),
              suggestion.getFrequencyUnit(),
              suggestion.getFrequencyInterval()));
    }
    return new AssetReviewResponse(assetId, asset.getStatus().name(), categorySuggestions.size());
  }

  private int activeProposalCount(UUID assetId) {
    int count = 0;
    for (ScheduleProposalStatus status :
        new ScheduleProposalStatus[] {
          ScheduleProposalStatus.GENERATED,
          ScheduleProposalStatus.MANAGER_APPROVED,
          ScheduleProposalStatus.CLIENT_SELECTED
        }) {
      count += proposals.findByAssetIdAndStatus(assetId, status).size();
    }
    return count;
  }
}
