package com.smartdroneinspection.maintenance.domain;

import com.smartdroneinspection.maintenance.domain.enums.InvoiceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "invoices")
public class Invoice {

  @Id @GeneratedValue private UUID id;

  @Column(name = "invoice_number", nullable = false, unique = true, length = 64)
  private String invoiceNumber;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "maintenance_order_id", nullable = false)
  private UUID maintenanceOrderId;

  @Column(name = "maintenance_ticket_id", nullable = false)
  private UUID maintenanceTicketId;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(nullable = false, length = 3)
  private String currency;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal subtotal;

  @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2)
  private BigDecimal taxAmount;

  @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
  private BigDecimal totalAmount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private InvoiceStatus status;

  @Column(name = "issued_at")
  private Instant issuedAt;

  @Column(name = "due_at")
  private Instant dueAt;

  @Column(name = "paid_at")
  private Instant paidAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Invoice() {}

  public Invoice(
      String invoiceNumber,
      UUID organizationId,
      UUID maintenanceOrderId,
      UUID maintenanceTicketId,
      String currency,
      BigDecimal subtotal,
      BigDecimal taxAmount,
      BigDecimal totalAmount) {
    this.invoiceNumber = invoiceNumber;
    this.organizationId = organizationId;
    this.maintenanceOrderId = maintenanceOrderId;
    this.maintenanceTicketId = maintenanceTicketId;
    this.currency = currency;
    this.subtotal = subtotal;
    this.taxAmount = taxAmount;
    this.totalAmount = totalAmount;
    this.status = InvoiceStatus.DRAFT;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public String getInvoiceNumber() {
    return invoiceNumber;
  }

  public InvoiceStatus getStatus() {
    return status;
  }
}
