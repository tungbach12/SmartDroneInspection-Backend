package com.smartdroneinspection.subscriptions.api.dto;

import java.math.BigDecimal;

public record PaymentInstructionsResponse(
    String bankName,
    String accountName,
    String accountNumber,
    String swiftBic,
    String referenceCode,
    BigDecimal amountUsd,
    String currency,
    String instructions) {

  public static PaymentInstructionsResponse forOrganization(
      String organizationCode, int billingPeriodMonths, BigDecimal amountUsd) {
    String normalizedOrg =
        (organizationCode != null && !organizationCode.isBlank())
            ? organizationCode.trim().toUpperCase()
            : "ORG";
    String ref = String.format("SDI-SUB-%s-%dM", normalizedOrg, billingPeriodMonths);
    return new PaymentInstructionsResponse(
        "JPMorgan Chase Bank, N.A.",
        "Smart Drone Inspection Platform Inc.",
        "849204810293",
        "CHASUS33XXX",
        ref,
        amountUsd,
        "USD",
        "Please initiate a corporate wire transfer quoting the exact reference code above."
            + " Platform administrators will reconcile incoming wire statements and activate your"
            + " enterprise workspace upon receipt.");
  }
}
