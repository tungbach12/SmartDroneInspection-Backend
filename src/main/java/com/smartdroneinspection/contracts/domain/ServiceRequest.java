package com.smartdroneinspection.contracts.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.util.StringUtils;

@Entity
@Table(name = "service_requests")
public class ServiceRequest {

  @Id @GeneratedValue private UUID id;

  @Column(name = "owner_customer_id", nullable = false)
  private UUID ownerCustomerId;

  @Column(name = "asset_id", nullable = false)
  private UUID assetId;

  @Column(name = "created_by_user_id", nullable = false)
  private UUID createdByUserId;

  @Column(nullable = false, length = 4000)
  private String description;

  @Column(name = "requested_from", nullable = false)
  private LocalDate requestedFrom;

  @Column(name = "requested_until", nullable = false)
  private LocalDate requestedUntil;

  @Column(nullable = false, length = 64)
  private String timezone;

  @Column(name = "window_start", nullable = false)
  private LocalTime windowStart;

  @Column(name = "window_end", nullable = false)
  private LocalTime windowEnd;

  @Enumerated(EnumType.STRING)
  @Column(name = "request_type", nullable = false, length = 16)
  private RequestType requestType;

  @Column(name = "recurrence_unit", length = 16)
  private String recurrenceUnit;

  @Column(name = "recurrence_interval")
  private Integer recurrenceInterval;

  @Column(name = "occurrence_count")
  private Integer occurrenceCount;

  @Column(name = "recurrence_end_date")
  private LocalDate recurrenceEndDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private ServiceRequestStatus status = ServiceRequestStatus.OPEN;

  @Column(name = "accepted_quote_id")
  private UUID acceptedQuoteId;

  @Column(name = "accepted_at")
  private Instant acceptedAt;

  @Column(name = "accepted_by_user_id")
  private UUID acceptedByUserId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "accepted_terms_snapshot", columnDefinition = "jsonb")
  private Map<String, Object> acceptedTermsSnapshot;

  @Column(name = "contract_object_key", length = 1000)
  private String contractObjectKey;

  @Column(name = "contract_uploaded_at")
  private Instant contractUploadedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected ServiceRequest() {}

  public ServiceRequest(
      UUID ownerCustomerId,
      UUID assetId,
      UUID createdByUserId,
      String description,
      LocalDate requestedFrom,
      LocalDate requestedUntil,
      String timezone,
      LocalTime windowStart,
      LocalTime windowEnd,
      RequestType requestType,
      String recurrenceUnit,
      Integer recurrenceInterval,
      Integer occurrenceCount,
      LocalDate recurrenceEndDate) {
    if (ownerCustomerId == null || assetId == null || createdByUserId == null) {
      throw new IllegalArgumentException("Request owner, asset, and creator are required");
    }
    if (requestedFrom == null || requestedUntil == null || requestedUntil.isBefore(requestedFrom)) {
      throw new IllegalArgumentException("Requested date range is invalid");
    }
    if (requestType == null) {
      throw new IllegalArgumentException("Request type is required");
    }
    if (requestType == RequestType.ONE_TIME && !requestedFrom.equals(requestedUntil)) {
      throw new IllegalArgumentException("A one-time request must use exactly one requested date");
    }
    if (!StringUtils.hasText(timezone)) {
      throw new IllegalArgumentException("Customer timezone is required");
    }
    try {
      java.time.ZoneId.of(timezone);
    } catch (java.time.DateTimeException exception) {
      throw new IllegalArgumentException("Customer timezone is invalid", exception);
    }
    if (windowStart == null || windowEnd == null || !windowEnd.isAfter(windowStart)) {
      throw new IllegalArgumentException("Daily time window is invalid");
    }
    if (description != null && description.length() > 4000) {
      throw new IllegalArgumentException("Request description must not exceed 4000 characters");
    }
    if (requestType == RequestType.ONE_TIME
        && (recurrenceUnit != null
            || recurrenceInterval != null
            || occurrenceCount != null
            || recurrenceEndDate != null)) {
      throw new IllegalArgumentException("A one-time request cannot include recurrence limits");
    }
    if (requestType == RequestType.RECURRING) {
      boolean monthlyCadence = "MONTH".equals(recurrenceUnit);
      boolean legacyCadence = "DAY".equals(recurrenceUnit) || "WEEK".equals(recurrenceUnit);
      int minimumOccurrences = monthlyCadence ? 2 : 1;
      int maximumInterval = monthlyCadence ? 120 : 52;
      boolean hasValidCount =
          occurrenceCount != null
              && occurrenceCount >= minimumOccurrences
              && occurrenceCount <= 100;
      boolean hasEndDate = recurrenceEndDate != null;
      if (hasValidCount == hasEndDate) {
        throw new IllegalArgumentException(
            "A recurring request must use exactly one valid recurrence limit");
      }
      if ((!monthlyCadence && !legacyCadence)
          || recurrenceInterval == null
          || recurrenceInterval < 1
          || recurrenceInterval > maximumInterval
          || (occurrenceCount != null && occurrenceCount < minimumOccurrences)) {
        throw new IllegalArgumentException("Recurring cadence is invalid");
      }
      if (recurrenceEndDate != null && recurrenceEndDate.isBefore(requestedFrom)) {
        throw new IllegalArgumentException("Recurrence end date must not precede requested start");
      }
    }
    this.ownerCustomerId = ownerCustomerId;
    this.assetId = assetId;
    this.createdByUserId = createdByUserId;
    this.description = StringUtils.hasText(description) ? description.trim() : "Inspection request";
    this.requestedFrom = requestedFrom;
    this.requestedUntil = requestedUntil;
    this.timezone = timezone;
    this.windowStart = windowStart;
    this.windowEnd = windowEnd;
    this.requestType = requestType;
    this.recurrenceUnit = recurrenceUnit;
    this.recurrenceInterval = recurrenceInterval;
    this.occurrenceCount = occurrenceCount;
    this.recurrenceEndDate = recurrenceEndDate;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOwnerCustomerId() {
    return ownerCustomerId;
  }

  public void markCustomerOwned(UUID ownerCustomerId) {
    this.ownerCustomerId = ownerCustomerId;
    this.updatedAt = Instant.now();
  }

  public UUID getAssetId() {
    return assetId;
  }

  public UUID getCreatedByUserId() {
    return createdByUserId;
  }

  public String getDescription() {
    return description;
  }

  public LocalDate getRequestedFrom() {
    return requestedFrom;
  }

  public LocalDate getRequestedUntil() {
    return requestedUntil;
  }

  public String getTimezone() {
    return timezone;
  }

  public LocalTime getWindowStart() {
    return windowStart;
  }

  public LocalTime getWindowEnd() {
    return windowEnd;
  }

  public RequestType getRequestType() {
    return requestType;
  }

  public String getRecurrenceUnit() {
    return recurrenceUnit;
  }

  public Integer getRecurrenceInterval() {
    return recurrenceInterval;
  }

  public Integer getOccurrenceCount() {
    return occurrenceCount;
  }

  public LocalDate getRecurrenceEndDate() {
    return recurrenceEndDate;
  }

  public ServiceRequestStatus getStatus() {
    return status;
  }

  public UUID getAcceptedQuoteId() {
    return acceptedQuoteId;
  }

  public Instant getAcceptedAt() {
    return acceptedAt;
  }

  public UUID getAcceptedByUserId() {
    return acceptedByUserId;
  }

  public Map<String, Object> getAcceptedTermsSnapshot() {
    return acceptedTermsSnapshot;
  }

  public long getRowVersion() {
    return rowVersion;
  }

  public boolean hasSignedContract() {
    return StringUtils.hasText(contractObjectKey);
  }

  public String getContractObjectKey() {
    return contractObjectKey;
  }

  public String getContractFileName() {
    if (!StringUtils.hasText(contractObjectKey)) return null;
    int slash = contractObjectKey.lastIndexOf('/');
    return slash >= 0 && slash + 1 < contractObjectKey.length()
        ? contractObjectKey.substring(slash + 1)
        : contractObjectKey;
  }

  public String getContractContentType() {
    String fileName = getContractFileName();
    if (fileName == null) return null;
    String lower = fileName.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".docx"))
      return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    if (lower.endsWith(".png")) return "image/png";
    if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
    return "application/pdf";
  }

  public Long getContractSizeBytes() {
    return null;
  }

  public Instant getContractUploadedAt() {
    return contractUploadedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void markQuoted() {
    if (status != ServiceRequestStatus.OPEN && status != ServiceRequestStatus.QUOTED) {
      throw new IllegalStateException("Request is not accepting quote revisions");
    }
    status = ServiceRequestStatus.QUOTED;
    updatedAt = Instant.now();
  }

  public void acceptQuote(ServiceRequestQuote quote, UUID userId) {
    if (status != ServiceRequestStatus.QUOTED
        || acceptedQuoteId != null
        || quote == null
        || userId == null
        || !id.equals(quote.getRequestId())) {
      throw new IllegalStateException("Request cannot accept this quote");
    }
    acceptedQuoteId = quote.getId();
    acceptedAt = Instant.now();
    acceptedByUserId = userId;
    acceptedTermsSnapshot = acceptedTerms(quote, acceptedAt, userId);
    status = ServiceRequestStatus.ACCEPTED;
    updatedAt = acceptedAt;
  }

  private Map<String, Object> acceptedTerms(ServiceRequestQuote quote, Instant at, UUID userId) {
    return Map.ofEntries(
        Map.entry("quoteId", quote.getId()),
        Map.entry("scopeSnapshot", quote.getScopeSnapshot()),
        Map.entry("firstOccurrenceStartAt", quote.getFirstOccurrenceStartAt()),
        Map.entry("firstOccurrenceEndAt", quote.getFirstOccurrenceEndAt()),
        Map.entry("inspectorId", quote.getInspectorId()),
        Map.entry("droneId", quote.getDroneId()),
        Map.entry("pricePerOccurrenceVnd", quote.getPricePerOccurrenceVnd()),
        Map.entry("requiredDroneFeatureCodes", quote.getRequiredDroneFeatureCodes()),
        Map.entry("issuedByUserId", quote.getIssuedByUserId()),
        Map.entry("acceptedAt", at),
        Map.entry("acceptedByUserId", userId));
  }

  public void recordSignedContract(String objectKey) {
    if ((status != ServiceRequestStatus.ACCEPTED
            && status != ServiceRequestStatus.CONTRACT_SIGNED
            && status != ServiceRequestStatus.ASSIGNED)
        || acceptedQuoteId == null) {
      throw new IllegalStateException("A quote must be accepted before contract upload");
    }
    if (!StringUtils.hasText(objectKey)) {
      throw new IllegalArgumentException("Contract object key is required");
    }
    contractObjectKey = objectKey.trim();
    contractUploadedAt = Instant.now();
    if (status == ServiceRequestStatus.ACCEPTED) status = ServiceRequestStatus.CONTRACT_SIGNED;
    updatedAt = contractUploadedAt;
  }

  public void recordSignedContract(
      String objectKey,
      String fileName,
      String contentType,
      long size,
      String checksum,
      UUID uploadedBy) {
    if (!StringUtils.hasText(objectKey) || !StringUtils.hasText(fileName)) {
      throw new IllegalArgumentException("Contract file metadata is required");
    }
    String keyWithFileName =
        objectKey.endsWith("/" + fileName) ? objectKey : objectKey + "/" + fileName;
    recordSignedContract(keyWithFileName);
  }

  public void markAssigned() {
    status = ServiceRequestStatus.ASSIGNED;
    updatedAt = Instant.now();
  }

  public enum RequestType {
    ONE_TIME,
    RECURRING
  }

  public enum ServiceRequestStatus {
    OPEN,
    QUOTED,
    ACCEPTED,
    CONTRACT_SIGNED,
    ASSIGNED,
    CANCELLED
  }
}
