package com.smartdroneinspection.contracts.api.dto.request;

import jakarta.validation.constraints.PositiveOrZero;

public record AcceptServiceQuoteRequest(@PositiveOrZero Long quoteRowVersion) {}
