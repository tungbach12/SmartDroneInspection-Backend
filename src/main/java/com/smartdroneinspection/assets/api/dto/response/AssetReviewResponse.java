package com.smartdroneinspection.assets.api.dto.response;

import java.util.UUID;

public record AssetReviewResponse(UUID assetId, String status, int proposalCount) {}
