package com.smartdroneinspection.assets.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateAssetInspectionItemsRequest(
    @NotNull @Size(max = 50) List<@Size(max = 300) String> inspectionItems) {}
