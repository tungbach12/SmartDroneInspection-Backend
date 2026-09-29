package com.smartdroneinspection.assets.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReviewAssetRequest(
    @NotBlank @Pattern(regexp = "APPROVE|REJECT") String action, @Size(max = 500) String note) {}
