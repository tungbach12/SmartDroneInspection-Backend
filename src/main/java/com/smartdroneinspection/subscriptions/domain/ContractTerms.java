package com.smartdroneinspection.subscriptions.domain;

import java.math.BigDecimal;
import java.util.List;

public final class ContractTerms {

  public static final String VERSION = "2026.1";
  public static final String EFFECTIVE_DATE = "2026-10-01";
  public static final String PLAN_CODE = "ENTERPRISE";

  public record PricingTier(
      int billingPeriodMonths,
      BigDecimal monthlyPriceUsd,
      BigDecimal totalPriceUsd,
      double savingsPercent) {}

  public record StandardPriceCalculation(
      int billingPeriodMonths,
      BigDecimal monthlyPriceUsd,
      BigDecimal totalPriceUsd,
      double savingsPercent) {}

  public record Clause(String id, String title, String text) {}

  public static final List<PricingTier> PRICING_TIERS =
      List.of(
          new PricingTier(1, BigDecimal.valueOf(1200), BigDecimal.valueOf(1200), 0.0),
          new PricingTier(6, BigDecimal.valueOf(1000), BigDecimal.valueOf(6000), 16.7),
          new PricingTier(12, BigDecimal.valueOf(850), BigDecimal.valueOf(10200), 29.2));

  public static final List<Clause> CLAUSES =
      List.of(
          new Clause(
              "scope",
              "Service Scope & Availability",
              "SmartDroneInspection provides infrastructure asset registration, inspection evidence"
                  + " storage, and AI-assisted defect analysis when configured. Service availability"
                  + " depends on the deployed environment; no numeric service-level commitment is made"
                  + " by this preview."),
          new Clause(
              "responsibility",
              "Operational Responsibility & Governance",
              "The customer organization retains sole regulatory authority and legal responsibility"
                  + " for drone flight permits, civil aviation clearances, and pilot credential"
                  + " qualifications. The platform provides compliance gates but does not pilot"
                  + " aircraft."),
          new Clause(
              "payment",
              "Offline Invoicing & Activation",
              "Enterprise subscription licenses may be settled through corporate wire/invoice"
                  + " reconciliation by a platform administrator. Optional PayPal Orders checkout is"
                  + " available only when enabled and configured by the deployment, and the backend must"
                  + " verify the provider-reported capture before activation. The displayed terms are a"
                  + " preview; acceptance recorded in the application is not a certified digital"
                  + " signature."),
          new Clause(
              "data",
              "Data Sovereignty & Audit Retention",
              "Inspection evidence, findings, reports, and maintenance data are handled according to"
                  + " the platform's configured storage, access-control, and retention policies. This"
                  + " preview does not make a separate data-ownership, cryptographic-integrity, or"
                  + " statutory-retention certification."),
          new Clause(
              "validity",
              "Term & Termination",
              "Subscriptions are valid for the selected billing period from the administrator-"
                  + "confirmed start date, subject to the final approved order and applicable service"
                  + " terms. Retention of subscription and audit records follows configured platform"
                  + " policies and applicable law."));

  private ContractTerms() {}

  public static PricingTier getPricingTier(int months) {
    return PRICING_TIERS.stream()
        .filter(t -> t.billingPeriodMonths() == months)
        .findFirst()
        .orElseGet(
            () -> {
              var calc = calculateStandardPrice(months);
              return new PricingTier(
                  months, calc.monthlyPriceUsd(), calc.totalPriceUsd(), calc.savingsPercent());
            });
  }

  public static StandardPriceCalculation calculateStandardPrice(int months) {
    if (months < 1 || months > 120) {
      throw new IllegalArgumentException(
          "Billing period must be between 1 and 120 months: " + months);
    }
    BigDecimal monthly;
    double savings;
    if (months < 6) {
      monthly = BigDecimal.valueOf(1200);
      savings = 0.0;
    } else if (months < 12) {
      monthly = BigDecimal.valueOf(1000);
      savings = 16.7;
    } else if (months < 24) {
      monthly = BigDecimal.valueOf(850);
      savings = 29.2;
    } else {
      monthly = BigDecimal.valueOf(750);
      savings = 37.5;
    }
    BigDecimal total = monthly.multiply(BigDecimal.valueOf(months));
    return new StandardPriceCalculation(months, monthly, total, savings);
  }
}
