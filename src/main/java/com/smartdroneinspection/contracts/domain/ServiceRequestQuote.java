package com.smartdroneinspection.contracts.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "service_request_quotes")
public class ServiceRequestQuote {

  @Id @GeneratedValue private UUID id;

  @Column(name = "request_id", nullable = false)
  private UUID requestId;

  @Column(name = "scope_snapshot", nullable = false, length = 4000)
  private String scopeSnapshot;

  @Column(name = "first_occurrence_start_at", nullable = false)
  private Instant firstOccurrenceStartAt;

  @Column(name = "first_occurrence_end_at", nullable = false)
  private Instant firstOccurrenceEndAt;

  @Column(name = "inspector_id", nullable = false)
  private UUID inspectorId;

  @Column(name = "drone_id", nullable = false)
  private UUID droneId;

  @Column(name = "price_per_occurrence_vnd", nullable = false, precision = 15, scale = 2)
  private BigDecimal pricePerOccurrenceVnd;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "required_drone_feature_codes", columnDefinition = "jsonb", nullable = false)
  private List<String> requiredDroneFeatureCodes = new ArrayList<>();

  @Column(name = "issued_by_user_id", nullable = false)
  private UUID issuedByUserId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @Column(name = "updated_by_user_id", nullable = false)
  private UUID updatedByUserId;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected ServiceRequestQuote() {}

  public ServiceRequestQuote(
      UUID requestId,
      String scopeSnapshot,
      Instant firstOccurrenceStartAt,
      Instant firstOccurrenceEndAt,
      UUID inspectorId,
      UUID droneId,
      BigDecimal pricePerOccurrenceVnd,
      UUID issuedByUserId) {
    this(
        requestId,
        scopeSnapshot,
        firstOccurrenceStartAt,
        firstOccurrenceEndAt,
        inspectorId,
        droneId,
        pricePerOccurrenceVnd,
        List.of(),
        issuedByUserId);
  }

  public ServiceRequestQuote(
      UUID requestId,
      String scopeSnapshot,
      Instant firstOccurrenceStartAt,
      Instant firstOccurrenceEndAt,
      UUID inspectorId,
      UUID droneId,
      BigDecimal pricePerOccurrenceVnd,
      List<String> requiredDroneFeatureCodes,
      UUID issuedByUserId) {
    validate(
        requestId,
        scopeSnapshot,
        firstOccurrenceStartAt,
        firstOccurrenceEndAt,
        inspectorId,
        droneId,
        pricePerOccurrenceVnd,
        issuedByUserId);
    this.requestId = requestId;
    this.scopeSnapshot = scopeSnapshot.trim();
    this.firstOccurrenceStartAt = firstOccurrenceStartAt;
    this.firstOccurrenceEndAt = firstOccurrenceEndAt;
    this.inspectorId = inspectorId;
    this.droneId = droneId;
    this.pricePerOccurrenceVnd = normalizePrice(pricePerOccurrenceVnd);
    this.requiredDroneFeatureCodes = normalizeFeatures(requiredDroneFeatureCodes);
    this.issuedByUserId = issuedByUserId;
    this.updatedByUserId = issuedByUserId;
  }

  public void revise(
      String scopeSnapshot,
      Instant firstOccurrenceStartAt,
      Instant firstOccurrenceEndAt,
      UUID inspectorId,
      UUID droneId,
      BigDecimal pricePerOccurrenceVnd,
      UUID editorUserId) {
    revise(
        scopeSnapshot,
        firstOccurrenceStartAt,
        firstOccurrenceEndAt,
        inspectorId,
        droneId,
        pricePerOccurrenceVnd,
        requiredDroneFeatureCodes,
        editorUserId);
  }

  public void revise(
      String scopeSnapshot,
      Instant firstOccurrenceStartAt,
      Instant firstOccurrenceEndAt,
      UUID inspectorId,
      UUID droneId,
      BigDecimal pricePerOccurrenceVnd,
      List<String> requiredDroneFeatureCodes,
      UUID editorUserId) {
    validate(
        requestId,
        scopeSnapshot,
        firstOccurrenceStartAt,
        firstOccurrenceEndAt,
        inspectorId,
        droneId,
        pricePerOccurrenceVnd,
        editorUserId);
    this.scopeSnapshot = scopeSnapshot.trim();
    this.firstOccurrenceStartAt = firstOccurrenceStartAt;
    this.firstOccurrenceEndAt = firstOccurrenceEndAt;
    this.inspectorId = inspectorId;
    this.droneId = droneId;
    this.pricePerOccurrenceVnd = normalizePrice(pricePerOccurrenceVnd);
    this.requiredDroneFeatureCodes = normalizeFeatures(requiredDroneFeatureCodes);
    this.issuedByUserId = editorUserId;
    this.updatedByUserId = editorUserId;
    this.updatedAt = Instant.now();
  }

  private static void validate(
      UUID requestId,
      String scopeSnapshot,
      Instant firstOccurrenceStartAt,
      Instant firstOccurrenceEndAt,
      UUID inspectorId,
      UUID droneId,
      BigDecimal pricePerOccurrenceVnd,
      UUID editorUserId) {
    if (requestId == null
        || scopeSnapshot == null
        || scopeSnapshot.isBlank()
        || firstOccurrenceStartAt == null
        || firstOccurrenceEndAt == null
        || !firstOccurrenceEndAt.isAfter(firstOccurrenceStartAt)
        || inspectorId == null
        || droneId == null
        || pricePerOccurrenceVnd == null
        || pricePerOccurrenceVnd.signum() <= 0
        || editorUserId == null) {
      throw new IllegalArgumentException("Quote fields are invalid");
    }
  }

  private static List<String> normalizeFeatures(List<String> codes) {
    if (codes == null || codes.isEmpty()) return new ArrayList<>();
    return new ArrayList<>(
        codes.stream()
            .filter(code -> code != null && !code.isBlank())
            .map(code -> code.trim().toUpperCase(Locale.ROOT))
            .distinct()
            .toList());
  }

  public List<String> getRequiredDroneFeatureCodes() {
    return Collections.unmodifiableList(new ArrayList<>(requiredDroneFeatureCodes));
  }

  private static BigDecimal normalizePrice(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }

  public BigDecimal getEffectivePrice() {
    return pricePerOccurrenceVnd;
  }

  public UUID getId() {
    return id;
  }

  public UUID getRequestId() {
    return requestId;
  }

  public String getScopeSnapshot() {
    return scopeSnapshot;
  }

  public Instant getFirstOccurrenceStartAt() {
    return firstOccurrenceStartAt;
  }

  public Instant getFirstOccurrenceEndAt() {
    return firstOccurrenceEndAt;
  }

  public UUID getInspectorId() {
    return inspectorId;
  }

  public UUID getDroneId() {
    return droneId;
  }

  public String getCurrency() {
    return "VND";
  }

  public BigDecimal getPricePerOccurrenceVnd() {
    return pricePerOccurrenceVnd;
  }

  public BigDecimal getPricePerOccurrenceUsd() {
    return pricePerOccurrenceVnd;
  }

  public UUID getIssuedByUserId() {
    return issuedByUserId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public UUID getUpdatedByUserId() {
    return updatedByUserId;
  }

  public long getRowVersion() {
    return rowVersion;
  }
}
