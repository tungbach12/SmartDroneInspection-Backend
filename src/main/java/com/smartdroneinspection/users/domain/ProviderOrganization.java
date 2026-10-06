package com.smartdroneinspection.users.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provider_organizations")
public class ProviderOrganization {

  @Id @GeneratedValue private UUID id;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(name = "legal_name", nullable = false, length = 250)
  private String legalName;

  @Column(name = "tax_code", nullable = false, length = 32)
  private String taxCode;

  @Column(name = "business_license_no", nullable = false, length = 64)
  private String businessLicenseNo;

  @Column(name = "drone_permit_code", length = 64)
  private String dronePermitCode;

  @Column(name = "insurance_policy_no", length = 128)
  private String insurancePolicyNo;

  @Column(nullable = false, length = 32)
  private String status;

  @Column(name = "rating_score")
  private java.math.BigDecimal ratingScore;

  @Column(name = "approved_by_operator_id")
  private UUID approvedByOperatorId;

  @Column(name = "approved_at")
  private Instant approvedAt;

  @Column(name = "standing_decision_reason", length = 2000)
  private String standingDecisionReason;

  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ProviderOrganization() {}

  public ProviderOrganization(
      String name, String legalName, String taxCode, String businessLicenseNo) {
    this.name = name;
    this.legalName = legalName;
    this.taxCode = taxCode;
    this.businessLicenseNo = businessLicenseNo;
    this.status = "PENDING";
    this.ratingScore = null;
    this.approvedByOperatorId = null;
    this.approvedAt = null;
    this.rowVersion = 0;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getLegalName() {
    return legalName;
  }

  public String getTaxCode() {
    return taxCode;
  }

  public String getBusinessLicenseNo() {
    return businessLicenseNo;
  }

  public String getStatus() {
    return status;
  }

  public long getRowVersion() {
    return rowVersion;
  }
}
