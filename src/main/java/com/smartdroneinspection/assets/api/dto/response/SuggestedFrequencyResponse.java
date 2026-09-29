package com.smartdroneinspection.assets.api.dto.response;

import java.util.UUID;

public record SuggestedFrequencyResponse(
    UUID id, String frequencyUnit, int frequencyInterval, int sortOrder) {}
