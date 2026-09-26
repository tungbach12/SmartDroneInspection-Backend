package com.smartdroneinspection.assets.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReviewProposalRequest(
    @NotBlank @Pattern(regexp = "APPROVE|REJECT") String action,
    @Size(max = 500) String note,
    @Pattern(regexp = "DAY|WEEK|MONTH|YEAR") String frequencyUnit,
    @Min(1) Integer frequencyInterval) {}
