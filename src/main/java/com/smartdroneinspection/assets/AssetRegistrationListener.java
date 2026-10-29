package com.smartdroneinspection.assets;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.AssetCategory;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.shared.CustomerRegistrationEvent;
import com.smartdroneinspection.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Persists the asset half of a Customer self-registration within the users transaction. */
@Component
public class AssetRegistrationListener {

  private final AssetCategoryRepository categories;
  private final AssetRepository assets;

  public AssetRegistrationListener(AssetCategoryRepository categories, AssetRepository assets) {
    this.categories = categories;
    this.assets = assets;
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void onCustomerRegistered(CustomerRegistrationEvent event) {
    AssetCategory category =
        categories
            .findById(event.categoryId())
            .filter(AssetCategory::isActive)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_ASSET_CATEGORY",
                        "Unknown or inactive category"));
    Asset asset =
        new Asset(
            event.organizationId(),
            category.getId(),
            event.assetCode(),
            event.assetName(),
            event.description(),
            event.locationText(),
            event.latitude(),
            event.longitude(),
            event.ownershipInformation(),
            event.customerId());
    assets.saveAndFlush(asset);
  }
}
