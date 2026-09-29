package com.smartdroneinspection.assets.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SuggestedFrequencyRequest(
    @NotBlank @Pattern(regexp = "DAY|WEEK|MONTH|YEAR") String frequencyUnit,
    @Min(1) int frequencyInterval) {}
