package com.smartdroneinspection.shared;

import java.math.BigDecimal;

/** Shared port used by customer onboarding without coupling users to the assets module. */
public interface CustomerAirspacePolicy {
  CustomerAirspaceEvaluation evaluateCustomerOnboarding(BigDecimal latitude, BigDecimal longitude);
}
