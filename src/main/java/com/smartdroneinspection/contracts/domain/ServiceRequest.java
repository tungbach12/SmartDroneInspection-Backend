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
import java.util.UUID;
import org.springframework.util.StringUtils;

@Entity
@Table(name = "service_requests")
public class ServiceRequest {

  @Id @GeneratedValue private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

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

  @Column(name = "accepted_quote_version")
  private Integer acceptedQuoteVersion;

  @Column(name = "accepted_by_user_id")
  private UUID acceptedByUserId;

  @Column(name = "accepted_at")
  private Instant acceptedAt;

  @Column(name = "contract_object_key", length = 1000)
  private String contractObjectKey;

  @Column(name = "contract_file_name", length = 500)
  private String contractFileName;

  @Column(name = "contract_content_type", length = 160)
  private String contractContentType;

  @Column(name = "contract_size_bytes")
  private Long contractSizeBytes;

  @Column(name = "contract_checksum_sha256", length = 64)
  private String contractChecksumSha256;

  @Column(name = "contract_uploaded_by_user_id")
  private UUID contractUploadedByUserId;

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
      UUID organizationId,
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
    if (organizationId == null || assetId == null || createdByUserId == null) {
      throw new IllegalArgumentException("Request organization, asset, and creator are required");
    }
    if (requestedFrom == null || requestedUntil == null || requestedUntil.isBefore(requestedFrom)) {
      throw new IllegalArgumentException("Requested date range is invalid");
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
    if (!StringUtils.hasText(description)) {
      throw new IllegalArgumentException("Request description is required");
    }
    this.organizationId = organizationId;
    this.assetId = assetId;
    this.createdByUserId = createdByUserId;
    this.description = description.trim();
    if (requestType == null) {
      throw new IllegalArgumentException("Request type is required");
    }
    if (requestType == RequestType.ONE_TIME
        && (recurrenceUnit != null
            || recurrenceInterval != null
            || occurrenceCount != null
            || recurrenceEndDate != null)) {
      throw new IllegalArgumentException("A one-time request cannot include recurrence limits");
    }
    if (requestType == RequestType.RECURRING) {
      boolean boundedByCount =
          occurrenceCount != null && occurrenceCount >= 1 && occurrenceCount <= 100;
      boolean boundedByEndDate = recurrenceEndDate != null;
      if (boundedByCount == boundedByEndDate) {
        throw new IllegalArgumentException(
            "A recurring request must use exactly one recurrence limit");
      }
      if (recurrenceUnit == null
          || !java.util.Set.of("DAY", "WEEK", "MONTH").contains(recurrenceUnit)
          || recurrenceInterval == null
          || recurrenceInterval < 1
          || recurrenceInterval > 52) {
        throw new IllegalArgumentException("Recurring cadence is invalid");
      }
      if (recurrenceEndDate != null && recurrenceEndDate.isBefore(requestedFrom)) {
        throw new IllegalArgumentException("Recurrence end date must not precede requested start");
      }
    }
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

  public UUID getOrganizationId() {
    return organizationId;
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

  public Integer getAcceptedQuoteVersion() {
    return acceptedQuoteVersion;
  }

  public UUID getAcceptedByUserId() {
    return acceptedByUserId;
  }

  public Instant getAcceptedAt() {
    return acceptedAt;
  }

  public String getContractObjectKey() {
    return contractObjectKey;
  }

  public String getContractFileName() {
    return contractFileName;
  }

  public String getContractContentType() {
    return contractContentType;
  }

  public Long getContractSizeBytes() {
    return contractSizeBytes;
  }

  public String getContractChecksumSha256() {
    return contractChecksumSha256;
  }

  public UUID getContractUploadedByUserId() {
    return contractUploadedByUserId;
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

  public void acceptQuote(int version, UUID userId) {
    if (status != ServiceRequestStatus.QUOTED || acceptedQuoteVersion != null) {
      throw new IllegalStateException("Request cannot accept another quote");
    }
    acceptedQuoteVersion = version;
    acceptedByUserId = userId;
    acceptedAt = Instant.now();
    status = ServiceRequestStatus.ACCEPTED;
    updatedAt = acceptedAt;
  }

  public void recordSignedContract(
      String objectKey,
      String fileName,
      String contentType,
      long size,
      String checksum,
      UUID uploadedBy) {
    if (status != ServiceRequestStatus.ACCEPTED || acceptedQuoteVersion == null) {
      throw new IllegalStateException("A quote must be accepted before contract upload");
    }
    if (!StringUtils.hasText(objectKey) || !StringUtils.hasText(fileName)) {
      throw new IllegalArgumentException("Contract file metadata is required");
    }
    contractObjectKey = objectKey;
    contractFileName = fileName;
    contractContentType = contentType;
    contractSizeBytes = size;
    contractChecksumSha256 = checksum;
    contractUploadedByUserId = uploadedBy;
    contractUploadedAt = Instant.now();
    status = ServiceRequestStatus.CONTRACT_SIGNED;
    updatedAt = contractUploadedAt;
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
