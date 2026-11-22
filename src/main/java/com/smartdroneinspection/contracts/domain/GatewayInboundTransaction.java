package com.smartdroneinspection.contracts.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One authenticated inbound payment-gateway notification and what was done with it. */
@Entity
@Table(name = "gateway_inbound_transactions")
public class GatewayInboundTransaction {

  public enum Status {
    APPLIED,
    AMOUNT_MISMATCH,
    ORDER_NOT_FOUND,
    REJECTED,
    ERROR
  }

  @Id @GeneratedValue private UUID id;

  @Column(name = "gateway", nullable = false, length = 20)
  private String gateway;

  @Column(name = "external_id", length = 100)
  private String externalId;

  @Column(name = "order_code")
  private Long orderCode;

  @Column(name = "amount")
  private Long amount;

  @Column(name = "account_number", length = 50)
  private String accountNumber;

  @Column(name = "payload", columnDefinition = "text")
  private String payload;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 24)
  private Status status;

  @Column(name = "detail", length = 255)
  private String detail;

  @Column(name = "received_at", nullable = false, updatable = false)
  private Instant receivedAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected GatewayInboundTransaction() {}

  public GatewayInboundTransaction(String gateway, String externalId) {
    this.gateway = gateway;
    this.externalId = externalId;
  }

  public void record(
      Long orderCode,
      Long amount,
      String accountNumber,
      String payload,
      Status status,
      String detail) {
    this.orderCode = orderCode;
    this.amount = amount;
    this.accountNumber = truncate(accountNumber, 50);
    this.payload = payload;
    this.status = status;
    this.detail = truncate(detail, 255);
    this.updatedAt = Instant.now();
  }

  private static String truncate(String value, int max) {
    return value != null && value.length() > max ? value.substring(0, max) : value;
  }

  public UUID getId() {
    return id;
  }

  public String getGateway() {
    return gateway;
  }

  public String getExternalId() {
    return externalId;
  }

  public Long getOrderCode() {
    return orderCode;
  }

  public Long getAmount() {
    return amount;
  }

  public Status getStatus() {
    return status;
  }

  public String getDetail() {
    return detail;
  }
}
