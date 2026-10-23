package com.smartdroneinspection.subscriptions.api.dto;

import com.smartdroneinspection.subscriptions.domain.ContractTerms;
import java.util.List;

public record ContractTermsResponse(
    String version,
    String effectiveDate,
    String planCode,
    List<ContractTerms.PricingTier> pricingTiers,
    List<ContractTerms.Clause> clauses) {

  public static ContractTermsResponse standard() {
    return new ContractTermsResponse(
        ContractTerms.VERSION,
        ContractTerms.EFFECTIVE_DATE,
        ContractTerms.PLAN_CODE,
        ContractTerms.PRICING_TIERS,
        ContractTerms.CLAUSES);
  }
}
