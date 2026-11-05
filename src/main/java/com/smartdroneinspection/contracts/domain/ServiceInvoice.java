package com.smartdroneinspection.contracts.domain;

import com.smartdroneinspection.contracts.service.InspectionFinanceCalculator;
import com.smartdroneinspection.contracts.service.InspectionFinanceCalculator.FinanceCalculation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "service_invoices")
public class ServiceInvoice {

  @Id
  @Column(name = "occurrence_id")
  private UUID occurrenceId;

  @MapsId
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "occurrence_id")
  private ServiceRequestOccurrence occurrence;

  @Column(name = "total_price", precision = 15, scale = 2)
  private BigDecimal totalPrice;

  @Column(name = "vat_rate", precision = 5, scale = 2)
  private BigDecimal vatRate;

  @Column(name = "commission_rate", precision = 5, scale = 2)
  private BigDecimal commissionRate;

  @Column(name = "tncn_rate", precision = 5, scale = 2)
  private BigDecimal tncnRate;

  @Column(name = "report_accepted_at")
  private Instant reportAcceptedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected ServiceInvoice() {}

  ServiceInvoice(ServiceRequestOccurrence occurrence) {
    this.occurrence = occurrence;
  }

  void snapshotFinance(
      BigDecimal totalPrice, BigDecimal vatRate, BigDecimal commissionRate, BigDecimal tncnRate) {
    if (this.totalPrice != null) {
      return;
    }
    this.totalPrice = totalPrice;
    this.vatRate = vatRate;
    this.commissionRate = commissionRate;
    this.tncnRate = tncnRate;
    this.updatedAt = Instant.now();
  }

  void acceptReport() {
    if (reportAcceptedAt == null) {
      reportAcceptedAt = Instant.now();
      updatedAt = reportAcceptedAt;
    }
  }

  private FinanceCalculation financeCalculation() {
    if (totalPrice == null
        || totalPrice.signum() <= 0
        || vatRate == null
        || commissionRate == null
        || tncnRate == null) {
      return null;
    }
    return InspectionFinanceCalculator.calculate(totalPrice, vatRate, commissionRate, tncnRate);
  }

  public UUID getOccurrenceId() {
    return occurrenceId;
  }

  public BigDecimal getTotalPrice() {
    return totalPrice;
  }

  public BigDecimal getServiceBaseAmount() {
    FinanceCalculation calc = financeCalculation();
    return calc != null ? calc.serviceBaseAmount() : null;
  }

  public BigDecimal getVatRate() {
    return vatRate;
  }

  public BigDecimal getVatAmount() {
    FinanceCalculation calc = financeCalculation();
    return calc != null ? calc.vatAmount() : null;
  }

  public BigDecimal getCommissionRate() {
    return commissionRate;
  }

  public BigDecimal getCommissionAmount() {
    FinanceCalculation calc = financeCalculation();
    return calc != null ? calc.commissionAmount() : null;
  }

  public BigDecimal getInspectorGrossPayout() {
    FinanceCalculation calc = financeCalculation();
    return calc != null ? calc.inspectorGrossPayout() : null;
  }

  public BigDecimal getTncnRate() {
    return tncnRate;
  }

  public BigDecimal getTncnAmount() {
    FinanceCalculation calc = financeCalculation();
    return calc != null ? calc.tncnAmount() : null;
  }

  public BigDecimal getInspectorNetPayout() {
    FinanceCalculation calc = financeCalculation();
    return calc != null ? calc.inspectorNetPayout() : null;
  }

  public Instant getReportAcceptedAt() {
    return reportAcceptedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
