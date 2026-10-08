package com.smartdroneinspection.maintenance.api.dto.response;

import com.smartdroneinspection.maintenance.domain.enums.MaintenanceQuotationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MaintenanceQuotationResponse(
    UUID id,
    UUID quotationSeriesId,
    UUID maintenanceTicketId,
    UUID maintenanceAssessmentId,
    int versionNumber,
    UUID previousVersionId,
    UUID preparedByUserId,
    UUID providerId,
    Integer lockedWarrantyDays,
    String currency,
    BigDecimal subtotal,
    BigDecimal taxAmount,
    BigDecimal totalAmount,
    String pricingDetails,
    String scopeSnapshot,
    String paymentTerms,
    MaintenanceQuotationStatus status,
    Instant sentAt,
    UUID decidedByUserId,
    Instant decidedAt,
    String revisionReason,
    Instant createdAt) {}
